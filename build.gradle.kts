import java.net.Socket
import org.gradle.api.tasks.JavaExec

plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("grouph.make-keystores")
}

group = "grouph"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("grouph.Main")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
}

tasks.test {
    useJUnitPlatform()
}

 // - O.


tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    manifest {
        attributes["Main-Class"] = "grouph.Main"
    }
}

val obtainKeystores by tasks.registering {
    dependsOn("makeKeystores")
}

val generateCsr by tasks.registering(JavaExec::class) {
    mainClass.set("grouph.core.CertHandler")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf()
    dependsOn(obtainKeystores)
}


if (System.getenv("GENERATE_CSR")?.toBoolean() == true) {
    tasks.named("build") {
        dependsOn(generateCsr)
    }

    tasks.named("run") {
        dependsOn(generateCsr)
    }

    tasks.withType<Test>().configureEach {
        dependsOn(generateCsr)
    }
}
