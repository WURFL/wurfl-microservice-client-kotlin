import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
    // Apply the plugin for maven publish
    `maven-publish`
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}
val ktorVersion = "1.6.7"
val http4kVersion = "4.17.9.0"
val projectVersion = "1.0.0"

tasks.withType<AbstractArchiveTask> {
    setProperty("archiveBaseName", rootProject.name)
}

allprojects {
    group = "com.scientiamobile.wurflmicroservice"
    version = projectVersion
}

publishing {
    publications {
        create<MavenPublication>("wmclient-publish") {
            from(components.findByName("kotlin"))
            pom {
                groupId = "$group"
                artifactId = rootProject.name
                version = projectVersion
                packaging = "jar"
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }

            repositories {
                // Use mavenLocal only if you want to install your development artifact into a maven local repo
                // using publishToMavenLocal command
                //mavenLocal()
                mavenCentral()
            }
        }
    }
}



    dependencies {
        // Align versions of all Kotlin components
        implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

        // Use the Kotlin JDK 8 standard library.
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

        // This dependency is used internally, and not exposed to consumers on their own compile classpath.
        implementation("com.google.guava:guava:30.1.1-jre")

        // Use ktor with Apache engine as HTTP client and kotlinx as json serializer/deserializer
        implementation (platform("org.http4k:http4k-bom:$http4kVersion"))
        implementation ("org.http4k:http4k-core")
        implementation ("org.http4k:http4k-client-apache")

        implementation("org.http4k:http4k-format-kotlinx-serialization:$http4kVersion")

        // this contains ApplicationRequest
        implementation("io.ktor:ktor-server-core:$ktorVersion")
        // this contains HTTPServletRequest
        implementation("javax.servlet:javax.servlet-api:4.0.1")

        // test mocks
        testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
        // we use mockito to mock old Java HttpServletRequest object and others
        testImplementation("org.mockito:mockito-core:4.2.0")

        // Use the Kotlin test library.
        testImplementation("org.jetbrains.kotlin:kotlin-test")

        // Use the Kotlin JUnit integration.
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    }
