package com.lumen.studio

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import java.lang.ref.WeakReference

/** Shared boundary for SDK-free and monetized builds. All SDK calls run on the main thread. */
object AdsController {
    private val main = Handler(Looper.getMainLooper())
    private var current: AdsConfiguration? = null
    private var receivedAt = 0L
    private var fetching = false
    private var foreground: WeakReference<Activity>? = null
    private val poll = object: Runnable {
        override fun run() { foreground?.get()?.takeIf { !it.isDestroyed && !it.isFinishing }?.let { refresh(it); main.postDelayed(this,60_000) } }
    }
    fun resumed(activity: Activity) {
        foreground=WeakReference(activity); main.removeCallbacks(poll)
        if(TermsConsent.accepted(activity)) { refresh(activity); main.postDelayed(poll,60_000) }
    }
    fun paused(activity: Activity) { if(foreground?.get()===activity) { foreground=null; main.removeCallbacks(poll) } }
    private val listeners = mutableListOf<WeakReference<AdBannerSlot>>()
    val sdkEnabled get() = BuildConfig.ADS_ENABLED
    fun configuration(): AdsConfiguration? = current?.takeIf { android.os.SystemClock.elapsedRealtime()-receivedAt < 15*60_000 }
    fun refresh(activity: Activity) {
        if (!AdminConnection.configured || fetching) return
        fetching = true
        AdminConnection.fetchAdConfiguration(activity) { result ->
            fetching = false
            result.onSuccess { current = it; receivedAt = android.os.SystemClock.elapsedRealtime() }
                .onFailure { current = null; receivedAt = 0 }
            if (activity is MainActivity && !activity.isDestroyed && !activity.isFinishing && activity.hasWindowFocus() && TermsConsent.accepted(activity)) AdsRuntime.start(activity)
            listeners.removeAll { it.get() == null }
            listeners.forEach { it.get()?.reload() }
        }
    }
    fun banner(slot: AdBannerSlot) { listeners.removeAll { it.get()==null || it.get()===slot }; listeners.add(WeakReference(slot)); slot.reload() }
    fun detached(slot: AdBannerSlot) { listeners.removeAll { it.get()==null || it.get()===slot }; AdsRuntime.destroyBanner(slot) }
    fun available(id: String) = sdkEnabled && configuration()?.let { it.enabled && it.placements[id]?.enabled == true } == true
    fun afterExport(activity: Activity, stillVisible: () -> Boolean) = AdsRuntime.afterExport(activity, stillVisible)
    fun reward(activity: Activity, id: String, done: (Boolean) -> Unit) = AdsRuntime.reward(activity, id, done)
    fun privacy(activity: Activity) = AdsRuntime.privacy(activity)
    fun changed() { main.post { listeners.forEach { it.get()?.reload() } } }
    fun status(): String = if (!sdkEnabled) "Ads SDK disabled in this build" else configuration()?.let { "Published ads configuration v${it.version} received · ${if(it.testMode) "test" else "live"} mode" } ?: "Ads unavailable · configuration not received"
}

/** Occupies space only after a real banner loads. Detached slots always release their native ad. */
class AdBannerSlot(context: Context, val placementId: String) : FrameLayout(context) {
    init { visibility = GONE; contentDescription = "Advertisement" }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); AdsController.banner(this) }
    fun reload() { AdsRuntime.banner(this) }
    override fun onDetachedFromWindow() { AdsController.detached(this); super.onDetachedFromWindow() }
}
