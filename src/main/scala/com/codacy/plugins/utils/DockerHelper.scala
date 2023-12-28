package com.codacy.plugins.utils

import com.codacy.plugins.api.PatternDescription
import com.codacy.plugins.api.results.{Pattern, Tool}
import com.codacy.plugins.runners.IDocker
import play.api.libs.json.{Format, Json}

import java.io.File
import java.nio.file.{Path, Paths}
import scala.util.Try

trait IDockerHelper {
  def loadDescription(docker: IDocker): Option[Set[PatternDescription]]
  def loadPatterns(docker: IDocker): Option[Tool.Specification]
  def loadVersion(docker: IDocker): Option[String]
  def loadToolDescription(docker: IDocker): Option[String]
}

abstract class DockerHelper extends IDockerHelper {
  protected val docsRoot: Path = Paths.get("/docs")
  protected val patternsFile = Paths.get(s"$docsRoot${File.separator}patterns.json")
  protected val toolDescriptionFile = Paths.get(s"$docsRoot${File.separator}tool-description.md")
  protected val descriptionsFile = Paths.get(s"$docsRoot${File.separator}description${File.separator}description.json")
  protected def patternExplanationFile(id: Pattern.Id) =
    Paths.get(s"$docsRoot${File.separator}description${File.separator}$id.md")

  /**
    * Reads the content of a file in a docker image. The file must be inside of `docsRoot`
    * @param docker the docker where to get the file content from
    * @param path the full path to the file to get (must be inside `docs`)
    * @return the content of the file
    */
  private[utils] def readRaw(docker: IDocker, path: Path): Option[String]

  override def loadDescription(docker: IDocker): Option[Set[PatternDescription]] =
    readDescription(docker)

  override def loadPatterns(docker: IDocker): Option[Tool.Specification] =
    readJsonDoc[Tool.Specification](docker, patternsFile)

  override def loadVersion(docker: IDocker): Option[String] =
    readRaw(docker, patternsFile)
      .flatMap(content => (Json.parse(content) \ "version").asOpt[String])

  override def loadToolDescription(docker: IDocker): Option[String] =
    readRaw(docker, toolDescriptionFile)

  protected def parseJsonDescriptions(content: String): Option[Set[PatternDescription]] = {
    Try(Json.parse(content).asOpt[Set[PatternDescription]]).toOption.flatten
  }

  /**
    * Returns a set of pattern descriptions for the given docker
    * This can be overriden for a more efficient version that does not call `readRaw` for each file
    */
  private[utils] def readDescription(docker: IDocker): Option[Set[PatternDescription]] = {
    for {
      content <- readRaw(docker, descriptionsFile)
      descriptions <- parseJsonDescriptions(content)
    } yield {
      descriptions.map { pattern =>
        val descPath = patternExplanationFile(pattern.patternId)
        val fileContents = readRaw(docker, descPath)
        pattern.copy(explanation = fileContents)
      }
    }
  }

  private def readJsonDoc[T](docker: IDocker, path: Path)(implicit docFmt: Format[T]): Option[T] = {
    val json = readRaw(docker, path).map(Json.parse)
    json.flatMap(_.asOpt[T])
  }

}
