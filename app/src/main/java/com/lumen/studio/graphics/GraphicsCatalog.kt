package com.lumen.studio.graphics

import android.app.Application
import android.content.res.AssetManager
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

class GraphicsApplication: Application() {
    override fun onCreate() { super.onCreate(); GraphicsCatalog.install(assets) }
}

/** Parse/validate once, then share read-only CPU data. Never share GL objects between contexts. */
class GraphicsCatalog private constructor(
    val filters: List<FilterPreset>,val effects: List<ShaderPreset>,val typography: List<TypographyPreset>,
    val animations: List<AnimationPreset>,val shaders: Map<String,ShaderSource>,val fonts: Map<String,String>,val adjustments: List<AdjustmentPreset>
) {
    private fun <T> index(items: List<T>,info: (T)->PresetInfo): Map<String,T> = buildMap {
        items.forEach { item -> val key=info(item); require(!containsKey(key.id) && !containsKey(key.name)) { "Ambiguous catalog key ${key.id}" }; put(key.id,item); put(key.name,item) }
    }
    private val filterIndex=index(filters) { it.info }
    private val effectIndex=index(effects) { it.info }
    private val typeIndex=index(typography) { it.info }
    private val animationIndex=index(animations) { it.info }
    fun filter(id: String)=filterIndex[id]
    fun effect(id: String)=effectIndex[id]
    fun type(id: String?)=typeIndex[id]
    fun animation(id: String?)=animationIndex[id]
    companion object {
        @Volatile var current: GraphicsCatalog?=null; private set
        @Synchronized fun install(assets: AssetManager): GraphicsCatalog {
            current?.let { return it }
            return parse(assets).also { current=it }
        }
        private fun parse(assets: AssetManager): GraphicsCatalog {
            fun asset(path: String): String { require(path.startsWith("graphics/") && ".." !in path); return assets.open(path).bufferedReader().use { it.readText() } }
            val json=JSONObject(asset("graphics/catalog.json")); require(json.getInt("schema")==1)
            fun info(o: JSONObject)=PresetInfo(o.getString("id"),o.getString("name"),o.getString("category")).also {
                require(it.id.matches(Regex("[a-z][a-z0-9._-]+")) && it.name.isNotBlank() && it.category.isNotBlank())
            }
            fun numbers(a: JSONArray)=List(a.length()) { a.getDouble(it).toFloat().also { value -> require(value.isFinite()) } }
            fun <T> entries(name: String,decode: (JSONObject)->T): List<T> { val a=json.getJSONArray(name); return List(a.length()) { decode(a.getJSONObject(it)) } }
            val sources=json.getJSONObject("shaders")
            val shaders=sources.keys().asSequence().associateWith { key -> val source=sources.getJSONObject(key); ShaderSource(asset(source.getString("vertex")),asset(source.getString("fragment"))) }
            val filters=entries("filters") { o -> FilterPreset(info(o),numbers(o.getJSONArray("matrix")).also { require(it.size==20) }) }
            val effects=entries("effects") { o ->
                val uniforms=o.getJSONObject("uniforms")
                ShaderPreset(info(o),o.getString("shader"),uniforms.keys().asSequence().associateWith { key ->
                    require(key.matches(Regex("u[A-Za-z0-9_]+")) && key !in setOf("uTexSampler","uTime","uResolution","uEnabled","uBitmap"))
                    numbers(uniforms.getJSONArray(key)).also { require(it.size in setOf(1,2,3,4,9,16)) }
                }).also { require(it.shader in shaders) }
            }
            val types=entries("typography") { o ->
                TypographyPreset(info(o),o.getString("family"),o.getInt("weight"),o.getBoolean("italic"),Color.parseColor(o.getString("strokeColor")),o.getDouble("strokeWidth").toFloat(),
                    Color.parseColor(o.getString("glowColor")),o.getDouble("glowRadius").toFloat(),Color.parseColor(o.getString("background")),o.getDouble("padding").toFloat(),o.getDouble("letterSpacing").toFloat(),o.optBoolean("underline"),o.optBoolean("strike"),Color.parseColor(o.getString("textColor"))).also {
                    require(it.weight in 100..900 && it.strokeWidth.isFinite() && it.strokeWidth in 0f..24f && it.glowRadius.isFinite() && it.glowRadius in 0f..40f && it.padding.isFinite() && it.padding in 0f..80f && it.letterSpacing.isFinite())
                    require(!it.family.startsWith("assets/") || (it.family.startsWith("assets/graphics/fonts/") && ".." !in it.family))
                }
            }
            val animations=entries("animations") { o ->
                val tracks=o.getJSONObject("tracks")
                AnimationPreset(info(o),o.getLong("durationMs"),tracks.keys().asSequence().associateWith { key ->
                    val a=tracks.getJSONArray(key)
                    ScalarTrack(List(a.length()) { i -> val k=a.getJSONObject(i); val e=k.getJSONObject("easing")
                        val curve=Curve(CurveKind.valueOf(e.getString("kind")),e.optDouble("x1",.25).toFloat(),e.optDouble("y1",.1).toFloat(),e.optDouble("x2",.25).toFloat(),e.optDouble("y2",1.0).toFloat())
                        require(listOf(curve.x1,curve.x2).all { it.isFinite() && it in 0f..1f } && listOf(curve.y1,curve.y2).all { it.isFinite() })
                        Keyframe(k.getDouble("t").toFloat(),k.getDouble("value").toFloat(),curve)
                    })
                })
            }
            val fonts=json.getJSONObject("fonts").let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
            val adjustments=entries("adjustments") { o ->
                AdjustmentPreset(o.getString("id"),o.getDouble("minimum").toFloat(),o.getDouble("maximum").toFloat(),o.getDouble("default").toFloat(),o.getBoolean("afterFilter"),o.getBoolean("exponential"),numbers(o.getJSONArray("base")),numbers(o.getJSONArray("slope"))).also {
                    require(it.minimum.isFinite() && it.maximum.isFinite() && it.default.isFinite() && it.default in it.minimum..it.maximum && it.base.size==20 && it.slope.size==20)
                }
            }
            CatalogTextRenderer.install(assets)
            return GraphicsCatalog(filters,effects,types,animations,shaders,fonts,adjustments)
        }
    }
}
