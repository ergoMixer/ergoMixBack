import sbt.Keys.{publishMavenStyle, scalaVersion}

name := "ergoMixer"

lazy val sonatypePublic = "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/"
lazy val sonatypeReleases = "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
lazy val sonatypeSnapshots = "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies += filters

lazy val commonSettings = Seq(
  organization := "ergoMixer",
  version := "1.0.1",
  scalaVersion := "2.12.10",
  resolvers ++= Seq(sonatypeReleases,
    "SonaType" at "https://oss.sonatype.org/content/groups/public",
    "Typesafe maven releases" at "http://repo.typesafe.com/typesafe/maven-releases/",
    sonatypeSnapshots,
    Resolver.mavenCentral),
  libraryDependencies += "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.3",
  licenses := Seq("CC0" -> url("https://creativecommons.org/publicdomain/zero/1.0/legalcode")),
  description := "Ergo Mixer Web Application",
  publishArtifact in (Compile, packageSrc) := true,
  publishArtifact in (Compile, packageDoc) := true,
  publishMavenStyle := true,
  publishTo := sonatypePublishToBundle.value,
)

val testingDependencies = Seq(
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "org.scalacheck" %% "scalacheck" % "1.14.+" % "test"
)

lazy val testSettings = Seq(
  libraryDependencies ++= testingDependencies,
  parallelExecution in Test := false,
  baseDirectory in Test := file("."),
  publishArtifact in Test := true,
  publishArtifact in(Test, packageSrc) := true,
  publishArtifact in(Test, packageDoc) := false,
  test in assembly := {})


lazy val allResolvers = Seq(
  sonatypePublic,
  sonatypeReleases,
  sonatypeSnapshots,
  Resolver.mavenCentral
)

publishArtifact in Compile := true
publishArtifact in Test := true


assemblyMergeStrategy in assembly := {
  case "logback.xml" => MergeStrategy.first
  case PathList("META-INF", _ @ _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

fullClasspath in assembly += Attributed.blank(PlayKeys.playPackageAssets.value)

assemblyMergeStrategy in assembly := {
  case PathList("reference.conf") => MergeStrategy.concat
  case manifest if manifest.contains("MANIFEST.MF") =>
    // We don't need the manifest files since sbt-assembly will create one with the given settings
    MergeStrategy.discard
  case referenceOverrides if referenceOverrides.contains("reference-overrides.conf") =>
    // Keep the content for all reference-overrides.conf files
    MergeStrategy.concat
  case x =>
    // For all the other files, use the default sbt-assembly merge strategy
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

assemblyJarName in assembly := s"${name.value}-${version.value}.jar"

lazy val allConfigDependency = "compile->compile;test->test"

val sigmaStateVersion = "i609-formal-verification-346717a7-SNAPSHOT"

lazy val sigmaState = ("org.scorexfoundation" %% "sigma-state" % sigmaStateVersion).force()
  .exclude("ch.qos.logback", "logback-classic")
  .exclude("org.scorexfoundation", "scrypto")
  .exclude("org.typelevel", "machinist")
  .exclude("org.typelevel", "cats-kernel")


lazy val mockWebServer = "com.squareup.okhttp3" % "mockwebserver" % "3.12.0" % "test"

libraryDependencies ++= Seq(
  sigmaState,
  mockWebServer,
  "org.scalaj" %% "scalaj-http" % "2.4.2",
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "org.scalacheck" %% "scalacheck" % "1.14.+" % "test",
  "com.squareup.retrofit2" % "retrofit" % "2.6.2",
  "com.squareup.retrofit2" % "converter-scalars" % "2.6.2",
  "com.squareup.retrofit2" % "converter-gson" % "2.6.2",
  "org.webjars" %% "webjars-play" % "2.8.0",
  "com.typesafe.play" %% "play-json" % "2.4.2",
  "org.webjars" % "swagger-ui" % "3.25.1",
  "org.xerial" % "sqlite-jdbc" % "3.30.1",
  "org.ergoplatform" %% "ergo-appkit" % "mixer-appkit-SNAPSHOT"
)

libraryDependencies += guice

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, BuildInfoPlugin)
  .settings(commonSettings ++ testSettings, libraryDependencies ++= Seq(guice))
  .settings(publish / aggregate := false)
  .settings(publishLocal / aggregate := false)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "info"
  )
