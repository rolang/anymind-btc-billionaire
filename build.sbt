ThisBuild / scalaVersion := "2.13.8"
ThisBuild / name         := "btc-billionaire"

lazy val akkaHttpVersion                 = "10.2.9"
lazy val akkaVersion                     = "2.6.9"
lazy val circeVersion                    = "0.14.2"
lazy val logbackVersion                  = "1.2.11"
lazy val scalaTestVersion                = "3.2.12"
lazy val akkaHttpCirceVersion            = "1.39.2"
lazy val akkaPersistenceCassandraVersion = "1.0.5"

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll")
addCommandAlias("updates", ";dependencyUpdates; reload plugins; dependencyUpdates; reload return")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http"                  % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-actor-typed"           % akkaVersion,
  "com.typesafe.akka" %% "akka-stream"                % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed"     % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra" % akkaPersistenceCassandraVersion,
  "io.circe"          %% "circe-core"                 % circeVersion,
  "io.circe"          %% "circe-generic"              % circeVersion,
  "io.circe"          %% "circe-parser"               % circeVersion,
  "de.heikoseeberger" %% "akka-http-circe"            % akkaHttpCirceVersion,
  "ch.qos.logback"     % "logback-classic"            % logbackVersion,
  "com.typesafe.akka" %% "akka-http-testkit"          % akkaHttpVersion  % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed"   % akkaVersion      % Test,
  "org.scalatest"     %% "scalatest"                  % scalaTestVersion % Test
)
