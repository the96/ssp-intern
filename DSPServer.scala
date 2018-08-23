import java.net.Socket
import java.util.ArrayList
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.parser._

class DSPServer(port: Int) extends Server(port) {
  var running = false
  override def controller() {
    running = true
    while(running) {
      val socket: Socket = server.accept
      val (requestLines: ArrayList[String], content: String) = getRequest(socket.getInputStream)
      var response:io.circe.Json = null
      decode[WinNotice](content) match {
        case Right(notice) =>
          println(notice)
          sendResponse(socket.getOutputStream, "{ \"result\": \"ok\" }")
        case Left(error) =>
          decode[DSPRequest](content) match {
            case Right(request) => {
              response = DSPResponse(request.request_id, "http://localhost/hoge.jpg", 12.345).asJson
              running = request.app_id match {
                case -1 => false
                case _ => true
              }
              sendResponse(socket.getOutputStream, response.toString)
            }
            case Left(error) => {
              println(error)
            }
          }
      }
    }
  }
}
