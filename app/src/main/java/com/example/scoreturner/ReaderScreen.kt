package com.example.scoreturner

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import com.example.scoreturner.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    workId: Long,
    settings: Settings,
    repo: WorksRepository,
    openSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val workWithPages by repo.observeWorkWithPages(workId).collectAsState(initial = null)

    var cameraGranted by remember { mutableStateOf(false) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }

    var pageCount by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { max(pageCount, 1) })

    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var fullScreen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(fullScreen, controlsVisible) {
        if (fullScreen && controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    LaunchedEffect(workId) { repo.touchWork(workId) }

    LaunchedEffect(workWithPages) {
        val w = workWithPages?.work ?: return@LaunchedEffect
        pageCount = when (w.type) {
            WorkType.PDF -> {
                val uri = Uri.parse(w.sourceUri)
                PdfPageCache.getPageCount(ctx, uri) ?: 1
            }
            WorkType.IMAGE_SET -> workWithPages?.pages?.size ?: 1
        }
    }

    LaunchedEffect(workWithPages, pagerState.currentPage) {
        val w = workWithPages?.work ?: return@LaunchedEffect
        if (w.type == WorkType.PDF) {
            try {
                val bmp = withContext(Dispatchers.IO) {
                    PdfPageCache.getPage(ctx, Uri.parse(w.sourceUri), pagerState.currentPage, scale = 3)
                }
                currentBitmap = bmp
                errorMessage = null
            } catch (e: Exception) {
                currentBitmap = null
                errorMessage = e.stackTraceToString()
            }
        }
    }

    val onNext: () -> Unit = {
        scope.launch {
            if (pagerState.currentPage < pagerState.pageCount - 1) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }
    val onPrev: () -> Unit = {
        scope.launch {
            if (pagerState.currentPage > 0) {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            if (!fullScreen) {
                TopAppBar(title = { Text(workWithPages?.work?.title ?: t("Произведение")) }, actions = {
                    IconButton(onClick = openSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }
                })
            }
        }
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .clickable(
                    enabled = fullScreen,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { controlsVisible = true }
        ) {
            val w = workWithPages?.work
            if (w == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIndex ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (w.type) {
                            WorkType.PDF -> {
                                currentBitmap?.let {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            WorkType.IMAGE_SET -> {
                                val pages = workWithPages!!.pages.sortedBy { it.sortOrder }
                                val uri = pages.getOrNull(pageIndex)?.uri?.toUri()
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                if (!fullScreen) {
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(onClick = onPrev) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                contentDescription = t("Назад")
                            )
                        }
                        FilledTonalButton(onClick = onNext) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_forward),
                                contentDescription = t("Вперёд")
                            )
                        }
                        FilledTonalButton(onClick = {
                            if (cameraGranted) cameraGranted = false
                            else askCamera.launch(Manifest.permission.CAMERA)
                        }) {
                            Icon(
                                painterResource(R.drawable.ic_camera),
                                contentDescription = t(if (cameraGranted) "Камера ✓" else "Камера"),
                                tint = if (cameraGranted) Color(0xFF4CAF50) else LocalContentColor.current
                            )
                        }
                        FilledTonalButton(onClick = { fullScreen = true; controlsVisible = false }) {
                            Icon(
                                painterResource(R.drawable.ic_fullscreen),
                                contentDescription = t("На весь экран")
                            )
                        }
                    }
                } else if (controlsVisible) {
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(onClick = { onPrev(); controlsVisible = true }) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                contentDescription = t("Назад")
                            )
                        }
                        FilledTonalButton(onClick = { onNext(); controlsVisible = true }) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_forward),
                                contentDescription = t("Вперёд")
                            )
                        }
                        FilledTonalButton(onClick = {
                            controlsVisible = true
                            if (cameraGranted) cameraGranted = false
                            else askCamera.launch(Manifest.permission.CAMERA)
                        }) {
                            Icon(
                                painterResource(R.drawable.ic_camera),
                                contentDescription = t(if (cameraGranted) "Камера ✓" else "Камера"),
                                tint = if (cameraGranted) Color(0xFF4CAF50) else LocalContentColor.current
                            )
                        }
                        FilledTonalButton(onClick = { fullScreen = false; controlsVisible = false }) {
                            Icon(
                                painterResource(R.drawable.ic_fullscreen_exit),
                                contentDescription = t("Выйти из полноэкранного режима")
                            )
                        }
                    }
                }
                if (cameraGranted && settings.useFaceGestures) {
                    FaceGestureOverlay(
                        config = GestureConfig(
                            winkLeftEnabled = settings.winkEnabled && settings.winkLeftEnabled,
                            winkRightEnabled = settings.winkEnabled && settings.winkRightEnabled,
                            nodUpEnabled = settings.nodEnabled && settings.nodUpEnabled,
                            nodDownEnabled = settings.nodEnabled && settings.nodDownEnabled,
                            cooldownMs = settings.cooldownMs.toLong(),
                            winkClosedThr = settings.winkClosedThreshold.toFloat(),
                            winkOpenThr = settings.winkOpenThreshold.toFloat(),
                            nodDownDeltaDeg = settings.nodDownDeltaDeg.toFloat(),
                            nodReturnDeltaDeg = settings.nodReturnDeltaDeg.toFloat()
                        ),
                        onWinkLeft = onPrev,
                        onWinkRight = onNext,
                        onNodUp = onPrev,
                        onNodDown = onNext
                    )
                }
                if (errorMessage != null) {
                    val clipboard = LocalClipboardManager.current
                    AlertDialog(
                        onDismissRequest = { errorMessage = null },
                        title = { Text(t("Ошибка")) },
                        text = { Text(errorMessage!!) },
                        confirmButton = {
                            TextButton(onClick = { clipboard.setText(AnnotatedString(errorMessage!!)) }) {
                                Text(t("Копировать"))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { errorMessage = null }) { Text(t("Закрыть")) }
                        }
                    )
                }
            }
        }
    }
}
