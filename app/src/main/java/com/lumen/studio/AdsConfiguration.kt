package com.lumen.studio

import org.json.JSONObject

/** A published configuration is data only; it can never install or initialize an SDK. */
data class AdPlacement(val id: String, val type: String, val network: String, val enabled: Boolean, val unit: String)
data class AdsConfiguration(
    val version: Long, val enabled: Boolean, val testMode: Boolean,
    val cooldownSeconds: Int, val maxPerSession: Int, val everyNExports: Int,
    val placements: Map<String, AdPlacement>
) {
    companion object {
        val networks = setOf("admob", "meta", "ironsource", "unity", "applovin")
        val formats = mapOf("home_banner" to "banner", "projects_banner" to "banner",
            "export_complete" to "interstitial", "export_reward" to "rewarded",
            "preview_reward" to "rewarded", "app_open" to "app_open")
        private val unitPattern = Regex("ca-app-pub-\\d{16}/\\d{10}")
        fun parse(json: JSONObject): AdsConfiguration {
            require(json.getInt("schema_version") == 1 && json.getString("delivery_mode") == "admob_mediation")
            val version = json.getLong("version"); require(version > 0)
            val selected = json.getJSONArray("network_requests").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
            require(networks.containsAll(selected))
            val array = json.getJSONArray("placements"); require(array.length() == formats.size)
            val placements = LinkedHashMap<String, AdPlacement>()
            for (i in 0 until array.length()) {
                val p = array.getJSONObject(i); val id = p.getString("id"); val type = p.getString("type")
                val network = p.optString("network", "admob"); val enabled = p.getBoolean("enabled")
                val unit = p.getString("ad_unit_id")
                require(formats[id] == type && !placements.containsKey(id) && network in networks)
                require(type != "app_open" || network == "admob")
                require(unit.isEmpty() || unitPattern.matches(unit))
                require(!enabled || (unit.isNotEmpty() && network in selected))
                placements[id] = AdPlacement(id, type, network, enabled, unit)
            }
            val cooldown = json.getInt("cooldown_seconds"); require(cooldown in 60..86400)
            val cap = json.getInt("max_per_session"); require(cap in 1..10)
            val frequency = json.getInt("every_n_exports"); require(frequency in 2..100)
            return AdsConfiguration(version, json.getBoolean("ads_enabled"), json.getBoolean("test_mode"), cooldown, cap, frequency, placements)
        }
    }
}
