import com.techshroom.inciseblue.commonLib
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    id("com.techshroom.incise-blue") version "0.5.7"
    kotlin("jvm") version "1.3.70"
}

inciseBlue {
    ide()
    license()
    util {
        javaVersion = JavaVersion.VERSION_13
    }
    lwjgl {
        lwjglVersion = "3.2.3"
        addDependency("")
        addDependency("glfw")
        // on OSX this requires natives, don't care right now though
        addDependency("vulkan", natives = false)
        addDependency("shaderc")
        addDependency("jemalloc")
        addDependency("stb")
    }
}

dependencies {
    "implementation"(kotlin("stdlib-jdk8"))
    "implementation"("org.slf4j:slf4j-api:1.7.30")
    "implementation"("org.joml:joml:1.9.22")
    commonLib("ch.qos.logback", "logback", "1.2.3") {
        "implementation"(lib("classic"))
        "implementation"(lib("core"))
    }
    "implementation"("io.github.microutils:kotlin-logging:1.7.8")
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.freeCompilerArgs = listOf(
        "-XXLanguage:+NewInference",
        "-Xopt-in=kotlin.RequiresOptIn"
    )
}

application.mainClassName = "net.octyl.marus.MainKt"
