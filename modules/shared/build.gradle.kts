plugins {
    id("shared")
    id("java-test-fixtures")
}

dependencies { 
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesApi(project(":parser"))
    testFixturesApi(project(":legacy-runtime"))
}