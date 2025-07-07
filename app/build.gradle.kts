plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "1.9.20"
}

android {
    namespace = "com.VeilLink.androidapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.VeilLink.androidapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val serverName: String = project.properties["vpnServerName"] as? String
            ?: ""
        buildConfigField("String", "VPN_SERVER_NAME", "\"$serverName\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"   // под Kotlin 1.9
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":tunnel"))
    // платформа выравнивает версии всех артефактов Compose
    implementation(platform("androidx.compose:compose-bom:2023.12.00"))

    implementation("androidx.compose.ui:ui")               // базовый UI
    implementation("androidx.compose.foundation:foundation")// Row/Column
    implementation("androidx.compose.material3:material3") // M3 виджеты
    implementation("androidx.activity:activity-compose:1.8.2")

    // превью и инспектор-layout
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
}