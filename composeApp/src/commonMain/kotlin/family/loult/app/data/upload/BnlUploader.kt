package family.loult.app.data.upload

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val UPLOAD_URL = "https://bnl.loult.family/media/upload/multipart"
private const val MEDIA_BASE = "https://bnl.loult.family"

/**
 * Mirrors the loult.family web client's `uploadBlob`: POST a multipart/form-data
 * to `bnl.loult.family/media/upload/multipart` with `file` + `cookie` fields,
 * read the JSON response containing `file_path`, return the absolute URL.
 *
 * Returns null on network/HTTP failure so the caller can surface a UI error.
 */
class BnlUploader(private val httpClient: HttpClient) {

    suspend fun upload(bytes: ByteArray, filename: String, cookie: String?): String? = runCatching {
        val response: HttpResponse = httpClient.post(UPLOAD_URL) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = bytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                            },
                        )
                        if (!cookie.isNullOrBlank()) append("cookie", cookie)
                    }
                )
            )
        }
        if (!response.status.isSuccess()) return null
        val payload: UploadResponse = response.body()
        "$MEDIA_BASE${payload.filePath}"
    }.getOrNull()
}

@Serializable
private data class UploadResponse(
    @SerialName("file_path") val filePath: String,
)
