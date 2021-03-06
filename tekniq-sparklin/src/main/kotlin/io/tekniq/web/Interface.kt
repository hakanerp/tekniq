package io.tekniq.web

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.tekniq.validation.*
import spark.*
import spark.utils.SparkUtils
import java.util.*
import kotlin.reflect.KClass

val sparklinMapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
        .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
internal val BODY_CACHE = "__${UUID.randomUUID()}"

open class NotAuthorizedException(rejections: Collection<Rejection>, val all: Boolean = true) : ValidationException(rejections)

interface AuthorizationManager {
    /**
     * Must return an empty list if no access is to be granted. Best practice says to return 'AUTHENTICATED' if the user
     * is authenticated and to return 'ANONYMOUS' if the user is not authenticated in addition to normal authorizations
     * that the user may possess.
     */
    fun getAuthz(request: Request): Collection<String>
}

interface SparklinRoute {
    fun before(path: String = SparkUtils.ALL_PATHS, acceptType: String = "*/*", filter: SparklinValidation.(Request, Response) -> Unit)
    fun after(path: String = SparkUtils.ALL_PATHS, acceptType: String = "*/*", filter: SparklinValidation.(Request, Response) -> Unit)
    fun get(path: String, acceptType: String = "*/*", transformer: ResponseTransformer? = null, route: SparklinValidation.(Request, Response) -> Any?)
    fun post(path: String, acceptType: String = "*/*", transformer: ResponseTransformer? = null, route: SparklinValidation.(Request, Response) -> Any?)
    fun put(path: String, acceptType: String = "*/*", transformer: ResponseTransformer? = null, route: SparklinValidation.(Request, Response) -> Any?)
    fun patch(path: String, acceptType: String = "*/*", transformer: ResponseTransformer? = null, route: SparklinValidation.(Request, Response) -> Any?)
    fun delete(path: String, acceptType: String = "*/*", transformer: ResponseTransformer? = null, route: SparklinValidation.(Request, Response) -> Any?)
    fun head(path: String, acceptType: String = "*/*", transformer: ResponseTransformer? = null, route: SparklinValidation.(Request, Response) -> Any?)
    fun trace(path: String, acceptType: String = "*/*", transformer: ResponseTransformer? = null, route: SparklinValidation.(Request, Response) -> Any?)
    fun connect(path: String, acceptType: String = "*/*", transformer: ResponseTransformer? = null, route: SparklinValidation.(Request, Response) -> Any?)
    fun options(path: String, acceptType: String = "*/*", transformer: ResponseTransformer? = null, route: SparklinValidation.(Request, Response) -> Any?)
    fun webSocket(path: String, handler: KClass<*>)

    fun <T : Exception> exception(exceptionClass: KClass<T>, handler: (T, Request, Response) -> Pair<Int, Any>)
}

fun Request.rawBody() = attribute<String?>(BODY_CACHE)
fun <T : Any> Request.jsonAs(type: KClass<T>): T {
    val body = this.attribute<String?>(BODY_CACHE)
    if (body.isNullOrBlank()) {
        throw IllegalStateException("No data available to transform")
    }
    return sparklinMapper.readValue(body, type.java)
}

inline fun <reified T : Any> Request.jsonAs(): T = jsonAs(T::class)

data class SparklinConfig(
        val ip: String = "0.0.0.0", val port: Int = 4567,
        val authorizationManager: AuthorizationManager? = null,
        val responseTransformer: ResponseTransformer = JsonResponseTransformer,
        val idleTimeoutMillis: Int = -1, val webSocketTimeout: Int? = null,
        val maxThreads: Int = 10, val minThreads: Int = -1,
        val keystore: SparklinKeystore? = null,
        val staticFiles: SparklinStaticFiles? = null)

data class SparklinKeystore(val keystoreFile: String, val keystorePassword: String,
                            val truststoreFile: String, val truststorePassword: String)

data class SparklinStaticFiles(val fileLocation: String? = null, val externalFileLocation: String? = null,
                               val headers: Map<String, String> = emptyMap(), val expireInSeconds: Int = 1)


abstract class SparklinValidation(src: Any?, path: String = "") : Validation(src, path) {
    abstract fun authz(vararg authz: String, all: Boolean = true): SparklinValidation
}

private object JsonResponseTransformer : ResponseTransformer {
    override fun render(model: Any?): String = when (model) {
        is Unit -> ""
        else -> sparklinMapper.writeValueAsString(model)
    }
}
