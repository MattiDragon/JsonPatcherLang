plugins {
    id("shared")
}

dependencies {
    api(project(":ast"))
    testImplementation(testFixtures(project(":runtime")))
}
