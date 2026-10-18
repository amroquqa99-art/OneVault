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
val redirectScheme = providers.gradleProperty("onevault.redirectScheme")
    .orElse(local.getProperty("onevault.redirectScheme", "org.onevault.app"))
    .get()

android {
    namespace = "org.onevault.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.onevault.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.2.4-alpha"
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
