package com.lumen.studio

import android.app.Activity

/** The offline edition never links, initializes or requests an advertising SDK. */
internal object AdsRuntime {
    fun available(id: String) = false
    fun start(activity: Activity) = Unit
    fun banner(slot: AdBannerSlot) = Unit
    fun destroyBanner(slot: AdBannerSlot) = Unit
    fun afterExport(activity: Activity, stillVisible: () -> Boolean) = Unit
    fun reward(activity: Activity, id: String, done: (Boolean) -> Unit) { done(false) }
    fun privacy(activity: Activity) { android.widget.Toast.makeText(activity, "Advertising is not enabled in this build.", android.widget.Toast.LENGTH_SHORT).show() }
}
