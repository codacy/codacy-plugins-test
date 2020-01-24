package codacy.plugins.test.resultprinter

import codacy.plugins.test.Utils.exceptionToString
import wvlet.log.LogSupport

import scala.util.{Failure, Success, Try}

private[test] object ResultPrinter extends LogSupport {

  def printToolResults[A](res: Try[Set[A]], expectedResults: Set[A]): Boolean =
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
