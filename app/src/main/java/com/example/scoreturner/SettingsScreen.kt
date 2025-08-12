package com.example.scoreturner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    repo: SettingsRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var selectedTab by remember { mutableStateOf(0) }
    val categories = listOf("Общие", "Подмигивания", "Кивки", "Прочее")
    val categoryIndices = mapOf(0 to 0, 1 to 3, 2 to 9, 3 to 15)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Настройки") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
        }) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                categories.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = {
                            selectedTab = idx
                            scope.launch { listState.animateScrollToItem(categoryIndices[idx]!!) }
                        },
                        text = { Text(title) }
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Text("Общие", style = MaterialTheme.typography.titleLarge) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Тёмная тема", modifier = Modifier.weight(1f))
                        Switch(checked = settings.darkTheme, onCheckedChange = { v -> scope.launch { repo.setDarkTheme(v) } })
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Листать мимикой", modifier = Modifier.weight(1f))
                        Switch(checked = settings.useFaceGestures, onCheckedChange = { v -> scope.launch { repo.setUseFaceGestures(v) } })
                    }
                }

                item { Text("Подмигивания", style = MaterialTheme.typography.titleLarge) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Подмигивания", modifier = Modifier.weight(1f))
                        Switch(checked = settings.winkEnabled, enabled = settings.useFaceGestures, onCheckedChange = { v -> scope.launch { repo.setWinkEnabled(v) } })
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Правый глаз → вперёд", modifier = Modifier.weight(1f))
                        Switch(checked = settings.winkRightEnabled, enabled = settings.useFaceGestures && settings.winkEnabled, onCheckedChange = { v -> scope.launch { repo.setWinkRightEnabled(v) } })
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Левый глаз → назад", modifier = Modifier.weight(1f))
                        Switch(checked = settings.winkLeftEnabled, enabled = settings.useFaceGestures && settings.winkEnabled, onCheckedChange = { v -> scope.launch { repo.setWinkLeftEnabled(v) } })
                    }
                }
                item {
                    LabeledSlider(
                        "Порог закрытия глаза",
                        "Считаем глаз «закрытым», если ниже порога",
                        settings.winkClosedThreshold.toFloat(),
                        0.05f..0.5f,
                        0.01f,
                        settings.useFaceGestures && settings.winkEnabled && (settings.winkLeftEnabled || settings.winkRightEnabled)
                    ) { v -> scope.launch { repo.setWinkClosedThr(v.toDouble()) } }
                }
                item {
                    LabeledSlider(
                        "Порог открытого глаза",
                        "Второй глаз должен быть «открыт» выше порога",
                        settings.winkOpenThreshold.toFloat(),
                        0.5f..0.95f,
                        0.01f,
                        settings.useFaceGestures && settings.winkEnabled && (settings.winkLeftEnabled || settings.winkRightEnabled)
                    ) { v -> scope.launch { repo.setWinkOpenThr(v.toDouble()) } }
                }

                item { Text("Кивки", style = MaterialTheme.typography.titleLarge) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Кивки", modifier = Modifier.weight(1f))
                        Switch(checked = settings.nodEnabled, enabled = settings.useFaceGestures, onCheckedChange = { v -> scope.launch { repo.setNodEnabled(v) } })
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Вниз → вперёд", modifier = Modifier.weight(1f))
                        Switch(checked = settings.nodDownEnabled, enabled = settings.useFaceGestures && settings.nodEnabled, onCheckedChange = { v -> scope.launch { repo.setNodDownEnabled(v) } })
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Вверх → назад", modifier = Modifier.weight(1f))
                        Switch(checked = settings.nodUpEnabled, enabled = settings.useFaceGestures && settings.nodEnabled, onCheckedChange = { v -> scope.launch { repo.setNodUpEnabled(v) } })
                    }
                }
                item {
                    LabeledSlider(
                        "Чувствительность кивка (Δ°,)",
                        "Насколько сильно отклонить голову",
                        settings.nodDownDeltaDeg.toFloat(),
                        5f..30f,
                        1f,
                        settings.useFaceGestures && settings.nodEnabled && (settings.nodDownEnabled || settings.nodUpEnabled)
                    ) { v -> scope.launch { repo.setNodDownDelta(v.roundToInt()) } }
                }
                item {
                    LabeledSlider(
                        "Возврат к базе (°)",
                        "Насколько вернуться к исходному углу",
                        settings.nodReturnDeltaDeg.toFloat(),
                        3f..20f,
                        1f,
                        settings.useFaceGestures && settings.nodEnabled && (settings.nodDownEnabled || settings.nodUpEnabled)
                    ) { v -> scope.launch { repo.setNodReturnDelta(v.roundToInt()) } }
                }

                item { Text("Прочее", style = MaterialTheme.typography.titleLarge) }
                item {
                    LabeledSlider(
                        "Антидребезг, мс",
                        "Минимальная пауза между срабатываниями",
                        settings.cooldownMs.toFloat(),
                        300f..2000f,
                        50f,
                        settings.useFaceGestures
                    ) { v -> scope.launch { repo.setCooldownMs(v.roundToInt()) } }
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value,
                onValueChange = { onChange(it) },
                valueRange = range,
                steps = ((range.endInclusive - range.start) / step).toInt() - 1,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = when {
                    title.contains("мс") -> "${value.toInt()} ms"
                    title.contains("°") -> "${value.toInt()}°"
                    else -> String.format("%.2f", value)
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
