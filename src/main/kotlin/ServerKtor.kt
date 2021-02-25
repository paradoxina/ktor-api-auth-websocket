import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.LinkedHashSet

object ServerKtor {
    fun start() =
        embeddedServer(Netty, port = 8080, host = "127.0.0.1", module = Application::routing).start(wait = false)
}

class WsClients {
    val wsClients = mutableMapOf<User, MutableList<SendChannel<Frame>>>()

    fun userSockets(user: User) = wsClients.getOrPut(user) { mutableListOf() }

    suspend fun send(user: User, frame: Frame) = userSockets(user).forEach { it.send(frame) }

    suspend fun sendAll(frame: Frame) = wsClients.values.forEach { list -> list.forEach { it.send(frame) } }

    suspend fun sendOthers(user: User, frame: Frame) = wsClients.entries.filter { it.key != user }.forEach {
        send(it.key, frame)
    }

    fun addSocket(user: User, socket: SendChannel<Frame>) {
        println("+socket: $user")
        userSockets(user).add(socket)
    }

    fun removeSocket(user: User, socket: SendChannel<Frame>) {
        println("-socket: $user")
        userSockets(user).remove(socket)
    }

    fun Route.socket() {
        authenticate("basic") {
            webSocket("/ws") {
                try {
                    addSocket(call.user, outgoing)
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                println("server: $text")
                                sendAll(Frame.Text(text))
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("socket: ${e.localizedMessage}")
                } finally {
                    removeSocket(call.user, outgoing)
                }
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class User(
    var id: Long,
    var username: String,
    var password: String,
    var token: String = "",
) : Principal

class HttpStatusCodeException(val status: HttpStatusCode, override val message: String) : Exception()

val ApplicationCall.user get() = authentication.principal<User>() ?: throw HttpStatusCodeException(HttpStatusCode.NotFound, "The specified user could not be found")

fun Application.routing() {
    install(WebSockets)
//    install(DefaultHeaders)
    install(CallLogging)
    install(StatusPages) {
        exception<BadRequestException> {
            call.respond(HttpStatusCode.BadRequest, RequestData.Error(it.message.orEmpty()))
        }
    }
    install(Authentication) {
        basic("basic") {
            realm = "Ktor Server"
            validate {
                if (it.name == "username" && it.password == "password")  User(1, it.name, it.password, "") else null
            }
        }
    }
    routing {
        with(WsClients()) {
            socket()
        }
    }
}
