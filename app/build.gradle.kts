import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun apiUrl(value: String, httpsOnly: Boolean = false): String {
    val uri = URI(value.trim())
    require(uri.host != null && uri.scheme in if (httpsOnly) listOf("https") else listOf("http", "https")) {
        "API URL must be an absolute ${if (httpsOnly) "HTTPS" else "HTTP(S)"} URL"
    }
    require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "API URL cannot contain credentials, a query or a fragment"
    }
    return uri.toASCIIString().trimEnd('/') + "/"
}

val debugApiUrl = apiUrl(providers.gradleProperty("apiBaseUrl")
    .orElse(providers.environmentVariable("LINGOLENS_API_BASE_URL"))
    .getOrElse("http://10.0.2.2:8080/"))
val releaseApiUrl = providers.gradleProperty("releaseApiBaseUrl")
    .orElse(providers.environmentVariable("LINGOLENS_RELEASE_API_BASE_URL"))
    .orNull?.let { apiUrl(it, httpsOnly = true) }

android {
    namespace = "com.quocdat.lingolens"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.quocdat.lingolens"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiUrl\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"${releaseApiUrl.orEmpty()}\"")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Debug builds work immediately; release artifacts require an explicit HTTPS endpoint.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    val configuredReleaseApiUrl = releaseApiUrl
    doFirst {
        require(!configuredReleaseApiUrl.isNullOrBlank()) {
            "Set -PreleaseApiBaseUrl=https://your-api-host/ or LINGOLENS_RELEASE_API_BASE_URL before building release"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)
    implementation(libs.google.mlkit.image.labeling)
    implementation(libs.google.mediapipe.tasks.vision)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
