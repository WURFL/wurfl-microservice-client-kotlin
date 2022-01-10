import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.5.0"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}
var ktorVersion = "1.6.7"
var projectVersion = "1.0.0"

tasks.withType<AbstractArchiveTask> {
    setProperty("archiveBaseName", rootProject.name)
}

allprojects {
    group = "com.scientiamobile.wurflmicroservice"
    version = projectVersion
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // This dependency is used internally, and not exposed to consumers on their own compile classpath.
    implementation("com.google.guava:guava:30.1.1-jre")

    // Use ktor with Apache engine as HTTP client and gson as json serializer/deserializer
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization:$ktorVersion")
    implementation("io.ktor:ktor-client-gson:$ktorVersion")
    implementation("io.ktor:ktor-gson:$ktorVersion")
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
