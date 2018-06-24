import sbt._

object Dependencies {

  val scalatest = ("org.scalatest" %% "scalatest" % "3.0.5").withSources()
  val playJson = ("com.typesafe.play" %% "play-json" % "2.6.9").withSources()
  val codacyPluginsApi = ("com.codacy" %% "codacy-plugins-api" % "3.0.96").withSources()
  val betterFiles = ("com.github.pathikrit" %% "better-files" % "3.5.0").withSources()

}
