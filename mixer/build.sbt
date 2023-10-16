import sbt.Keys.{publishMavenStyle, scalaVersion}

name := "ergoMixer"

lazy val sonatypeSnapshots = "Sonatype Snapshots".at("https://oss.sonatype.org/content/repositories/snapshots/")

libraryDependencies += filters

lazy val commonSettings = Seq(
  organization := "ergoMixer",
  version := "4.4.0",
  scalaVersion := "2.12.10",
  resolvers ++= Seq(
    Resolver.sonatypeRepo("public"),
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    Resolver.typesafeRepo("releases"),
    Resolver.mavenCentral
  ),
  description := "Ergo Mixer Web Application",
  publishArtifact in (Compile, packageSrc) := true,
  publishArtifact in (Compile, packageDoc) := true,
  publishMavenStyle := true,
  publishTo := sonatypePublishToBundle.value,
)

val testingDependencies = Seq(
  "org.scalatest"     %% "scalatest"       % "3.2.9"   % "test",
  "org.scalatestplus" %% "scalacheck-1-15" % "3.2.9.0" % "test",
  "org.scalatestplus" %% "mockito-3-4"     % "3.2.9.0" % "test"
)

lazy val testSettings = Seq(
  libraryDependencies ++= testingDependencies,
  parallelExecution in Test := false,
  baseDirectory in Test := file("."),
  publishArtifact in Test := true,
  publishArtifact in (Test, packageSrc) := true,
  publishArtifact in (Test, packageDoc) := false,
  test in assembly := {}
)

lazy val scalafmtSettings = Seq(
  scalafmtOnCompile := true
)

publishArtifact in Compile := true
publishArtifact in Test := true

fullClasspath in assembly += Attributed.blank(PlayKeys.playPackageAssets.value)

assemblyMergeStrategy in assembly := {
  case "logback.xml"                                                                 => MergeStrategy.first
  case PathList("reference.conf")                                                    => MergeStrategy.concat
  case manifest if manifest.contains("MANIFEST.MF")                                  => MergeStrategy.discard
  case manifest if manifest.contains("module-info.class")                            => MergeStrategy.discard
  case referenceOverrides if referenceOverrides.contains("reference-overrides.conf") => MergeStrategy.concat
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

assemblyJarName in assembly := s"${name.value}-${version.value}.jar"

lazy val mockWebServer = "com.squareup.okhttp3" % "mockwebserver" % "3.12.0" % "test"

libraryDependencies ++= Seq(
  mockWebServer,
  "org.scalatestplus.play" %% "scalatestplus-play"    % "4.0.3" % Test,
  "com.h2database"          % "h2"                    % "1.4.199",
  "org.ergoplatform"       %% "ergo-appkit"           % "5.0.0",
  "org.mockito"             % "mockito-core"          % "3.3.0",
  "org.apache.commons"      % "commons-lang3"         % "3.11",
  "org.webjars"             % "swagger-ui"            % "3.38.0",
  "com.typesafe.play"      %% "play-slick"            % "4.0.0",
  "com.typesafe.play"      %% "play-slick-evolutions" % "4.0.0"
)

enablePlugins(JDKPackagerPlugin)
(antPackagerTasks in JDKPackager) := (antPackagerTasks in JDKPackager).value orElse {
  for {
    f <- System.getProperty("os.name").toLowerCase match {
         case win if win.contains("win")  => Some(file(sys.env("JAVA_HOME") ++ "\\lib\\ant-javafx.jar"))
         case osName => Some(file(sys.env("JAVA_HOME") ++ "/lib/ant-javafx.jar"))
         } if f.exists()
  } yield f
}

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, BuildInfoPlugin)
  .settings(commonSettings ++ testSettings ++ scalafmtSettings, libraryDependencies ++= Seq(guice))
  .settings(publish / aggregate := false)
  .settings(publishLocal / aggregate := false)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "info"
  )

Compile / unmanagedResourceDirectories += baseDirectory.value / "resources"

javaOptions in test += "-Dlogger.resource=test/resources/logback-test.xml"
