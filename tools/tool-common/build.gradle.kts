plugins {
    id("shared")
}

dependencies {
    api(project(":doctool"))
    api(project(":analysis"))
    api(project(":stdlib"))
}
