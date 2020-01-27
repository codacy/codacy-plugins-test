package codacy.plugins.test.checkstyle

import scala.xml.NodeSeq

object CheckstyleImplicits {

  implicit class NodeSeqOps(val node: NodeSeq) extends AnyVal {

    def getProperty(name: String): Option[String] = {
      for {
        propertyNode <- (node \ "property").find(p => (p \@ "name") == name)
      } yield propertyNode \@ "value"
    }

    def isPropertyDefined(name: String): Boolean = {
      val property = for {
        propertyNode <- (node \ "property").find(p => (p \@ "name") == name)
      } yield propertyNode \@ "name"

      property.fold(false)(_ => true)
    }

    def getAttribute(name: String): String = {
      node \@ name
    }
  }

}
