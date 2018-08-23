import java.net.ServerSocket
import java.net.Socket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayList

abstract class Server(port: Int){
  val server = new ServerSocket(port)
  def run() {
    controller()
    server.close()
  }

  def controller() {}

  def getRequest(input: InputStream): (ArrayList[String],String) = {
    val isr = new InputStreamReader(input)
    val br = new BufferedReader(isr)
    val list = readRequestHead(br, new ArrayList[String])
    val contentLength = getContentLength(list)
    val content = contentLength match {
      case -1 => ""
      case _ => getContent(br,list,contentLength)
    }
    return (list,content)
  }

  def readRequestHead(br: BufferedReader, req: ArrayList[String]): ArrayList[String] = {
    val line = br.readLine
    req.add(line)
    line match {
      case "" => req
      case _ => readRequestHead(br, req)
    }
  }

  def getContentLength(request: ArrayList[String]): Int = {
    val regax = "(^Content-Length: [0-9]+)".r
    val trim = "[0-9]+$".r
    for (e <- request.toArray) {
      e match {
        case regax(e) => return trim.findFirstMatchIn(e).get.toString.toInt
        case _ => Unit
      }
    }
    // content-length not found
    return -1
  }

  def getContent(br: BufferedReader, request: ArrayList[String], length: Int): String = {
    var content = ""
    for (i <- 0 until length) {
      content += br.read.toChar
    }
    return content
  }

  def sendResponse(output: OutputStream, responseBody: String) {
    val response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Length: " + responseBody.length + "\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "\r\n" + 
                    responseBody
    output.write(response.getBytes(StandardCharsets.UTF_8))
  }

}
