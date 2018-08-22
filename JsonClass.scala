case class WinNotice  (request_id: String, price: Double)
case class DSPRequest (ssp_name: String, request_time: String, request_id: String, app_id: Int)
case class DSPResponse(request_id: String, url: String, price: Double) {
  def compareTo(dspRes: DSPResponse): Boolean = {
    if (this.price >= dspRes.price) {
      return true
    } else {
      return false
    }
  }
}
