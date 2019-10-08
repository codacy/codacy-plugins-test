package codacy.plugins.test
import com.codacy.plugins.api.languages.{Language, Languages}
import codacy.utils.FileHelper
import scala.annotation.tailrec

private [test] object Utils {
  
  val languageComments = Map[Language, Seq[String]](Languages.Javascript -> Seq("//", "/*"),
                                                    Languages.Scala -> Seq("/*", "//"),
                                                    Languages.CSS -> Seq("/*"),
                                                    Languages.LESS -> Seq("/*"),
                                                    Languages.SASS -> Seq("/*"),
                                                    Languages.PHP -> Seq("#", "//"),
                                                    Languages.C -> Seq("/*", "//"),
                                                    Languages.CPP -> Seq("/*", "//"),
                                                    Languages.ObjectiveC -> Seq("/*", "//"),
                                                    Languages.Python -> Seq("#"),
                                                    Languages.Ruby -> Seq("#"),
                                                    Languages.Kotlin -> Seq("//", "/*"),
                                                    Languages.Perl -> Seq("#"),
                                                    Languages.Java -> Seq("//", "/*"),
                                                    Languages.CSharp -> Seq("//", "/*"),
                                                    Languages.VisualBasic -> Seq("'"),
                                                    Languages.Go -> Seq("//"),
                                                    Languages.Elixir -> Seq("#"),
                                                    Languages.Clojure -> Seq("#", ";;"),
                                                    Languages.CoffeeScript -> Seq("#"),
                                                    Languages.Rust -> Seq("//"),
                                                    Languages.Swift -> Seq("//"),
                                                    Languages.Haskell -> Seq("--"),
                                                    Languages.Shell -> Seq("#"),
                                                    Languages.TypeScript -> Seq("//", "/*"),
                                                    Languages.XML -> Seq("<!--"),
                                                    Languages.Dockerfile -> Seq("#"),
                                                    Languages.PLSQL -> Seq("--", "/*"),
                                                    Languages.JSON -> Seq("//", "/*"),
                                                    Languages.Apex -> Seq("//", "/*"),
                                                    Languages.Velocity -> Seq("/*"),
                                                    Languages.JSP -> Seq("<%--"),
                                                    Languages.VisualForce -> Seq("<!--"),
                                                    Languages.R -> Seq("#"),
                                                    Languages.Powershell -> Seq("#", "<#"),
                                                    Languages.Solidity -> Seq("//", "/*"),
                                                    Languages.Markdown -> Seq("<!--"),
                                                    Languages.Crystal -> Seq("#"),
                                                    Languages.YAML -> Seq("#"))

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
  private def getNextCodeLine(currentLine: Int, comments: Seq[Int]): Int =
    if (comments.contains(currentLine)) getNextCodeLine(currentLine + 1, comments)
    else
      currentLine

  def toCodacyPluginsApiMetricsFileMetrics(fileMetrics: com.codacy.analysis.core.model.FileMetrics) =
    com.codacy.plugins.api.metrics.FileMetrics(filename = fileMetrics.filename.toString,
                                               complexity = fileMetrics.complexity,
                                               loc = fileMetrics.loc,
                                               cloc = fileMetrics.cloc,
                                               nrMethods = fileMetrics.nrClasses,
                                               lineComplexities = fileMetrics.lineComplexities)
}
