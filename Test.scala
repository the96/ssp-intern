import java.net.ServerSocket
import java.net.Socket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.ArrayList
import java.nio.charset.StandardCharsets

object Test {
	def main(args: Array[String]) {
		val server: ServerSocket = new ServerSocket(8081)
		while (true) {
			val socket:Socket = server.accept

			val input = socket.getInputStream
			val isr = new InputStreamReader(input);
			val br = new BufferedReader(isr);

			def readRequest(br: BufferedReader, req: ArrayList[String]): ArrayList[String] = {
				val line = br.readLine
				req.add(line)
				line match {
					case "" => req
					case _ => readRequest(br, req)
				}
			}

      var request = readRequest(br,new ArrayList[String]())
			var contentLength = 0
			println("======HTTP REQUEST=====")
			for (e <- request.toArray) {
				println(e)
				val regax = "(^Content-Length: [0-9]+)".r
				val trimRegax = "[0-9]+$".r
				e match {
					case regax(e) => contentLength = trimRegax.findFirstMatchIn(e).get.toString.toInt
					case _ => Unit
				}
			}
      var content = ""
			for (x <- 0 until contentLength) content += br.read.toChar
			println(content)
			println("======END OF REQUEST=====")
      
      val output = socket.getOutputStream
      val response = "HTTP/1.1 200 OK\r\n" +
                     "Content-Length: " + contentLength + "\r\n" +
                     "Content-Type: text/plain\r\n" +
                     "\r\n" + 
                     content;
      output.write(response.getBytes(StandardCharsets.UTF_8))
      println(response)
			br.close()
			isr.close()
			input.close()
      output.close()
		}
		server.close()
	}
}
