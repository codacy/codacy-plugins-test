package codacy

import play.api.libs.json.Json
import com.codacy.plugins.api.results.{Parameter, Pattern}

package object plugins {


  case class ParameterDescription(name: Parameter.Name, description: String)

  object ParameterDescription {
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

  object PatternDescription {
    implicit lazy val fmt = Json.format[PatternDescription]
  }

}
