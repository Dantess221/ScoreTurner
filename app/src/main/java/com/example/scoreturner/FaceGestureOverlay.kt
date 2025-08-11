package com.example.scoreturner

import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.face.*

private const val COORDINATE_SYSTEM_VIEW_REFERENCED = 1

data class GestureConfig(
    val winkEnabled: Boolean,
    val nodEnabled: Boolean,
    val cooldownMs: Long,
    val winkClosedThr: Float,
    val winkOpenThr: Float,
    val nodDownDeltaDeg: Float,
    val nodReturnDeltaDeg: Float
)

@Composable
fun FaceGestureOverlay(
    modifier: Modifier = Modifier,
    config: GestureConfig,
    showPreview: Boolean = false,
    onWinkLeft: () -> Unit,
    onWinkRight: () -> Unit,
    onNod: () -> Unit
) {
    val ctx = LocalContext.current
    val controller = remember { LifecycleCameraController(ctx) }
    LaunchedEffect(Unit) { controller.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA }

    val faceDetector by remember {
        mutableStateOf(
            FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .enableTracking()
                    .build()
            )
        )
    }

    val engine = remember {
        BlinkNodEngine(
            enableWink = config.winkEnabled,
            enableNod = config.nodEnabled,
            cooldownMs = config.cooldownMs,
            winkClosedThr = config.winkClosedThr,
            winkOpenThr = config.winkOpenThr,
            nodDownDelta = config.nodDownDeltaDeg,
            nodReturnDelta = config.nodReturnDeltaDeg,
            onWinkLeft = onWinkLeft,
            onWinkRight = onWinkRight,
            onNod = onNod
        )
    }

    DisposableEffect(config) {
        engine.updateConfig(
            enableWink = config.winkEnabled,
            enableNod = config.nodEnabled,
            cooldownMs = config.cooldownMs,
            winkClosedThr = config.winkClosedThr,
            winkOpenThr = config.winkOpenThr,
            nodDownDelta = config.nodDownDeltaDeg,
            nodReturnDelta = config.nodReturnDeltaDeg
        )
        onDispose { }
    }

    DisposableEffect(Unit) {
        val executor = ContextCompat.getMainExecutor(ctx)
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(
                listOf(faceDetector),
                COORDINATE_SYSTEM_VIEW_REFERENCED,
                executor
            ) { result ->
                val faces = result?.getValue(faceDetector) ?: emptyList<Face>()
                engine.onFaces(faces)
            }
        )
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            faceDetector.close()
        }
    }

    val viewModifier = if (showPreview) modifier else modifier.size(1.dp).alpha(0f)
    AndroidView(modifier = viewModifier, factory = { context ->
        PreviewView(context).apply {
            controller.bindToLifecycle(context as ComponentActivity)
            this.controller = controller
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    })
}

private class BlinkNodEngine(
    enableWink: Boolean,
    enableNod: Boolean,
    cooldownMs: Long,
    winkClosedThr: Float,
    winkOpenThr: Float,
    nodDownDelta: Float,
    nodReturnDelta: Float,
    private val onWinkLeft: () -> Unit,
    private val onWinkRight: () -> Unit,
    private val onNod: () -> Unit
) {
    private var lastTriggerMs = 0L

    private var enableWink = enableWink
    private var enableNod = enableNod
    private var cooldownMs = cooldownMs
    private var winkClosed = winkClosedThr
    private var winkOpen = winkOpenThr
    private var nodDownDelta = nodDownDelta
    private var nodReturnDelta = nodReturnDelta

    private var baselinePitch: Float? = null
    private var nodDown = false

    fun updateConfig(
        enableWink: Boolean,
        enableNod: Boolean,
        cooldownMs: Long,
        winkClosedThr: Float,
        winkOpenThr: Float,
        nodDownDelta: Float,
        nodReturnDelta: Float
    ) {
        this.enableWink = enableWink
        this.enableNod = enableNod
        this.cooldownMs = cooldownMs
        this.winkClosed = winkClosedThr
        this.winkOpen = winkOpenThr
        this.nodDownDelta = nodDownDelta
        this.nodReturnDelta = nodReturnDelta
    }

    fun onFaces(faces: List<Face>) {
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return
        val now = System.currentTimeMillis()
        fun ready() = now - lastTriggerMs > cooldownMs
        fun fire(block: () -> Unit) { if (ready()) { lastTriggerMs = now; block() } }

        val l = face.leftEyeOpenProbability ?: 1f
        val r = face.rightEyeOpenProbability ?: 1f

        if (enableWink) {
            if (l < winkClosed && r > winkOpen) { fire(onWinkLeft); return }
            if (r < winkClosed && l > winkOpen) { fire(onWinkRight); return }
        }

        if (enableNod) {
            val pitch = face.headEulerAngleX
            if (baselinePitch == null) baselinePitch = pitch
            val base = baselinePitch!!
            val down = base - pitch
            if (!nodDown && down > nodDownDelta) { nodDown = true }
            if (nodDown && kotlin.math.abs(pitch - base) < nodReturnDelta) { nodDown = false; fire(onNod) }
        }
    }
}
