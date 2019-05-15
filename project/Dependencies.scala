import sbt._

object Dependencies {

  private val scalatestVersion = "3.0.5"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % scalatestVersion

  private val analysisCoreVersion = "0.1.0-alpha3.244"
  lazy val analysisCore = "com.codacy" %% "codacy-analysis-core" % analysisCoreVersion

  private val commonsIOVersion = "2.4"
  lazy val commonsIO = "commons-io" % "commons-io" % commonsIOVersion
}
