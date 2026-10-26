// 版本号单一来源：改版本只改这两行即可（scripts/release.sh 与 CI 都按此格式解析）。
// ⚠️ 故意不放进 Version Catalog：release.sh 用 grep 按固定文本格式解析。
val appVersionName = "1.0.0"
val appVersionCode = 1

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.gluconote"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gluconote"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 应用内更新：读公共分发仓库 LuckyLearning/Release 下的 GlucoNote/latest.json
        buildConfigField("String", "RELEASE_OWNER", "\"LuckyLearning\"")
        buildConfigField("String", "RELEASE_REPO", "\"Release\"")
        buildConfigField("String", "RELEASE_BRANCH", "\"main\"")
        buildConfigField("String", "RELEASE_APP_DIR", "\"GlucoNote\"")
        buildConfigField(
            "String",
            "RELEASE_PAGE_URL",
            "\"https://github.com/LuckyLearning/Release/tree/main/GlucoNote\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 仅在 CI 提供密钥时启用签名（KEYSTORE_PATH 等环境变量）
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.create("ciRelease") {
                    storeFile = file(System.getenv("KEYSTORE_PATH")!!)
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("KEY_ALIAS")
                    keyPassword = System.getenv("KEY_PASSWORD")
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // 应用内更新需要读取 BuildConfig.VERSION_NAME / RELEASE_*
        buildConfig = true
    }

    composeOptions {
        // Compose Compiler 1.5.14 与 Kotlin 1.9.24 兼容（1.5.15 需 Kotlin 1.9.25）
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose：BOM 对齐全部 compose-* 版本，下面的库都不带版本号
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.mockito:mockito-core:5.11.0")
}
