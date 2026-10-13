package io.github.sammeeeeeeee.higiene.data

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/** An OkHttp client that answers from [handler] instead of the network, recording each request. */
class FakeHttp(var handler: (Request) -> Response = { error("unexpected request ${it.url}") }) {
    val requests = mutableListOf<Request>()

    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        requests += chain.request()
        handler(chain.request())
    }).build()

    companion object {
        fun respond(req: Request, code: Int, body: ByteArray = ByteArray(0), type: String = "application/json", headers: Map<String, String> = emptyMap()): Response {
            val b = Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x").body(body.toResponseBody(type.toMediaType()))
            headers.forEach { (k, v) -> b.header(k, v) }
            return b.build()
        }

        fun json(req: Request, code: Int, json: String) = respond(req, code, json.toByteArray())

        fun bodyOf(req: Request): String = Buffer().also { req.body?.writeTo(it) }.readUtf8()
    }
}
