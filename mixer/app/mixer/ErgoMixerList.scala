package mixer

import play.api.libs.json._

case class MixBoxList(items: Iterable[MixBox])

object MixBoxList {
  implicit val ReadsMixBoxList: Reads[MixBoxList] = new Reads[MixBoxList] {
    override def reads(json: JsValue): JsResult[MixBoxList] = {
      JsSuccess(MixBoxList(json.as[JsArray].value.map(item => {
        val withdraw = (item \ "withdraw").as[String]
        val amount = (item \ "amount").as[Long]
        val token = (item \ "token").as[Int]
        MixBox(withdraw, amount, token)
      })))
    }
  }
}
