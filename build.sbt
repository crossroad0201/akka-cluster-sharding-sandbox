import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._

lazy val AkkaVersion = "2.6.15"
lazy val AkkaManagementVersion = "1.1.1"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    organization := "crossroad0201",
    name := "akka-cluster-sharding-sandbox",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := "2.13.1",

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion,

      "ch.qos.logback" % "logback-classic" % "1.2.3",

      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,

      "org.scalatest" %% "scalatest" % "3.1.0" % Test
    ),

    Docker / packageName := s"crossroad0201/akka-cluster-sharding-sandbox",
    dockerBaseImage := "adoptopenjdk/openjdk8",
    dockerUpdateLatest := true
  )
