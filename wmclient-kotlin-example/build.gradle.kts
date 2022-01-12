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



tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // wm client
    implementation("$group:wurfl-microservice-client-kotlin:$version")
}


application {
    mainClass.set("MainKt")
}