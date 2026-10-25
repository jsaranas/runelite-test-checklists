plugins {
    java
}

group = "com.testchecklists"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    maven {
        url = uri("https://repo.runelite.net")
        content { includeGroupByRegex("net\\.runelite.*") }
    }
    mavenCentral()
}

val runeLiteVersion = "latest.release"

dependencies {
    compileOnly("net.runelite:client:$runeLiteVersion")
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveFileName.set("test-checklists-${project.version}.jar")
    manifest {
        attributes["Implementation-Title"] = "Test Checklists"
        attributes["Implementation-Version"] = project.version
    }
}
