package codacy.plugins.test.runner

import org.scalatest.FunSuite
import better.files._
import File._
import com.codacy.plugins.api.results
import com.codacy.analysis.core.model._
import java.nio.file.Paths

class ToolRunnerTest extends FunSuite {

  test("run example tool with no configuration") {
    val file = Paths.get("foobar.txt")

    val result: Seq[Result] = (for {
      srcDir <- File.temporaryDirectory(parent = Some(root / "tmp"))
      sourceFile = srcDir / "foobar.txt"
      _ = sourceFile.write("foo")
      config = FileCfg()
      res = ToolRunner.run("codacy/codacy-example-tool:latest", srcDir, Set(file), config)
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
}
