import sbt.Keys.{publishMavenStyle, scalaVersion}
import scala.util.Try

name := "ergo-appkit"

lazy val sonatypePublic = "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/"
lazy val sonatypeReleases = "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
lazy val sonatypeSnapshots = "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

lazy val commonSettings = Seq(
  organization := "org.ergoplatform",
  version := "mixer-appkit-SNAPSHOT",
  scalaVersion := "2.12.10",
  resolvers ++= Seq(sonatypeReleases,
    "SonaType" at "https://oss.sonatype.org/content/groups/public",
    "Typesafe maven releases" at "https://dl.bintray.com/typesafe/maven-releases/",
    sonatypeSnapshots,
    Resolver.mavenCentral),
  libraryDependencies += "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.3",
  licenses := Seq("CC0" -> url("https://creativecommons.org/publicdomain/zero/1.0/legalcode")),
  description := "Ergo Mixer Web Application",
  publishArtifact in (Compile, packageSrc) := true,
  publishArtifact in (Compile, packageDoc) := true,
  publishMavenStyle := true,
  publishTo := sonatypePublishToBundle.value
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

// set bytecode version to 8 to fix NoSuchMethodError for various ByteBuffer methods
// see https://github.com/eclipse/jetty.project/issues/3244
// these options applied only in "compile" task since scalac crashes on scaladoc compilation with "-release 8"
// see https://github.com/scala/community-builds/issues/796#issuecomment-423395500
//scalacOptions in(Compile, compile) ++= Seq("-release", "8")
assemblyJarName in assembly := s"ergo-appkit-${version.value}.jar"

assemblyMergeStrategy in assembly := {
  case "logback.xml" => MergeStrategy.first
  case "module-info.class" => MergeStrategy.discard
  case other => (assemblyMergeStrategy in assembly).value(other)
}

lazy val allConfigDependency = "compile->compile;test->test"

val sigmaStateVersion = "i609-formal-verification-346717a7-SNAPSHOT"
val ergoWalletVersion = "appkit-wallet-f7f7d673-SNAPSHOT"

lazy val sigmaState = ("org.scorexfoundation" %% "sigma-state" % sigmaStateVersion).force()
  .exclude("ch.qos.logback", "logback-classic")
  .exclude("org.scorexfoundation", "scrypto")
  .exclude("org.typelevel", "machinist")
  .exclude("org.typelevel", "cats-kernel")

lazy val ergoWallet = "org.ergoplatform" %% "ergo-wallet" % ergoWalletVersion

lazy val mockWebServer = "com.squareup.okhttp3" % "mockwebserver" % "3.12.0" % "test"

libraryDependencies ++= Seq(
  sigmaState,
  ergoWallet,
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "org.scalacheck" %% "scalacheck" % "1.14.+" % "test",
  "com.squareup.retrofit2" % "retrofit" % "2.6.2",
  "com.squareup.retrofit2" % "converter-scalars" % "2.6.2",
  "com.squareup.retrofit2" % "converter-gson" % "2.6.2"
)

val apiClientDeps = Seq(
  "com.squareup.okhttp3" % "okhttp" % "3.12.0",
  "com.google.code.findbugs" % "jsr305" % "3.0.2",
  "io.gsonfire" % "gson-fire" % "1.8.3" % "compile",
  "io.swagger.core.v3" % "swagger-annotations" % "2.0.0",
  "com.squareup.retrofit2" % "retrofit" % "2.6.2",
  "com.squareup.retrofit2" % "converter-scalars" % "2.6.2",
  "com.squareup.retrofit2" % "converter-gson" % "2.6.2",
  "junit" % "junit" % "4.12" % "test",
)

lazy val javaClientGenerated = (project in file("java-client-generated"))
  .settings(
    commonSettings,
    name := "java-client-generated",
    crossPaths := false,
    libraryDependencies ++= apiClientDeps,
    publishArtifact in (Compile, packageDoc) := false,
    publish / skip := true
  )

lazy val common = (project in file("common"))
  .settings(
    commonSettings ++ testSettings,
    name := "common",
    resolvers ++= allResolvers,
    libraryDependencies ++= Seq(
      sigmaState,
      ergoWallet
    ),
    publish / skip := true
  )

lazy val libApi = (project in file("lib-api"))
  .dependsOn(common % allConfigDependency)
  .settings(
    commonSettings ++ testSettings,
    resolvers ++= allResolvers,
    name := "lib-api",
    libraryDependencies ++= Seq(
    ),
    publish / skip := true
  )

lazy val libImpl = (project in file("lib-impl"))
  .dependsOn(javaClientGenerated % allConfigDependency, libApi % allConfigDependency)
  .settings(
    commonSettings ++ testSettings,
    name := "lib-impl",
    resolvers ++= allResolvers,
    libraryDependencies ++= Seq(
    ),
    publish / skip := true
  )

lazy val appkit = (project in file("appkit"))
  .dependsOn(
    common % allConfigDependency,
    javaClientGenerated % allConfigDependency,
    libApi % allConfigDependency,
    libImpl % allConfigDependency)
  .settings(commonSettings ++ testSettings,
    libraryDependencies ++= Seq(mockWebServer))
  .settings(
    libraryDependencies += "net.snaq" % "dbpool" % "7.0.1",
    libraryDependencies += "org.bouncycastle" % "bcprov-jdk15on" % "1.61",
    libraryDependencies += "com.h2database" % "h2" % "1.4.199",
    libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.6.3"
  )
  .settings(
    libraryDependencies ++= (if (scalaBinaryVersion.value == "2.12")
      Seq(("org.scorexfoundation" %% "verified-contracts" % sigmaStateVersion).force()
        .exclude("ch.qos.logback", "logback-classic")
        .exclude("org.scorexfoundation", "scrypto")
        .exclude("org.typelevel", "machinist")
        .exclude("org.typelevel", "cats-kernel"))
    else
      Seq.empty)
  )
  .settings(publish / skip := true)

lazy val aggregateCompile = ScopeFilter(
  inProjects(common, javaClientGenerated, libApi, libImpl, appkit),
  inConfigurations(Compile))

lazy val rootSettings = Seq(
  sources in Compile := sources.all(aggregateCompile).value.flatten,
  libraryDependencies := libraryDependencies.all(aggregateCompile).value.flatten,
  mappings in (Compile, packageSrc) ++= (mappings in(Compile, packageSrc)).all(aggregateCompile).value.flatten,
  mappings in (Test, packageBin) ++= (mappings in(Test, packageBin)).all(aggregateCompile).value.flatten,
  mappings in(Test, packageSrc) ++= (mappings in(Test, packageSrc)).all(aggregateCompile).value.flatten
)

lazy val root = (project in file("."))
  .aggregate(appkit, common, javaClientGenerated, libApi, libImpl)
  .settings(commonSettings ++ testSettings, rootSettings)
  .settings(publish / aggregate := false)
  .settings(publishLocal / aggregate := false)
