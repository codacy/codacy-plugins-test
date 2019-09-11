name := "codacy-plugins-test"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "com.codacy" %% "codacy-analysis-core" % "0.1.0-alpha3.581", codacy.libs.scalatest)
