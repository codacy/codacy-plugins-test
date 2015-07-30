name := """codacy-plugins-test"""

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.5",
  "com.typesafe.play" %% "play-json" % "2.3.9",
  "commons-io" % "commons-io" % "2.4"
)
