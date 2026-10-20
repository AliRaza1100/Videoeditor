package com.lumen.studio

import android.test.InstrumentationTestCase

@Suppress("DEPRECATION")
class ThemeMigrationTest: InstrumentationTestCase() {
    fun testSupportedThemesPersistAndRemovedChoicesMigrate() {
        val context=instrumentation.targetContext
        assertTrue(context.packageName.endsWith(".qa"))
        val prefs=context.getSharedPreferences("appearance",0)
        val original=AppPreferences.theme(context)
        try {
            for(removed in listOf("slate","solar","ember")) {
                prefs.edit().putString("theme",removed).putBoolean("light",true).commit()
                assertEquals("gold",AppPreferences.theme(context)); assertEquals("gold",prefs.getString("theme",null))
                assertTrue(AppPreferences.light(context))
            }
            for(theme in listOf("light","dark","violet","gold")) { AppPreferences.setTheme(context,theme); assertEquals(theme,AppPreferences.theme(context)) }
            prefs.edit().remove("theme").remove("light").commit()
            assertEquals("gold",AppPreferences.theme(context))
            AppPreferences.setTheme(context,"dark")
            assertEquals("dark",AppPreferences.theme(context))
            var rejected=false
            try { AppPreferences.setTheme(context,"solar") } catch(_: IllegalArgumentException) { rejected=true }
            assertTrue(rejected)
            prefs.edit().remove("theme").putBoolean("light",true).commit()
            assertEquals("light",AppPreferences.theme(context))
        } finally { AppPreferences.setTheme(context,original) }
    }
}
