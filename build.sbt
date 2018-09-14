resolvers += Resolver.sonatypeRepo("releases")

name := """codacy-plugins-test"""

val scalaBinaryVersionNumber = "2.12"
scalaVersion := s"$scalaBinaryVersionNumber.6"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.5",
  "com.codacy" %% "codacy-analysis-core" % "0.1.0-alpha3.3529",
  "commons-io" % "commons-io" % "2.4"
)