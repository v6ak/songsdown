name := "chordbook-processing"

version := "1.0-SNAPSHOT"

lazy val parser = project in file("parser")

lazy val batchConverter = project in file("batch-converter") dependsOn parser

lazy val root = (project in file(".")).dependsOn(parser)

scalaVersion := "3.2.2"

scalacOptions ++= Seq("-feature", "-deprecation")
