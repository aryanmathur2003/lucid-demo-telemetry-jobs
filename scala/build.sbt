ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "demo.telemetry"
ThisBuild / version := "0.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "telemetry-jobs",
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-text" % "1.9",
      "com.lihaoyi" %% "ujson" % "3.3.1",
      "org.scalameta" %% "munit" % "1.0.0" % Test
    ),
    scalacOptions ++= Seq("-deprecation", "-feature", "-Xlint:unused")
  )
