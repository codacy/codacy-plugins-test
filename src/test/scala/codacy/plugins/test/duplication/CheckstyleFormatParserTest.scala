package codacy.plugins.test.duplication

import com.codacy.plugins.duplication.api.DuplicationCloneFile
import com.codacy.analysis.core.model.DuplicationClone
import org.scalatest.FunSuite

class CheckstyleFormatParserTest extends FunSuite {
  val validCheckstyleXml = <checkstyle version="4.3">
    <property name="nrTokens" value="2"></property>
    <property name="nrLines" value="1"></property>
    <property name="message" value="foobar"></property>
    <file name="foobar.txt">
      <property name="startLine" value="1"></property>
      <property name="endLine" value="2"></property>
    </file>
    <file name="foobar2.txt">
      <property name="startLine" value="1"></property>
      <property name="endLine" value="2"></property>
    </file>
  </checkstyle>

  val invalidCheckstyleXml = <checkstyle version="4.3">
    <property name="nrLines" value="1"></property>
    <file name="foobar.txt">
      <property name="startLine" value="1"></property>
      <property name="endLine" value="2"></property>
    </file>
  </checkstyle>

  test("checkstyle format parser with valid input") {
    val files = Set(DuplicationCloneFile("foobar.txt", 1, 2), DuplicationCloneFile("foobar2.txt", 1, 2))
    val expected = List(DuplicationClone("foobar", 2, 1, files))

    val result = CheckstyleFormatParser.parseResultsXml(validCheckstyleXml)

    assert(result == expected)
  }

  test("checkstyle format parser with invalid input") {
    intercept[NumberFormatException](CheckstyleFormatParser.parseResultsXml(invalidCheckstyleXml))
  }
}
