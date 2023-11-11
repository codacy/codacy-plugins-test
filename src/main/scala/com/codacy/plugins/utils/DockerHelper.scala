package com.codacy.plugins.utils

import com.codacy.plugins.api.PatternDescription
import com.codacy.plugins.api.results.Tool
import com.codacy.plugins.runners.IDocker
import play.api.libs.json.{Format, Json}

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
  protected val cachedDescriptionsPathPrefix = docsRoot.resolve("description")

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
    readJsonDoc[Tool.Specification](docker, docsRoot.resolve("patterns.json"))

  override def loadVersion(docker: IDocker): Option[String] =
    readRaw(docker, docsRoot.resolve("patterns.json"))
      .flatMap(content => (Json.parse(content) \ "version").asOpt[String])

  override def loadToolDescription(docker: IDocker): Option[String] =
    readRaw(docker, docsRoot.resolve("tool-description.md"))

  protected def parseJsonDescriptions(content: String): Option[Set[PatternDescription]] = {
    Try(Json.parse(content).asOpt[Set[PatternDescription]]).toOption.flatten
  }

  /**
    * Returns a set of pattern descriptions for the given docker
    * This can be overriden for a more efficient version that does not call `readRaw` for each file
    */
  private[utils] def readDescription(docker: IDocker): Option[Set[PatternDescription]] = {
    for {
      content <- readRaw(docker, cachedDescriptionsPathPrefix.resolve("description.json"))
      descriptions <- parseJsonDescriptions(content)
    } yield {
      descriptions.map { pattern =>
        val descPath = cachedDescriptionsPathPrefix.resolve(s"${pattern.patternId}.md")
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
