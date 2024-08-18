rootProject.name = "JsonPatcherLang"

include(":shared")
project(":shared").projectDir = file("modules/shared")
include(":parser")
project(":parser").projectDir = file("modules/parser")
include(":legacy-runtime")
project(":legacy-runtime").projectDir = file("modules/legacy-runtime")

include(":doctool")
project(":doctool").projectDir = file("tools/doctool")
include(":lang-server")
project(":lang-server").projectDir = file("tools/lang-server")