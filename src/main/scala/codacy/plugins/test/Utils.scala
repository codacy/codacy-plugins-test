package codacy.plugins.test
import com.codacy.plugins.api.languages.Language
import com.codacy.plugins.api.languages.Languages._
import codacy.utils.FileHelper
import scala.annotation.tailrec

private[test] object Utils {

  val languageComments: Language => Seq[String] = {
    case Apex => Seq("//", "/*")
    case C => Seq("/*", "//")
    case CPP => Seq("/*", "//")
    case CSS => Seq("/*")
    case CSharp => Seq("//", "/*")
    case Clojure => Seq("#", ";;")
    case Cobol => Seq()
    case CoffeeScript => Seq("#")
    case Crystal => Seq("#")
    case Dart => Seq("//", "/*")
    case Dockerfile => Seq("#")
    case Elixir => Seq("#")
    case Elm => Seq("--", "{-")
    case Erlang => Seq("%")
    case Fortran => Seq("!")
    case FSharp => Seq("//", "(*")
    case Go => Seq("//")
    case Groovy => Seq("//", "/*")
    case Haskell => Seq("--")
    case HTML => Seq("<!--")
    case JSON => Seq("//", "/*")
    case JSP => Seq("<%--")
    case Java => Seq("//", "/*")
    case Javascript => Seq("//", "/*")
    case Julia => Seq("#", "#=")
    case Kotlin => Seq("//", "/*")
    case LESS => Seq("/*")
    case Lisp => Seq(";")
    case Lua => Seq("--", "--[[")
    case Markdown => Seq("<!--")
    case ObjectiveC => Seq("/*", "//")
    case OCaml => Seq("(*")
    case PHP => Seq("#", "//")
    case PLSQL => Seq("--", "/*")
    case Perl => Seq("#")
    case Powershell => Seq("#", "<#")
    case Prolog => Seq("%")
    case Python => Seq("#")
    case R => Seq("#")
    case Ruby => Seq("#")
    case Rust => Seq("//")
    case SASS => Seq("/*")
    case Scala => Seq("/*", "//")
    case Scratch => Seq()
    case Shell => Seq("#")
    case Solidity => Seq("//", "/*")
    case SQL => Seq("--", "/*")
    case Swift => Seq("//")
    case TypeScript => Seq("//", "/*")
    case Velocity => Seq("/*")
    case VisualBasic => Seq("'")
    case VisualForce => Seq("<!--")
    case XML => Seq("<!--")
    case YAML => Seq("#")
    // case _ => Seq()
  }

  def getAllComments(file: java.io.File, language: Language): Seq[(Int, String)] = {
    //Returns the content of a line comment or None if the line is not a comment
    def getComment(language: Language, line: String): Option[String] = {
      languageComments(language).collectFirst {
        case lineComment if line.trim.startsWith(lineComment) && line.trim.endsWith(lineComment.reverse) =>
          line.trim.drop(lineComment.length).dropRight(lineComment.length)
        case lineComment if line.trim.startsWith(lineComment) =>
          line.trim.drop(lineComment.length)
      }
    }

    FileHelper.read(file).getOrElse(Seq.empty).zipWithIndex.flatMap {
      case (line, lineNr) =>
        getComment(language, line).map { comment =>
          (lineNr + 1, comment)
        }
    }
  }

  //The match is in the next line that is not a comment
  @tailrec
  def getNextCodeLine(currentLine: Int, comments: Seq[Int]): Int =
    if (comments.contains(currentLine)) getNextCodeLine(currentLine + 1, comments)
    else
      currentLine

  def toCodacyPluginsApiMetricsFileMetrics(fileMetrics: com.codacy.analysis.core.model.FileMetrics) =
    com.codacy.plugins.api.metrics.FileMetrics(filename = fileMetrics.filename.toString,
                                               complexity = fileMetrics.complexity,
                                               loc = fileMetrics.loc,
                                               cloc = fileMetrics.cloc,
                                               nrMethods = fileMetrics.nrMethods,
                                               nrClasses = fileMetrics.nrClasses,
                                               lineComplexities = fileMetrics.lineComplexities)
}
