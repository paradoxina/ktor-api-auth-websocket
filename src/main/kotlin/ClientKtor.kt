import RequestData.ActB
import io.ktor.client.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.serialization.json.Json
import java.util.*

val client = HttpClient {
    install(WebSockets)
    install(Auth) {
        basic {
            username = "username"
            password = "password"
        }
    }
}

val socketJson = Json { encodeDefaults = false }

suspend fun HttpClient.socket(send: Boolean = false) = ws(
    method = HttpMethod.Get,
    host = "127.0.0.1",
    port = 8080,
    path = "/ws",
    request = { auth("username", "password") }
) {
    if (send) {
        outgoing.send(Frame.Text(socketJson.encodeToString(RequestData.serializer(), ActB(2, "B", false))))
    }
    for (frame in incoming) {
        when (frame) {
            is Frame.Text -> {
                val text = frame.readText()
                println("client: $text")
            }
        }
    }
}

fun HttpRequestBuilder.auth(username: String = "username", password: String = "password") {
    header(
        HttpHeaders.Authorization,
        "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))}"
    )
}