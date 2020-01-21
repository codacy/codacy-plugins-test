package codacy.plugins.test.checkstyle

import scala.xml.NodeSeq

object CheckstyleImplicits {
  implicit class NodeSeqOps(val node: NodeSeq) extends AnyVal {

    def getProperty(name: String): String = {
      (node \ "property").find(_ \@ "name" == name).get \@ "value"
    }
  }
}
