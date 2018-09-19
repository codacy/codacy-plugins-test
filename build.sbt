name := """codacy-plugins-test"""

val scalaBinaryVersionNumber = "2.12"
scalaVersion := s"$scalaBinaryVersionNumber.6"

resolvers := Seq(
  Resolver.sonatypeRepo("releases"),
  "Codacy Public Mvn bucket" at "https://s3-eu-west-1.amazonaws.com/public.mvn.codacy.com") ++ resolvers.value

scalacOptions ++= Common.compilerFlags

libraryDependencies ++= Seq(
  Dependencies.scalaTest,
  Dependencies.analysisCore,
  Dependencies.commonsIO
)