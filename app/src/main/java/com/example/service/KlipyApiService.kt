package com.example.service

import com.example.data.model.KlipyCategoriesResponse
import com.example.data.model.KlipyResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API nativa de Klipy (api.klipy.com). La API key viaja en el path.
 *
 * Solo se exponen endpoints de contenido. El endpoint de anuncios de Klipy
 * (Ads) no se integra a proposito: la app no muestra publicidad.
 */
interface KlipyApiService {

    @GET("api/v1/{apiKey}/gifs/search")
    suspend fun searchGifs(
        @Path("apiKey") apiKey: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 24,
        @Query("page") page: Int = 1,
        @Query("locale") locale: String = DEFAULT_LOCALE,
        @Query("content_filter") contentFilter: String = DEFAULT_CONTENT_FILTER
    ): Response<KlipyResponse>

    @GET("api/v1/{apiKey}/gifs/categories")
    suspend fun gifCategories(
        @Path("apiKey") apiKey: String,
        @Query("limit") limit: Int = 30,
        @Query("locale") locale: String = DEFAULT_LOCALE
    ): Response<KlipyCategoriesResponse>

    @GET("api/v1/{apiKey}/gifs/trending")
    suspend fun trendingGifs(
        @Path("apiKey") apiKey: String,
        @Query("limit") limit: Int = 24,
        @Query("page") page: Int = 1,
        @Query("locale") locale: String = DEFAULT_LOCALE,
        @Query("content_filter") contentFilter: String = DEFAULT_CONTENT_FILTER
    ): Response<KlipyResponse>

    @GET("api/v1/{apiKey}/stickers/search")
    suspend fun searchStickers(
        @Path("apiKey") apiKey: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 24,
        @Query("page") page: Int = 1,
        @Query("locale") locale: String = DEFAULT_LOCALE,
        @Query("content_filter") contentFilter: String = DEFAULT_CONTENT_FILTER
    ): Response<KlipyResponse>

    @GET("api/v1/{apiKey}/stickers/trending")
    suspend fun trendingStickers(
        @Path("apiKey") apiKey: String,
        @Query("limit") limit: Int = 24,
        @Query("page") page: Int = 1,
        @Query("locale") locale: String = DEFAULT_LOCALE,
        @Query("content_filter") contentFilter: String = DEFAULT_CONTENT_FILTER
    ): Response<KlipyResponse>

    companion object {
        const val DEFAULT_LOCALE = "es_ES"
        const val DEFAULT_CONTENT_FILTER = "high"
    }
}
