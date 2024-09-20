import groovy.util.Node
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.*

apply{
    plugin("maven-publish")
}
val localProperties = Properties().apply {
    val file = project.rootProject.file("local.properties")
    if (file.exists()) {
        load(FileInputStream(file))
    }
}
val bintrayUser: String = System.getenv("MAVEN_PASSWORD") ?: localProperties.getProperty("maven-username") ?: ""
val bintrayApiKey: String = System.getenv("MAVEN_PASSWORD") ?: localProperties.getProperty("maven-password") ?: ""
val siteUrl = "http://192.168.2.20:8081/nexus/content/repositories/release"
//val siteUrl = "http://192.168.2.20:90/groups/android"
val libraryDescription: String by lazy {
    project.description ?: project.name
}
val licenseName = "Apache License"
val licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0"
val gitUrl = "http://192.168.2.20:90/groups/android"

val GROUP_ID: String by lazy {
    if (project.extra.has("PUBLISH_GROUP_ID")) {
        project.extra["PUBLISH_GROUP_ID"] as String
    } else {
        ""
    }
}
val ARTIFACT_ID: String by lazy {
    if (project.extra.has("PUBLISH_ARTIFACT_ID")) {
        project.extra["PUBLISH_ARTIFACT_ID"] as String
    } else {
        ""
    }
}
val VERSION_NAME: String by lazy {
    if (project.extra.has("PUBLISH_VERSION")) {
        project.extra["PUBLISH_VERSION"] as String
    } else {
        ""
    }
}
val POM_PACKAGING: String by lazy {
    if (project.extra.has("POM_PACKAGING")) {
        project.extra["POM_PACKAGING"] as String
    } else {
        ""
    }
}
val PUBLISH_ARTIFACTS: PublishArtifact? by lazy {
    if (project.extra.has("PUBLISH_ARTIFACTS")) {
        project.extra["PUBLISH_ARTIFACTS"] as PublishArtifact
    } else {
        null
    }
}


configure<PublishingExtension> {

    publications {
        create<MavenPublication>("Nexus") {
            // 添加source
//            artifact(tasks.getByName("sourcesJar") as Jar)
            groupId = GROUP_ID
            artifactId = ARTIFACT_ID
            version = VERSION_NAME
            // 必须有这个 否则不会上传AAR包
            if (POM_PACKAGING == "aar") {
                afterEvaluate {
                    artifact(tasks.getByName("bundleReleaseAar"))
                    if(tasks.findByName("sourcesJar")!=null){
                        artifact(tasks.getByName("sourcesJar"))
                    }
                }
            } else if (POM_PACKAGING == "artifacts") {
                artifact(PUBLISH_ARTIFACTS)
            } else if (POM_PACKAGING == "jar") {
                from(components["java"])
            }

            pom {
                name.set(project.name)
                description.set(libraryDescription)
                url.set(siteUrl)
                packaging = if (POM_PACKAGING == "artifacts") "aar" else POM_PACKAGING

                generatePom(this)
            }
        }
        create<MavenPublication>("DebugNexus") {
            groupId = GROUP_ID
            artifactId = "$ARTIFACT_ID-debug"
            version = VERSION_NAME

            // 必须有这个 否则不会上传AAR包
            if (POM_PACKAGING == "aar") {
                afterEvaluate { artifact(tasks.getByName("bundleDebugAar")) }
                if(tasks.findByName("sourcesJar")!=null){
                    artifact(tasks.getByName("sourcesJar"))
                }
            } else if (POM_PACKAGING == "artifacts") {
                artifact(PUBLISH_ARTIFACTS)
            }


            pom {
                name.set(project.name)
                description.set(libraryDescription)
                url.set(siteUrl)
                packaging = if (POM_PACKAGING == "artifacts") "aar" else POM_PACKAGING

                generatePom(this)
            }
        }
    }

    repositories {
        maven {
            isAllowInsecureProtocol = true
            if (VERSION_NAME.endsWith("-SNAPSHOT")) {
                setUrl("http://192.168.2.20:8081/nexus/content/repositories/snapshots/")
            } else {
                setUrl("http://192.168.2.20:8081/nexus/content/repositories/release/")
            }
            credentials {
                username = bintrayUser
                password = bintrayApiKey
            }
        }
    }
}

fun appendNode(it: Dependency, dependenciesNode: Node) {
    if (it.group != null && (it.name != null || "unspecified" == it.name) && it.version != null) {
        val dependencyNode = dependenciesNode.appendNode("dependency")
        dependencyNode.appendNode("groupId", it.group)
        dependencyNode.appendNode("artifactId", it.name)
        dependencyNode.appendNode("version", it.version)
    }
}

fun generatePom(mavenPom: MavenPom) {
    mavenPom.licenses {
        license {
            name.set(licenseName)
            url.set(licenseUrl)
        }
    }
    mavenPom.scm {
        connection.set(gitUrl)
        developerConnection.set(gitUrl)
        url.set(siteUrl)
    }
    mavenPom.withXml {
        val root = asNode()
        val dependenciesNode = root.appendNode("dependencies")
        if (POM_PACKAGING == "aar" || POM_PACKAGING == "pom") {
            configurations.findByName("implementation")?.allDependencies?.forEach {
                appendNode(it, dependenciesNode)
            }
            configurations.findByName("releaseImplementation")?.allDependencies?.forEach {
                appendNode(it, dependenciesNode)
            }
        }
//        val os = ByteArrayOutputStream()
//        val result = exec {
//            executable = "git"
//            args = listOf("log", "--format=%aN %aE")
//            standardOutput = os
//        }
//        if (0 == result.exitValue) {
//            val developers = asNode().appendNode("developers")
//            val str = os?.let {
//                it.toString()
//            }
//            str.lines().distinct().forEach { line ->
//                val sp = line.lastIndexOf(" ")
//                if (sp > 0) {
//                    val id = line.substring(0, sp).trim()
//                    val email = line.substring(sp + 1).trim()
//                    developers.appendNode("developer").let {
//                        it.appendNode("id", id)
//                        it.appendNode("email", email)
//                    }
//                }
//            }
//        }
    }
}
