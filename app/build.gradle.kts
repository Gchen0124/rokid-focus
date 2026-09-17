import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.chenniuniu.rokidfocus"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.chenniuniu.rokidfocus"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val props = Properties()
        val f = rootProject.file("local.properties")
        if (f.exists()) props.load(f.inputStream())
        fun esc(k: String) = "\"${props.getProperty(k, "").replace("\"", "\\\"")}\""
        buildConfigField("String", "DEEPSEEK_API_KEY", esc("deepseek.api.key"))
        buildConfigField("String", "XFYUN_APP_ID", esc("xfyun.app.id"))
        buildConfigField("String", "XFYUN_API_KEY", esc("xfyun.api.key"))
        buildConfigField("String", "XFYUN_API_SECRET", esc("xfyun.api.secret"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation("com.rokid.cxr:client-l:1.0.4")
    implementation("com.rokid.cxr:cxr-service-bridge:1.0-20260417.063502-103")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
}
