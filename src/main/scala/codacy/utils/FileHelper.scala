package codacy.utils

import java.io.File
import java.nio.charset.CodingErrorAction
import java.nio.file.Files

import org.apache.commons.io.FileUtils

import scala.io.{Codec, Source}
import scala.util.Try
import scala.util.control.NonFatal

object FileHelper {
  implicit val codec = Codec("UTF-8")
  codec.onMalformedInput(CodingErrorAction.REPLACE)
  codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

  def read(file: File): Option[Seq[String]] = {
    val sourceOpt = Try(Source.fromFile(file)).toOption

    try {
      sourceOpt.map(_.getLines().toList)
    } catch {
      case NonFatal(e) =>
        None
    } finally {
      sourceOpt.foreach(_.close())
    }
  }

  def listFiles(file: File): Seq[File] = {
    file.isDirectory match {
      case true => file.listFiles().flatMap(listFiles)
      case _ => Seq(file)
    }
  }

  def withRandomDirectory[A](block: File => A): A = {
    val randomDir = Files.createTempDirectory("codacy-").toFile
    val result = block(randomDir)
    FileUtils.deleteDirectory(randomDir)
    result
  }

  private def randomFile(extension: String = "conf"): File = {
    Files.createTempFile("codacy-", s".$extension").toFile
  }
}
