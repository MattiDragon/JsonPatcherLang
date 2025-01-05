plugins {
    `java-library`
    id("me.champeau.jmh") version "0.7.2"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.jspecify)

    implementation(project(":bytecode-runtime"))
    implementation(project(":legacy-runtime"))
    implementation(project(":parser"))
    constraints {
        implementation(libs.bundles.asm)
        jmh(libs.bundles.asm)
    }
}