import sbt.Keys._
import sbt._

name := "codacy-plugins-test"
version := "1.0.0-SNAPSHOT"
organization := "com.codacy"

val scalaBinaryVersionNumber = "2.12"
val scalaVersionNumber = s"$scalaBinaryVersionNumber.4"

scalaVersion in ThisBuild := scalaVersionNumber
resolvers += Resolver.sonatypeRepo("releases")

libraryDependencies ++= Seq(Dependencies.playJson, Dependencies.codacyPluginsApi, Dependencies.betterFiles) ++
  // Tests
  Seq(Dependencies.scalatest)

scalaBinaryVersion in ThisBuild := scalaBinaryVersionNumber

scalacOptions ++= Common.compilerFlags

scapegoatVersion in ThisBuild := "1.3.5"
