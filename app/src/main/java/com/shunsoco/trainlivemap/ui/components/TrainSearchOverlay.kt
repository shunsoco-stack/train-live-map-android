package com.shunsoco.trainlivemap.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.shunsoco.trainlivemap.data.model.TrainLocation
import com.shunsoco.trainlivemap.domain.train.searchTrainsByNumberPrefix
import com.shunsoco.trainlivemap.ui.theme.TokaidoOrange

/** Compact train-number search shown over the map. */
@Composable
fun TrainSearchOverlay(
    trains: List<TrainLocation>,
    selectedTrainId: String?,
    onTrainSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showResults by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val matches = remember(trains, query) {
        searchTrainsByNumberPrefix(trains = trains, query = query, limit = MAX_RESULTS)
    }

    fun select(train: TrainLocation) {
        query = train.trainNumber
        showResults = false
        message = null
        keyboardController?.hide()
        onTrainSelected(train.id)
    }

    fun submit() {
        val first = matches.firstOrNull()
        if (first != null) {
            select(first)
        } else if (query.isNotBlank()) {
            showResults = false
            message = NO_MATCH_MESSAGE
            keyboardController?.hide()
        }
    }

    Column(
        modifier = modifier.widthIn(max = 304.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { value ->
                query = value
                message = null
                showResults = value.isNotBlank()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TRAIN_SEARCH_FIELD_TAG)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && query.isNotBlank()) {
                        showResults = true
                    }
                }
                .semantics { contentDescription = "列車番号を検索" },
            singleLine = true,
            label = { Text("列車番号を検索") },
            placeholder = { Text("例: 1000G") },
            leadingIcon = {
                Text(
                    text = "⌕",
                    color = TokaidoOrange,
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            query = ""
                            message = null
                            showResults = false
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "列車番号の検索をクリア"
                        },
                    ) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
        )

        if (showResults && query.isNotBlank() && matches.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .testTag(TRAIN_SEARCH_RESULTS_TAG),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                shadowElevation = 8.dp,
            ) {
                Column {
                    matches.forEachIndexed { index, train ->
                        TrainSearchResult(
                            train = train,
                            selected = train.id == selectedTrainId,
                            onClick = { select(train) },
                        )
                        if (index != matches.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }

        message?.let { statusMessage ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = statusMessage,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TrainSearchResult(
    train: TrainLocation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val lineColor = remember(train.lineColor) {
        runCatching { Color(train.lineColor.toColorInt()) }.getOrDefault(TokaidoOrange)
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    append("列車番号 ")
                    append(train.trainNumber)
                    append("、")
                    append(train.lineName)
                    append("、")
                    append(train.destination.ifBlank { "行き先不明" })
                    if (train.destination.isNotBlank()) append("行き")
                }
                this.selected = selected
            },
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "●",
                color = lineColor,
                modifier = Modifier.padding(end = 10.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = train.trainNumber,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${train.lineName}・${train.destination.ifBlank { "行き先不明" }}" +
                        if (train.destination.isNotBlank()) "行き" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private const val MAX_RESULTS = 5
private const val NO_MATCH_MESSAGE = "一致する列車が見つかりません"
const val TRAIN_SEARCH_FIELD_TAG = "train_number_search"
const val TRAIN_SEARCH_RESULTS_TAG = "train_number_search_results"
