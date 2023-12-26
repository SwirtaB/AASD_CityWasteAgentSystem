name := "."

version := "1.0"

scalaVersion := s"2.13.12"

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

scalacOptions ++= Seq("-unchecked", "-deprecation", "-Xfatal-warnings")

lazy val akkaVersion     = "2.9.0"
lazy val akkaHttpVersion = "10.6.0"

// Run in a separate JVM, to make sure sbt waits until all threads have
// finished before returning.
// If you want to keep the application running while executing other
// sbt tasks, consider https://github.com/spray/sbt-revolver/
fork := true

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-math3"        % "3.6.1",
  "com.typesafe.akka" %% "akka-actor-typed"     % akkaVersion,
  "ch.qos.logback"     % "logback-classic"      % "1.4.7",
  "com.typesafe.akka" %% "akka-stream"          % akkaVersion,
  "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
)
