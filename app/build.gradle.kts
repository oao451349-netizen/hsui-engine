plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.engine"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.engine"
        minSdk = 24          // GestureDescription requires API 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17", "-O3", "-DNDEBUG")
                arguments(
                    "-DANDROID_STL=c++_shared",
                    // Set your local OpenCV SDK path here or pass via gradle.properties
                    "-DOPENCV_DIR=${project.findProperty("OPENCV_DIR") ?: ""}"
                )
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("androidx.core:core-ktx:1.13.1")
}
