package codacy.docker

import play.api.libs.json.{JsNull, JsString, JsValue, Json}

import scala.util.Try

package object api extends JsonApi{

  implicit class ParameterExtensions(param:Parameter.type){
    def Value(jsValue:JsValue):Parameter.Value = ParamValue(jsValue)
    def Value(raw:String):Parameter.Value = Value(Try(Json.parse(raw)).getOrElse(JsString(raw)))
  }

}