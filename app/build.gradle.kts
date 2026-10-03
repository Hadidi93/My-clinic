import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Supabase URL and anon key are read from local.properties (git-ignored) or
// environment variables (for CI), never hard-coded in source control.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String, default: String): String =
    (localProps.getProperty(name) ?: System.getenv(name))?.trim()?.takeIf { it.isNotBlank() } ?: default

// Push notifications (Firebase Cloud Messaging). The Firebase settings come
// from google-services.json: the file app/google-services.json (git-ignored)
// or, in CI, the GOOGLE_SERVICES_JSON secret holding the file's text. Only
// four public identifiers are taken from it; without it, push is just off.
data class FirebaseConfig(val projectId: String, val appId: String, val apiKey: String, val senderId: String)

fun firebaseConfig(): FirebaseConfig {
    val text = System.getenv("GOOGLE_SERVICES_JSON")?.takeIf { it.isNotBlank() }
        ?: rootProject.file("app/google-services.json").takeIf { it.exists() }?.readText()
        ?: return FirebaseConfig("", "", "", "")
    @Suppress("UNCHECKED_CAST")
    val json = groovy.json.JsonSlurper().parseText(text) as Map<String, Any?>
    val info = json["project_info"] as Map<String, Any?>
    val client = (json["client"] as List<Map<String, Any?>>).firstOrNull { c ->
        ((c["client_info"] as Map<String, Any?>)["android_client_info"] as Map<String, Any?>)["package_name"] == "com.myclinic.app"
    } ?: error("google-services.json has no Android app with package com.myclinic.app")
    val apiKey = (client["api_key"] as List<Map<String, Any?>>).first()["current_key"] as String
    return FirebaseConfig(
        projectId = info["project_id"] as String,
        appId = (client["client_info"] as Map<String, Any?>)["mobilesdk_app_id"] as String,
        apiKey = apiKey,
        senderId = info["project_number"] as String,
    )
}
val firebase = firebaseConfig()

// Accept the URL as copied from any Supabase settings page: drop a trailing
// "/rest/v1" (or auth/storage/realtime) and slashes, which the library rejects.
fun supabaseProjectUrl(raw: String): String =
    raw.trim().trimEnd('/')
        .replace(Regex("/(rest|auth|storage|realtime)/v1$"), "")
        .trimEnd('/')

android {
    namespace = "com.myclinic.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myclinic.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${supabaseProjectUrl(secret("SUPABASE_URL", "https://example.supabase.co"))}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY", "missing-anon-key")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebase.projectId}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebase.appId}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebase.apiKey}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${firebase.senderId}\"")
    }

    signingConfigs {
        // A fixed, shared key for TEST builds only, so each new test app from
        // GitHub installs as an update instead of needing an uninstall first.
        // It is not secret (standard Android debug passwords). The Play Store
        // release key is separate and must never be committed (Phase 5).
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/io.netty.versions.properties")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.coil.compose)

    // Offline cache: Room on top of SQLCipher (encrypted SQLite)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)

    // App lock (fingerprint / face / phone PIN)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.process)

    // Push notifications (no patient data is ever sent through them)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Background sync
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
