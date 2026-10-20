package com.lumen.studio

import android.test.InstrumentationTestCase
import org.json.JSONArray
import org.json.JSONObject

@Suppress("DEPRECATION")
class AdsConfigurationTest: InstrumentationTestCase() {
    private fun fixture(): JSONObject = JSONObject().put("schema_version",1).put("delivery_mode","admob_mediation")
        .put("version",7).put("ads_enabled",true).put("test_mode",true)
        .put("network_requests",JSONArray(listOf("admob","meta","ironsource","unity","applovin")))
        .put("cooldown_seconds",120).put("max_per_session",3).put("every_n_exports",3)
        .put("placements",JSONArray(AdsConfiguration.formats.map { (id,type) -> JSONObject()
            .put("id",id).put("type",type).put("network",if(type=="app_open") "admob" else "ironsource")
            .put("enabled",true).put("ad_unit_id","ca-app-pub-1234567890123456/1234567890") }))
    private fun rejected(value: JSONObject) { assertTrue(runCatching { AdsConfiguration.parse(value) }.isFailure) }
    fun testPublishedRoutesAndFormats() {
        val c=AdsConfiguration.parse(fixture())
        assertEquals(7L,c.version);assertEquals(6,c.placements.size)
        assertEquals("ironsource",c.placements.getValue("home_banner").network)
        assertEquals("rewarded",c.placements.getValue("export_reward").type)
    }
    fun testRejectsWrongSourceFormatsAndLimits() {
        rejected(fixture().put("schema_version",2))
        rejected(fixture().put("cooldown_seconds",0))
        rejected(fixture().also { it.getJSONArray("placements").getJSONObject(0).put("ad_unit_id","partner-placement") })
        rejected(fixture().also { it.getJSONArray("placements").getJSONObject(0).put("type","interstitial") })
        rejected(fixture().also { it.getJSONArray("placements").getJSONObject(5).put("network","meta") })
        rejected(fixture().put("network_requests",JSONArray(listOf("admob"))))
    }
}
