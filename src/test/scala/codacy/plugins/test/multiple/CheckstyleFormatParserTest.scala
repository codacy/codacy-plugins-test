package codacy.plugins.test.multiple

import com.codacy.analysis.core.model.Issue.Message
import com.codacy.analysis.core.model._
import com.codacy.plugins.api
import com.codacy.plugins.api.results.Result.Level
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Paths

class CheckstyleFormatParserTest extends AnyFunSuite {
  val patternId = "my_pattern"

  val validCheckstyleXml = <checkstyle version="4.3">
    <file name="foo.txt">
      <error message="Error message"></error>
    </file>
    <file name="bar.txt">
      <error source={patternId} line="1" message="Error1" severity="info"></error>
      <error source={patternId} line="2" message="Error2" severity="warning"></error>
    </file>
  </checkstyle>

  test("checkstyle format parser with valid input") {
    val patternIdWrapper = api.results.Pattern.Id(patternId)
    val expected = Set(FileError(Paths.get("foo.txt"), "Error message"),
                       Issue(patternIdWrapper,
                             Paths.get("bar.txt"),
                             Message("Error1"),
                             category = None,
                             level = Level.Info,
                             location = LineLocation(1)),
                       Issue(patternIdWrapper,
                             Paths.get("bar.txt"),
                             Message("Error2"),
                             category = None,
                             level = Level.Warn,
                             location = LineLocation(2)))

    val result = CheckstyleFormatParser.parseResultsXml(validCheckstyleXml).toSet

    assert(result === expected)
  }
}
