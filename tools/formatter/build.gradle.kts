plugins {
    id("shared")
}

dependencies {
    implementation(project(":runtime"))
    implementation(project(":parser"))
    implementation(project(":analysis"))
    testImplementation(testFixtures(project(":runtime")))
}
