// Shared Kotlin module: models, validation and permission rules.
// It is Kotlin Multiplatform: the same code runs in the Android app (jvm)
// and the iPhone app (ios*). No platform code here, so the rules are
// unit-tested on any machine in seconds (the tests run on the JVM).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // iPhones and the iPhone simulator on Apple-silicon Macs. These compile only on a Mac (CI job "ios").
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}
