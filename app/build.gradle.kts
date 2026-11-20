plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.ascii_wallpapers"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.ascii_wallpapers"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8: удаляет неиспользуемый код и переименовывает классы
            isMinifyEnabled = true
            // Удаляет неиспользуемые ресурсы (картинки, стили, локали библиотек)
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "src/main/keepRules/rules.keep"
            )
        }
    }

    androidResources {
        // Оставляем только реально нужные локали: заметно уменьшает resources.arsc
        localeFilters += listOf("ru", "en")
    }

    packaging {
        resources {
            // Служебные файлы библиотек не нужны в APK
            excludes += setOf(
                "META-INF/*.version",
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.material)
    // preference-ktx тянет старую fragment-ktx; фиксируем более новую версию
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
