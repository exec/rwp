plugins {
    `java-library`
    `maven-publish`
}

group = "io.github.exec"
version = "0.1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies { api("com.google.code.gson:gson:2.14.0") }

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

val examples = sourceSets.create("examples") {
    java.srcDir("examples")
    compileClasspath += sourceSets.main.get().output + configurations.getByName("compileClasspath")
    runtimeClasspath += output + compileClasspath
}

tasks.register<JavaExec>("runExample") {
    group = "application"
    description = "Run the small Wait worker against a draft RWP host"
    classpath = examples.runtimeClasspath
    mainClass = "io.github.exec.rwp.examples.WaitWorker"
}

tasks.register<JavaExec>("selfTest") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "io.github.exec.rwp.EnvelopeSelfTest"
    jvmArgs("-ea")
}

tasks.named<Test>("test") { enabled = false } // The dependency-free selfTest is the test runner.
tasks.named("check") { dependsOn("selfTest") }

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name = "RWP Java"
                description = "Draft Redstone Worker Protocol envelope and session helpers"
                url = "https://github.com/exec/rwp"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
            }
        }
    }
}
