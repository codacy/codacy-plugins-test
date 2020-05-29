package codacy.plugins.test.implicits

import com.codacy.analysis.core.model._
import com.codacy.plugins.api.duplication.DuplicationCloneFile
import com.codacy.plugins.api.results

import Ordering.Implicits._

object OrderingInstances {
  implicit val duplicationCloneFileOrdering: Ordering[DuplicationCloneFile] = Ordering.by(DuplicationCloneFile.unapply)
  implicit val duplicationCloneFileSetOrdering = Ordering.by((s: Set[DuplicationCloneFile]) => s.toSeq)
  implicit val duplicationClonesOrdering: Ordering[DuplicationClone] = Ordering.by(DuplicationClone.unapply)

  implicit val parameterOrdering = Ordering.by(Parameter.unapply)
  implicit val patternIdOrdering = Ordering.by(results.Pattern.Id.unapply)
  implicit val messageOrdering = Ordering.by(Issue.Message.unapply)
  implicit val levelOrdering: Ordering[results.Result.Level] = Ordering.by(_.toString)
  implicit val categoryOrdering: Ordering[results.Pattern.Category] = Ordering.by(_.toString)
  implicit val subcategoryOrdering: Ordering[results.Pattern.Subcategory] = Ordering.by(_.toString)
  implicit val locationOrdering: Ordering[Location] = Ordering.by[Location, Int] {
    case LineLocation(l) => l
    case FullLocation(l, _) => l
  }
  implicit val issueOrdering = Ordering.by(Issue.unapply)
  implicit val fileErrorOrdering = Ordering.by(FileError.unapply)

  implicit val toolResultOrdering: Ordering[ToolResult] =
    new Ordering[ToolResult] {

      def compare(x: ToolResult, y: ToolResult): Int = (x, y) match {
        case (_: FileError, _: Issue) => -1
        case (_: Issue, _: FileError) => 1
        case (e1: FileError, e2: FileError) => fileErrorOrdering.compare(e1, e2)
        case (i1: Issue, i2: Issue) => issueOrdering.compare(i1, i2)
      }
    }
}
