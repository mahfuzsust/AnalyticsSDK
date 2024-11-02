package info.mahfuz.analyticssdk

import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL


object HttpClient {
    @Throws(Exception::class)
    fun post(endpointUrl: String?, events: List<PlayerEvent?>?) {
        val url = URL(endpointUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val jsonPayload = Gson().toJson(events)

        connection.outputStream.use { os ->
            val input = jsonPayload.toByteArray(charset("utf-8"))
            os.write(input, 0, input.size)
        }
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw RuntimeException("Failed to publish events. HTTP response code: $responseCode")
        }
    }
}
