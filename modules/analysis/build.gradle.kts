plugins {
    id("shared")
}

dependencies {
    api(project(":ast"))
    api(project(":shared-runtime"))
    testImplementation(testFixtures(project(":shared-runtime")))
}
