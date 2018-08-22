import java.net.Socket
import java.util.ArrayList
import java.net.HttpURLConnection
import java.net.URL
import java.io.PrintStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeoutException
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.parser._

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}

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
    app_id match {
      case -1 => 
        sendResponse(os,"shutdown...")
        running = false
      case _ => Unit
    }

    // request for dsp
    var maxPriceResponse = new DSPResponse(null, null, 0)
    var winner_url = ""
    var secondPriceResponse = new DSPResponse(null, null, 0)
    for (i <- 0 until dspList.length) {
        val dspResponse = dspRequest(dspList(i),app_id)
        if (! maxPriceResponse.compareTo(dspResponse)) {
          secondPriceResponse = maxPriceResponse
          maxPriceResponse = dspResponse
          winner_url = dspList(i)
        }
    }

    // send win notice
    val body = WinNotice(maxPriceResponse.request_id, secondPriceResponse.price).asJson

    // return response for sdk
    val json = "test json"
    sendResponse(os, json)
  }

  def getAppId(content: String): Int = {
    val regax = "(\\{ \"app_id\": [0-9]+ \\})".r
    val trimRegax = "([0-9]+)".r
    content match {
      case regax(content) => return trimRegax.findFirstMatchIn(content).get.toString.toInt
      case _ => return -1
    }
  }

  def dspRequest(urlstr: String, app_id: Int):DSPResponse = {
    val request_time = "yyyyMMdd-HHMMSS.ssss"
    val request_id = SERVER_NAME + "-" + request_time
    val body = DSPRequest(SERVER_NAME, request_time, request_id, app_id).asJson
    val url = new URL(urlstr)
    val con: HttpURLConnection = url.openConnection().asInstanceOf[HttpURLConnection]
    con.setRequestMethod("POST")
    con.addRequestProperty("User-Agent", SERVER_NAME)
    con.addRequestProperty("Content-Type", "application/json; charset=UTF-8")
    con.setDoOutput(true)
    con.setDoInput(true)

    val ps = new PrintStream(con.getOutputStream);
    ps.print(body)
    ps.close()
    con.connect()
    val status = con.getResponseCode()
    println(status)
    status match {
      case HttpURLConnection.HTTP_OK => Unit
      case _ =>
        con.disconnect()
        return null
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
    var json = sb.toString
    val response = decode[DSPResponse](json)
    response match {
      case Right(res) => {
        return res
      }
      case Left(err) => {
        println(err)
      }
    }
    return null
  }
}
