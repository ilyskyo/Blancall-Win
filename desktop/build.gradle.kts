import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.pdfbox)
    implementation(libs.apache.poi)
    implementation(libs.apache.poi.scratchpad)
    implementation(libs.org.json)
    implementation(libs.jna)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

/**
 * 便携打包用的可执行 fat jar。
 *
 * Compose 插件的 createDistributable 在部分 Gradle/JDK 组合下不会生成 jpackage 输入目录，
 * 因此保留一条显式、可复现的路径：先打 fat jar，再由 tools/package-windows.ps1 调 jpackage。
 */
tasks.register<Jar>("uberJar") {
    group = "distribution"
    description = "Build a self-contained runnable jar for jpackage"
    archiveFileName.set("blancall-win.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.ilyskyo.blancall.desktop.MainKt"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    // 只保留 Windows x64 的 JNA native；其余平台的 .so/.a 会让包体无谓增大
    eachFile {
        val path = this.path
        if (path.startsWith("com/sun/jna/") &&
            path.contains("libjnidispatch") &&
            !path.startsWith("com/sun/jna/win32-x86-64/")
        ) {
            exclude()
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.ilyskyo.blancall.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Blancall"
            packageVersion = "7.6.1"
            description = "Blancall - local-first cloze memorization"
            vendor = "ilyskyo"
            copyright = "Copyright (c) 2026 ilyskyo"
            windows {
                // Per-user 安装：不需要管理员权限，卸载不删除 %LOCALAPPDATA%\Blancall 学习数据。
                perUserInstall = true
                menuGroup = "Blancall"
                shortcut = true
                // 固定 UUID：升级安装识别同一个产品（改动会导致「升级」变「并存」）
                upgradeUuid = "2f7f6c25-9a7b-4f53-8d0e-6a0a1e8a7c11"
                dirChooser = true
                console = false
            }
        }
    }
}
