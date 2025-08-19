package com.example.scoreturner

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewImagesScreen(
    initialUris: List<Uri>,
    repo: WorksRepository,
    onCancel: () -> Unit,
    onSavedOpenReader: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<Uri>().apply { addAll(initialUris) } }
    var title by remember { mutableStateOf(defaultTitle("Images")) }
    var saving by remember { mutableStateOf(false) }

    val itemHeight: Dp = 96.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    Scaffold(topBar = { TopAppBar(title = { Text(t("Новое произведение из картинок")) }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(t("Название")) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text(t("Перетащите, чтобы поменять порядок:"), style = MaterialTheme.typography.titleSmall)

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(items, key = { _, u -> u.toString() }) { index, uri ->
                    val isDragging = dragIndex == index
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .background(if (isDragging) Color(0x1133B5E5) else Color.Transparent)
                            .pointerInput(index, items.size) {
                                detectDragGestures(
                                    onDragStart = { dragIndex = index; dragOffset = 0f },
                                    onDragEnd = { dragIndex = null; dragOffset = 0f },
                                    onDragCancel = { dragIndex = null; dragOffset = 0f },
                                    onDrag = { _, delta ->
                                        dragOffset += delta.y
                                        val start = index
                                        val moved = (dragOffset / itemHeightPx).toInt()
                                        val target = (start + moved).coerceIn(0, items.lastIndex)
                                        if (target != start) {
                                            items.swap(start, target)
                                            dragOffset -= (target - start) * itemHeightPx
                                        }
                                    }
                                )
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(Modifier.fillMaxSize().padding(8.dp)) {
                            AsyncImage(model = uri, contentDescription = null, modifier = Modifier.size(72.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(uri.lastPathSegment ?: t("Изображение"), modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Menu, contentDescription = null)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, enabled = !saving, modifier = Modifier.weight(1f)) { Text(t("Отмена")) }
                Button(
                    onClick = {
                        if (items.isNotEmpty()) {
                            saving = true
                            scope.launch {
                                val id = repo.createImageSetWork(items.toList(), title.ifBlank { defaultTitle("Images") })
                                onSavedOpenReader(id)
                            }
                        }
                    },
                    enabled = items.isNotEmpty() && !saving,
                    modifier = Modifier.weight(1f)
                ) { Text(t(if (saving) "Сохранение..." else "Сохранить и открыть")) }
            }
        }
    }
}

private fun <T> MutableList<T>.swap(i: Int, j: Int) {
    if (i == j) return
    val a = this[i]
    this[i] = this[j]
    this[j] = a
}

private fun defaultTitle(prefix: String): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return "$prefix ${sdf.format(Date())}"
}
