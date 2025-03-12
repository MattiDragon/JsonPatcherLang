rootProject.name = "JsonPatcherLang"

fun module(name: String) {
    include(":$name")
    project(":$name").projectDir = file("modules/$name")
}

module("benchmark")
module("parser")
module("compiler")
module("runtime")
module("ast")
module("analysis")

fun tool(name: String) {
    include(":$name")
    project(":$name").projectDir = file("tools/$name")
}

tool("doctool")
tool("lang-server")
tool("formatter")
