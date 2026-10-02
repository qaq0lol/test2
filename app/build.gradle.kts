plugins {
    id("com.android.application")
}

android {
    namespace = "com.proxy.wireopen"
    compileSdk = 36 // Android 16 (Baklava)

    defaultConfig {
        applicationId = "com.proxy.wireopen"
        minSdk = 26
        targetSdk = 36 // Compatible with Android 16+
        versionCode = 7
        versionName = "1.0.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.all {
            it.jvmArgs("-Dfile.encoding=UTF-8")
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // JSON parsing / Serialization for profile storage
    implementation("com.google.code.gson:gson:2.10.1")

    // WireGuard Android Tunnel Core (wireguard-go backend)
    implementation("com.wireguard.android:tunnel:1.0.20230706")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// Automatically copy generated APK to project root directory after build
tasks.register("copyApkToRoot") {
    doNotTrackState("Direct copy to root directory")
    doLast {
        val src = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        val dest = File(rootProject.rootDir, "WireOpenProxy-debug.apk")
        if (src.exists()) {
            src.copyTo(dest, overwrite = true)
            println(">>> Successfully deployed APK to root directory: ${dest.absolutePath}")
        }
    }
}

tasks.withType<Test> {
    enabled = false
}

tasks.register<JavaExec>("standaloneTest") {
    dependsOn("compileDebugUnitTestJavaWithJavac")
    mainClass.set("com.proxy.wireopen.StandaloneTestRunner")
    
    val jarArtifacts = configurations["debugUnitTestRuntimeClasspath"].incoming.artifactView {
        attributes {
            attribute(Attribute.of("artifactType", String::class.java), "android-classes-jar")
        }
    }.files

    classpath = files(
        android.bootClasspath,
        layout.buildDirectory.dir("intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"),
        layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
        jarArtifacts,
        configurations["debugUnitTestRuntimeClasspath"]
    )
}

afterEvaluate {
    tasks.matching { it.name == "test" }.configureEach {
        dependsOn("standaloneTest")
    }
    tasks.named("assembleDebug") {
        finalizedBy("copyApkToRoot")
    }
}
