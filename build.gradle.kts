plugins {
    `java-library`
    `maven-publish`
}

version = project.property("version")!!
group = project.property("maven_group")!!
base.archivesName.set(project.property("archives_base_name") as String)
