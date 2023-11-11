package com.codacy.plugins.results.traits

import com.codacy.plugins.api.languages.Language
import com.codacy.plugins.runners.IDocker
import com.codacy.plugins.utils.DockerHelper

abstract class DockerTool(dockerImage: String,
                          val isDefault: Boolean,
                          val languages: Set[Language],
                          val name: String,
                          val shortName: String,
                          val uuid: String,
                          val documentationUrl: String,
                          val sourceCodeUrl: String,
                          val prefix: String = "",
                          needsCompilation: Boolean = false,
                          val configFilename: Seq[String] = Seq.empty,
                          val isClientSide: Boolean = false,
                          val hasUIConfiguration: Boolean = true)
    extends IDocker(dockerImage, needsCompilation = needsCompilation) {

  def toolVersion(dockerHelper: DockerHelper): Option[String] = dockerHelper.loadVersion(this)

  def getPatternIdentifier(patternName: String): Option[String] = Some(patternName)

  def hasConfigFile: Boolean = configFilename.nonEmpty

}
