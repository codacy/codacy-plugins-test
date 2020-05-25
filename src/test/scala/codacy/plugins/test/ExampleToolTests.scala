package codacy.plugins.test

import codacy.plugins.DockerTest
import org.scalatest._

class ExampleToolTests extends FunSuite {
  test("tests with ExampleTool should pass") {
    DockerTest.main(Array("pattern", "codacy/codacy-example-tool:latest"))
  }
  test("tests with wrong test name should throw exception") {
    intercept[Exception](DockerTest.main(Array("patern", "codacy/codacy-example-tool:latest")))
  }
}
