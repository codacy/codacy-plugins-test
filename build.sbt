import com.typesafe.sbt.packager.docker.Cmd

resolvers += Resolver.sonatypeRepo("releases")

name := """codacy-plugins-test"""

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.5",
  "com.typesafe.play" %% "play-json" % "2.4.8",
  "com.codacy" %% "codacy-plugins-api" % "0.1.2",
  "commons-io" % "commons-io" % "2.4"
)

enablePlugins(DockerPlugin, AshScriptPlugin)

val installAll =
  s"""apk update &&
     |apk add openjdk8-jre &&
     |rm -rf /tmp/* &&
     |rm -rf /var/cache/apk/*"""
    .stripMargin.replaceAll(System.lineSeparator(), " ")

daemonUser in Docker := "root"
dockerBaseImage := "docker:stable"
dockerCommands := dockerCommands.value.flatMap {
  case cmd@Cmd("WORKDIR", _) => List(cmd,
    Cmd("RUN", installAll)
  )
  case other => List(other)
}
