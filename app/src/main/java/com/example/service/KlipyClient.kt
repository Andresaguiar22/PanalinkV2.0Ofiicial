package com.example.service

import com.example.data.supabase.SupabaseClient
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object KlipyClient {
    private const val BASE_URL = "https://api.klipy.com/"

    // Klipy está detrás de Cloudflare: enviar un User-Agent propio (no el
    // default de OkHttp) y Accept JSON evita rechazos silenciosos.
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Accept", "application/json")
                        .header("User-Agent", "PanaLink/Android")
                        .build()
                )
            }
            .build()
    }

    val apiService: KlipyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(SupabaseClient.moshi))
            .build()
            .create(KlipyApiService::class.java)
    }
}
