import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val local = Properties()
val localFile = rootProject.file("local.properties")
if (localFile.exists()) localFile.inputStream().use(local::load)
val googleClientId = providers.gradleProperty("onevault.googleClientId")
    .orElse(local.getProperty("onevault.googleClientId", ""))
    .get()
// Android OAuth client ID format: <numeric-id>-<hash>.apps.googleusercontent.com
// The redirect scheme MUST match your OAuth client registration.
// AppAuth's Google example uses com.googleusercontent.apps.<client-prefix>
// as the Android redirect scheme. Newly registered Android clients need
// the custom URI scheme explicitly enabled in Google Cloud advanced settings.
val defaultRedirectScheme = if (googleClientId.isBlank()) "org.onevault.app"
    else "com.googleusercontent.apps.${googleClientId.substringBefore(".apps.googleusercontent.com")}" 
val redirectScheme = providers.gradleProperty("onevault.redirectScheme")
    .orElse(local.getProperty("onevault.redirectScheme", defaultRedirectScheme))
    .get()
require(redirectScheme.matches(Regex("[a-zA-Z][a-zA-Z0-9+.-]*"))) {
    "Invalid OAuth redirect scheme: $redirectScheme"
}

android {
    namespace = "org.onevault.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.onevault.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.2.6-alpha"
        manifestPlaceholders["appAuthRedirectScheme"] = redirectScheme
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")
        buildConfigField("String", "OAUTH_REDIRECT_URI", "\"$redirectScheme:/oauth2redirect\"")
        resourceConfigurations += listOf("ar", "en")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "META-INF/*" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("net.openid:appauth:0.11.1")
    testImplementation("junit:junit:4.13.2")
}
