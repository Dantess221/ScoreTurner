package com.example.scoreturner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    openSettings: () -> Unit,
    openReader: (Long) -> Unit,
    openNewImagesFlow: (List<Uri>) -> Unit,
    repo: WorksRepository
) {
    val recents by repo.observeRecents().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    var pendingPdf by remember { mutableStateOf<Uri?>(null) }
    var titleDialogForPdf by remember { mutableStateOf(false) }
    var titleText by remember { mutableStateOf("") }

    val openPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingPdf = uri; titleText = ""; titleDialogForPdf = true }
    }
    val openImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) openNewImagesFlow(uris)
    }
    val openFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            val id = repo.importFolderImages(uri, null)
            openReader(id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Score Turner") },
                actions = {
                    IconButton(onClick = openSettings) { Icon(Icons.Default.Settings, contentDescription = null) }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { openPdf.launch(arrayOf("application/pdf")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Открыть PDF")
                }
                FilledTonalButton(onClick = { openImages.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Из изображений")
                }
            }
            FilledTonalButton(onClick = { openFolder.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Folder, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Импорт папки (SAF)")
            }

            Text("Недавние", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            if (recents.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Пока пусто. Импортируйте произведения.") }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recents, key = { it.work.id }) { item ->
                        val thumb = remember(item.work.id, item.pages) { ThumbnailCache.getOrCreateWorkThumb(ctx, item.work, item.pages) }
                        RecentItem(item, thumbFile = thumb, onOpen = { openReader(item.work.id) }, onToggleFav = {
                            scope.launch { repo.setFavorite(item.work.id, !item.work.isFavorite) }
                        }, onRename = { newTitle -> scope.launch { repo.renameWork(item.work.id, newTitle) } },
                           onDelete = { scope.launch { repo.deleteWork(item.work) } })
                    }
                }
            }
        }
    }

    if (titleDialogForPdf && pendingPdf != null) {
        AlertDialog(
            onDismissRequest = { titleDialogForPdf = false; pendingPdf = null },
            confirmButton = {
                TextButton(onClick = {
                    val t = titleText.ifBlank { defaultTitle("PDF") }
                    scope.launch {
                        val id = repo.createPdfWork(pendingPdf!!, t); openReader(id)
                    }
                    titleDialogForPdf = false; pendingPdf = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { titleDialogForPdf = false; pendingPdf = null }) { Text("Отмена") } },
            title = { Text("Название произведения") },
            text = { OutlinedTextField(value = titleText, onValueChange = { titleText = it }, placeholder = { Text("Например: Шопен — Ноктюрн (PDF)") }, singleLine = true) }
        )
    }
}

@Composable
private fun RecentItem(
    item: WorkWithPages,
    thumbFile: java.io.File?,
    onOpen: () -> Unit,
    onToggleFav: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameTitle by remember { mutableStateOf(item.work.title) }

    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (thumbFile != null && thumbFile.exists()) {
                val bmp = androidx.compose.ui.graphics.asImageBitmap(android.graphics.BitmapFactory.decodeFile(thumbFile.absolutePath))
                Image(bmp, contentDescription = null, modifier = Modifier.size(56.dp))
            } else {
                Icon(if (item.work.type == WorkType.PDF) Icons.Default.PictureAsPdf else Icons.Default.Image, contentDescription = null)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.work.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(if (item.work.type == WorkType.PDF) "PDF" else "IMG: ${item.pages.size}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onToggleFav) { Icon(if (item.work.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = null) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; renameOpen = true })
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            confirmButton = { TextButton(onClick = { onRename(renameTitle); renameOpen = false }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Отмена") } },
            title = { Text("Переименовать") },
            text = { OutlinedTextField(value = renameTitle, onValueChange = { renameTitle = it }, singleLine = true) }
        )
    }
}

private fun defaultTitle(prefix: String): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return "$prefix ${sdf.format(Date())}"
}
