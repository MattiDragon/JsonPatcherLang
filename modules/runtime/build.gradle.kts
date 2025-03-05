plugins {
    id("shared")
}

dependencies {
    api(project(":shared-runtime"))
    implementation(project(":compiler"))
    implementation(project(":parser"))

    testImplementation(testFixtures(project(":shared-runtime")))
}