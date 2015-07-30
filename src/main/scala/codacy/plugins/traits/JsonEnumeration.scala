package codacy.plugins.traits

import play.api.libs.json._

trait JsonEnumeration {
  self: Enumeration =>

  import scala.language.implicitConversions

  implicit def enumWrites[E <: Enumeration#Value]: Writes[E] = Writes((e: E) => Json.toJson(e.toString))

  implicit def enumReads[E <: Enumeration](e: E): Reads[e.Value] = {
    Reads.StringReads.flatMap { case value => Reads((_: JsValue) =>
      e.values.collectFirst { case enumValue if enumValue.toString == value =>
        JsSuccess(enumValue)
      }.getOrElse(JsError(s"Invalid enumeration value $value"))
    )
    }
  }

  implicit lazy val reads = enumReads(self)
  implicit val format = Format(reads, enumWrites)

}
