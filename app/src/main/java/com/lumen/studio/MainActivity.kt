package com.lumen.studio

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class MainActivity : BaseActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var phase = "splash"
    private var page = 0
    private var tab = "home"
    private var choiceSelection: String?=null
    private val preferences by lazy { getSharedPreferences("onboarding", MODE_PRIVATE) }
    private val installedVersion: Long by lazy {
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }
    private val finishSplash = Runnable {
        if (preferences.getLong("completed_version", -1) > 0 && TermsConsent.accepted(this)) showEditor()
        else showOnboarding()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = (savedInstanceState?.getInt("page", 0) ?: 0).coerceIn(0,OnboardingView.PAGE_COUNT-1)
        phase = savedInstanceState?.getString("phase") ?: "splash"
        tab = savedInstanceState?.getString("tab") ?: "home"
        choiceSelection=savedInstanceState?.getString("choice_selection")
        if(intent.getBooleanExtra("from_reminder",false)) tab = "home"
        Reminders.configure(this)
        AdminConnection.opened(applicationContext)
        when (phase) {
            "onboarding" -> showOnboarding()
            "editor" -> showEditor()
            else -> {
                setContentView(LumenSplashView(this))
                handler.postDelayed(finishSplash, 400)
            }
        }
    }

    private fun showOnboarding() {
        if(!TermsConsent.accepted(this)) page=0
        phase = "onboarding"
        setContentView(OnboardingView(this, page,
            onNext = {
                if(!TermsConsent.accepted(this)) return@OnboardingView
                if (page < OnboardingView.PAGE_COUNT-1) { page++; showOnboarding() } else completeOnboarding()
            },
            onSkip = { completeOnboarding() },
            onBack = if(preferences.getLong("completed_version",-1)>0 && TermsConsent.accepted(this)) ({ tab="settings"; showEditor() }) else null))
    }

    private fun completeOnboarding() {
        if(!TermsConsent.accepted(this)) { showOnboarding(); return }
        preferences.edit().putLong("completed_version", installedVersion).apply()
        showEditor()
        AdsController.resumed(this)
    }
    fun canShowLaunchAd() = phase=="splash" && preferences.getLong("completed_version",-1)>0 && TermsConsent.accepted(this) && hasWindowFocus()
    fun holdLaunchForAd(hold: Boolean) { handler.removeCallbacks(finishSplash); if(!hold && !isDestroyed && phase=="splash") handler.post(finishSplash) }

    private fun showEditor() {
        if(!TermsConsent.accepted(this)) { page=0; showOnboarding(); return }
        phase = "editor"
        when(tab) {
            "projects" -> setContentView(ProjectsView(this,::navigate))
            "saved" -> setContentView(ProjectsView(this,::navigate))
            "settings" -> setContentView(SettingsView(this,::navigate))
            "notifications" -> setContentView(NotificationsView(this,::navigate))
            "theme", "languages" -> setContentView(AppearanceChoicesView(this,tab=="theme",::navigate).apply { restoreDraft(choiceSelection) })
            "privacy" -> setContentView(PrivacyPolicyView(this,::navigate))
            "about" -> setContentView(AboutUsView(this,::navigate))
            else -> setContentView(HomeView(this,::navigate))
        }
    }
    private fun navigate(action: String) {
        if(action=="introduction") { page=0; showOnboarding(); return }
        if(action=="terms") { startActivity(Intent(this,TermsActivity::class.java)); return }
        if(action=="contact") { ContactUs.show(this); return }
        if(action=="diagnostics") { startActivity(Intent(this,PlaybackCheckActivity::class.java)); return }
        if(action in listOf("home","projects","saved","settings","notifications","theme","languages","privacy","about")) {
            choiceSelection=null;tab = action; showEditor(); return
        }
        if(action=="ai_regrade") { android.widget.Toast.makeText(this,Ui.t(this,"Coming soon"),android.widget.Toast.LENGTH_SHORT).show(); return }
        if(action.startsWith("open:")) { val id=action.removePrefix("open:"); val target=if(ProjectStore.load(this,id)?.state?.photoEditor==true) PhotoEditorActivity::class.java else EditorActivity::class.java; startActivity(Intent(this,target).putExtra("project_id",id)); return }
            val mode = when (action) {
                "new_video" -> "all"
                "edit_photo" -> "photo"
                else -> null
            }
            if (mode != null) {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(this, MediaPickerActivity::class.java).putExtra("mode", mode).putExtra("direct_selection",true), if(mode=="photo") 101 else 100)
            }
    }
    private fun selectedAppearance()=findViewById<AppearanceChoicesView>(if(tab=="theme") R.id.theme_choices else R.id.language_choices)?.draftSelection
    override fun onResume() { super.onResume(); if(phase == "editor") { choiceSelection=selectedAppearance() ?: choiceSelection;showEditor() }; if(TermsConsent.accepted(this)) AdsController.refresh(this) }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if(intent.getBooleanExtra("from_reminder",false) && phase == "editor") { tab = "home"; showEditor() }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if(code == 400 || code == 401) {
            val granted = results.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED
            if(code == 400) { Reminders.setEnabled(this,granted); Reminders.configure(this) }
            if(code == 401 && granted) Reminders.show(this)
            showEditor()
        }
    }

    @Deprecated("Platform activity results used without AndroidX dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode == 100 || requestCode == 101) && resultCode == RESULT_OK) {
            val uris = data?.getStringArrayListExtra("uris") ?: return
            if(uris.isEmpty()) return
            val types=data.getBooleanArrayExtra("videos")
            val photosOnly=if(types?.size==uris.size) types.none { it } else uris.none { TimelineEngine.isVideo(this,it) }
            if(photosOnly) AdminConnection.event(applicationContext,"image_edit_started")
            startActivity(Intent(this, if(photosOnly) PhotoEditorActivity::class.java else EditorActivity::class.java)
                .putStringArrayListExtra("uris", uris)
                .putStringArrayListExtra("names", data.getStringArrayListExtra("names")))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("choice_selection",selectedAppearance() ?: choiceSelection)
        outState.putString("phase", phase)
        outState.putInt("page", page)
        outState.putString("tab",tab)
        super.onSaveInstanceState(outState)
    }

    override fun handleBack() {
        if(phase == "editor" && tab != "home") { tab = if(tab in listOf("theme","languages","privacy","about")) "settings" else "home"; showEditor() }
        else if (phase == "onboarding" && page > 0) { page--; showOnboarding() }
        else super.handleBack()
    }

    override fun onDestroy() {
        handler.removeCallbacks(finishSplash)
        super.onDestroy()
    }
}


