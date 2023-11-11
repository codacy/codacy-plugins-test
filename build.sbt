name := "codacy-plugins-test"

scalaVersion := "2.12.15"

// Needed to avoid ResourceLeak with Airframe-Log
run / fork := true
Global / cancelable := true

libraryDependencies ++= Seq("com.codacy" %% "codacy-analysis-core" % "7.6.0",
                            "com.lihaoyi" %% "pprint" % "0.6.2",
                            "org.wvlet.airframe" %% "airframe-log" % "21.3.0",
                            "org.scalatest" %% "scalatest" % "3.0.8")

enablePlugins(NativeImagePlugin)

nativeImageVersion := "22.1.0"

nativeImageOptions ++= Seq("--enable-http",
                           "--enable-https",
                           "--enable-url-protocols=http,https,jar",
                           "-H:+JNI",
                           "-H:IncludeResourceBundles=com.sun.org.apache.xerces.internal.impl.msg.XMLMessages",
                           "-H:+AllowIncompleteClasspath",
                           "-H:+ReportExceptionStackTraces",
                           "--no-fallback",
                           "--report-unsupported-elements-at-runtime")

// Scalafix

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"
ThisBuild / scalacOptions += "-Ywarn-unused"

addCommandAlias("scalafixRun", "scalafixEnable; Compile / scalafix; Test / scalafix")

addCommandAlias("scalafixCheck", "scalafixEnable; Compile / scalafix --check; Test / scalafix --check")
