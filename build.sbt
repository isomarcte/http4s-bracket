import ReleaseTransformations._

// Constants //

lazy val scala212      = "2.12.12"
lazy val scala213      = "2.13.3"
lazy val scalaVersions = Set(scala212, scala213)
lazy val projectName   = "http4s-bracket"
lazy val projectUrl    = url("https://github.com/isomarcte/http4s-bracket")

// Groups //

lazy val chrisDavenportG  = "io.chrisdavenport"
lazy val fs2G             = "co.fs2"
lazy val http4sG          = "org.http4s"
lazy val organizeImportsG = "com.github.liancheng"
lazy val scalatestG       = "org.scalatest"
lazy val typelevelG       = "org.typelevel"

// Artifacts //

lazy val catsCoreA        = "cats-core"
lazy val catsEffectA      = "cats-effect"
lazy val catsKernelA      = "cats-kernel"
lazy val fs2CoreA         = "fs2-core"
lazy val http4sCirceA     = "http4s-circe"
lazy val http4sClientA    = "http4s-client"
lazy val http4sCoreA      = "http4s-core"
lazy val http4sServerA    = "http4s-server"
lazy val organizeImportsA = "organize-imports"
lazy val scalatestA       = "scalatest"
lazy val vaultA           = "vault"

// Versions //

lazy val catsEffectV      = "2.2.0"
lazy val catsV            = "2.2.0"
lazy val fs2V             = "2.4.5"
lazy val http4sV          = "0.21.11"
lazy val organizeImportsV = "0.4.0"
lazy val scalatestV       = "3.2.3"
lazy val vaultV           = "2.0.0"

// Common Settings

inThisBuild(
  List(
    organization := "io.isomarcte",
    scalaVersion := scala213,
    scalafixScalaBinaryVersion := "2.13",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalafixDependencies ++= List(organizeImportsG %% organizeImportsA % organizeImportsV),
    doc / scalacOptions --= List("-Werror", "-Xfatal-warnings"),
    scalacOptions ++= List("-target:jvm-1.8")
  )
)

lazy val commonSettings = List(
  scalaVersion := scala213,
  crossScalaVersions := scalaVersions.toSeq,
  addCompilerPlugin(typelevelG    % "kind-projector"     % "0.11.1" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  autoAPIMappings := true
)

// Publish Settings //

lazy val publishSettings = List(
  homepage := Some(projectUrl),
  licenses := Seq("BSD3" -> url("https://opensource.org/licenses/BSD-3-Clause")),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ =>
    false
  },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  scmInfo := Some(ScmInfo(projectUrl, "scm:git:git@github.com:isomarcte/http4s-bracket.git")),
  developers :=
    List(Developer("isomarcte", "David Strawn", "isomarcte@gmail.com", url("https://github.com/isomarcte"))),
  credentials += Credentials(Path.userHome / ".sbt" / ".credentials"),
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value
)

// Release Process //

releaseProcess :=
  Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    releaseStepCommandAndRemaining("+publishSigned"),
    commitReleaseVersion,
    tagRelease,
    setNextVersion,
    commitNextVersion,
    pushChanges
  )

// Root //

lazy val root = (project in file("."))
  .settings(commonSettings, publishSettings)
  .settings(List(name := projectName))
  .aggregate(core)
  .enablePlugins(ScalaUnidocPlugin)

// Core //

lazy val core = project
  .settings(commonSettings, publishSettings)
  .settings(
    name := s"${projectName}-core",
    libraryDependencies ++=
      List(
        chrisDavenportG %% vaultA        % vaultV,
        fs2G            %% fs2CoreA      % fs2V,
        http4sG         %% http4sServerA % http4sV,
        http4sG         %% http4sCoreA   % http4sV,
        typelevelG      %% catsCoreA     % catsV,
        typelevelG      %% catsEffectA   % catsEffectV,
        typelevelG      %% catsKernelA   % catsV,
        http4sG         %% http4sCirceA  % http4sV    % Test,
        http4sG         %% http4sClientA % http4sV    % Test,
        scalatestG      %% scalatestA    % scalatestV % Test
      )
  )
