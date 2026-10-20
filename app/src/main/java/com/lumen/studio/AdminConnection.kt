package com.lumen.studio

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Text and whitelisted counters only. No media URI, filename, project text or attachments. */
object AdminConnection {
    val configured get()=BuildConfig.ADMIN_API_BASE_URL.isNotBlank()
    private val main=Handler(Looper.getMainLooper())
    private val worker=ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,ArrayBlockingQueue(128),{ task -> Thread(task,"LumenAdminSync") },ThreadPoolExecutor.AbortPolicy())
    private fun preferences(c: Context)=c.getSharedPreferences("admin-connection",Context.MODE_PRIVATE)
    fun analyticsEnabled(c: Context)=configured && preferences(c).getBoolean("analytics",false)
    private class Queue(c: Context): SQLiteOpenHelper(c,"admin-events.db",null,1) {
        override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE events(id TEXT PRIMARY KEY,type TEXT NOT NULL,feature TEXT NOT NULL,at TEXT NOT NULL)") }
        override fun onUpgrade(db: SQLiteDatabase,old: Int,new: Int) { error("Unsupported queue migration") }
    }
    private fun raw(method: String,path: String,token: String?=null,input: JSONObject?=null): JSONObject {
        check(configured) { "Support server is not configured in this build." }
        val connection=URL(BuildConfig.ADMIN_API_BASE_URL+"/api/app"+path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod=method; connection.connectTimeout=10_000; connection.readTimeout=15_000; connection.instanceFollowRedirects=false
            connection.setRequestProperty("Accept","application/json")
            token?.let { connection.setRequestProperty("Authorization","Bearer $it") }
            input?.let { connection.doOutput=true; connection.setRequestProperty("Content-Type","application/json"); connection.outputStream.use { out -> out.write(it.toString().toByteArray(Charsets.UTF_8)) } }
            val code=connection.responseCode
            val stream=if(code in 200..299) connection.inputStream else connection.errorStream
            val result=stream?.bufferedReader()?.use { reader ->
                val buffer=CharArray(4096);val text=StringBuilder();while(true) {val size=reader.read(buffer);if(size<0)break;text.append(buffer,0,size);check(text.length<=512_000) { "Server response too large" }}
                JSONObject(text.toString())
            } ?: JSONObject()
            if(code !in 200..299) throw IllegalStateException(if(code==401) "Installation access expired. Use Delete online data / reset connection, then retry." else result.optString("error","Server request failed ($code)"))
            return result
        } finally { connection.disconnect() }
    }
    private fun token(c: Context): String {
        val prefs=preferences(c);prefs.getString("token",null)?.let { return it }
        // A new credential creates a fresh install identity; never reconstruct a hardware fingerprint.
        val id=prefs.getString("id",null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id",it).commit() }
        val result=raw("POST","/register",input=JSONObject().put("installation_id",id))
        return result.getString("token").also { check(prefs.edit().putString("token",it).commit()) }
    }
    private fun profile(c: Context) {
        val enabled=analyticsEnabled(c)
        val value=JSONObject().put("analytics",enabled)
        if(enabled) {
            val locale=c.resources.configuration.locales[0]
            value.put("brand",Build.BRAND.take(80)).put("model",Build.MODEL.take(120)).put("api_level",Build.VERSION.SDK_INT)
                .put("density",c.resources.displayMetrics.densityDpi).put("locale",locale.toLanguageTag().take(40)).put("country",locale.country.take(3)).put("app_version",BuildConfig.VERSION_NAME)
        }
        raw("PUT","/profile",token(c),value)
        preferences(c).edit().putBoolean("profile_dirty",false).apply()
    }
    private fun sync(c: Context) {
        if(!analyticsEnabled(c)) return
        profile(c)
        Queue(c).use { helper ->
            val db=helper.writableDatabase
            db.delete("events","at < ?",arrayOf(java.time.Instant.now().minusSeconds(30*86400L).toString()))
            val batch=JSONArray();val ids=mutableListOf<String>()
            db.query("events",null,null,null,null,null,"at ASC","100").use { rows -> while(rows.moveToNext()) {
                val id=rows.getString(rows.getColumnIndexOrThrow("id"));ids.add(id)
                batch.put(JSONObject().put("id",id).put("type",rows.getString(rows.getColumnIndexOrThrow("type"))).put("feature",rows.getString(rows.getColumnIndexOrThrow("feature"))).put("occurred_at",rows.getString(rows.getColumnIndexOrThrow("at"))))
            } }
            if(ids.isNotEmpty()) { raw("POST","/events",token(c),JSONObject().put("events",batch));db.beginTransaction();try { ids.forEach { db.delete("events","id=?",arrayOf(it)) };db.setTransactionSuccessful() } finally { db.endTransaction() } }
        }
    }
    private fun enqueue(c: Context,type: String,feature: String) {
        if(!analyticsEnabled(c)) return
        Queue(c).use { helper ->
            val db=helper.writableDatabase
            db.execSQL("DELETE FROM events WHERE id IN (SELECT id FROM events ORDER BY at DESC LIMIT -1 OFFSET 999)")
            db.insertOrThrow("events",null,ContentValues().apply { put("id",UUID.randomUUID().toString());put("type",type);put("feature",feature.take(100));put("at",java.time.Instant.now().toString()) })
        }
    }
    fun event(context: Context,type: String,feature: String="") {
        require(type in setOf("app_opened","video_exported","image_edit_started","feature_selected","ad_impression"))
        if(!analyticsEnabled(context))return
        val app=context.applicationContext
        runCatching { worker.execute { runCatching { enqueue(app,type,feature);sync(app) }.onFailure { android.util.Log.w("LumenAdmin","Usage sync deferred") } } }
    }
    fun opened(c: Context) {
        if(!configured)return
        val app=c.applicationContext
        runCatching { worker.execute { runCatching { if(preferences(app).getBoolean("profile_dirty",false))profile(app) } } }
        event(app,"app_opened")
    }
    fun fetchAdConfiguration(context: Context,done: (Result<AdsConfiguration>)->Unit) {
        val app=context.applicationContext
        async(done) {
            val json=raw("GET","/public-config")
            val config=AdsConfiguration.parse(json)
            preferences(app).edit().putString("ads_config",json.toString()).putLong("ads_received_at",System.currentTimeMillis()).apply()
            // Receipts are optional diagnostics under the existing usage-analytics consent.
            if(analyticsEnabled(app)) runCatching { raw("POST","/config-receipt",token(app),JSONObject().put("version",config.version).put("sdk_enabled",BuildConfig.ADS_ENABLED).put("app_version",BuildConfig.VERSION_NAME)) }
            config
        }
    }
    fun setAnalytics(context: Context,enabled: Boolean,done: (Result<Unit>)->Unit) {
        val app=context.applicationContext
        preferences(app).edit().putBoolean("analytics",enabled).putBoolean("profile_dirty",true).commit()
        async(done) {
            if(!enabled) Queue(app).use { it.writableDatabase.delete("events",null,null) }
            profile(app); if(enabled)sync(app)
        }
    }
    fun <T> async(done: (Result<T>)->Unit,action: ()->T) {
        try { worker.execute { val result=runCatching(action);main.post { done(result) } } }
        catch(error: java.util.concurrent.RejectedExecutionException) { main.post { done(Result.failure(IllegalStateException("Connection is busy. Try again shortly.",error))) } }
    }
    fun sendTicket(c: Context,id: String,subject: String,description: String,done: (Result<String>)->Unit) {
        val app=c.applicationContext
        async(done) { raw("POST","/tickets",token(app),JSONObject().put("id",id).put("subject",subject).put("description",description)).getString("id") }
    }
    fun inbox(c: Context,done: (Result<JSONArray>)->Unit) { val app=c.applicationContext;async(done) { raw("GET","/tickets",token(app)).getJSONArray("items") } }
    fun thread(c: Context,id: String,done: (Result<JSONObject>)->Unit) { val app=c.applicationContext;async(done) { raw("GET","/tickets/$id",token(app)) } }
    fun reply(c: Context,id: String,messageId: String,text: String,done: (Result<Unit>)->Unit) {val app=c.applicationContext;async(done) {raw("POST","/tickets/$id",token(app),JSONObject().put("id",messageId).put("reply",text));Unit} }
    fun deleteOnlineData(c: Context,done: (Result<Unit>)->Unit) {
        val app=c.applicationContext
        async(done) {
            preferences(app).getString("token",null)?.let { raw("DELETE","/installation",it) }
            Queue(app).use { it.writableDatabase.delete("events",null,null) }
            preferences(app).edit().clear().commit();Unit
        }
    }
}
