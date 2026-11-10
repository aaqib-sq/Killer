package dev.killer.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.killer.ui.KillerUiState
import dev.killer.ui.components.OutlineButton
import dev.killer.ui.theme.KillerColors
import dev.killer.ui.theme.KillerDimens
import dev.killer.ui.theme.KillerTheme
import dev.killer.ui.theme.KillerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * "Display over other apps" permission, checked the real way: the system
 * decides, the app only asks. Never gates closing — see [ClosingOverlayController].
 */
fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

/** Opens the system overlay-permission page for this app. */
fun openOverlayPermissionSettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", context.packageName, null),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

/** The overlay's view of the ViewModel progress state. One source of truth. */
data class OverlayUiState(
    val progress: Float = 0f,
    val index: Int = 0,
    val total: Int = 0,
    val label: String = "",
)

/**
 * Slice 3 — the Hibernator-style progress overlay.
 *
 * The Closing screen is a normal in-app screen, so the Settings app that the
 * engine opens for each app covers it for the whole loop. This controller
 * draws the same progress (ring + % + current app + queue position + Cancel)
 * as a small centred [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]
 * card that floats above Settings for the whole closing loop.
 *
 * - The card mirrors the ViewModel's [KillerUiState] progress fields
 *   (`ringProgress`, `closingIndex`/`closingTotal`, `closingLabel`) by
 *   collecting the same StateFlow the Closing screen reads. No second copy
 *   of the progress logic exists here.
 * - The window is WRAP_CONTENT and centred, with FLAG_NOT_TOUCH_MODAL, so
 *   touches outside the card reach the Settings screens underneath and the
 *   accessibility automation keeps receiving its taps. Only the card itself
 *   (and its Cancel button) takes touches.
 * - [showAndAwait] returns only after the card is attached and laid out, so
 *   no Settings intent fires before the progress UI is up.
 * - [hide] is idempotent and is called on every exit path (Done, Cancel,
 *   ViewModel cleared, exception). If the process dies, the system removes
 *   the window with it — the card can never be left behind.
 * - Without the overlay permission, [showAndAwait] returns false and closing
 *   proceeds exactly as before (in-app Closing screen, Settings during).
 */
class ClosingOverlayController(
    context: Context,
    private val state: StateFlow<KillerUiState>,
    private val onCancel: () -> Unit,
) {
    private val appContext: Context = context.applicationContext
    private val windowManager: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val uiState = mutableStateOf(OverlayUiState())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var overlayView: View? = null
    private var collectJob: Job? = null

    /**
     * Shows the card and suspends until it is drawn (or a short timeout
     * elapses, so closing can never hang on the overlay). Returns false when
     * the overlay is unavailable and the caller must use the fallback.
     */
    suspend fun showAndAwait(): Boolean = withContext(Dispatchers.Main) {
        if (overlayView != null) return@withContext true
        if (!hasOverlayPermission(appContext)) return@withContext false
        try {
            val view = ComposeView(appContext)
            view.setContent {
                KillerTheme {
                    val ui by uiState
                    OverlayCard(ui = ui, onCancel = onCancel)
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.CENTER }
            windowManager.addView(view, params)
            overlayView = view
            collectJob = scope.launch {
                state.collect { s ->
                    uiState.value = OverlayUiState(
                        progress = s.ringProgress,
                        index = s.closingIndex,
                        total = s.closingTotal,
                        label = s.closingLabel,
                    )
                }
            }
            awaitFirstLayout(view)
            true
        } catch (_: Exception) {
            hide()
            false
        }
    }

    /** Removes the card. Safe to call repeatedly, from any thread. */
    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { hide() }
            return
        }
        collectJob?.cancel()
        collectJob = null
        val view = overlayView
        overlayView = null
        if (view != null) {
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // Already removed — nothing to clean up.
            }
        }
    }

    private suspend fun awaitFirstLayout(view: View) {
        withTimeoutOrNull(1500) {
            suspendCancellableCoroutine<Unit> { cont ->
                var resumed = false
                fun resumeOnce() {
                    if (!resumed && !cont.isCompleted) {
                        resumed = true
                        cont.resume(Unit)
                    }
                }
                val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() = resumeOnce()
                }
                view.post {
                    view.viewTreeObserver.addOnGlobalLayoutListener(listener)
                    if (view.width > 0 && view.height > 0) resumeOnce()
                }
                cont.invokeOnCancellation {
                    view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                }
            }
        }
    }
}

/**
 * The centred overlay card: ring + % + current app + queue position + Cancel.
 * Same tokens as the in-app Closing screen (Surface, Coral, 24dp card),
 * sized as a card rather than a full screen.
 */
@Composable
private fun OverlayCard(ui: OverlayUiState, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(KillerDimens.CardRadius))
            .background(KillerColors.Surface)
            .border(1.dp, KillerColors.Border, RoundedCornerShape(KillerDimens.CardRadius))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
            OverlayRing(progress = ui.progress)
            Text(
                text = overlayPercentText(ui.progress),
                color = KillerColors.TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = KillerType.TightTracking.sp,
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = overlayCurrentAppText(ui.label),
            color = KillerColors.TextPrimary,
            fontSize = KillerType.CardTitle,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = overlayPositionText(ui.index, ui.total),
            color = KillerColors.TextSecondary,
            fontSize = KillerType.Pill,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        OutlineButton(label = "Cancel", onClick = onCancel, height = 56)
    }
}

/** Coral ring, same geometry as the Closing screen's ring, scaled to the card. */
@Composable
private fun OverlayRing(progress: Float) {
    Canvas(modifier = Modifier.size(120.dp)) {
        val stroke = 10.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = KillerColors.Surface2,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = KillerColors.Coral,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
