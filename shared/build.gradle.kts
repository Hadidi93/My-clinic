// Shared app code for Android and iPhone: Supabase access, repositories,
// offline copy and sync (screens follow later). Code that needs the phone's
// own system (files, encryption keys, notifications) goes in androidMain / iosMain.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(project.dependencies.platform(libs.supabase.bom))
            api(libs.supabase.auth)
            api(libs.supabase.postgrest)
            api(libs.supabase.storage)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
            // Offline copy (Room works on Android and iPhone)
            api(libs.room.runtime)
            // Screens (Compose Multiplatform) and their English/Arabic texts
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.components.resources)
            api(libs.jb.navigation.compose)
            api(libs.jb.lifecycle.viewmodel.compose)
            api(libs.jb.lifecycle.runtime.compose)
            api(libs.koin.compose)
            api(libs.koin.compose.viewmodel)
            // Doctor photos and licence images (signed links)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // Android's offline copy is encrypted with SQLCipher
            implementation(libs.sqlcipher)
            implementation(libs.androidx.sqlite)
            // In-app language switch (English / Arabic)
            implementation(libs.androidx.appcompat)
            // Android back button, camera and file pickers
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqlite.bundled)
        }
        androidUnitTest.dependencies {
            implementation(libs.junit)
        }
    }
}

// Texts and images in src/commonMain/composeResources, used as Res.string.<name>.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.myclinic.shared.resources"
    generateResClass = always
}

// Room's code generator, once per platform.
dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

android {
    namespace = "com.myclinic.shared"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
