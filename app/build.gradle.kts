


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // 确保你的 libs.versions.toml 里有 compose 插件的定义
    // 如果没有，你可能需要用 id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.alendawang.manhua"
    // compileSdk 36 是一个预览版 SDK (Android V)，如果遇到问题可以降级到 34 (Android U)
    compileSdk = 35 // 使用一个更新但可能比 36 更稳定的版本

    defaultConfig {
        applicationId = "com.alendawang.manhua"
        minSdk = 21
        // targetSdk 应该与 compileSdk 保持一致
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../manhua-release-key.jks")
            storePassword = "123456"
            keyAlias = "666"
            keyPassword = "123456"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        // 推荐使用更新的 Java 版本，比如 17，与现代Android Studio匹配
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

// 启用 Compose 强跳过模式以优化重组性能
composeCompiler {
    enableStrongSkippingMode = true
}

// 这是清理和整合后的依赖块
dependencies {

    // 1. 核心依赖：使用版本目录（libs）来管理，这是最佳实践
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0") // DocumentFile 需要的依赖

    // 2. Compose BOM：统一使用稳定版，并且只声明一次！
    // 确保你的 libs.versions.toml 文件中的 compose-bom 指向 "2024.05.00"
    // 如果不确定，可以直接用下面的硬编码方式，更保险
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))

    // Compose UI 相关的库，不需要指定版本，BOM会管理
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.biometric:biometric:1.1.0")



    // 3. 其他第三方库：同样只声明一次
    implementation("io.coil-kt:coil-compose:2.6.0") // Coil图片加载库，直接写版本号，2.6.0是最新稳定版
    implementation("com.google.code.gson:gson:2.10.1")  // Gson JSON库
    implementation("androidx.compose.material:material-icons-extended-android:1.6.8") // 最新的扩展图标库
    implementation("com.github.albfernandez:juniversalchardet:2.5.0") // 编码检测库
    implementation("com.github.junrar:junrar:7.5.5") {
        // 排除可能在Android上不兼容的依赖
        exclude(group = "commons-logging", module = "commons-logging")
    }

    // 4. 测试依赖
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // 测试也需要BOM来统一版本
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.05.00"))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // 5. 调试依赖
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
