package codacy.plugins.test.duplication

import com.codacy.analysis.core.model.DuplicationClone
import com.codacy.plugins.api.duplication.DuplicationCloneFile
import org.scalatest.funsuite.AnyFunSuite

class CheckstyleFormatParserTest extends AnyFunSuite {
  val validCheckstyleXml = <checkstyle version="4.3">
    <duplication nrTokens="2" nrLines="1" message="foobar">
      <property name="ignoreMessage"></property>
      <file name="foobar.txt">
        <property name="startLine" value="1"></property>
        <property name="endLine" value="2"></property>
      </file>
      <file name="foobar2.txt">
        <property name="startLine" value="1"></property>
        <property name="endLine" value="2"></property>
      </file>
    </duplication>
    <duplication nrTokens="2" nrLines="1" message="bar">
      <file name="bar.txt">
        <property name="startLine" value="1"></property>
        <property name="endLine" value="2"></property>
      </file>
      <file name="bar2.txt">
        <property name="startLine" value="1"></property>
        <property name="endLine" value="2"></property>
      </file>
    </duplication>
  </checkstyle>

  val ignoreMessageCheckstyle = <checkstyle version="4.3">
    <property name="ignoreMessage"></property>
    <duplication nrTokens="2" nrLines="1" message="foobar">
      <file name="foobar.txt">
        <property name="startLine" value="1"></property>
        <property name="endLine" value="2"></property>
      </file>
    </duplication>
  </checkstyle>

  val invalidCheckstyleXml = <checkstyle version="4.3">
    <duplication>
      <property name="nrLines" value="1"></property>
      <file name="foobar.txt">
        <property name="startLine" value="1"></property>
        <property name="endLine" value="2"></property>
      </file>
    </duplication>
  </checkstyle>

  test("checkstyle format parser with valid input") {
    val foobarFiles = Set(DuplicationCloneFile("foobar.txt", 1, 2), DuplicationCloneFile("foobar2.txt", 1, 2))
    val barFiles = Set(DuplicationCloneFile("bar.txt", 1, 2), DuplicationCloneFile("bar2.txt", 1, 2))
    val expected = List(DuplicationClone("foobar", 2, 1, foobarFiles), DuplicationClone("bar", 2, 1, barFiles))

    val (result, ignoreMessage) = CheckstyleFormatParser.parseResultsXml(validCheckstyleXml)

    assert(result == expected)
    assert(!ignoreMessage)
  }

  test("checkstyle ignore cloned lines message") {
    val foobarFiles = Set(DuplicationCloneFile("foobar.txt", 1, 2))
    val expected = List(DuplicationClone("", 2, 1, foobarFiles))

    val (result, ignoreMessage) = CheckstyleFormatParser.parseResultsXml(ignoreMessageCheckstyle)

    assert(result == expected)
    assert(ignoreMessage)
  }

  test("checkstyle format parser with invalid input") {
    intercept[NumberFormatException](CheckstyleFormatParser.parseResultsXml(invalidCheckstyleXml))
  }
}
