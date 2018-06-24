package codacy.utils

import java.io.File
import java.nio.charset.CodingErrorAction
import java.nio.file.Files

import scala.io.{Codec, Source}
import scala.util.Try
import scala.util.control.NonFatal

object FileHelper {
  implicit val codec: Codec = Codec("UTF-8")
  codec.onMalformedInput(CodingErrorAction.REPLACE)
  codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

  def read(file: File): Option[Seq[String]] = {
    val sourceOpt = Try(Source.fromFile(file)).toOption

    try {
      sourceOpt.map(_.getLines().toList)
    } catch {
      case NonFatal(_) =>
        None
    } finally {
      sourceOpt.foreach(_.close())
    }
  }

  def listFiles(file: File): Seq[File] = {
    if (file.isDirectory) {
      file.listFiles().flatMap(listFiles)
    } else {
      Seq(file)
    }
  }

  def withRandomDirectory[A](block: File => A): A = {
    val randomDir = Files.createTempDirectory("codacy-").toFile
    val result = block(randomDir)
    better.files.File(randomDir.toPath).delete()
    result
  }

}
