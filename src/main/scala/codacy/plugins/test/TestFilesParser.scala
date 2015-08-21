package codacy.plugins.test

import java.io.File

import codacy.plugins.docker.{Language, ResultLevel}
import codacy.utils.FileHelper
import play.api.libs.json.{JsValue, Json}

case class PatternTestFile(file: File, language: Language.Value,
                           enabledPatterns: Seq[PatternSimple], matches: Seq[TestFileResult])

case class PatternSimple(name: String, parameters: Map[String, JsValue])

class TestFilesParser(filesDir: File) {

  val warning = """#Warn:\s*([A-Za-z0-9\_\-\.]+)""".r
  val error = """#Err:\s*([A-Za-z0-9\_\-\.]+)""".r
  val info = """#Info:\s*([A-Za-z0-9\_\-\.]+)""".r
  val patterns = """#Patterns:\s*([\s\,A-Za-z0-9\_\-\{\}\'\"\:\.]+)""".r

  val languages = Map[Language.Value, Seq[String]](
    Language.Javascript -> Seq("//", "/*"),
    Language.CoffeeScript -> Seq("#"),
    Language.Java -> Seq("//", "/*"),
    Language.CSS -> Seq("/*"),
    Language.Python -> Seq("#"),
    Language.PHP -> Seq("#", "//"),
    Language.Scala -> Seq("/*", "//"),
    Language.C -> Seq("/*", "//"),
    Language.Ruby -> Seq("#"),
    Language.Jade -> Seq("//", "//-"),
    Language.Stylus -> Seq("//")
  )

  def getTestFiles: Seq[PatternTestFile] = {

    val validExtensions = Language.values.flatMap(Language.getExtensions).toArray
    val files = FileHelper.listFiles(filesDir).filter(f => validExtensions.contains(s".${f.getName.split('.').last}"))

    files.map { file =>
      val extension = "." + file.getName.split('.').last

      val languageMap = Language.values.toList.map(lang => (lang, Language.getExtensions(lang)))

      languageMap.collect {
        case (language, extensions) if extensions.contains(extension) =>

          val comments = getAllComments(file, language)
          val commentLines = comments.map { case (commentFile, _) => commentFile }

          val matches = comments.flatMap {
            case (line, comment) =>

              val nextLine = getNextCodeLine(line, commentLines)

              comment.trim match {
                case warning(value) => Some(TestFileResult(value, nextLine, ResultLevel.Warn))
                case error(value) => Some(TestFileResult(value, nextLine, ResultLevel.Err))
                case info(value) => Some(TestFileResult(value, nextLine, ResultLevel.Info))
                case _ => None
              }
          }

          //we probably need to convert this into a smarter regex
          val enabledPatterns = comments.map(_._2).flatMap {
            case patterns(value) => value.split(",").map {
              //pattern has parameters
              case pattern if pattern.contains(":") =>
                val name = pattern.split(":").head.trim
                val jsonParams = Json.parse(pattern.substring(pattern.indexOf(":") + 1).mkString)
                val typedJsonParams = cleanParameterTypes(jsonParams)
                val params = typedJsonParams.asOpt[Map[String, JsValue]].getOrElse(Map.empty)

                Some(PatternSimple(name, params))
              //pattern does not have parameters
              case pattern =>
                Some(PatternSimple(pattern.trim, Map()))
            }
            case _ => None
          }.flatten

          PatternTestFile(file, language, enabledPatterns, matches)
      }.toSeq
    }.toSeq.flatten
  }

  private def getAllComments(file: File, language: Language.Value): Seq[(Int, String)] = {
    FileHelper.read(file).getOrElse(Seq.empty).zipWithIndex.flatMap {
      case (line, lineNr) =>
        getComment(language, line).map {
          comment =>
            (lineNr + 1, comment)
        }
    }
  }

  //Returns the content of a line comment or None if the line is not a comment
  private def getComment(language: Language.Value, line: String): Option[String] = {
    languages(language).foreach {
      lineComment =>

        if (line.trim.startsWith(lineComment)) {
          if (line.trim.endsWith(lineComment.reverse)) {
            return Some(line.trim.drop(lineComment.length).dropRight(lineComment.length))
          }

          return Some(line.trim.drop(lineComment.length))
        }
    }
    None
  }

  //The match is in the next line that is not a comment
  private def getNextCodeLine(currentLine: Int, comments: Seq[Int]): Int = {
    if (comments.contains(currentLine)) {
      return getNextCodeLine(currentLine + 1, comments)
    }
    currentLine
  }

  private def cleanParameterTypes(json: JsValue): JsValue = {
    val jsonString = json.toString()
    val fixedString = jsonString.replaceAll( """"(true|false)"""", "$1").replaceAll( """"([0-9]+)"""", "$1")
    Json.parse(fixedString)
  }
}
