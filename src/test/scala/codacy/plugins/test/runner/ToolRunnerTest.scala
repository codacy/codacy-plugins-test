package codacy.plugins.test.runner

import java.nio.file.Paths

import better.files._
import com.codacy.analysis.core.model._
import com.codacy.plugins.api.results
import org.scalatest.FunSuite

import File._

class ToolRunnerTest extends FunSuite {

  private val dockerImage = "codacy/codacy-example-tool:latest"

  test("run example tool with no configuration") {
    val file = Paths.get("foobar.txt")
    val result: Seq[Result] = (for {
      srcDir <- File.temporaryDirectory(parent = Some(root / "tmp"))
      sourceFile = srcDir / "foobar.txt"
      _ = sourceFile.write("foo\n")
      config = FileCfg()
      res = ToolRunner.run(dockerImage, srcDir, Set(file), config)
    } yield res).get()

    val expected = Seq(
      Issue(patternId = results.Pattern.Id("foobar"),
            filename = file,
            message = Issue.Message("found foo"),
            level = results.Result.Level.Info,
            category = None,
            location = LineLocation(1))
    )

    assert(result === expected)
  }

  test("run example tool with /.codacyrc") {
    val result: Seq[Result] = (for {
      srcDir <- File.temporaryDirectory(parent = Some(root / "tmp"))
      _ = (srcDir / "foobar.txt").write("foo\n")
      _ = (srcDir / "test.txt").write("test\n")
      config = CodacyCfg(Set(Pattern(id = "foobar", parameters = Set(Parameter(name = "value", value = "test")))))
      res = ToolRunner.run(dockerImage, srcDir, Set("foobar.txt", "test.txt").map(Paths.get(_)), config)
    } yield res).get()

    val expected = Seq(
      Issue(patternId = results.Pattern.Id("foobar"),
            filename = Paths.get("test.txt"),
            message = Issue.Message("found test"),
            level = results.Result.Level.Info,
            category = None,
            location = LineLocation(1))
    )

    assert(result === expected)
  }
}
