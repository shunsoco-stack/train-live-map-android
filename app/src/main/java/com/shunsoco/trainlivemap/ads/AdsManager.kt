package com.shunsoco.trainlivemap.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConsentFlowStatus {
    DISABLED,
    NOT_STARTED,
    UPDATING,
    SHOWING_FORM_IF_REQUIRED,
    COMPLETE,
    ERROR,
}

enum class PrivacyOptionsRequirement {
    UNKNOWN,
    NOT_REQUIRED,
    REQUIRED,
}

data class AdsState(
    val isConfigured: Boolean,
    val consentFlowStatus: ConsentFlowStatus,
    val canRequestAds: Boolean,
    val isMobileAdsInitialized: Boolean,
    val privacyOptionsRequirement: PrivacyOptionsRequirement,
    val errorMessage: String? = null,
) {
    val isBannerReady: Boolean
        get() = isConfigured && canRequestAds && isMobileAdsInitialized
}

/**
 * Process-scoped coordinator for UMP consent and Google Mobile Ads initialization.
 *
 * Call [requestConsent] from the foreground Activity once at app launch. It is idempotent within
 * the process, so Activity recreation does not show a second form. A new app process performs a
 * fresh consent information update as required by UMP.
 */
class AdsManager private constructor(
    context: Context,
    private val configuration: AdsConfiguration,
) {
    private val applicationContext = context.applicationContext
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(applicationContext)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val consentRequestStarted = AtomicBoolean(false)
    private val mobileAdsInitializationStarted = AtomicBoolean(false)

    private val initialPrivacyRequirement =
        if (configuration.isConfigured) {
            consentInformation.privacyOptionsRequirementStatus.toAppRequirement()
        } else {
            PrivacyOptionsRequirement.NOT_REQUIRED
        }

    private val _state =
        MutableStateFlow(
            AdsState(
                isConfigured = configuration.isConfigured,
                consentFlowStatus =
                    if (configuration.isConfigured) {
                        ConsentFlowStatus.NOT_STARTED
                    } else {
                        ConsentFlowStatus.DISABLED
                    },
                canRequestAds = false,
                isMobileAdsInitialized = false,
                privacyOptionsRequirement = initialPrivacyRequirement,
            ),
        )
    val state: StateFlow<AdsState> = _state.asStateFlow()

    private val _privacyOptionsRequirement = MutableStateFlow(initialPrivacyRequirement)

    /**
     * Observe this to decide whether the app must expose a "privacy options" entry.
     */
    val privacyOptionsRequirement: StateFlow<PrivacyOptionsRequirement> =
        _privacyOptionsRequirement.asStateFlow()

    /**
     * The banner unit to pass to [AnchoredAdaptiveBanner], or null when ads are disabled.
     */
    val bannerAdUnitId: String?
        get() = configuration.bannerAdUnitId.takeIf { configuration.isConfigured }

    /**
     * Starts the required per-launch sequence:
     * requestConsentInfoUpdate -> consent form if needed -> canRequestAds -> MobileAds.initialize.
     */
    fun requestConsent(activity: Activity) {
        if (!configuration.isConfigured) return
        if (!consentRequestStarted.compareAndSet(false, true)) return

        _state.update {
            it.copy(
                consentFlowStatus = ConsentFlowStatus.UPDATING,
                errorMessage = null,
            )
        }
        onMainThread {
            requestConsentInfoUpdate(activity)
        }
    }

    /**
     * Allows an explicit retry after an update or form error.
     *
     * Existing consent is re-read on every failure, so ads may continue when UMP confirms that
     * consent from an earlier launch is still valid.
     */
    fun retryConsent(activity: Activity) {
        if (!configuration.isConfigured) return
        if (_state.value.consentFlowStatus != ConsentFlowStatus.ERROR) return
        consentRequestStarted.set(false)
        requestConsent(activity)
    }

    /**
     * Shows UMP's privacy options form when it is required for the current user.
     *
     * The result is published through [state] and [privacyOptionsRequirement].
     */
    fun showPrivacyOptionsForm(activity: Activity) {
        if (!configuration.isConfigured) return
        if (_privacyOptionsRequirement.value != PrivacyOptionsRequirement.REQUIRED) return

        onMainThread {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                refreshPrivacyRequirement()
                val canRequestAds = consentInformation.canRequestAds()
                _state.update {
                    it.copy(
                        canRequestAds = canRequestAds,
                        errorMessage = formError?.message,
                    )
                }
                if (canRequestAds) {
                    initializeMobileAdsOnce()
                }
            }
        }
    }

    private fun requestConsentInfoUpdate(activity: Activity) {
        val parameters = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                refreshPrivacyRequirement()
                _state.update {
                    it.copy(consentFlowStatus = ConsentFlowStatus.SHOWING_FORM_IF_REQUIRED)
                }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    finishConsentFlow(formError?.message)
                }
            },
            { requestError ->
                // UMP can retain usable consent from an earlier session even when refresh fails.
                finishConsentFlow(requestError.message)
            },
        )
    }

    private fun finishConsentFlow(errorMessage: String?) {
        refreshPrivacyRequirement()
        val canRequestAds = consentInformation.canRequestAds()
        _state.update {
            it.copy(
                consentFlowStatus =
                    if (errorMessage == null || canRequestAds) {
                        ConsentFlowStatus.COMPLETE
                    } else {
                        ConsentFlowStatus.ERROR
                    },
                canRequestAds = canRequestAds,
                errorMessage = errorMessage,
            )
        }
        if (canRequestAds) {
            initializeMobileAdsOnce()
        }
    }

    private fun refreshPrivacyRequirement() {
        val requirement = consentInformation.privacyOptionsRequirementStatus.toAppRequirement()
        _privacyOptionsRequirement.value = requirement
        _state.update { it.copy(privacyOptionsRequirement = requirement) }
    }

    private fun initializeMobileAdsOnce() {
        if (!mobileAdsInitializationStarted.compareAndSet(false, true)) return

        backgroundScope.launch {
            try {
                MobileAds.initialize(applicationContext) {
                    _state.update {
                        it.copy(isMobileAdsInitialized = true)
                    }
                }
            } catch (exception: RuntimeException) {
                _state.update {
                    it.copy(
                        isMobileAdsInitialized = false,
                        errorMessage = exception.message ?: "Google Mobile Ads initialization failed",
                    )
                }
            }
        }
    }

    private fun onMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    companion object {
        @Volatile
        private var instance: AdsManager? = null

        fun getInstance(context: Context): AdsManager =
            instance
                ?: synchronized(this) {
                    instance
                        ?: AdsManager(
                            context = context,
                            configuration = AdsConfiguration.fromBuildConfig(),
                        ).also { instance = it }
                }
    }
}

private fun ConsentInformation.PrivacyOptionsRequirementStatus.toAppRequirement():
    PrivacyOptionsRequirement =
    when (this) {
        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED ->
            PrivacyOptionsRequirement.REQUIRED

        ConsentInformation.PrivacyOptionsRequirementStatus.NOT_REQUIRED ->
            PrivacyOptionsRequirement.NOT_REQUIRED

        ConsentInformation.PrivacyOptionsRequirementStatus.UNKNOWN ->
            PrivacyOptionsRequirement.UNKNOWN
    }
