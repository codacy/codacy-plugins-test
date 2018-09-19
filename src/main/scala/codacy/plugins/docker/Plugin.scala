package codacy.plugins.docker

import play.api.libs.json.JsValue

case class Pattern(patternIdentifier: String,
                   parameters: Option[Map[String, JsValue]])
