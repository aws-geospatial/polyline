import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("java-library")
    alias(libs.plugins.jetbrainsKotlinJvm)
    id("com.vanniktech.maven.publish") version "0.27.0"
}


mavenPublishing {
    publishToMavenCentral(SonatypeHost.S01, automaticRelease = true)
    signAllPublications()

    coordinates("software.amazon.location", "polyline", "0.1.1")

    pom {
        name.set("Polyline library for Kotlin")
        description.set("A library for encoding and decoding polylines.")
        inceptionYear.set("2024")
        url.set("https://github.com/aws-geospatial/polyline")
        licenses {
            license {
                name.set("MIT No Attribution License")
                url.set("https://opensource.org/license/mit-0")
                distribution.set("https://opensource.org/license/mit-0")
            }
        }
        developers {
            developer {
                id.set("aws-geospatial")
                name.set("AWS Geospatial")
                url.set("https://github.com/aws-geospatial")
            }
        }
        scm {
            url.set("https://github.com/aws-geospatial/polyline")
            connection.set("scm:git:git://github.com/aws-geospatial/polyline")
            developerConnection.set("scm:git:ssh://git@github.com/aws-geospatial/polyline")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
     jvmToolchain(17)
}

dependencies {
    // Other dependencies.
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
    testImplementation("com.google.code.gson:gson:2.8.5")
}

tasks.test {
    useJUnitPlatform()
}
