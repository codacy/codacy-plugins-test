package codacy.plugins.test.duplication

import com.codacy.plugins.api.duplication.{DuplicationClone, DuplicationCloneFile}

import scala.xml.Elem
import codacy.plugins.test.checkstyle.CheckstyleImplicits._

private[duplication] object CheckstyleFormatParser {

  def parseResultsXml(root: Elem): Seq[DuplicationClone] = {
    for {
      fileTags <- root \\ "checkstyle" \ "file"
      checkstyle = root \\ "checkstyle"
      nrTokens = checkstyle.getProperty("nrTokens").toInt
      nrLines = checkstyle.getProperty("nrLines").toInt
      files = fileTags.map { fileTag =>
        val startLine = fileTag.getProperty("startLine").toInt
        val endLine = fileTag.getProperty("endLine").toInt
        DuplicationCloneFile(fileTag \@ "name", startLine, endLine)
      }
    } yield DuplicationClone("", nrTokens, nrLines, files)
  }
}
