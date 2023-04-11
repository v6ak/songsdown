import scala.meta._
import java.nio.file.Files.readString
import java.nio.charset.Charset.{forName => charset}

name := "web-editor"

version := "1.0"

scalaVersion := "3.2.2"

scalacOptions ++= Seq("-feature", "-deprecation")

libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.4.0"

Compile / sourceGenerators += Def.task {
  val fileContent = readString(
    ((Compile / resourceDirectory).value / "lorem-ipsum.txt").toPath,
    charset("utf-8")
  ).replace("voluptates | repudiandae", "voluptates repudiandae")
  val generatedTree =
    q"""
    package com.v6ak.songsdown {
      object FileContent {
        val LoremIpsum = $fileContent
      }
    }
  """

  val sourceFile = (Compile / sourceManaged).value / "com" / "v6ak" / "songsdown" / "FileContent.scala"
  IO.write(sourceFile, generatedTree.syntax)
  Seq(sourceFile)
}
