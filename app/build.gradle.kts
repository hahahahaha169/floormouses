plugins {
    id("com.android.application")
}
android {
    namespace = "com.example.floormouse"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.floormouse"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
