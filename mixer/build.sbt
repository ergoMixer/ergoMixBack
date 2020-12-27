import sbt.Keys.{publishMavenStyle, scalaVersion}

name := "ergoMixer"

lazy val sonatypePublic = "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/"
lazy val sonatypeReleases = "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
lazy val sonatypeSnapshots = "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies += filters
libraryDependencies ++= Seq(evolutions, jdbc)

lazy val commonSettings = Seq(
  organization := "ergoMixer",
  version := "3.2.0",
  scalaVersion := "2.12.10",
  resolvers ++= Seq(sonatypeReleases,
    "SonaType" at "https://oss.sonatype.org/content/groups/public",
    "Typesafe maven releases" at "https://dl.bintray.com/typesafe/maven-releases/",
    sonatypeSnapshots,
    Resolver.mavenCentral),
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

fullClasspath in assembly += Attributed.blank(PlayKeys.playPackageAssets.value)

assemblyMergeStrategy in assembly := {
  case PathList("reference.conf") => MergeStrategy.concat
  case manifest if manifest.contains("MANIFEST.MF") => MergeStrategy.discard
  case manifest if manifest.contains("module-info.class") => MergeStrategy.discard
  case referenceOverrides if referenceOverrides.contains("reference-overrides.conf") => MergeStrategy.concat
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

assemblyJarName in assembly := s"${name.value}-${version.value}.jar"

lazy val mockWebServer = "com.squareup.okhttp3" % "mockwebserver" % "3.12.0" % "test"

libraryDependencies ++= Seq(
  mockWebServer,
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test,
  "com.h2database" % "h2" % "1.4.199",
  "org.ergoplatform" %% "ergo-appkit" % "mixer-appkit-SNAPSHOT",
  "org.mockito" % "mockito-core" % "3.3.0",
  "org.apache.commons" % "commons-lang3" % "3.11",
  "org.webjars" % "swagger-ui" % "3.38.0"
)

enablePlugins(JDKPackagerPlugin)
(antPackagerTasks in JDKPackager) := Some(file(sys.env.getOrElse("ANT_PATH", "/usr/lib/jvm/java-8-oracle/lib/ant-javafx.jar")))

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, BuildInfoPlugin)
  .settings(commonSettings ++ testSettings, libraryDependencies ++= Seq(guice))
  .settings(publish / aggregate := false)
  .settings(publishLocal / aggregate := false)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "info"
  )

Compile / unmanagedResourceDirectories += baseDirectory.value / "resources"
