name := "codacy-plugins-test"

scalaVersion := "2.12.18"

// Needed to avoid ResourceLeak with Airframe-Log
run / fork := true
Global / cancelable := true

connectInput / run := true
outputStrategy := Some(StdoutOutput)

libraryDependencies ++= Seq("com.codacy" %% "codacy-analysis-core" % "7.9.25",
                            "com.lihaoyi" %% "pprint" % "0.8.1",
                            "org.wvlet.airframe" %% "airframe-log" % "21.3.0",
                            "org.scalatest" %% "scalatest" % "3.2.17")

enablePlugins(NativeImagePlugin)

nativeImageOptions ++= Seq("--enable-http",
                           "--enable-https",
                           "--enable-url-protocols=http,https,jar",
                           "-H:+JNI",
                           "-H:IncludeResourceBundles=com.sun.org.apache.xerces.internal.impl.msg.XMLMessages",
                           "-H:+AllowIncompleteClasspath",
                           "-H:+ReportExceptionStackTraces",
                           "--no-fallback",
                           "--report-unsupported-elements-at-runtime")

nativeImageVersion := "22.1.0"
Global / excludeLintKeys += nativeImageVersion

addCommandAlias("scalafixRun", "scalafixEnable; Compile / scalafix; Test / scalafix")

addCommandAlias("scalafixCheck", "scalafixEnable; Compile / scalafix --check; Test / scalafix --check")
