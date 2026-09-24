plugins {
    application
}

group = "net.illunium"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.dv8tion:JDA:5.6.1")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("org.yaml:snakeyaml:2.4")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "net.illunium.lumen.Lumen"
    // sqlite-jdbc loads a native library; Java restricts that by default from 24 on.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
