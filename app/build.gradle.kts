import java.util.Properties
import java.net.URI
import groovy.json.JsonOutput

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val publicDetails=Properties().apply {
    val source=rootProject.file("release/public.properties")
    if(source.isFile) source.reader(Charsets.UTF_8).use { load(it) }
}
val developerName=publicDetails.getProperty("developerName","Example Developer (test only)")
val supportEmail=publicDetails.getProperty("supportEmail","support@example.com")
val privacyUrl=publicDetails.getProperty("privacyUrl","https://example.com/lumen-privacy")
val placeholderPolicy=developerName.contains("example",true) || supportEmail.contains("example.",true) || privacyUrl.contains("example.",true) || developerName.isBlank() || supportEmail.isBlank() || !privacyUrl.startsWith("https://")
val uploadStore=providers.environmentVariable("LUMEN_UPLOAD_STORE_FILE").orNull
val uploadStorePassword=providers.environmentVariable("LUMEN_UPLOAD_STORE_PASSWORD").orNull
val uploadAlias=providers.environmentVariable("LUMEN_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword=providers.environmentVariable("LUMEN_UPLOAD_KEY_PASSWORD").orNull
val signingReady=listOf(uploadStore,uploadStorePassword,uploadAlias,uploadKeyPassword).all { !it.isNullOrBlank() }
val qaBuild=providers.gradleProperty("qaBuild").orNull=="true"
val enableAds=providers.gradleProperty("enableAds").orNull=="true"
val admobAppId=providers.gradleProperty("admobAppId").orElse("ca-app-pub-3940256099942544~3347511713").get()
check(admobAppId.matches(Regex("ca-app-pub-\\d{16}~\\d{10}"))) { "Invalid AdMob application ID (use ~, not /)." }
val adminApiUrl=providers.gradleProperty("adminApiUrl").orElse("").get().trimEnd('/')
check(adminApiUrl.isBlank() || URI(adminApiUrl).let { it.host!=null && it.rawQuery==null && it.rawFragment==null && (it.scheme=="https" || it.scheme=="http" && it.host in listOf("127.0.0.1","localhost","10.0.2.2")) }) { "Use an HTTPS admin API, or a local emulator/USB address for debugging." }
check(providers.gradleProperty("enableFirebase").orNull!="true") { "This is the local-only edition. Cloud services require a separate data/privacy review before being enabled." }

android {
    namespace="com.lumen.studio"
    compileSdk=36
    defaultConfig {
        applicationId=if(qaBuild) "com.lumen.studio.qa" else "com.lumen.studio"
        manifestPlaceholders["appLabel"]=if(qaBuild) "Lumen Studio QA" else "Lumen Studio"
        manifestPlaceholders["admobAppId"]=admobAppId
        buildConfigField("boolean","ADS_ENABLED",enableAds.toString())
        buildConfigField("boolean","ADS_TEST_APP",(admobAppId=="ca-app-pub-3940256099942544~3347511713").toString())
        minSdk=26
        targetSdk=36
        versionCode=17
        versionName="1.16"
        testInstrumentationRunner="android.test.InstrumentationTestRunner"
        buildConfigField("String","DEVELOPER_NAME",JsonOutput.toJson(developerName))
        buildConfigField("String","SUPPORT_EMAIL",JsonOutput.toJson(supportEmail))
        buildConfigField("String","PRIVACY_URL",JsonOutput.toJson(privacyUrl))
        buildConfigField("boolean","POLICY_PLACEHOLDER",placeholderPolicy.toString())
        buildConfigField("String","ADMIN_API_BASE_URL",JsonOutput.toJson(adminApiUrl))
    }
    signingConfigs {
        if(signingReady) create("upload") {
            storeFile=rootProject.file(uploadStore!!)
            storePassword=uploadStorePassword
            keyAlias=uploadAlias
            keyPassword=uploadKeyPassword
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable=false
            if(signingReady) signingConfig=signingConfigs.getByName("upload")
        }
    }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
    buildFeatures { buildConfig=true }
    sourceSets.getByName("main") {
        java.srcDir(if(enableAds) "src/ads/java" else "src/noads/java")
    }
    if(enableAds) listOf("debug","release").forEach { sourceSets.getByName(it).manifest.srcFile("src/ads/AndroidManifest.xml") }
    // Catalogs are bundled as assets, not language-specific split resources.
    bundle { language { enableSplit=false } }
    useLibrary("android.test.runner",false)
    useLibrary("android.test.base",false)
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("androidx.media3:media3-transformer:1.8.0")
    implementation("androidx.media3:media3-effect:1.8.0")
    if(enableAds) {
        implementation("com.google.android.gms:play-services-ads:25.5.0")
        implementation("com.google.android.ump:user-messaging-platform:4.0.0")
        implementation("com.google.ads.mediation:facebook:6.22.0.1")
        implementation("com.google.ads.mediation:ironsource:9.6.1.0")
        implementation("com.google.ads.mediation:applovin:13.6.4.2")
        implementation("com.google.ads.mediation:unity:4.21.0.0")
        implementation("com.unity3d.ads:unity-ads:4.21.0")
    }
}
val verifyPlayRelease=tasks.register("verifyPlayRelease") {
    group="verification"
    description="Reject placeholder privacy details or missing upload-key configuration before a Play release."
    doLast {
        check(!placeholderPolicy) { "Replace the example developer name, support email and HTTPS privacy URL in release/public.properties." }
        check(!qaBuild) { "A QA package cannot be submitted as the production application." }
        if(enableAds) {
            check(admobAppId!="ca-app-pub-3940256099942544~3347511713") { "Set your real AdMob application ID before a Play release." }
            check(adminApiUrl.startsWith("https://")) { "The ads edition requires a public HTTPS dashboard API." }
            check(providers.gradleProperty("adsPolicyReviewed").orNull=="true") { "Review the ads privacy policy, network consent messages, audience and Play Data safety declarations; then set -PadsPolicyReviewed=true." }
        }
        check(adminApiUrl.isBlank() || adminApiUrl.startsWith("https://")) { "Public releases require HTTPS for the admin API." }
        val host=URI(privacyUrl).host.orEmpty()
        check(host.isNotBlank() && !host.endsWith(".invalid") && !host.endsWith(".test") && host!="localhost") { "Use a real, publicly hosted HTTPS privacy URL." }
        check(supportEmail.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) { "Configure a valid public support email." }
        check(signingReady && rootProject.file(uploadStore!!).isFile) { "Configure the existing upload keystore using LUMEN_UPLOAD_* environment variables. No key is generated automatically." }
        println("Local release configuration passed. Verify the hosted policy, Play declarations and runtime test results before submission.")
    }
}
if(providers.gradleProperty("playRelease").orNull=="true") {
    tasks.matching { it.name=="bundleRelease" || it.name=="assembleRelease" }.configureEach { dependsOn(verifyPlayRelease) }
}
tasks.register("preparePlayDocuments") {
    group="publishing"
    description="Generate local HTML privacy policy from the same content used inside the app. Does not publish."
    doLast {
        fun html(value: String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
        val policy=groovy.json.JsonSlurper().parse(file(if(enableAds) "src/main/assets/legal/privacy-ads.json" else if(adminApiUrl.isBlank()) "src/main/assets/legal/privacy-policy.json" else "src/main/assets/legal/privacy-connected.json")) as Map<*,*>
        val sections=(policy["sections"] as List<*>).joinToString("\n") { item -> val section=item as Map<*,*>; "<section><h2>${html(section["title"].toString())}</h2><p>${html(section["body"].toString())}</p></section>" }
        val notice=if(placeholderPolicy) "<aside><strong>TEST DRAFT — NOT FOR PUBLICATION.</strong> Developer and contact details are examples. Replace them and verify the final policy before publishing.</aside>" else ""
        val document="""<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Lumen Studio — Privacy policy</title><style>body{font:17px/1.7 system-ui,sans-serif;max-width:760px;margin:0 auto;padding:48px 24px;color:#222436;background:#f6f7fb}h1{font-size:36px;line-height:1.2}h2{font-size:22px;margin-top:36px}aside{padding:20px;background:#fff0cd;border-radius:12px}a{color:#654dcd}footer{margin-top:48px;font-size:14px;color:#596176}</style></head><body>$notice<h1>Lumen Studio<br>Privacy policy</h1><p>Effective ${html(policy["effectiveDate"].toString())}</p><p>Developer: ${html(developerName)}<br>Privacy contact: ${html(supportEmail)}</p>$sections<footer>Public policy address: ${html(privacyUrl)}</footer></body></html>"""
        val output=rootProject.file("release/generated").apply { mkdirs() }
        output.resolve("privacy-policy.html").writeText(document,Charsets.UTF_8)
        println("Privacy policy HTML generated locally. Placeholder details: $placeholderPolicy. No website was published.")
    }
}
