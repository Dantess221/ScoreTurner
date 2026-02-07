package com.example.scoreturner

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    var editMode by remember { mutableStateOf(false) }
    var eraserMode by remember { mutableStateOf(false) }
    var brushSize by remember { mutableStateOf(6f) }
    var brushColor by remember { mutableStateOf(Color(0xFFE53935)) }

    val inkStates = remember { mutableStateMapOf<Int, PageInkState>() }
    fun pageInkState(pageIndex: Int): PageInkState =
        inkStates.getOrPut(pageIndex) { PageInkState() }

    var zoom by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { zoomChange, panChange, rotationChange ->
        zoom = (zoom * zoomChange).coerceIn(0.5f, 5f)
        rotation += rotationChange
        pan += panChange
    }

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
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !editMode
                ) { pageIndex ->
                    val inkState = pageInkState(pageIndex)
                    val activePoints = remember(pageIndex) { mutableStateListOf<Offset>() }
                    val strokeWidthPx = with(LocalDensity.current) { brushSize.dp.toPx() }
                    val eraserRadiusPx = strokeWidthPx * 1.4f

                    Box(
                        Modifier
                            .fillMaxSize()
                            .transformable(state = transformableState, enabled = editMode)
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    rotationZ = rotation
                                    translationX = pan.x
                                    translationY = pan.y
                                }
                        ) {
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
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = 0.99f }
                                    .pointerInput(editMode, eraserMode, brushColor, brushSize, pageIndex) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            if (!editMode) return@awaitEachGesture
                                            activePoints.clear()
                                            activePoints.add(down.position)
                                            var isMultiTouch = false
                                            val pointerId = down.id
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.changes.size > 1) {
                                                    isMultiTouch = true
                                                    break
                                                }
                                                val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                                                if (change.pressed) {
                                                    activePoints.add(change.position)
                                                    change.consume()
                                                } else {
                                                    break
                                                }
                                            }
                                            if (!isMultiTouch && activePoints.size > 1) {
                                                if (eraserMode) {
                                                    val removed = findStrokesToErase(inkState.strokes, activePoints, eraserRadiusPx)
                                                    inkState.removeStrokes(removed)
                                                } else {
                                                    inkState.addStroke(
                                                        InkStroke(
                                                            points = activePoints.toList(),
                                                            color = brushColor,
                                                            width = strokeWidthPx
                                                        )
                                                    )
                                                }
                                            }
                                            activePoints.clear()
                                        }
                                    }
                            ) {
                                inkState.strokes.forEach { stroke ->
                                    val path = stroke.toPath()
                                    drawPath(
                                        path = path,
                                        color = stroke.color,
                                        style = Stroke(
                                            width = stroke.width,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                                if (activePoints.isNotEmpty() && !eraserMode) {
                                    val activeStroke = InkStroke(activePoints.toList(), brushColor, strokeWidthPx)
                                    drawPath(
                                        path = activeStroke.toPath(),
                                        color = activeStroke.color,
                                        style = Stroke(
                                            width = activeStroke.width,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
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
                        FilledTonalButton(onClick = {
                            editMode = !editMode
                            if (!editMode) {
                                eraserMode = false
                            }
                        }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = t(if (editMode) "Правка ✓" else "Правка")
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
                        FilledTonalButton(onClick = {
                            controlsVisible = true
                            editMode = !editMode
                            if (!editMode) {
                                eraserMode = false
                            }
                        }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = t(if (editMode) "Правка ✓" else "Правка")
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
                if (editMode) {
                    val currentInkState = pageInkState(pagerState.currentPage)
                    EditToolbar(
                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                        inkState = currentInkState,
                        brushSize = brushSize,
                        onBrushSizeChange = { brushSize = it },
                        brushColor = brushColor,
                        onBrushColorChange = {
                            brushColor = it
                            eraserMode = false
                        },
                        eraserMode = eraserMode,
                        onEraserModeChange = { eraserMode = it }
                    )
                }
                if (cameraGranted && settings.useFaceGestures) {
                    FaceGestureOverlay(
                        config = GestureConfig(
                            winkLeftEnabled = settings.winkEnabled && settings.winkLeftEnabled,
                            winkRightEnabled = settings.winkEnabled && settings.winkRightEnabled,
                            smileEnabled = settings.smileEnabled,
                            nodUpEnabled = settings.nodEnabled && settings.nodUpEnabled,
                            nodDownEnabled = settings.nodEnabled && settings.nodDownEnabled,
                            cooldownMs = settings.cooldownMs.toLong(),
                            winkClosedThr = settings.winkClosedThreshold.toFloat(),
                            winkOpenThr = settings.winkOpenThreshold.toFloat(),
                            smileThreshold = settings.smileThreshold.toFloat(),
                            nodDownDeltaDeg = settings.nodDownDeltaDeg.toFloat(),
                            nodReturnDeltaDeg = settings.nodReturnDeltaDeg.toFloat()
                        ),
                        onWinkLeft = onPrev,
                        onWinkRight = onNext,
                        onSmile = onNext,
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

@Composable
private fun EditToolbar(
    modifier: Modifier = Modifier,
    inkState: PageInkState,
    brushSize: Float,
    onBrushSizeChange: (Float) -> Unit,
    brushColor: Color,
    onBrushColorChange: (Color) -> Unit,
    eraserMode: Boolean,
    onEraserModeChange: (Boolean) -> Unit
) {
    val colors = listOf(
        Color(0xFFE53935),
        Color(0xFF1E88E5),
        Color(0xFF43A047),
        Color(0xFF000000),
        Color(0xFFFFB300)
    )
    Surface(
        modifier = modifier,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { inkState.undo() }, enabled = inkState.canUndo) {
                    Icon(Icons.Default.Undo, contentDescription = t("Отменить"))
                }
                IconButton(onClick = { inkState.redo() }, enabled = inkState.canRedo) {
                    Icon(Icons.Default.Redo, contentDescription = t("Вернуть"))
                }
                IconToggleButton(checked = eraserMode, onCheckedChange = onEraserModeChange) {
                    Icon(Icons.Default.AutoFixOff, contentDescription = t("Ластик"))
                }
                Text(t("Толщина"), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = brushSize,
                    onValueChange = onBrushSizeChange,
                    valueRange = 2f..20f,
                    steps = 17,
                    modifier = Modifier.width(140.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colors.forEach { color ->
                    val selected = color == brushColor && !eraserMode
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer { alpha = if (eraserMode) 0.4f else 1f }
                            .padding(2.dp)
                            .clickable(enabled = !eraserMode) { onBrushColorChange(color) }
                    ) {
                        Surface(
                            color = color,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxSize(),
                            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {}
                    }
                }
            }
        }
    }
}

private data class InkStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float
) {
    fun toPath(): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { point -> path.lineTo(point.x, point.y) }
        return path
    }
}

private sealed class InkAction {
    data class Add(val stroke: InkStroke) : InkAction()
    data class Remove(val strokes: List<InkStroke>) : InkAction()
}

private class PageInkState {
    val strokes = mutableStateListOf<InkStroke>()
    private val undoStack = mutableStateListOf<InkAction>()
    private val redoStack = mutableStateListOf<InkAction>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()
    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun addStroke(stroke: InkStroke) {
        strokes.add(stroke)
        undoStack.add(InkAction.Add(stroke))
        redoStack.clear()
    }

    fun removeStrokes(strokesToRemove: List<InkStroke>) {
        if (strokesToRemove.isEmpty()) return
        strokes.removeAll(strokesToRemove)
        undoStack.add(InkAction.Remove(strokesToRemove))
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val action = undoStack.removeAt(undoStack.lastIndex)
        when (action) {
            is InkAction.Add -> strokes.remove(action.stroke)
            is InkAction.Remove -> strokes.addAll(action.strokes)
        }
        redoStack.add(action)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val action = redoStack.removeAt(redoStack.lastIndex)
        when (action) {
            is InkAction.Add -> strokes.add(action.stroke)
            is InkAction.Remove -> strokes.removeAll(action.strokes)
        }
        undoStack.add(action)
    }
}

private fun findStrokesToErase(
    strokes: List<InkStroke>,
    eraserPoints: List<Offset>,
    radiusPx: Float
): List<InkStroke> {
    if (strokes.isEmpty() || eraserPoints.isEmpty()) return emptyList()
    val radiusSquared = radiusPx * radiusPx
    return strokes.filter { stroke ->
        stroke.points.any { point ->
            eraserPoints.any { eraserPoint ->
                val dx = point.x - eraserPoint.x
                val dy = point.y - eraserPoint.y
                dx * dx + dy * dy <= radiusSquared
            }
        }
    }
}
