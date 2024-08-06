import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    `jvm-test-suite`
    idea
    `maven-publish`
    signing
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.versions)
    alias(libs.plugins.release)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xopt-in=kotlin.RequiresOptIn")
    }
}

group = "com.github.thake.avro4k"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://packages.confluent.io/maven/")
}

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        compileClasspath += sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.test.get().output
        kotlin.srcDirs("src/integrationTest/kotlin")
        resources.srcDirs("src/integrationTest/resources")
    }
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

val integrationTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

val integrationTest = tasks.register("integrationTest", Test::class) {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
}

dependencies {
    api(libs.kafka.avro.serde)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlibJdk8)
    implementation(libs.kotlinx.serialization)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.avro)
    implementation(libs.kafka.avro.serializer)
    implementation(libs.avro4k)
    implementation(libs.classgraph)
    implementation(libs.retry)
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.runtime)
    integrationTestImplementation(libs.bundles.logging)
    integrationTestImplementation(libs.bundles.integrationTest)

}
// Configure existing Dokka task to output HTML to typical Javadoc directory
tasks.dokkaHtml.configure {
    outputDirectory.set(layout.buildDirectory.dir("javadoc").get().asFile)
}

// Create dokka Jar task from dokka task output
val dokkaJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Kotlin docs with Dokka"
    archiveClassifier.set("javadoc")
    // dependsOn(tasks.dokka) not needed; dependency automatically inferred by from(tasks.dokka)
    from(tasks.dokkaHtml)
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}

tasks {
    named<Copy>("processIntegrationTestResources") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    check {
        dependsOn(integrationTest)
    }
    beforeReleaseBuild {
        dependsOn(integrationTest)
    }

    idea {
        module {
            isDownloadSources = true
            isDownloadJavadoc = false
        }
    }
}

// Create sources Jar from main kotlin sources
val sourcesJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles sources JAR"
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}
publishing{
    repositories{
        maven{
            name = "mavenCentral"
            url = if (project.isSnapshot) {
                uri("https://oss.sonatype.org/content/repositories/snapshots/")
            } else {
                uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            }
            credentials {
                username = project.findProperty("ossrhUsername") as? String
                password = project.findProperty("ossrhPassword") as? String
            }
        }
    }
    publications{
        create<MavenPublication>("mavenJava"){
            from(components["java"])
            artifact(sourcesJar)
            artifact(dokkaJar)
            //artifact(javadocJar.get())
            pom{
                name.set("Kafka serializer using avro4k")
                description.set("Provides Kafka SerDes and Serializer / Deserializer implementations for avro4k")
                url.set("https://github.com/thake/avro4k-kafka-serializer")
                developers {
                    developer {
                        name.set("Thorsten Hake")
                        email.set("mail@thorsten-hake.com")
                    }
                }
                scm {
                    connection.set("https://github.com/thake/avro4k-kafka-serializer.git")
                    developerConnection.set("scm:git:ssh://github.com:thake/avro4k-kafka-serializer.git")
                    url.set("https://github.com/thake/avro4k-kafka-serializer")
                }
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    isRequired = !isSnapshot
    sign(publishing.publications["mavenJava"])
}

tasks.named("afterReleaseBuild") {
    dependsOn("publish")
}

inline val Project.isSnapshot
    get() = version.toString().endsWith("-SNAPSHOT")
