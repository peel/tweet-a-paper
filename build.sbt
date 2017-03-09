name := "Tweet-A-Paper"
organization := "com.codearsonist"
version      := "0.1.0-SNAPSHOT"
scalaVersion := "2.11.8"
herokuAppName in Compile := "tweet-a-paper"
herokuProcessTypes in Compile := Map(
  "console" -> "target/universal/stage/bin/tweet-a-paper"
)

enablePlugins(JavaAppPackaging)

lazy val fs2Version = "0.9.4"
lazy val http4sV = "0.15.5"
lazy val circeV = "0.6.1"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl"          % http4sV,
  "org.http4s" %% "http4s-blaze-client" % http4sV,
  "org.http4s" %% "http4s-circe" % http4sV,
  "com.danielasfregola" %% "twitter4s" % "5.0",
  "io.circe" %% "circe-generic" % circeV,
  "co.fs2" %% "fs2-core" % fs2Version
)
