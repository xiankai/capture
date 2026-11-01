import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

val headCommitTime: Instant = providers.exec {
    commandLine("git", "log", "-1", "--format=%ct")
}.standardOutput.asText.get().trim().toLongOrNull()
    ?.let(Instant::ofEpochSecond)
    ?: error("versionCode is the HEAD commit's timestamp, so the app builds only from a git checkout")

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.bespectacledcyborg.capture"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bespectacledcyborg.capture"
        minSdk = 34
        targetSdk = 36
        versionCode = headCommitTime.epochSecond.toInt()
        versionName = ZonedDateTime.ofInstant(headCommitTime, ZoneId.of("Asia/Tokyo"))
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }

    val releaseKeystore = System.getenv("RELEASE_KEYSTORE_PATH")?.let(::file)
    fun requireSigningEnv(name: String): String =
        checkNotNull(System.getenv(name)?.takeIf { it.isNotEmpty() }) { "$name must be set when RELEASE_KEYSTORE_PATH is" }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = requireSigningEnv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = requireSigningEnv("RELEASE_KEY_ALIAS")
                keyPassword = requireSigningEnv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            vcsInfo.include = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        textReport = true
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.work.runtime.ktx)
    testImplementation(libs.junit)
}
