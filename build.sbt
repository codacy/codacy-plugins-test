name := "codacy-plugins-test"

scalaVersion := "2.12.9"

libraryDependencies ++= Seq(
  "com.codacy" %% "codacy-analysis-core" % "0.1.0-alpha3.694",
  codacy.libs.scalatest
)
