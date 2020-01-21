package codacy.plugins.test
import com.codacy.plugins.api
import com.codacy.plugins.api.languages.Language
import com.codacy.plugins.api.languages.Languages._
import codacy.utils.FileHelper
import scala.annotation.tailrec
import java.io.{File => JFile}

private[test] object Utils {

  val languageComments: Language => Seq[String] = {
    case ABAP => Seq("*", "\"")
    case Apex | C | CPP | CSharp | Dart | Groovy | JSON | Java | Javascript | Kotlin | ObjectiveC | Scala | Solidity |
        TypeScript =>
      Seq("/*", "//")
    case CSS | LESS | SASS | Velocity => Seq("/*")
    case Clojure => Seq("#", ";;")
    case Cobol => Seq()
    case CoffeeScript | Crystal | Dockerfile | Elixir | Perl | Python | R | Ruby | Shell | YAML => Seq("#")
    case Elm => Seq("--", "{-")
    case Erlang | Prolog => Seq("%")
    case Fortran => Seq("!")
    case FSharp => Seq("//", "(*")
    case Go | Rust | Swift => Seq("//")
    case Haskell => Seq("--")
    case HTML | Markdown | VisualForce | XML => Seq("<!--")
    case JSP => Seq("<%--")
    case Julia => Seq("#", "#=")
    case Lisp => Seq(";")
    case Lua => Seq("--", "--[[")
    case OCaml => Seq("(*")
    case PHP => Seq("#", "//")
    case PLSQL | SQL | TSQL => Seq("--", "/*")
    case Powershell => Seq("#", "<#")
    case Scratch => Seq()
    case VisualBasic => Seq("'")
  }

  def getAllComments(file: JFile, language: Language): Seq[(Int, String)] = {
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
    api.metrics.FileMetrics(filename = fileMetrics.filename.toString,
                            complexity = fileMetrics.complexity,
                            loc = fileMetrics.loc,
                            cloc = fileMetrics.cloc,
                            nrMethods = fileMetrics.nrMethods,
                            nrClasses = fileMetrics.nrClasses,
                            lineComplexities = fileMetrics.lineComplexities)

  def toCodacyPluginsApiDuplicationDuplicationClone(duplicationClone: com.codacy.analysis.core.model.DuplicationClone) =
    api.duplication.DuplicationClone(cloneLines = duplicationClone.cloneLines,
                                     nrTokens = duplicationClone.nrTokens,
                                     nrLines = duplicationClone.nrLines,
                                     files = duplicationClone.files
                                       .map(
                                         file =>
                                           api.duplication
                                             .DuplicationCloneFile(filePath = file.filePath,
                                                                   startLine = file.startLine,
                                                                   endLine = file.endLine)
                                       )
                                       .toSeq)

  def exceptionToString(e: Throwable): String = {
    s"""${e.getMessage()}
    |Stacktrace:
    |${e.getStackTraceString}""".stripMargin
  }
}
