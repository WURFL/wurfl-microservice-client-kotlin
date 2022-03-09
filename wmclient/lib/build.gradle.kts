import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.6.10"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
    // Apply the plugin for maven publish
    `maven-publish`
    signing
    id("org.jetbrains.dokka") version "1.6.0"

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

tasks {

    val sourcesJar by creating(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets.main.get().allSource)
    }

    dokkaHtml {
        moduleName.set(rootProject.name)
    }

    val dokkaJar = register<Jar>("dokkaJar") {
        from(dokkaHtml)
        dependsOn(dokkaHtml)
        archiveClassifier.set("javadoc")
    }

    withType<Jar> {
        metaInf.with(
            copySpec {
                from("${project.rootDir}/LICENSE")
            }
        )
    }

    artifacts {
        archives(sourcesJar)
        archives(jar)
        archives(dokkaJar)
    }
}

publishing {
    publications {
        create<MavenPublication>("wmclient-publish") {
            from(components.findByName("kotlin"))
            pom {
                name.set("WURFL Microservice client for Kotlin")
                url.set("https://github.com/WURFL/wurfl-microservice-client-kotlin")
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
                scm {
                    connection.set("scm:git:git@github.com:WURFL/wurfl-microservice-client-kotlin.git")
                    developerConnection.set("scm:git:git@github.com:WURFL/wurfl-microservice-client-kotlin.git")
                    url.set("https://github.com/WURFL/wurfl-microservice-client-kotlin/tree/master")
                }
            }

            repositories {
                // Use mavenLocal only if you want to install your development artifact into a maven local repo
                // using publishToMavenLocal command
                //mavenLocal()
                maven {
                    url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                    credentials {
                        username = project.findProperty("mavenCentralUsername").toString()
                        password = project.findProperty("mavenCentralPassword").toString()
                    }
                }
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

signing {
    sign(publishing.publications)
}
