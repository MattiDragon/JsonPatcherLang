rootProject.name = "JsonPatcherLang"

include(":doctool")
project(":doctool").projectDir = file("tools/doctool")
include(":lang-server")
project(":lang-server").projectDir = file("tools/lang-server")