package com.codacy.plugins.results.traits

import com.codacy.plugins.api.PatternDescription
import com.codacy.plugins.api.results.Tool
import com.codacy.plugins.results.docker.utils.PluginPrefixHelper
import com.codacy.plugins.runners.IDocker
import com.codacy.plugins.utils.DockerHelper

trait ToolDocumentation {
  def toolSpecification: Option[Tool.Specification]
  def patternDescriptions: Option[Set[PatternDescription]]
  def toolPrefix: String
  def toolDescription: Option[String]

  // Prefixed versions of the private instances above
  lazy val prefixedSpecs: Option[Tool.Specification] = PluginPrefixHelper.prefixSpec(toolPrefix, toolSpecification)
  lazy val prefixedDescriptions: Option[Set[PatternDescription]] =
    PluginPrefixHelper.prefixDescription(toolPrefix, patternDescriptions)
}

@deprecated(
  "This should only be used by codacy-tools and all other services should fetch this information from the tools API",
  since = ""
)
class DockerToolDocumentation(dockerTool: IDocker, dockerHelper: DockerHelper) extends ToolDocumentation {

  val docsRoot = "/docs"

  override lazy val toolSpecification: Option[Tool.Specification] =
    dockerHelper.loadPatterns(dockerTool)

  override lazy val patternDescriptions: Option[Set[PatternDescription]] =
    dockerHelper.loadDescription(dockerTool)

  override val toolPrefix: String = ""

  override lazy val toolDescription: Option[String] = dockerHelper.loadToolDescription(dockerTool)

}
