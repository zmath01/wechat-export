plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.wechatexport"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wechatexport"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = "2024.02.00"
    implementation(platform("androidx.compose:compose-bom:$composeBom"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    testImplementation("junit:junit:4.13.2")
}
