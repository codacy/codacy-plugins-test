package codacy.plugins.test.duplication

import com.codacy.plugins.duplication.api.DuplicationCloneFile
import com.codacy.analysis.core.model.DuplicationClone
import scala.xml.Elem
import codacy.plugins.test.checkstyle.CheckstyleImplicits._

private[duplication] object CheckstyleFormatParser {

  def parseResultsXml(root: Elem): Seq[DuplicationClone] = {
    for {
      checkstyle <- root \\ "checkstyle"
      duplication <- checkstyle \ "duplication"
      nrTokens = duplication.getAttribute("nrTokens").toInt
      nrLines = duplication.getAttribute("nrLines").toInt
      message = duplication.getAttribute("message")

      fileTags = duplication \ "file"
      files = fileTags.map { fileTag =>
        val startLine = fileTag.getProperty("startLine").fold(throw new NumberFormatException)(_.toInt)
        val endLine = fileTag.getProperty("endLine").fold(throw new NumberFormatException)(_.toInt)
        DuplicationCloneFile(fileTag \@ "name", startLine, endLine)
      }
    } yield DuplicationClone(message, nrTokens, nrLines, files.toSet)
  }
}
