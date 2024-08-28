plugins {
    id("shared")
}

dependencies {
    api(project(":shared"))
    implementation(libs.bundles.asm)
    testImplementation(testFixtures(project(":shared")))
}