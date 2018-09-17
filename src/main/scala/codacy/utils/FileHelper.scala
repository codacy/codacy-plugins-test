package codacy.utils

import java.io.File
import java.nio.charset.CodingErrorAction

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
      case NonFatal(_) =>
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

}
