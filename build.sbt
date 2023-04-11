name := "chordbook-processing"

version := "1.0-SNAPSHOT"

lazy val parser = project.in(file("parser"))
  .enablePlugins(ScalaJSPlugin) // Scala.JS + JVM

lazy val batchConverter = project.in(file("batch-converter"))
  .dependsOn(parser)
  .settings(
    assembly / assemblyExcludedJars := {
      // Exclude Scala.JS dependencies from JAR
      (assembly / fullClasspath).value filter { file =>
        file.data.name.startsWith("scalajs-") || file.data.name.startsWith("scala3-library_sjs")
      }
    }
  )

lazy val webEditor = project.in(file("web-editor"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    // for an application with a main method
    scalaJSUseMainModuleInitializer := true,
  )
  .dependsOn(parser)

lazy val root = (project in file(".")).dependsOn(parser)

scalaVersion := "3.2.2"

scalacOptions ++= Seq("-feature", "-deprecation")
