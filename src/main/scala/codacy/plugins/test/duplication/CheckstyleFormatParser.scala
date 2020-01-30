package codacy.plugins.test.duplication

import com.codacy.plugins.api.duplication.DuplicationCloneFile
import com.codacy.analysis.core.model.DuplicationClone
import scala.xml.Elem
import codacy.plugins.test.checkstyle.CheckstyleImplicits._

private[duplication] object CheckstyleFormatParser {

  private def shouldIgnoreMessage(root: Elem): Boolean = {
    (root \\ "checkstyle").isPropertyDefined("ignoreMessage")
  }

  def parseResultsXml(root: Elem): (Seq[DuplicationClone], Boolean) = {
    val ignoreMessage = shouldIgnoreMessage(root)

    val duplications = for {
      checkstyle <- root \\ "checkstyle"
      duplication <- checkstyle \ "duplication"
      nrTokens = duplication.getAttribute("nrTokens").toInt
      nrLines = duplication.getAttribute("nrLines").toInt
      message = if (ignoreMessage) "" else duplication.getAttribute("message")

      fileTags = duplication \ "file"
      files = fileTags.map { fileTag =>
        val startLine = fileTag.getProperty("startLine").fold(throw new NumberFormatException)(_.toInt)
        val endLine = fileTag.getProperty("endLine").fold(throw new NumberFormatException)(_.toInt)
        DuplicationCloneFile(fileTag \@ "name", startLine, endLine)
      }
    } yield DuplicationClone(message, nrTokens, nrLines, files.toSet)

    (duplications, ignoreMessage)
  }
}
