plugins {
    id("org.jetbrains.kotlin.jvm")
}

val rdLibDirectory: () -> File by rootProject.extra
val productMonorepoDir: File? by rootProject.extra

repositories {
    mavenCentral()
    maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
    if (productMonorepoDir == null) {
        flatDir {
            dir(rdLibDirectory())
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceSets {
            main {
                java {
                    if (productMonorepoDir != null) {
                        srcDir(
                            listOf(
                                File("$productMonorepoDir/Rider/Frontend/rider/model/sources"),
                                File("$productMonorepoDir/Rider/ultimate/remote-dev/rd-ide-model-sources"),
                            )
                        )
                    }
                    srcDir("src/main/kotlin")
                }
            }
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    if (productMonorepoDir == null) {
        implementation(group = "", name = "rd-gen")
        implementation(group = "", name = "rider-model")
    }
}