import java.net.Socket
import java.util.ArrayList
import java.util.Calendar
import java.util.UUID
import java.io.InputStream
import java.io.OutputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.parser._

import scalaj.http._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source

object Main {
  def main(args: Array[String]) {
    val serverlist = Source.fromFile("serverlist.txt")
    val lines = serverlist.getLines
    lines.foreach(println)
    serverlist.close
    val ssps = new SSPServer(8081)
    ssps.run()
  }
}

class SSPServer(port: Int) extends Server(port) {
  val SERVER_NAME = "SSPServer_Mori"
  val dspList = Array("http://localhost:8080")
  var running = false
 override def controller() {
    running = true
    while(running) {
      val socket: Socket = server.accept
      catchRequest(socket.getInputStream, socket.getOutputStream)
      socket.close()
    }
  }

  // catch request from sdk.
  // decode request -> request for dsp & judge winner -> send win notice -> return response for sdk
  def catchRequest(is: InputStream, os: OutputStream) {
    // decode request
    val (requestLines: ArrayList[String], content: String) = getRequest(is)
    val app_id = getAppId(content)
    app_id match {
      case -1 => running = false
      case _ =>
    }

    // request for dsp
    var maxPriceResponse = new DSPResponse("not found", "not found", 0)
    var winner_url = "not found"
    var secondPriceResponse = new DSPResponse("not found", "not found", 0)
    val futureList: ArrayList[Future[DSPResponse]] = new ArrayList[Future[DSPResponse]]

    for (i <- 0 until dspList.length) {
      val f: Future[DSPResponse] = Future {
        dspRequest(dspList(i), app_id)
      }
      futureList.add(f)
    }

    for (i <- 0 until dspList.length) {
      val f = futureList.get(i)
      val dspResponse = Await.result(f,Duration.Inf)
      if (null != dspResponse && !maxPriceResponse.compareTo(dspResponse)) {
        secondPriceResponse = maxPriceResponse
        maxPriceResponse = dspResponse
        winner_url = dspList(i)
      }
    }

    // send win notice
    winner_url match {
      case "not found" =>
      case _ => sendWinNotice(winner_url, maxPriceResponse.request_id, secondPriceResponse.price)
    }

    // return response for sdk
    val json = AdUrl(maxPriceResponse.url).asJson
    sendResponse(os, json.toString)
    os.close()
    is.close()

    // logging sale
    val fos = new FileOutputStream("sale.log", true)
    val filewriter = new OutputStreamWriter(fos, "UTF-8")
    filewriter.write(maxPriceResponse.request_id + ": " + maxPriceResponse.price + "\r\n")
    filewriter.close()
    fos.close()
  }

  def getAppId(content: String): Int = {
    val regax = "(\\{ \"app_id\": [0-9]+ \\})".r
    val trimRegax = "([0-9]+)".r
    content match {
      case regax(content) => return trimRegax.findFirstMatchIn(content).get.toString.toInt
      case _ => return -1
    }
  }

  // Server class have this method better than SSPServer class. :(
  def post(urlstr: String, body: String):String = {
   val resp: HttpResponse[String] =
      Http(urlstr)
        .postData(body)
        .headers(
          "User-Agent" -> SERVER_NAME,
          "Content-Type" -> "application/json; charset=UTF-8"
        ).asString
    return if (resp.is2xx) resp.body else null
  }

  def postDSPRequest(urlstr: String, body: String):String = {
    val resp: HttpResponse[String] =
       Http(urlstr)
         .postData(body)
//         .timeout(connTimeoutMs = 150, readTimeoutMs = 100)
         .headers(
           "User-Agent" -> SERVER_NAME,
           "Content-Type" -> "application/json; charset=UTF-8"
         ).asString

    return if (resp.is2xx) resp.body else null
  }


  def sendWinNotice(urlstr: String, request_id: String, price: Double):Boolean = {
    val body = WinNotice(request_id, price).asJson
    val result = post(urlstr + "/win", body.toString)
    val response = decode[WinNoticeResult](result)
    response match {
      case Right(res) => {
        res.result match {
          case "ok" => return true
          case _ => return false
        }
      }
      case Left(err) => {
        return false
      }
    }
  }

  def dspRequest(urlstr: String, app_id: Int):DSPResponse = {
//    return DSPResponse("aaa", "hoge.jpg", 123.45)
    val sdf = new SimpleDateFormat("yyyyMMdd-HHmmss.SSSS")
    val request_time = sdf.format(Calendar.getInstance().getTime())
    val request_id = SERVER_NAME + "-" + request_time + UUID.randomUUID().toString()
    val body = DSPRequest(SERVER_NAME, request_time, request_id, app_id).asJson
    val result = postDSPRequest(urlstr + "/req", body.toString)
    val response = decode[DSPResponse](result)
    response match {
      case Right(res) => {
        return res
      }
      case Left(err) => {
        println(err)
        return null
      }
    }
  }
}
