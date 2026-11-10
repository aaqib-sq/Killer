package dev.killer.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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

/** Log tag for the overlay + progress path (read with `adb logcat -s Killer`). */
private const val TAG = "Killer"

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
 * The overlay window lives outside any Activity, so Compose has no lifecycle
 * owner to find in the view tree. Without these three owners a ComposeView
 * crashes on attach (IllegalStateException: ViewTreeLifecycleOwner not found)
 * the moment Close is pressed with the overlay permission granted — that was
 * the Slice 3 crash. This owner is created per show and destroyed on hide.
 */
private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    override val lifecycle: LifecycleRegistry = LifecycleRegistry(this)
    override val viewModelStore: ViewModelStore = ViewModelStore()
    private val savedStateRegistryController: SavedStateRegistryController =
        SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    init {
        savedStateRegistryController.performAttach()
    }

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart() = lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

    fun onResume() = lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

    fun onDestroy() {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}

/**
 * Slice 3 — the Hibernator-style progress overlay. Slice 6 — full screen.
 *
 * The Closing screen is a normal in-app screen, so the Settings app that the
 * engine opens for each app covers it for the whole loop. This controller
 * draws the same progress (ring + % + current app + queue position + dots +
 * Cancel) as a full-screen opaque [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]
 * window that covers the whole display — status bar included — for the whole
 * closing loop, so the Settings screens opening underneath are never seen.
 *
 * - The window mirrors the ViewModel's [KillerUiState] progress fields
 *   (`ringProgress`, `closingIndex`/`closingTotal`, `closingLabel`) by
 *   collecting the same StateFlow the Closing screen reads. No second copy
 *   of the progress logic exists here.
 * - The window is MATCH_PARENT and opaque, with FLAG_NOT_TOUCH_MODAL, so
 *   touches outside the Cancel button reach the Settings screens underneath
 *   and the accessibility automation keeps receiving its taps. Only the
 *   Cancel button takes touches.
 * - [showAndAwait] returns only after the window is attached and laid out, so
 *   no Settings intent fires before the progress UI is up.
 * - The window is a ComposeView in a WindowManager window, outside any Activity,
 *   so [showAndAwait] gives it its own [OverlayLifecycleOwner] (lifecycle +
 *   ViewModelStore + SavedStateRegistry) before adding the window. Without
 *   those owners Compose crashes on attach — the Slice 3 Close crash.
 * - [hide] is idempotent and is called on every exit path (Done, Cancel,
 *   ViewModel cleared, exception). If the process dies, the system removes
 *   the window with it — the window can never be left behind.
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
    private var overlayOwner: OverlayLifecycleOwner? = null
    private var collectJob: Job? = null

    /**
     * Shows the window and suspends until it is drawn (or a short timeout
     * elapses, so closing can never hang on the overlay). Returns false when
     * the overlay is unavailable and the caller must use the fallback.
     */
    suspend fun showAndAwait(): Boolean = withContext(Dispatchers.Main) {
        if (overlayView != null) return@withContext true
        val permitted = hasOverlayPermission(appContext)
        Log.d(TAG, "showAndAwait: overlay permission granted=$permitted")
        if (!permitted) return@withContext false
        try {
            val view = ComposeView(appContext)
            // The window has no Activity, so Compose needs its owners set
            // explicitly — otherwise it crashes on attach (Slice 3 bug).
            val owner = OverlayLifecycleOwner()
            overlayOwner = owner
            view.setViewTreeLifecycleOwner(owner)
            view.setViewTreeViewModelStoreOwner(owner)
            view.setViewTreeSavedStateRegistryOwner(owner)
            owner.onCreate()
            owner.onStart()
            view.setContent {
                KillerTheme {
                    val ui by uiState
                    OverlayCard(ui = ui, onCancel = onCancel)
                }
            }
            // Slice 6: full-screen opaque cover. MATCH_PARENT in both axes,
            // FLAG_LAYOUT_IN_SCREEN + SHORT_EDGES so the window also covers
            // the status bar and the cutout area — the Settings screens
            // opening underneath must never be seen. OPAQUE, not translucent:
            // the content fills the window with the app background.
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE,
            ).apply {
                gravity = Gravity.FILL
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            windowManager.addView(view, params)
            owner.onResume()
            overlayView = view
            var lastLoggedIndex = -1
            var lastLoggedLabel = ""
            collectJob = scope.launch {
                state.collect { s ->
                    uiState.value = OverlayUiState(
                        progress = s.ringProgress,
                        index = s.closingIndex,
                        total = s.closingTotal,
                        label = s.closingLabel,
                    )
                    // Log only when the queue actually moves: the ring easing
                    // would otherwise flood logcat on every animation frame.
                    if (s.closingIndex != lastLoggedIndex || s.closingLabel != lastLoggedLabel) {
                        lastLoggedIndex = s.closingIndex
                        lastLoggedLabel = s.closingLabel
                        Log.d(TAG, "overlay progress: app ${s.closingIndex}/${s.closingTotal} ${s.closingLabel}")
                    }
                }
            }
            awaitFirstLayout(view)
            Log.d(TAG, "showAndAwait: window shown")
            true
        } catch (_: Exception) {
            Log.d(TAG, "showAndAwait: failed, falling back to in-app screen")
            hide("show-failed")
            false
        }
    }

    /** Removes the window. Safe to call repeatedly, from any thread. */
    fun hide(reason: String = "unknown") {
        Log.d(TAG, "hide: $reason")
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { hide(reason) }
            return
        }
        collectJob?.cancel()
        collectJob = null
        overlayOwner?.onDestroy()
        overlayOwner = null
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
 * The full-screen closing overlay: ring + % + current app + queue position +
 * dots + Cancel, on the opaque app background. It covers the whole display
 * so the Settings screens the engine opens are never visible while closing.
 * Same tokens and same running layout as the in-app Closing screen.
 */
@Composable
private fun OverlayCard(ui: OverlayUiState, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KillerColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = KillerDimens.PagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        Text(
            text = "Closing apps",
            color = KillerColors.TextSecondary,
            fontSize = KillerType.Pill,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )

        Spacer(Modifier.height(32.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(KillerDimens.RingSize)) {
            OverlayRing(progress = ui.progress)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = overlayPercentText(ui.progress),
                    color = KillerColors.TextPrimary,
                    fontSize = KillerType.ClosingPercent,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = KillerType.TightTracking.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Closing…",
                    color = KillerColors.TextSecondary,
                    fontSize = KillerType.Pill,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = overlayCurrentAppText(ui.label),
            color = KillerColors.TextPrimary,
            fontSize = KillerType.Headline,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = KillerType.TightTracking.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 31.sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = overlayPositionText(ui.index, ui.total),
            color = KillerColors.TextSecondary,
            fontSize = KillerType.Pill,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        OverlaySeekLine(progress = ui.progress)

        Spacer(Modifier.weight(1f))

        OutlineButton(label = "Cancel", onClick = onCancel, height = 56)

        Spacer(Modifier.height(20.dp))
    }
}

/**
 * Seek line (Slice 7 — replaces the dots): a full-width track with a Coral
 * fill proportional to the ring progress, like a video seek bar. The queue
 * position stays as the "App X of Y" text above it.
 */
@Composable
private fun OverlaySeekLine(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(KillerColors.Surface2),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(KillerColors.Coral),
        )
    }
}

/** Coral ring, same geometry as the Closing screen's ring (220dp, 14dp stroke). */
@Composable
private fun OverlayRing(progress: Float) {
    Canvas(modifier = Modifier.size(KillerDimens.RingSize)) {
        val stroke = 14.dp.toPx()
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
