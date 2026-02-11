


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.alendawang.manhua"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.alendawang.manhua"
        minSdk = 21
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.4"

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

dependencies {


    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0") // DocumentFile 需要的依赖


    implementation(platform("androidx.compose:compose-bom:2024.05.00"))

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
    implementation("com.positiondev.epublib:epublib-core:3.1") {
        exclude(group = "org.slf4j")
        exclude(group = "xmlpull")
    }

    // Room 数据库
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

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
