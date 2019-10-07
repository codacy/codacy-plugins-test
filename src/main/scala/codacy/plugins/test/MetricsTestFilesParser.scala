package codacy.plugins.test

import java.io.File

import codacy.utils.FileHelper
import com.codacy.plugins.api.languages.{Language, Languages}
import com.codacy.plugins.api.metrics.FileMetrics
import play.api.libs.json.Json

import scala.annotation.tailrec
import scala.util.Try
import Utils._
import com.codacy.plugins.api.metrics.LineComplexity

// object ComplexityWithLine {
//   implicit val formatter = Json.format[ComplexityWithLine]
// }

class MetricsTestFilesParser(filesDir: File) {
  private case class MetricsHeaderData(complexity: Option[Int],
                                       loc: Option[Int],
                                       cloc: Option[Int],
                                       nrMethods: Option[Int],
                                       nrClasses: Option[Int])
  implicit private val formatter = Json.format[MetricsHeaderData]

  val MetricsHeader = """\s*#Metrics:\s*(.*)""".r
  val LineComplexityRegex = """\s*#LineComplexity:\s*(.*)""".r

  def getTestFiles: Seq[FileMetrics] = {
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
                metricsHeaderData.map(
                  data =>
                    FileMetrics(file.getAbsolutePath,
                                data.complexity,
                                data.loc,
                                data.cloc,
                                data.nrMethods,
                                data.nrClasses,
                                lineComplexities)
                )
              case _ => None
            }
            .getOrElse(throw new Exception(s"File $file has no metrics header."))
      }
  }

  private def getAllComments(file: File, language: Language): Seq[(Int, String)] = {
    FileHelper.read(file).getOrElse(Seq.empty).zipWithIndex.flatMap {
      case (line, lineNr) =>
        getComment(language, line).map { comment =>
          (lineNr + 1, comment)
        }
    }
  }

  //Returns the content of a line comment or None if the line is not a comment
  private def getComment(language: Language, line: String): Option[String] = {
    languageComments(language).collectFirst {
      case lineComment if line.trim.startsWith(lineComment) && line.trim.endsWith(lineComment.reverse) =>
        line.trim.drop(lineComment.length).dropRight(lineComment.length)
      case lineComment if line.trim.startsWith(lineComment) =>
        line.trim.drop(lineComment.length)
    }
  }

  //The match is in the next line that is not a comment
  @tailrec
  private def getNextCodeLine(currentLine: Int, comments: Seq[Int]): Int = {
    if (!comments.contains(currentLine)) {
      currentLine
    } else {
      getNextCodeLine(currentLine + 1, comments)
    }
  }
}
