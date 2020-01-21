package codacy.plugins.test.duplication

import scala.util.{Failure, Success, Try}
import com.codacy.plugins.api.duplication.DuplicationClone
import codacy.plugins.test.Utils.exceptionToString
import wvlet.log.LogSupport

private[duplication] object ResultPrinter extends LogSupport {

  def printToolResults(res: Try[Set[DuplicationClone]], expectedResults: Set[DuplicationClone]) =
    res match {
      case Failure(e) =>
        info("Got failure in the analysis:")
        error(exceptionToString(e))
        false
      case Success(results) =>
        if (results == expectedResults) {
          debug(s"Got ${results.size} results.")
          true
        } else {
          error("Tool results don't match expected results:")
          error("Extra: ")
          info(pprint.apply(results.diff(expectedResults), height = Int.MaxValue))
          error("Missing:")
          info(pprint.apply(expectedResults.diff(results), height = Int.MaxValue))
          false
        }
    }
}
