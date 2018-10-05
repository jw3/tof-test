
lazy val `time-of-flight` =
  project
    .in(file("."))
    .aggregate(api, akka, scalaz)
    .settings(commonSettings: _*)
    .enablePlugins(GitVersioning)

lazy val api =
  project
    .in(file("api"))
    .settings(commonSettings: _*)
    .settings(name := "tof-api")

lazy val akka =
  project
    .in(file("akka"))
    .dependsOn(api)
    .settings(commonSettings: _*)
    .settings(akkaSettings: _*)
    .settings(name := "tof-akka")
    .enablePlugins(GitVersioning)

lazy val scalaz =
  project
    .in(file("scalaz"))
    .dependsOn(api)
    .settings(commonSettings: _*)
    .settings(name := "tof-scalaz")
    .enablePlugins(GitVersioning)

lazy val commonSettings = Seq(
  organization := "com.github.jw3",
  name := "time-of-flight",
  git.useGitDescribe := true,
  scalaVersion := "2.12.7",
  scalacOptions ++= Seq(
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-deprecation",
    "-language:postfixOps",
    "-language:implicitConversions",
    "-Ywarn-unused-import",
    //"-Xfatal-warnings",
    "-Xlint:_",
    //
    // resolve apparent proguard collision
    "-Yresolve-term-conflict:object"
  ),
  libraryDependencies ++= {
    lazy val scalatestVersion = "3.0.3"

    Seq(
      "com.github.jw3" % "pigpio-vl53l1x" % "0.1.1",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
      "org.scalactic" %% "scalactic" % scalatestVersion % Test,
      "org.scalatest" %% "scalatest" % scalatestVersion % Test,
    )
  }
)

lazy val akkaSettings = {
  libraryDependencies ++= Seq(
    "com.github.jw3" %% "pigpio-scala" % "0.1.1"
  )
}

lazy val dockerSettings = Seq(
  dockerBaseImage := sys.env.getOrElse("BASE_IMAGE", "openjdk:8"),
  dockerUpdateLatest := true
)
