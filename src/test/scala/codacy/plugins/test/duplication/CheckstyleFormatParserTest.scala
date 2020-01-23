package codacy.plugins.test.duplication

import com.codacy.plugins.api.duplication.{DuplicationClone, DuplicationCloneFile}
import org.scalatest.FunSuite

class CheckstyleFormatParserTest extends FunSuite {
  val validCheckstyleXml = <checkstyle version="4.3">
    <property name="nrTokens" value="2"></property>
    <property name="nrLines" value="1"></property>
    <file name="foobar.txt">
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
    val files = Seq(DuplicationCloneFile("foobar.txt", 1, 2))
    val expected = List(DuplicationClone("", 2, 1, files))

    val result = CheckstyleFormatParser.parseResultsXml(validCheckstyleXml)

    assert(result == expected)
  }

  test("checkstyle format parser with invalid input") {
    intercept[NumberFormatException](CheckstyleFormatParser.parseResultsXml(invalidCheckstyleXml))
  }
}
