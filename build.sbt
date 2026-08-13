val scala3Version = "3.7.4"

ThisBuild / organization := "com.risquanter"
ThisBuild / version      := "0.13.0"
ThisBuild / scalaVersion := scala3Version

// POM metadata required by the Maven Central Publisher Portal
ThisBuild / description := "First-order logic engine with vague quantifiers (probabilistic semantics after Fermüller et al. 2016)"
ThisBuild / homepage    := Some(url("https://github.com/risquanter/vql-engine"))
ThisBuild / licenses    := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / developers := List(
  Developer("fixbits", "Daniel Agota", "danago@risquanter.com", url("https://github.com/risquanter"))
)
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/risquanter/vql-engine"),
    "scm:git:git@github.com:risquanter/vql-engine.git"
  )
)
ThisBuild / versionScheme := Some("early-semver")

// `sbt publish` stages a Maven-layout bundle under target/bundle; CI signs it
// and the release workflow uploads the zip to the Central Portal via REST.
ThisBuild / publishTo := Some(
  "central-bundle" at ((LocalRootProject / baseDirectory).value / "target" / "bundle").toURI.toString
)

lazy val root = project
  .in(file("."))
  .aggregate(folEngine.jvm, folEngine.js)
  .settings(
    publish / skip := true
  )

lazy val folEngine = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "vql-engine",
    libraryDependencies ++= Seq(
      "org.scalameta"  %%% "munit"   % "1.0.0"          % Test,
      "com.risquanter" %%% "hdr-rng" % "0.1.0"
    )
  )
