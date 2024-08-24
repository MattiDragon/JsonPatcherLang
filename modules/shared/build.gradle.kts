plugins {
    id("shared")
    id("java-test-fixtures")
}

dependencies { 
    testFixturesImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testFixturesApi(project(":parser"))
    testFixturesApi(project(":legacy-runtime"))
}