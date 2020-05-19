name := "codacy-plugins-test"

scalaVersion := "2.12.10"

// Needed to avoid ResourceLeak with Airframe-Log
fork in run := true
cancelable in Global := true

libraryDependencies ++= Seq("com.codacy" %% "codacy-analysis-core" % "3.2.0",
                            "com.lihaoyi" %% "pprint" % "0.5.7",
                            "org.wvlet.airframe" %% "airframe-log" % "19.12.4",
                            codacy.libs.scalatest)

lazy val nativeImageOnDocker = settingKey[Boolean]("Use docker to build the native-image")
nativeImageOnDocker := true

enablePlugins(GraalVMNativeImagePlugin)

graalVMNativeImageGraalVersion := {
  if (nativeImageOnDocker.value) Some("20.0.0-java8")
  else None
}

graalVMNativeImageOptions := Seq("--enable-http",
                                 "--enable-https",
                                 "--enable-url-protocols=http,https,file,jar",
                                 "--enable-all-security-services",
                                 "-H:+JNI",
                                 "-H:IncludeResourceBundles=com.sun.org.apache.xerces.internal.impl.msg.XMLMessages",
                                 "-H:+ReportExceptionStackTraces",
                                 "--no-fallback",
                                 "--initialize-at-build-time",
                                 "--report-unsupported-elements-at-runtime") ++ {
  if (nativeImageOnDocker.value || sys.props.get("os.name").forall(_ != "Mac OS X")) Seq("--static") else Seq.empty
}
