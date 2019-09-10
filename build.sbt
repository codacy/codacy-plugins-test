name := "codacy-plugins-test"

scalaVersion := "2.12.8"

resolvers := Seq("Sonatype OSS Snapshots".at("https://oss.sonatype.org/content/repositories/releases"),
                 "Codacy Public Mvn bucket".at("https://s3-eu-west-1.amazonaws.com/public.mvn.codacy.com"),
                 "Typesafe Repo".at("https://repo.typesafe.com/typesafe/releases/")) ++ resolvers.value

scalacOptions ++= Common.compilerFlags

libraryDependencies ++= Seq(
  "com.codacy" %% "codacy-analysis-core" %"0.1.0-SNAPSHOT",
  "org.scalatest" %% "scalatest" % "3.0.8"
)
