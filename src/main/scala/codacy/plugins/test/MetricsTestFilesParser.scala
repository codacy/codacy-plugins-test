package codacy.plugins.test

import java.io.File

import codacy.utils.FileHelper
import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.plugins.api.metrics.{FileMetrics, LineComplexity}
import play.api.libs.json.Json

import scala.annotation.tailrec
import scala.util.Try
import Utils._

class MetricsTestFilesParser(filesDir: File) {
  private case class MetricsHeaderData(complexity: Option[Int],
                                       loc: Option[Int],
                                       cloc: Option[Int],
                                       nrMethods: Option[Int],
                                       nrClasses: Option[Int])
  implicit private val formatter = Json.format[MetricsHeaderData]

  val MetricsHeader = """\s*#Metrics:\s*(.*)""".r
  val LineComplexityRegex = """\s*#LineComplexity:\s*(.*)""".r

  def getTestFiles: Seq[(FileMetrics, Language)] = {
    FileHelper
      .listFiles(filesDir)
      .map { file =>
        (file, Languages.forPath(file.getAbsolutePath))
      }
      .collect { case (file, Some(language)) => (file, language) }
      .map {
        case (file, language) =>
          val comments = getAllComments(file, language)
          val commentLines = comments.map {
            case (commentFile, _) => commentFile
          }

          val lineComplexities: Set[LineComplexity] = comments.flatMap {
            case (line, comment) =>
              val nextLine = getNextCodeLine(line, commentLines)

              comment.trim match {
                case LineComplexityRegex(value) =>
                  Try(value.toInt).toOption.map(LineComplexity(nextLine, _))
                case _ => None
              }
          }.toSet

          comments.headOption
            .flatMap {
              //pattern has no parameters
              case (_, MetricsHeader(json)) =>
                val metricsHeaderData = Json.parse(json).asOpt[MetricsHeaderData]
                metricsHeaderData.map { data =>
                  (FileMetrics(file.getName(),
                               data.complexity,
                               data.loc,
                               data.cloc,
                               data.nrMethods,
                               data.nrClasses,
                               lineComplexities),
                   language)
                }
              case _ => None
            }
            .getOrElse(throw new Exception(s"File $file has no metrics header."))
      }
  }
}
