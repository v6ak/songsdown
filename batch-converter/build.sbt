name := "batch-converter"

version := "1.0"

scalaVersion := "3.2.2"

val monocleVersion = "3.2.0"

libraryDependencies ++= Seq(
  "dev.optics"  %%  "monocle-core"    % monocleVersion,
  "dev.optics"  %%  "monocle-macro"   % monocleVersion,
  "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4",
  "org.scalactic" %% "scalactic" % "3.2.15" % "test",
  "org.scalatest" %% "scalatest" % "3.2.15" % "test",
)

libraryDependencies += "com.github.scopt" %% "scopt" % "4.1.0"

scalacOptions ++= Seq("-feature", "-deprecation")

assembly / assemblyJarName := "batch-converter.jar"
