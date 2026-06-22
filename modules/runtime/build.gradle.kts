plugins {
    id("shared")
    id("java-test-fixtures")
}

dependencies {
    implementation(project(":compiler"))
    implementation(project(":parser"))
    implementation(project(":stdlib"))
    implementation(libs.bundles.asm)

    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.jspecify)
    testFixturesApi(project(":parser"))
    testFixturesApi(project(":analysis"))
    testFixturesApi(project(":tool-common"))
}
