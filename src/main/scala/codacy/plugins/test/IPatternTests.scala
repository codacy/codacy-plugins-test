package codacy.plugins.test

import com.codacy.plugins.api.results.Result
import org.scalatest.matchers.{MatchResult, Matcher}

import scala.util.Properties

final case class TestFileResult(patternInternalId: String, line: Int, level: Result.Level)

trait CustomMatchers {

  private val sep = Properties.lineSeparator

  class TestFileMatcher(expectedMatches: Seq[TestFileResult]) extends Matcher[Seq[TestFileResult]] {

    def apply(matches: Seq[TestFileResult]): MatchResult = {
      val sortedMatches = matches.sortBy(m => (m.line, m.patternInternalId, m.level))
      val sortedExpectedMatches = expectedMatches.sortBy(m => (m.line, m.patternInternalId, m.level))

      val missingMatches = sortedExpectedMatches.diff(sortedMatches)
      val extraMatches = sortedMatches.diff(sortedExpectedMatches)

      MatchResult(sortedMatches == sortedExpectedMatches, s"""|  -> Found:
            |   ${printResults(sortedMatches)}
            |  -> Expected:
            |   ${printResults(sortedExpectedMatches)}
            |  --------------------------------------
            |  -> Missing:
            |   ${printResults(missingMatches)}
            |  -> Extra:
            |   ${printResults(extraMatches)}""".stripMargin, s"""|  Results found matched the expected:
            |   ${printResults(sortedMatches)}""".stripMargin)
    }

    private def printResults(results: Seq[TestFileResult]): String = {
      results
        .map { result =>
          (result.patternInternalId, result.line, result.level)
        }
        .mkString(s"$sep   ")
    }
  }

  def beEqualTo(expectedMatches: Seq[TestFileResult]) = new TestFileMatcher(expectedMatches)

}
