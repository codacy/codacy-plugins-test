package codacy.plugins.test

import java.nio.file.Path

import codacy.plugins.docker.DockerPlugin

trait ITest {
  val opt: String

  def run(plugin: DockerPlugin, sourcePath: Path): Boolean
}
