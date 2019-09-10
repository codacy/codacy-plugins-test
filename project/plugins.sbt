resolvers := Seq("Sonatype OSS Snapshots".at("https://oss.sonatype.org/content/repositories/releases"),
                 "Typesafe Repo".at("https://repo.typesafe.com/typesafe/releases/")) ++ resolvers.value

// Formating
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.5.1")
