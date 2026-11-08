package id.eduduel.app.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Penjaga kebijakan produk: semua pemain Guest, offline, tanpa Firebase SDK/Auth/Firestore.
 * Membaca source proyek sehingga regresi (mis. menambah library HTTP/analitik) langsung gagal di CI.
 */
class GuestOnlyPolicyTest {
    private val moduleDir = listOf(File("."), File("app")).first { File(it, "src/main/AndroidManifest.xml").exists() }
    private val manifest = File(moduleDir, "src/main/AndroidManifest.xml").readText()
    private val gradle = File(moduleDir, "build.gradle.kts").readText()
    private val catalog = File(moduleDir.canonicalFile.parentFile, "gradle/libs.versions.toml").readText()
    private val sources = File(moduleDir, "src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    /**
     * INTERNET ada HANYA karena Android mewajibkannya untuk socket lokal (room Wi-Fi/hotspot).
     * Yang dijaga: tidak ada klien HTTP, URL server, atau library jaringan pihak ketiga.
     */
    @Test fun networkIsLocalOnly_noHttpClientsOrServerUrls() {
        val bannedCode = listOf("HttpURLConnection", "HttpsURLConnection", "java.net.URL", "okhttp3", "retrofit2", "io.ktor",
            "com.android.volley", "android.webkit.WebView")
        for (f in sources) {
            val t = f.readText()
            for (b in bannedCode) assertFalse("${f.name} memuat $b", t.contains(b))
            assertFalse("${f.name} memuat URL http(s)", Regex("""["']https?://""").containsMatchIn(t))
        }
        for (text in listOf(gradle, catalog)) {
            for (b in listOf("okhttp", "retrofit", "ktor", "volley", "analytics", "crashlytics", "admob", "ads")) {
                assertFalse("build memuat $b", Regex("""(?i)["':.-]$b["':.-]""").containsMatchIn(text))
            }
        }
        assertTrue(File(moduleDir, "src/main/res/xml/network_security_config.xml").readText().contains("cleartextTrafficPermitted=\"false\""))
    }

    @Test fun noFirebaseOrGoogleServicesInBuild() {
        for (text in listOf(gradle, catalog)) {
            assertFalse(text.contains("firebase", ignoreCase = true))
            assertFalse(text.contains("google-services", ignoreCase = true))
            assertFalse(text.contains("play-services-auth", ignoreCase = true))
        }
        assertFalse(File(moduleDir, "google-services.json").exists())
    }

    @Test fun noAuthOrAccountCodeOrScreens() {
        val banned = listOf("signInAnonymously", "FirebaseAuth", "Firestore", "GoogleSignIn", "LoginRoute", "RegisterRoute", "ProfileRoute", "AccountRoute")
        for (f in sources) {
            val t = f.readText()
            for (b in banned) assertFalse("${f.name} memuat $b", t.contains(b))
        }
    }

    @Test fun legacyLocationPermissionLimitedToApi30() {
        val loc = Regex("""ACCESS_FINE_LOCATION"\s+android:maxSdkVersion="30"""")
        assertTrue(loc.containsMatchIn(manifest))
        assertTrue(manifest.contains("neverForLocation"))
    }
}
