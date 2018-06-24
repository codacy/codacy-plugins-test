package codacy.docker

import com.codacy.plugins.api.Options
import com.codacy.plugins.api.results.Parameter
import play.api.libs.json.{JsString, JsValue, Json}

import scala.util.Try

package object api extends JsonApi {

  @SuppressWarnings(Array("UnusedMethodParameter"))
  implicit class ParameterExtensions(param: Parameter.type) {
    def Value(jsValue: JsValue): Parameter.Value = ParamValue(jsValue)
    def Value(raw: String): Parameter.Value = Value(Try(Json.parse(raw)).getOrElse(JsString(raw)))
  }

  @SuppressWarnings(Array("UnusedMethodParameter"))
  implicit class OptionsExtensions(options: Options.type) {
    def Value(jsValue: JsValue): Options.Value = OptionValue(jsValue)
    def Value(raw: String): Options.Value = Value(Try(Json.parse(raw)).getOrElse(JsString(raw)))
  }

}
