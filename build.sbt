name := "codacy-plugins-test"

scalaVersion := "2.12.10"

// Needed to avoid ResourceLeak with Airframe-Log
fork in run := true
cancelable in Global := true

libraryDependencies ++= Seq("com.codacy" %% "codacy-analysis-core" % "0.2.1",
                            "com.lihaoyi" %% "pprint" % "0.5.7",
                            "org.wvlet.airframe" %% "airframe-log" % "19.12.4",
                            codacy.libs.scalatest)
