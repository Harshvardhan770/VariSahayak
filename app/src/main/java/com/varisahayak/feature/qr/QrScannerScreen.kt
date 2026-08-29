package com.varisahayak.feature.qr

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varisahayak.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.component.GlassSurface
import com.varisahayak.core.designsystem.component.LoadingState
import com.varisahayak.core.designsystem.component.VariPrimaryButton
import com.varisahayak.core.designsystem.component.VariSecondaryButton
import com.varisahayak.core.permissions.AppPermissions
import com.varisahayak.core.permissions.PermissionPermanentlyDeniedDialog
import com.varisahayak.core.permissions.PermissionRationaleDialog
import com.varisahayak.core.permissions.rememberPermissionController
import com.varisahayak.domain.model.QrToken

private const val TAG = "QrScannerScreen"

/**
 * Scans a Varkari's SOS Bridge tag.
 *
 * Manual entry is a first-class path, not a fallback buried in a menu. Tags get scuffed,
 * soaked, and torn on a three-week walk, and a camera that cannot read one must never be
 * the reason somebody does not get help. The same applies to the camera permission: deny
 * it and the manual field is still right there.
 */
@Composable
fun QrScannerScreen(
    onTokenAccepted: (QrToken) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QrScannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissions = rememberPermissionController(AppPermissions.CAMERA)

    var showRationale by remember { mutableStateOf(false) }
    var showPermanentlyDenied by remember { mutableStateOf(false) }
    val analyzer = remember { QrAnalyzer(onQrCode = viewModel::onCodeScanned) }

    DisposableEffect(Unit) {
        onDispose { analyzer.close() }
    }

    // Re-arm the analyzer whenever a result is cleared, so a rejected tag can be retried
    // without leaving and re-entering the screen.
    LaunchedEffect(uiState.outcome) {
        if (uiState.outcome == null) analyzer.resume()
    }

    LaunchedEffect(permissions.state) {
        when {
            permissions.state.isAnyGranted -> Unit
            permissions.isPermanentlyDenied -> showPermanentlyDenied = true
            !permissions.state.hasBeenRequested -> showRationale = true
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (permissions.state.isAnyGranted) {
            CameraPreview(
                analyzer = analyzer,
                torchEnabled = uiState.torchEnabled,
                modifier = Modifier.fillMaxSize(),
            )

            ScannerViewport(modifier = Modifier.fillMaxSize())

            GlassSurface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.ScreenPadding)
                    .size(Dimens.MinTouchTarget)
                    .clip(MaterialTheme.shapes.large)
                    .clickable(onClick = viewModel::toggleTorch),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (uiState.torchEnabled) {
                            Icons.Filled.FlashOn
                        } else {
                            Icons.Filled.FlashOff
                        },
                        contentDescription = stringResource(
                            if (uiState.torchEnabled) R.string.cd_torch_off else R.string.cd_torch_on,
                        ),
                        tint = VariTheme.colors.textPrimary,
                    )
                }
            }
        }

        GlassSurface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(Dimens.FloatingInset),
        ) {
            Column(
                modifier = Modifier.padding(Dimens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
            ) {
                Text(
                    text = stringResource(
                        if (permissions.state.isAnyGranted) {
                            R.string.qr_scan_hint
                        } else {
                            R.string.permission_camera_denied
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VariTheme.colors.textSecondary,
                )

                VariSecondaryButton(
                    text = stringResource(R.string.qr_manual_entry),
                    onClick = { viewModel.setManualEntryOpen(true) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (uiState.isResolving) {
            Surface(modifier = Modifier.fillMaxSize()) { LoadingState() }
        }
    }

    if (uiState.isManualEntryOpen) {
        ManualEntryDialog(
            value = uiState.manualEntry,
            onValueChange = viewModel::onManualEntryChanged,
            onSubmit = viewModel::submitManualEntry,
            onDismiss = { viewModel.setManualEntryOpen(false) },
        )
    }

    uiState.outcome?.let { outcome ->
        ScanOutcomeDialog(
            outcome = outcome,
            onContinue = { token ->
                viewModel.dismissOutcome()
                onTokenAccepted(token)
            },
            onDismiss = viewModel::dismissOutcome,
        )
    }

    if (showRationale) {
        PermissionRationaleDialog(
            title = stringResource(R.string.permission_camera_title),
            rationale = stringResource(R.string.permission_camera_rationale),
            onConfirm = {
                showRationale = false
                permissions.request()
            },
            onDismiss = { showRationale = false },
        )
    }

    if (showPermanentlyDenied) {
        PermissionPermanentlyDeniedDialog(
            title = stringResource(R.string.permission_camera_title),
            message = stringResource(R.string.permission_camera_denied),
            onOpenSettings = {
                showPermanentlyDenied = false
                permissions.openAppSettings()
            },
            onDismiss = { showPermanentlyDenied = false },
        )
    }
}

/**
 * CameraX preview bound to the composable's lifecycle.
 *
 * Uses PreviewView through AndroidView rather than camera-compose's viewfinder: this is
 * the long-stable surface and it keeps the binding logic explicit.
 */
@Composable
private fun CameraPreview(
    analyzer: QrAnalyzer,
    torchEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()

                    val preview = Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analysis = ImageAnalysis.Builder()
                        // Drop stale frames rather than queueing them: a backlog makes the
                        // scanner feel laggy and delays the moment of recognition.
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .apply {
                            setAnalyzer(ContextCompat.getMainExecutor(context), analyzer)
                        }

                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                } catch (error: Exception) {
                    // A camera that will not bind is recoverable — manual entry remains.
                    Log.w(TAG, "Camera binding failed; manual entry still available", error)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    LaunchedEffect(torchEnabled, camera) {
        val control = camera?.cameraControl ?: return@LaunchedEffect
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            control.enableTorch(torchEnabled)
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
private fun ManualEntryDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qr_manual_entry)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(stringResource(R.string.qr_manual_entry_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onSubmit) { Text(stringResource(R.string.action_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ScanOutcomeDialog(
    outcome: ScanOutcome,
    onContinue: (QrToken) -> Unit,
    onDismiss: () -> Unit,
) {
    val token = when (outcome) {
        is ScanOutcome.Resolved -> outcome.token
        is ScanOutcome.ResolvedOffline -> outcome.token
        else -> null
    }

    val message = when (outcome) {
        is ScanOutcome.Resolved -> outcome.subjectReference
        is ScanOutcome.ResolvedOffline -> stringResource(R.string.qr_resolved_offline)
        ScanOutcome.NotRecognised -> stringResource(R.string.qr_not_recognised)
        ScanOutcome.Malformed -> stringResource(R.string.qr_not_recognised)
        ScanOutcome.ContainsPersonalData -> stringResource(R.string.qr_contains_personal_data)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sos_bridge_title)) },
        text = { Text(message) },
        confirmButton = {
            if (token != null) {
                VariPrimaryButton(
                    text = stringResource(R.string.sos_bridge_create),
                    onClick = { onContinue(token) },
                )
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_retry)) }
            }
        },
        dismissButton = {
            if (token != null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/**
 * The scanning target.
 *
 * Two jobs, and only two. It tells the volunteer where to point the camera, and it darkens
 * everything outside that square so the tag is the brightest thing on screen — which is
 * also what makes the framing readable in full sun, where a thin outline on a bright
 * preview disappears entirely.
 *
 * The cut-out is a real hole punched with [BlendMode.Clear] rather than four rectangles
 * arranged around a gap. Four rectangles have to agree with each other at every screen
 * size, and they stop agreeing the moment someone changes a padding.
 *
 * Purely decorative to the accessibility tree: there is nothing here to announce that the
 * hint text below does not already say, and the manual-entry path exists precisely so that
 * nobody has to aim a camera at all.
 */
@Composable
private fun ScannerViewport(modifier: Modifier = Modifier) {
    val bracket = VariTheme.colors.brandAccent

    Canvas(
        // Offscreen compositing is what makes BlendMode.Clear punch through the scrim
        // instead of clearing the window behind it.
        modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        // 72% of the shorter edge: big enough to frame a tag at arm's length, small enough
        // that the surround still reads as "outside the target".
        val side = minOf(size.width, size.height) * 0.72f
        val left = (size.width - side) / 2f
        // Sits slightly above centre so the frosted hint panel at the foot of the screen
        // does not crowd it.
        val top = (size.height - side) / 2f - size.height * 0.06f
        val radius = CornerRadius(VIEWPORT_CORNER_PX, VIEWPORT_CORNER_PX)

        drawRect(color = Color.Black.copy(alpha = 0.55f))

        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(side, side),
            cornerRadius = radius,
            blendMode = BlendMode.Clear,
        )

        // Corner brackets rather than a full outline. A closed rectangle competes with the
        // QR code's own quiet zone; brackets mark the corners and leave the code clean.
        val armLength = side * 0.12f
        val strokeWidth = 4.dp.toPx()
        val right = left + side
        val bottom = top + side

        val arms = listOf(
            // top-left
            Offset(left, top + armLength) to Offset(left, top),
            Offset(left, top) to Offset(left + armLength, top),
            // top-right
            Offset(right - armLength, top) to Offset(right, top),
            Offset(right, top) to Offset(right, top + armLength),
            // bottom-right
            Offset(right, bottom - armLength) to Offset(right, bottom),
            Offset(right, bottom) to Offset(right - armLength, bottom),
            // bottom-left
            Offset(left + armLength, bottom) to Offset(left, bottom),
            Offset(left, bottom) to Offset(left, bottom - armLength),
        )

        arms.forEach { (start, end) ->
            drawLine(
                color = bracket,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Corner radius of the viewport cut-out, in pixels at draw time. */
private const val VIEWPORT_CORNER_PX = 24f
