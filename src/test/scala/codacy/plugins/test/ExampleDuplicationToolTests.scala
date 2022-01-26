package codacy.plugins.test

import codacy.plugins.DockerTest
import org.scalatest._

class ExampleDuplicationToolTests extends FunSuite {
  test("[ExampleDuplicationTool] tests should pass") {
    DockerTest.main(Array("pattern", "codacy/codacy-duplication-example-tool:latest"))
  }
  test("[ExampleDuplicationTool] tests with wrong test name should throw exception") {
    intercept[Exception](DockerTest.main(Array("patern", "codacy/codacy-duplication-example-tool:latest")))
  }
}
