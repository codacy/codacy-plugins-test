name := "codacy-plugins-test"

scalaVersion := "2.12.21"

// Needed to avoid ResourceLeak with Airframe-Log
run / fork := true
Global / cancelable := true

connectInput / run := true
outputStrategy := Some(StdoutOutput)

dependencyOverrides += "ch.qos.logback" % "logback-core" % "1.2.12"

libraryDependencies ++= Seq("com.codacy" %% "codacy-analysis-core" % "7.9.25",
                            "com.lihaoyi" %% "pprint" % "0.9.0",
                            "org.wvlet.airframe" %% "airframe-log" % "2025.1.12",
                            "org.scalatest" %% "scalatest" % "3.2.19")

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
