package com.lumen.studio

import android.test.InstrumentationTestCase
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView

@Suppress("DEPRECATION")
class TermsOnboardingTest: InstrumentationTestCase() {
    private fun views(view: View): List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    fun testConsentControlsContinueAndSkipInAllThemes() {
        val context=instrumentation.targetContext
        assertTrue("Run on isolated QA package",context.packageName.endsWith(".qa"))
        val theme=AppPreferences.theme(context)
        val preferences=context.getSharedPreferences("onboarding",0)
        val previous=preferences.getString("accepted_terms",null)
        try {
            for(palette in listOf("light","dark","violet","gold")) {
                AppPreferences.setTheme(context,palette)
                TermsConsent.setAccepted(context,false)
                instrumentation.runOnMainSync {
                    var continued=false; var skipped=false
                    val first=OnboardingView(context,0,{ continued=true },{ skipped=true })
                    val children=views(first)
                    val check=children.filterIsInstance<CheckBox>().single()
                    fun action(label: String)=children.filterIsInstance<TextView>().single { it.text.toString()==label }
                    assertFalse(check.isChecked)
                    assertFalse(action("Next").isEnabled); assertFalse(action("Skip").isEnabled)
                    assertTrue(action("Read Terms & Conditions").isEnabled)
                    check.performClick()
                    assertTrue(TermsConsent.accepted(context))
                    assertTrue(action("Next").isEnabled); assertTrue(action("Skip").isEnabled)
                    action("Next").performClick(); action("Skip").performClick()
                    assertTrue(continued); assertTrue(skipped)
                    check.performClick()
                    assertFalse(TermsConsent.accepted(context))
                    assertFalse(action("Next").isEnabled); assertFalse(action("Skip").isEnabled)
                }
                instrumentation.runOnMainSync {
                    TermsConsent.setAccepted(context,true)
                    for(page in 0 until OnboardingView.PAGE_COUNT) {
                        var advanced=false
                        val children=views(OnboardingView(context,page,{ advanced=true },{}))
                        val labels=children.filterIsInstance<TextView>()
                        val button=labels.single { it.text.toString()==if(page==3) "Get started" else "Next" }
                        assertTrue(button.isEnabled)
                        button.performClick(); assertTrue(advanced)
                        assertEquals(page<3,labels.any { it.text.toString()=="Skip" })
                        assertEquals(page==0,children.any { it is CheckBox })
                        assertTrue(children.any { it.contentDescription=="Introduction ${page+1} of 4" })
                    }
                }
            }
            preferences.edit().putString("accepted_terms","old-version").commit()
            assertFalse("Old terms require renewed agreement",TermsConsent.accepted(context))
        } finally {
            preferences.edit().apply { if(previous==null) remove("accepted_terms") else putString("accepted_terms",previous) }.commit()
            AppPreferences.setTheme(context,theme)
        }
    }
}

