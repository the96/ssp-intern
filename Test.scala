import java.net.ServerSocket
import java.net.Socket
import java.io.BufferedReader
import java.io.InputStreamReader

object Test {
	def main(args: Array[String]) {
		val server: ServerSocket = new ServerSocket(8081)
		while (true) {
			val socket:Socket = server.accept

			val input = socket.getInputStream
			val isr = new InputStreamReader(input);
			val br = new BufferedReader(isr);
			var request:String = ""
			while(br.ready()) {
				request += br.readLine
			}
			br.close()
			isr.close()
			input.close()
			println(request)
		}
	}
}
