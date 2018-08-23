import java.net.Socket
import java.util.ArrayList
import java.util.Calendar
import java.net.HttpURLConnection
import java.net.URL
import java.io.PrintStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.parser._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import scala.language.postfixOps

object Main {
  def main(args: Array[String]) {
    val ssps = new SSPServer(80)
    val dsps = new DSPServer(8080)
    Future { dsps.run() }
    ssps.run()
  }
}

class SSPServer(port: Int) extends Server(port) {
  val SERVER_NAME = "SSPServer_Mori"
  val dspList = Array("http://localhost:8080","http://localhost:8080")
  var running = false
  override def controller() {
    running = true
    while(running) {
      val socket: Socket = server.accept
      catchRequest(socket.getInputStream, socket.getOutputStream)
    }
  }

  // catch request from sdk.
  // decode request -> request for dsp & judge winner -> send win notice -> return response for sdk
  def catchRequest(is: InputStream, os: OutputStream) {
    // decode request
    val (requestLines: ArrayList[String], content: String) = getRequest(is)
    val app_id = getAppId(content)

    // request for dsp
    var maxPriceResponse = new DSPResponse("not found", "not found", 0)
    var winner_url = "not found"
    var secondPriceResponse = new DSPResponse("not found", "not found", 0)
    for (i <- 0 until dspList.length) {
      val sdf = new SimpleDateFormat("yyyyMMdd-HHmmss.SSSS")
      println(dspRequest(dspList(i),app_id).request_id)
      println(sdf.format(Calendar.getInstance().getTime()))
      val f: Future[DSPResponse] = Future {
        dspRequest(dspList(i), app_id)
      }
      try {
        val dspResponse = Await.result(f, 1000 milliseconds)
        if (! maxPriceResponse.compareTo(dspResponse)) {
          secondPriceResponse = maxPriceResponse
          maxPriceResponse = dspResponse
          winner_url = dspList(i)
        }
      } catch { case e => println("timeout") }
      println(sdf.format(Calendar.getInstance().getTime()))
    }

    // send win notice
    winner_url match {
      case "not found" =>
      case _ => print(sendWinNotice(winner_url, maxPriceResponse.request_id, secondPriceResponse.price))
    }

    // return response for sdk
    val json = AdUrl(maxPriceResponse.url).asJson
    sendResponse(os, json.toString)
  }

  def getAppId(content: String): Int = {
    val regax = "(\\{ \"app_id\": [0-9]+ \\})".r
    val trimRegax = "([0-9]+)".r
    content match {
      case regax(content) => return trimRegax.findFirstMatchIn(content).get.toString.toInt
      case _ => return -1
    }
  }

  def post(urlstr: String, body: String):String = {
    val url = new URL(urlstr)
    val con: HttpURLConnection = url.openConnection().asInstanceOf[HttpURLConnection]
    con.setRequestMethod("POST")
    con.addRequestProperty("User-Agent", SERVER_NAME)
    con.addRequestProperty("Content-Type", "application/json; charset=UTF-8")
    con.setDoOutput(true)
    con.setDoInput(true)

    val ps = new PrintStream(con.getOutputStream)
    ps.print(body)
    ps.close()
    con.connect()
    con.getResponseCode() match {
      case HttpURLConnection.HTTP_OK => Unit
      case _ => return null
    }
    val br = new BufferedReader(new InputStreamReader(con.getInputStream))
    val sb = new StringBuilder()
    var line = br.readLine()
    while (line != null) {
      sb.append(line)
      line = br.readLine()
    }
    con.disconnect()
    br.close()
    return sb.toString
  }

  def sendWinNotice(urlstr: String, request_id: String, price: Double):Boolean = {
    val body = WinNotice(request_id, price).asJson
    val result = post(urlstr, body.toString)
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
    val sdf = new SimpleDateFormat("yyyyMMdd-HHmmss.SSSS")
    val request_time = sdf.format(Calendar.getInstance().getTime())
    val request_id = SERVER_NAME + "-" + request_time + Random.nextInt(400000000)
    val body = DSPRequest(SERVER_NAME, request_time, request_id, app_id).asJson
    val result = post(urlstr, body.toString)
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
