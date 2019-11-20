package codacy.plugins.test.multiple
import scala.util.{Failure, Success, Try}
import com.codacy.plugins.results.PluginResult
import wvlet.log.LogSupport

object ResultPrinter extends LogSupport {
  private[multiple] def printToolResults(res: Try[Set[PluginResult]], expectedResults: Set[PluginResult]) = res match {
    case Failure(e) =>
      info("Got failure in the analysis:")
      error(e.getStackTraceString)
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
