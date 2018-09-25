import codacy.utils.Printer

import scala.util.{Failure, Success, Try}

package object codacy {

  implicit class TryExtension[T](nativeTry: Try[T]) {

    def toOptionWithLog(logger: String => Unit = defaultLogger): Option[T] = {
      nativeTry match {
        case success @ Success(_) =>
          success.toOption
        case Failure(e) =>
          val msg =
            s"""
               |Exception when converting Try.toOption
               |message: ${e.getMessage}
               |${e.getStackTrace.mkString(System.lineSeparator)}
        """.stripMargin
          logger(msg)
          None
      }
    }

    private def defaultLogger(msg: String): Unit = Printer.red(msg)

  }

}
