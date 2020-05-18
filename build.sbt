name := "codacy-plugins-test"

scalaVersion := "2.12.10"

// Needed to avoid ResourceLeak with Airframe-Log
fork in run := true
cancelable in Global := true

libraryDependencies ++= Seq("com.codacy" %% "codacy-analysis-core" % "3.2.0",
                            "com.lihaoyi" %% "pprint" % "0.5.7",
                            "org.wvlet.airframe" %% "airframe-log" % "19.12.4",
                            codacy.libs.scalatest)

enablePlugins(GraalVMNativeImagePlugin)
graalVMNativeImageGraalVersion := Some("20.0.0-java8")
graalVMNativeImageOptions := Seq("--enable-http",
                                 "--enable-https",
                                 "--enable-url-protocols=http,https,file,jar",
                                 "--enable-all-security-services",
                                 "-H:+JNI",
                                 "--static",
                                 "-H:IncludeResourceBundles=com.sun.org.apache.xerces.internal.impl.msg.XMLMessages",
                                 "-H:+ReportExceptionStackTraces",
                                 "--no-fallback",
                                 "--initialize-at-build-time",
                                 "--report-unsupported-elements-at-runtime")
