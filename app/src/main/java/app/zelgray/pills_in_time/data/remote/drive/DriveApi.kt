package app.zelgray.pills_in_time.data.remote.drive

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Minimal Drive v3 REST surface for the appDataFolder (spec 4.8) — only the
 * handful of endpoints this app needs (find/create/update/download one JSON
 * file), called directly via Retrofit/OkHttp rather than pulling in the much
 * larger generated google-api-services-drive client.
 */
interface DriveApi {

    @GET("drive/v3/files")
    suspend fun listAppDataFiles(
        @Header("Authorization") authHeader: String,
        @Query("q") query: String,
        @Query("spaces") spaces: String = "appDataFolder",
        @Query("fields") fields: String = "files(id,name,modifiedTime)",
    ): DriveFileListResponse

    @Multipart
    @POST("upload/drive/v3/files?uploadType=multipart")
    suspend fun uploadNewFile(
        @Header("Authorization") authHeader: String,
        @Part metadata: MultipartBody.Part,
        @Part file: MultipartBody.Part,
    ): DriveFileMetadata

    @Multipart
    @PATCH("upload/drive/v3/files/{fileId}?uploadType=multipart")
    suspend fun updateFile(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Part metadata: MultipartBody.Part,
        @Part file: MultipartBody.Part,
    ): DriveFileMetadata

    @Streaming
    @GET("drive/v3/files/{fileId}?alt=media")
    suspend fun downloadFile(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
    ): ResponseBody
}

@Serializable
data class DriveFileListResponse(val files: List<DriveFileMetadata> = emptyList())

@Serializable
data class DriveFileMetadata(
    val id: String,
    val name: String? = null,
    val modifiedTime: String? = null,
)
