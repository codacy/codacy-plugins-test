package codacy.plugins.traits

import codacy.plugins.docker.{PluginRequest, PluginResults}

trait IResultsPlugin {

  def run(pluginRequest: PluginRequest): PluginResults

  protected def toRelativePath(rootDirectory: String, paths: Seq[String]): Seq[String] =
    paths.map(_.stripPrefix(rootDirectory).stripPrefix("/"))

}
