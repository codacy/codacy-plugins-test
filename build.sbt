name := "codacy-plugins-test"

scalaVersion := "2.12.10"

// Needed to avoid ResourceLeak with Airframe-Log
fork in run := true
cancelable in Global := true

libraryDependencies ++= Seq("com.codacy" %% "codacy-analysis-core" % "0.1.0-alpha3.694",
                            "com.lihaoyi" %% "pprint" % "0.5.4",
                            "org.wvlet.airframe" %% "airframe-log" % "19.12.0",
                            codacy.libs.scalatest)
