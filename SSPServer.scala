import java.net.Socket
import java.util.ArrayList

object Main {
  def main(args: Array[String]) {
    val ssps = new SSPServer()
    ssps.run()
  }
}

class SSPServer() extends Server() {
  override def controller() {
    while(true) {
      val socket:Socket = server.accept
      val (requestLines: ArrayList[String], content: String) = getRequest(socket.getInputStream)
      sendResponse(socket.getOutputStream, content)
      content match {
        case "shutdown" => return
        case _ => Unit
      }
    }
  }
}
