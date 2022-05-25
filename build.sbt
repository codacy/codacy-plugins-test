name := "codacy-plugins-test"

scalaVersion := "2.12.15"

// Needed to avoid ResourceLeak with Airframe-Log
run / fork := true
Global / cancelable := true

libraryDependencies ++= Seq("com.codacy" %% "codacy-analysis-core" % "5.2.5",
                            "com.lihaoyi" %% "pprint" % "0.6.2",
                            "org.wvlet.airframe" %% "airframe-log" % "21.3.0",
                            codacy.libs.scalatest)

enablePlugins(NativeImagePlugin)

nativeImageVersion := "22.1.0"

nativeImageOptions ++= Seq("--enable-http",
                           "--enable-https",
                           "--enable-url-protocols=http,https,file,jar",
                           "--enable-all-security-services",
                           "-H:+JNI",
                           "-H:IncludeResourceBundles=com.sun.org.apache.xerces.internal.impl.msg.XMLMessages",
                           "-H:+AllowIncompleteClasspath",
                           "-H:+ReportExceptionStackTraces",
                           "--no-fallback",
                           "--initialize-at-build-time",
                           "--report-unsupported-elements-at-runtime") ++ {
  if (sys.props.get("os.name").contains("Mac OS X")) Seq.empty
  else Seq("--static")
}

// Scalafix

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0"
ThisBuild / scalacOptions += "-Ywarn-unused"

addCommandAlias("scalafixRun", "scalafixEnable; compile:scalafix; test:scalafix")

addCommandAlias("scalafixCheck", "scalafixEnable; compile:scalafix --check; test:scalafix --check")
