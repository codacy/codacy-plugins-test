package codacy.plugins.traits

import codacy.plugins.docker.{PluginRequest, PluginResult}

trait IResultsPlugin {

  def run(pluginRequest: PluginRequest): PluginResult

  protected def toRelativePath(rootDirectory: String, paths: Seq[String]): Seq[String] =
    paths.map(_.stripPrefix(rootDirectory).stripPrefix("/"))

}
