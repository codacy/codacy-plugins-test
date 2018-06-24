package codacy.plugins.traits

import play.api.libs.json.{Format, Reads, _}

trait JsonEnumeration {
  self: Enumeration =>

  import scala.language.implicitConversions

  implicit def enumWrites[E <: Enumeration#Value]: Writes[E] = Writes((e: E) => Json.toJson(e.toString))

  implicit def enumReads[E <: Enumeration](e: E): Reads[e.Value] = {
    Reads.StringReads.flatMap { value =>
      Reads(
        (_: JsValue) =>
          e.values
            .collectFirst {
              case enumValue if enumValue.toString == value =>
                JsSuccess(enumValue)
            }
            .getOrElse[JsResult[e.Value]](JsError(s"Invalid enumeration value $value"))
      )
    }
  }

  implicit lazy val reads: Reads[JsonEnumeration.this.Value] = enumReads(self)
  implicit val format: Format[JsonEnumeration.this.Value] = Format(reads, enumWrites)

}
