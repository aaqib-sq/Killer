package dev.killer.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Collections
import java.util.HashSet
import java.util.LinkedHashSet

/**
 * Accessibility service for Killer.
 *
 * Extracted from TRMX-GUI's AppKillerAccessibilityService (PR #70, 09c8f722):
 * the observation/persistence half is unchanged. What changed is the action it
 * exposes: [dismissFromRecents] dismisses a card on the Recents screen instead of
 * the old Settings-driven force-stop.
 *
 * Privacy guarantee:
 * STRICTLY ON-DEVICE ONLY. Accessibility events and observed foreground packages are processed
 * solely in local device memory and local private storage. ZERO network calls, zero uploads,
 * zero analytics, zero ad SDKs, and zero telemetry. No data leaves the device.
 */
class AppKillerAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        initStorage(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null && (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED)) {
            val pkg = event.packageName?.toString()?.trim()
            if (!pkg.isNullOrEmpty() && !AppCategories.isCore(pkg)) {
                recordObservedPackage(this, pkg)
            }
        }
        // Dispatched if active listener is registered (for automated kill loop)
        eventListener?.invoke(event)
    }

    override fun onInterrupt() {
        // Required callback when accessibility service is interrupted
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
        }
        eventListener = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        eventListener = null
    }

    /**
     * Dismisses the Recents card for [label] / [packageName].
     *
     * Returns true only when a matching card was found AND a dismiss gesture was
     * dispatched. The caller counts real dismissals, so a false here is never
     * reported to the user as "closed".
     */
    fun dismissFromRecents(label: String, packageName: String): Boolean {
        val overview = openRecents() ?: return false
        val card = findCard(overview, label, packageName) ?: return false
        return swipeAway(card)
    }

    /** Opens Recents and returns its root node, or null when it never appeared. */
    private fun openRecents(): AccessibilityNodeInfo? {
        val current = rootInActiveWindow
        if (current != null && looksLikeRecents(current)) return current

        performGlobalAction(GLOBAL_ACTION_RECENTS)
        // The overview animates in; give the window tree a moment to populate.
        Thread.sleep(RECENTS_SETTLE_MS)

        val root = rootInActiveWindow ?: return null
        return if (looksLikeRecents(root)) root else null
    }

    private fun looksLikeRecents(root: AccessibilityNodeInfo): Boolean {
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg.contains("launcher") || pkg.contains("systemui") || pkg.contains("overview")) return true

        // Structure fallback: overview exposes dismiss/clear-all affordances.
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var inspected = 0
        while (stack.isNotEmpty() && inspected < 400) {
            val node = stack.removeFirst()
            inspected++
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            if (desc.contains("dismiss") || desc.contains("clear all") ||
                desc.contains("remove from recents")
            ) {
                return true
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { stack.add(it) }
        }
        return false
    }

    /** Finds the overview card whose label matches. Launchers expose "Label, …" per card. */
    private fun findCard(
        root: AccessibilityNodeInfo,
        label: String,
        packageName: String,
    ): AccessibilityNodeInfo? {
        val wanted = label.lowercase().trim()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val text = node.text?.toString()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val labelHit = wanted.isNotEmpty() &&
                (text.trim() == wanted || desc.trim() == wanted || desc.startsWith("$wanted,"))
            val pkgHit = packageName.isNotEmpty() &&
                (text.contains(packageName) || desc.contains(packageName))
            if (labelHit || pkgHit) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { stack.add(it) }
        }
        return null
    }

    /** Swipes the card upward — the same gesture a person makes, on the card's own bounds. */
    private fun swipeAway(card: AccessibilityNodeInfo): Boolean {
        val cardBounds = Rect()
        card.getBoundsInScreen(cardBounds)
        if (cardBounds.width() <= 0 || cardBounds.height() <= 0) return false

        val path = Path().apply {
            moveTo(cardBounds.centerX().toFloat(), cardBounds.centerY().toFloat())
            lineTo(cardBounds.centerX().toFloat(), (cardBounds.top - cardBounds.height()).toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, SWIPE_MS))
            .build()

        val dispatched = dispatchGesture(gesture, null, null)
        if (dispatched) Thread.sleep(SWIPE_MS + RECENTS_SETTLE_MS)
        return dispatched
    }

    companion object {
        const val RECENTS_SETTLE_MS = 420L
        const val SWIPE_MS = 240L

        private const val PREFS_NAME = "killer_observed"
        private const val KEY_OBSERVED_PACKAGES = "observed_packages"

        @Volatile
        var instance: AppKillerAccessibilityService? = null
            private set

        @Volatile
        var eventListener: ((AccessibilityEvent?) -> Unit)? = null

        val isRunning: Boolean
            get() = instance != null

        private val observedPackages = Collections.synchronizedSet(LinkedHashSet<String>())
        @Volatile
        private var storageInitialized = false

        fun initStorage(context: Context) {
            if (storageInitialized) return
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val saved = prefs.getStringSet(KEY_OBSERVED_PACKAGES, null)
                if (saved != null) {
                    observedPackages.addAll(saved)
                }
                storageInitialized = true
            } catch (_: Exception) {
            }
        }

        fun recordObservedPackage(context: Context?, packageName: String) {
            val pkg = packageName.trim()
            if (pkg.isEmpty() || AppCategories.isCore(pkg)) return
            if (observedPackages.add(pkg)) {
                persistStorage(context)
            }
        }

        fun removeObservedPackage(context: Context?, packageName: String) {
            val pkg = packageName.trim()
            if (observedPackages.remove(pkg)) {
                persistStorage(context)
            }
        }

        fun getObservedPackages(context: Context): Set<String> {
            initStorage(context)
            val inst = instance
            if (inst != null) {
                try {
                    val root = inst.rootInActiveWindow
                    val rootPkg = root?.packageName?.toString()?.trim()
                    if (!rootPkg.isNullOrEmpty() && !AppCategories.isCore(rootPkg)) {
                        recordObservedPackage(context, rootPkg)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        inst.windows?.forEach { win ->
                            val p = win.root?.packageName?.toString()?.trim()
                            if (!p.isNullOrEmpty() && !AppCategories.isCore(p)) {
                                recordObservedPackage(context, p)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            return synchronized(observedPackages) {
                HashSet(observedPackages)
            }
        }

        fun pruneObservedPackages(context: Context?, validPackages: Set<String>) {
            var changed = false
            synchronized(observedPackages) {
                val it = observedPackages.iterator()
                while (it.hasNext()) {
                    val p = it.next()
                    if (!validPackages.contains(p) || AppCategories.isCore(p)) {
                        it.remove()
                        changed = true
                    }
                }
            }
            if (changed) {
                persistStorage(context)
            }
        }

        private fun persistStorage(context: Context?) {
            if (context == null) return
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val copy = synchronized(observedPackages) { HashSet(observedPackages) }
                prefs.edit().putStringSet(KEY_OBSERVED_PACKAGES, copy).apply()
            } catch (_: Exception) {
            }
        }
    }
}
