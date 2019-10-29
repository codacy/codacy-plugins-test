name := "codacy-plugins-test"

scalaVersion := "2.12.10"

libraryDependencies ++= Seq("com.codacy" %% "codacy-analysis-core" % "0.1.0-alpha3.694",
                            "com.github.tototoshi" %% "scala-csv" % "1.3.6",
                            "com.lihaoyi" %% "pprint" % "0.5.4",
                            codacy.libs.scalatest)
