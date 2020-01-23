package codacy.plugins.test.duplication

import com.codacy.plugins.api.duplication.{DuplicationClone, DuplicationCloneFile}

import scala.xml.Elem
import codacy.plugins.test.checkstyle.CheckstyleImplicits._

private[duplication] object CheckstyleFormatParser {

  def parseResultsXml(root: Elem): Seq[DuplicationClone] = {
    for {
      fileTags <- root \\ "checkstyle" \ "file"
      checkstyle = root \\ "checkstyle"
      nrTokens = checkstyle.getProperty("nrTokens").fold(throw new NumberFormatException)(_.toInt)
      nrLines = checkstyle.getProperty("nrLines").fold(throw new NumberFormatException)(_.toInt)

      files = fileTags.map { fileTag =>
        val startLine = fileTag.getProperty("startLine").fold(throw new NumberFormatException)(_.toInt)
        val endLine = fileTag.getProperty("endLine").fold(throw new NumberFormatException)(_.toInt)
        DuplicationCloneFile(fileTag \@ "name", startLine, endLine)
      }
    } yield DuplicationClone("", nrTokens, nrLines, files)
  }
}
