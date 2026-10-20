package com.lumen.studio

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.IdentityHashMap

/** AdMob owns the auction; partner-specific routes are AdMob units mapped in mediation groups. */
internal object AdsRuntime {
    private var consentRequested = false
    private var consentReady = false
    private var initializing = false
    private var initialized = false
    private var fullscreenBusy = false
    private var shown = 0
    private var exports = 0
    private var lastShown = Long.MIN_VALUE
    private var adFreeUntil = 0L
    private data class Banner(val key: String, val view: AdView)
    private val banners = IdentityHashMap<AdBannerSlot, Banner>()
    private fun alive(a: Activity) = !a.isFinishing && !a.isDestroyed
    private fun Context.activity(): Activity? = when(this) { is Activity -> this; is ContextWrapper -> baseContext.takeIf { it !== this }?.activity(); else -> null }
    private fun muted() = SystemClock.elapsedRealtime() < adFreeUntil
    private fun ready() = initialized && consentReady && !muted()
    private fun request() = AdRequest.Builder().addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter::class.java, Bundle().apply { putString("npa", "1"); putInt("rdp",1) }).build()
    private fun unit(p: AdPlacement, c: AdsConfiguration): String = if(c.testMode || BuildConfig.DEBUG || BuildConfig.ADS_TEST_APP) when(p.type) {
        "banner" -> "ca-app-pub-3940256099942544/6300978111"
        "interstitial" -> "ca-app-pub-3940256099942544/1033173712"
        "rewarded" -> "ca-app-pub-3940256099942544/5224354917"
        else -> "ca-app-pub-3940256099942544/9257395921"
    } else p.unit
    private fun placement(id: String): Pair<AdsConfiguration, AdPlacement>? {
        val c = AdsController.configuration() ?: return null
        val p = c.placements[id] ?: return null
        return if(c.enabled && p.enabled && ready()) c to p else null
    }
    fun start(activity: Activity) {
        if(!alive(activity) || AdsController.configuration()?.enabled != true) return
        if(initialized) { AdsController.changed(); return }
        if(consentRequested) return
        consentRequested = true
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            if(!alive(activity)) { consentRequested = false; return@requestConsentInfoUpdate }
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                consentReady = info.canRequestAds()
                if(consentReady && alive(activity)) initialize(activity) else consentRequested = false
            }
        }, { consentRequested = false; consentReady = false; AdsController.changed() })
    }
    private fun initialize(activity: Activity) {
        if(initializing || initialized) return
        initializing = true
        // UMP writes TCF/Additional Consent signals read by the bundled mediation adapters.
        // Requests are additionally restricted to non-personalized Google ads.
        // Conservative US data-processing defaults, independent of the dashboard switches.
        com.applovin.sdk.AppLovinPrivacySettings.setDoNotSell(true,activity)
        com.facebook.ads.AdSettings.setDataProcessingOptions(arrayOf("LDU"),0,0)
        com.unity3d.mediation.LevelPlay.setMetaData("do_not_sell","true")
        com.unity3d.ads.metadata.MetaData(activity).apply { set("privacy.consent",false); commit() }
        MobileAds.initialize(activity.applicationContext) {
            activity.runOnUiThread {
                initializing = false; initialized = true; AdsController.changed()
                if(activity is MainActivity && activity.canShowLaunchAd()) launchAd(activity)
            }
        }
    }
    fun banner(slot: AdBannerSlot) {
        val pair = placement(slot.placementId)
        val activity = slot.context.activity()
        if(pair == null || activity == null || !alive(activity) || !slot.isAttachedToWindow || activity.resources.configuration.screenWidthDp < 320) { destroyBanner(slot); return }
        val (c,p) = pair
        val key = "${c.version}:${unit(p,c)}"
        if(banners[slot]?.key == key) return
        destroyBanner(slot)
        val view = AdView(activity).apply {
            adUnitId = unit(p,c)
            // Fixed standard sizes also work with AppLovin waterfall and Unity/ironSource.
            setAdSize(if(activity.resources.configuration.screenWidthDp >= 728) AdSize.LEADERBOARD else AdSize.BANNER)
            adListener = object: AdListener() {
                override fun onAdLoaded() {
                    if(banners[slot]?.view !== this@apply) return
                    if(slot.isAttachedToWindow && placement(slot.placementId) != null) slot.visibility = View.VISIBLE
                    else destroyBanner(slot)
                }
                override fun onAdFailedToLoad(error: LoadAdError) { if(banners[slot]?.view === this@apply) destroyBanner(slot) }
                override fun onAdImpression() { AdminConnection.event(activity,"ad_impression",p.id) }
            }
        }
        banners[slot] = Banner(key,view)
        slot.addView(view, android.widget.FrameLayout.LayoutParams(-2,-2,android.view.Gravity.CENTER))
        view.loadAd(request())
    }
    fun destroyBanner(slot: AdBannerSlot) { banners.remove(slot)?.view?.destroy(); slot.removeAllViews(); slot.visibility = View.GONE }
    private fun mayShow(c: AdsConfiguration) = !fullscreenBusy && !muted() && shown < c.maxPerSession && (lastShown == Long.MIN_VALUE || SystemClock.elapsedRealtime()-lastShown >= c.cooldownSeconds*1000L)
    private fun shown() { shown++; lastShown = SystemClock.elapsedRealtime() }
    fun afterExport(activity: Activity, stillVisible: () -> Boolean) {
        exports++
        val (c,p) = placement("export_complete") ?: return
        if(exports % c.everyNExports != 0 || !mayShow(c) || !alive(activity) || !stillVisible()) return
        fullscreenBusy = true
        InterstitialAd.load(activity,unit(p,c),request(),object: InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(error: LoadAdError) { fullscreenBusy = false }
            override fun onAdLoaded(ad: InterstitialAd) {
                if(!alive(activity) || !stillVisible() || AdsController.configuration()?.version != c.version || placement(p.id) == null) { fullscreenBusy = false; return }
                ad.fullScreenContentCallback = object: FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() { shown() }
                    override fun onAdImpression() { AdminConnection.event(activity,"ad_impression",p.id) }
                    override fun onAdDismissedFullScreenContent() { fullscreenBusy = false }
                    override fun onAdFailedToShowFullScreenContent(error: AdError) { fullscreenBusy = false }
                }
                ad.show(activity)
            }
        })
    }
    fun reward(activity: Activity, id: String, done: (Boolean) -> Unit) {
        val pair = placement(id)
        if(pair == null || pair.second.type != "rewarded" || !mayShow(pair.first) || !alive(activity)) { done(false); return }
        val (c,p) = pair; fullscreenBusy = true
        RewardedAd.load(activity,unit(p,c),request(),object: RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(error: LoadAdError) { fullscreenBusy = false; done(false) }
            override fun onAdLoaded(ad: RewardedAd) {
                if(!alive(activity) || !activity.hasWindowFocus() || AdsController.configuration()?.version != c.version || placement(id) == null) { fullscreenBusy = false; done(false); return }
                var earned = false
                ad.fullScreenContentCallback = object: FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() { shown() }
                    override fun onAdImpression() { AdminConnection.event(activity,"ad_impression",p.id) }
                    override fun onAdDismissedFullScreenContent() { fullscreenBusy = false; done(earned) }
                    override fun onAdFailedToShowFullScreenContent(error: AdError) { fullscreenBusy = false; done(false) }
                }
                ad.show(activity) { earned = true; adFreeUntil = SystemClock.elapsedRealtime()+30*60_000; AdsController.changed() }
            }
        })
    }
    private fun launchAd(activity: MainActivity) {
        val (c,p) = placement("app_open") ?: return
        if(!activity.canShowLaunchAd() || !mayShow(c)) return
        fullscreenBusy = true
        AppOpenAd.load(activity,unit(p,c),request(),object: AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdFailedToLoad(error: LoadAdError) { fullscreenBusy = false }
            override fun onAdLoaded(ad: AppOpenAd) {
                if(!alive(activity) || !activity.canShowLaunchAd() || AdsController.configuration()?.version != c.version || placement(p.id) == null) { fullscreenBusy = false; return }
                ad.fullScreenContentCallback = object: FullScreenContentCallback() {
                    override fun onAdShowedFullScreenContent() { shown(); activity.holdLaunchForAd(true) }
                    override fun onAdImpression() { AdminConnection.event(activity,"ad_impression",p.id) }
                    override fun onAdDismissedFullScreenContent() { fullscreenBusy = false; activity.holdLaunchForAd(false) }
                    override fun onAdFailedToShowFullScreenContent(error: AdError) { fullscreenBusy = false; activity.holdLaunchForAd(false) }
                }
                ad.show(activity)
            }
        })
    }
    fun privacy(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        if(info.privacyOptionsRequirementStatus != com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            Toast.makeText(activity,"No advertising privacy form is required for this region.",Toast.LENGTH_LONG).show(); return
        }
        consentReady = false; AdsController.changed()
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            consentReady = error == null && info.canRequestAds(); AdsController.changed()
            if(error != null) Toast.makeText(activity,"Privacy choices unavailable. Please try again.",Toast.LENGTH_LONG).show()
        }
    }
}
