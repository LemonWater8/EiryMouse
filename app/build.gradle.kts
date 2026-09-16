plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.diazymouse"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.diazymouse"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "connection"

    productFlavors {
        create("bt") {
            dimension = "connection"

            buildConfigField("String", "VARIANT_NAME", "\"bluetooth\"")
            buildConfigField("boolean", "SUPPORTS_BLUETOOTH", "true")
            buildConfigField("boolean", "SUPPORTS_ADB", "false")
        }

        create("full") {
            dimension = "connection"

            buildConfigField("String", "VARIANT_NAME", "\"full\"")
            buildConfigField("boolean", "SUPPORTS_BLUETOOTH", "true")
            buildConfigField("boolean", "SUPPORTS_ADB", "true")
        }
    }
    buildTypes {
        release {
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}