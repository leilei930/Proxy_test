import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

fun main() {
    // Proxy configuration
    val proxyIP = "172.18.77.13"
    val proxyPort = 9080

    // Target URLs for primary and secondary requests
    val primaryUrl = "https://thomas.com/mm7/noti"
    val secondaryUrl = "https://zserver1.zensis.com/mm7/noti"

    // Dummy data to send in the POST request body
    val requestBodyData = "<xml>Sample Request Body</xml>"
    val mediaType = "text/xml".toMediaTypeOrNull()
    val requestBody = requestBodyData.toRequestBody(mediaType)

    // OkHttpClient with proxy and TLS 1.2 configuration
    val client = OkHttpClient.Builder()
        .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyIP, proxyPort)))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .connectionSpecs(listOf(ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2)
            .build()))
        .protocols(listOf(Protocol.HTTP_1_1))
        .retryOnConnectionFailure(false)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("Connection", "close")
                .header("Proxy-Connection", "close")
                .method(original.method, original.body)
            chain.proceed(requestBuilder.build())
        }
        .build()

    // Build the primary request
    val primaryRequest = Request.Builder()
        .url(primaryUrl)
        .post(requestBody)
        .build()

    // Build the secondary request
    val secondaryRequest = Request.Builder()
        .url(secondaryUrl)
        .post(requestBody)
        .build()

    val maxRetries = 3  // Number of retries
    var attempt = 0
    var success = false

    // Attempt to send requests with retries
    while (attempt < maxRetries && !success) {
        attempt++
        println("Attempt $attempt to send request to primary URL")

        try {
            client.newCall(primaryRequest).execute().use { response ->
                if (response.isSuccessful) {
                    println("Primary request succeeded with response: ${response.body?.string()}")
                    success = true
                } else {
                    println("Primary request failed with code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            println("Primary request error: ${e.message}")
        }

        if (!success) {
            println("Attempting request to secondary URL as primary failed")

            try {
                client.newCall(secondaryRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        println("Secondary request succeeded with response: ${response.body?.string()}")
                        success = true
                    } else {
                        println("Secondary request failed with code: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                println("Secondary request error: ${e.message}")
            }
        }
    }

    if (!success) {
        println("Both primary and secondary requests failed after $maxRetries attempts.")
    }
}
