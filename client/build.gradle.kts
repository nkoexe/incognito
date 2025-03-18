plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.guava)

    testImplementation(libs.junit.jupiter)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "org.example.Client"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
