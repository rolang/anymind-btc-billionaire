ThisBuild / scalaVersion               := "2.13.8"
ThisBuild / name                       := "btc-billionaire"
ThisBuild / scalafmtCheck              := true
ThisBuild / scalafmtSbtCheck           := true
ThisBuild / semanticdbEnabled          := true
ThisBuild / semanticdbOptions += "-P:semanticdb:synthetics:on"
ThisBuild / semanticdbVersion          := scalafixSemanticdb.revision // use Scalafix compatible version
ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value)
ThisBuild / scalafixDependencies ++= List(
  "com.github.liancheng" %% "organize-imports" % "0.5.0",
  "com.github.vovapolu"  %% "scaluzzi"         % "0.1.21"
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("fix", "; all compile:scalafix test:scalafix; all scalafmtSbt scalafmtAll")
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll; compile:scalafix --check; test:scalafix --check")
addCommandAlias("updates", ";dependencyUpdates; reload plugins; dependencyUpdates; reload return")

// dependency versions
val akkaVersion           = "2.6.19"
val alpakkaKafkaVersion   = "2.0.7"
val circeVersion          = "0.14.2"
val logbackVersion        = "1.2.11"
val zioVersion            = "2.0.0"
val zioCatsInteropVersion = "3.3.0"
val refinedVersion        = "0.10.1"
val tapirVersion          = "1.0.1"
val http4sVersion         = "0.23.13"
val http4sBlazeVersion    = "0.23.12"
val zioConfigVersion      = "3.0.1"
val skunkVersion          = "0.3.1"
val embeddedKafkaVersion  = "3.2.0"
val pgEmbeddedVersion     = "1.0.1"

lazy val root =
  Project(id = "anymind-btc-billionaire", base = file("."))
    .settings(
      libraryDependencies += compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
      javacOptions ++= Seq("-source", "17", "-target", "17"),
      scalacOptions ++= Seq("-Ymacro-annotations", "-Xsource:3", "-target:17"),
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      (Test / parallelExecution)               := true,
      (Test / fork)                            := true,
      (Compile / doc / sources)                := Seq.empty,
      (Compile / packageDoc / publishArtifact) := false,
      addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    )
    .enablePlugins(AkkaGrpcPlugin)
    .settings(
      libraryDependencies ++= Seq(
        // akka streams
        "com.typesafe.akka" %% "akka-stream"       % akkaVersion,
        "com.typesafe.akka" %% "akka-stream-kafka" % alpakkaKafkaVersion,
        "com.typesafe.akka" %% "akka-discovery"    % akkaVersion,

        // effects
        "dev.zio" %% "zio"        % zioVersion,
        "dev.zio" %% "zio-config" % zioConfigVersion,

        // refined
        "eu.timepit" %% "refined"      % refinedVersion,
        "eu.timepit" %% "refined-cats" % refinedVersion,

        // json
        "io.circe"      %% "circe-core"      % circeVersion,
        "io.circe"      %% "circe-generic"   % circeVersion,
        "io.circe"      %% "circe-parser"    % circeVersion,
        "io.circe"      %% "circe-refined"   % circeVersion,
        "ch.qos.logback" % "logback-classic" % logbackVersion,

        // http api
        "org.http4s"                  %% "http4s-blaze-server"     % http4sBlazeVersion,
        "org.http4s"                  %% "http4s-circe"            % http4sVersion,
        "dev.zio"                     %% "zio-interop-cats"        % zioCatsInteropVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-http4s-server-zio" % tapirVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-refined"           % tapirVersion,
        "com.softwaremill.sttp.tapir" %% "tapir-json-circe"        % tapirVersion,

        // http api docs
        "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,

        // db
        "org.tpolecat" %% "skunk-core" % skunkVersion,

        // test
        "dev.zio"                 %% "zio-test"        % zioVersion           % Test,
        "dev.zio"                 %% "zio-test-sbt"    % zioVersion           % Test,
        "io.github.embeddedkafka" %% "embedded-kafka"  % embeddedKafkaVersion % Test,
        "com.opentable.components" % "otj-pg-embedded" % pgEmbeddedVersion    % Test
      )
    )
