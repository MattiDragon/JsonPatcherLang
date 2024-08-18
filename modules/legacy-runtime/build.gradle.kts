plugins {
    id("shared")
}

dependencies {
    api(project(":shared"))
    testImplementation(testFixtures(project(":shared")))
}