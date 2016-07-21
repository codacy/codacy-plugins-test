import play.api.libs.json.Reads.{IntReads, StringReads}
import play.api.libs.json._

package plugins {

import codacy.docker.api.{Parameter, Pattern}

/*value classes to make things typesafe*/
class DockerImageName(val value: String) extends AnyVal {
  override def toString = value.toString
}

object DockerImageName {
  def apply(value: String): DockerImageName = new DockerImageName(value)
}

  case class ParameterDescription(name: Parameter.Name, description: String)
  object ParameterDescription{
    implicit lazy val fmt = Json.format[ParameterDescription]
  }



// TODO: timeToFix needs to be added to jshint patterns
case class PatternDescription(patternId: Pattern.Id, title: String, description: Option[String],
                              parameters: Option[Set[ParameterDescription]],
                              private val timeToFix: Option[Int],
                              private val explanationOpt: Option[String]) {
  val fixTime = timeToFix.getOrElse(5)
  val explanation = explanationOpt.getOrElse("")
}
object PatternDescription{
  implicit lazy val fmt = Json.format[PatternDescription]
}
}