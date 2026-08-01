package com.shunsoco.trainlivemap.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.shunsoco.trainlivemap.data.local.UserPreferences
import com.shunsoco.trainlivemap.data.model.RailwayCoverage
import com.shunsoco.trainlivemap.data.model.RailwayFilterOption
import com.shunsoco.trainlivemap.domain.railway.filterRailways
import com.shunsoco.trainlivemap.ui.theme.TokaidoOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RailwayFilterSheet(
    options: List<RailwayFilterOption>,
    preferences: UserPreferences,
    loading: Boolean,
    onDismiss: () -> Unit,
    onVisibleChanged: (String, Boolean) -> Unit,
    onFavoriteChanged: (String, Boolean) -> Unit,
    onFavoritesOnlyChanged: (Boolean) -> Unit,
    onVisibleLinesChanged: (Set<String>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(
        options,
        query,
        preferences.favoritesOnly,
        preferences.favoriteLineIds,
    ) {
        filterRailways(
            options = options,
            query = query,
            favoriteOnly = preferences.favoritesOnly,
            favoriteLineIds = preferences.favoriteLineIds,
        )
    }
    val grouped = remember(filtered) { filtered.groupBy(RailwayFilterOption::category) }
    val availableIds = remember(options) {
        options.filter(RailwayFilterOption::available).mapTo(linkedSetOf(), RailwayFilterOption::id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "表示する路線",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${preferences.visibleLineIds.count { it in availableIds }} / " +
                            "${availableIds.size} 路線を選択",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("閉じる")
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .testTag("railway_search"),
                singleLine = true,
                label = { Text("路線名を検索") },
                leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleLarge) },
            )

            FilterChip(
                selected = preferences.favoritesOnly,
                onClick = { onFavoritesOnlyChanged(!preferences.favoritesOnly) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("favorites_only"),
                label = {
                    Text(
                        "★ お気に入りのみ (${preferences.favoriteLineIds.size})" +
                            if (preferences.favoritesOnly) " — 地図にも適用中" else "",
                    )
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onVisibleLinesChanged(availableIds) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TokaidoOrange),
                ) {
                    Text("すべて表示", color = Color(0xFF27140A))
                }
                OutlinedButton(
                    onClick = { onVisibleLinesChanged(emptySet()) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("すべて隠す")
                }
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                if (loading) {
                    item {
                        Text(
                            text = "利用可能な路線を確認中…",
                            modifier = Modifier.padding(vertical = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                grouped.forEach { (category, categoryOptions) ->
                    item(key = "header-$category") {
                        Text(
                            text = category,
                            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    items(
                        items = categoryOptions,
                        key = RailwayFilterOption::id,
                    ) { option ->
                        RailwayOptionRow(
                            option = option,
                            visible = option.id in preferences.visibleLineIds,
                            favorite = option.id in preferences.favoriteLineIds,
                            onVisibleChanged = onVisibleChanged,
                            onFavoriteChanged = onFavoriteChanged,
                        )
                    }
                }

                if (!loading && filtered.isEmpty()) {
                    item {
                        Text(
                            text = if (
                                preferences.favoritesOnly &&
                                preferences.favoriteLineIds.isEmpty()
                            ) {
                                "右端の☆からお気に入りを登録できます。"
                            } else {
                                "該当する路線がありません。"
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun RailwayOptionRow(
    option: RailwayFilterOption,
    visible: Boolean,
    favorite: Boolean,
    onVisibleChanged: (String, Boolean) -> Unit,
    onFavoriteChanged: (String, Boolean) -> Unit,
) {
    val lineColor = remember(option.color) {
        runCatching { Color(option.color.toColorInt()) }
            .getOrDefault(TokaidoOrange)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (option.available) 1f else 0.58f),
        shape = RoundedCornerShape(14.dp),
        color = if (visible) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.64f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(lineColor, CircleShape)
                    .semantics { contentDescription = "${option.name}の路線カラー" },
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = option.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = option.coverageDescription(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Checkbox(
                checked = visible,
                enabled = option.available,
                onCheckedChange = { onVisibleChanged(option.id, it) },
                modifier = Modifier
                    .testTag("route_toggle_${option.id}")
                    .semantics {
                        contentDescription = "${option.name}を地図に表示"
                        stateDescription = when {
                            !option.available -> "利用不可"
                            visible -> "表示中"
                            else -> "非表示"
                        }
                    },
            )
            IconButton(
                onClick = { onFavoriteChanged(option.id, !favorite) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("favorite_${option.id}")
                    .semantics {
                        selected = favorite
                        stateDescription = if (favorite) "お気に入り登録済み" else "未登録"
                        contentDescription = if (favorite) {
                            "${option.name}をお気に入りから解除"
                        } else {
                            "${option.name}をお気に入りに登録"
                        }
                    },
            ) {
                Text(
                    text = if (favorite) "★" else "☆",
                    color = if (favorite) Color(0xFFFFC107) else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

private fun RailwayFilterOption.coverageDescription(): String {
    val coverageLabel = when (coverage) {
        RailwayCoverage.REALTIME -> "リアルタイム対応"
        RailwayCoverage.LIMITED -> "一部区間のみ対応"
        RailwayCoverage.UNAVAILABLE -> "列車位置情報の対象外"
        RailwayCoverage.UNKNOWN -> "提供範囲を確認中"
    }
    val availability = if (available) "" else "・現在は地図表示できません"
    val note = coverageNote?.takeIf(String::isNotBlank)?.let { "・$it" }.orEmpty()
    return coverageLabel + availability + note
}
