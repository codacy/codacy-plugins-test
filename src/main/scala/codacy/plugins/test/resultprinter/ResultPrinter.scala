package codacy.plugins.test.resultprinter

import scala.util.{Failure, Success, Try}

import codacy.plugins.test.Utils.exceptionToString
import wvlet.log.LogSupport

private[test] object ResultPrinter extends LogSupport {

  def printToolResults[A: Ordering](res: Try[Seq[A]], expectedResults: Seq[A]): Boolean =
    res match {
      case Failure(e) =>
        info("Got failure in the analysis:")
        error(exceptionToString(e))
        false
      case Success(results) =>
        val sortedResults = results.sorted
        val sortedExpectedResults = expectedResults.sorted
        if (sortedResults == sortedExpectedResults) {
          debug(s"Got ${results.size} results.")
          true
        } else {
          error("Tool results don't match expected results:")
          error("Extra: ")
          info(pprint.apply(sortedResults.diff(sortedExpectedResults), height = Int.MaxValue))
          error("Missing:")
          info(pprint.apply(sortedExpectedResults.diff(sortedResults), height = Int.MaxValue))
          false
        }
    }
}
