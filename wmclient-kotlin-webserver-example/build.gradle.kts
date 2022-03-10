import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
    application
}

group = "com.scientiamobile.wurflmicroservice"
version = "1.0.0"

repositories {
    mavenCentral()
    mavenLocal()
}

var ktorVersion = "1.6.7"

dependencies{
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    // ktor server
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    // wm client
    implementation("$group:wurfl-microservice-client-kotlin:$version")
    // we use this to build HTML reponse
    implementation ("io.ktor:ktor-html-builder:$ktorVersion")
}



tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

application {
    mainClass.set("MainKt")
}