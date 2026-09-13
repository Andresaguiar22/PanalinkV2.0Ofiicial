package com.example.panatv

import android.content.Context
import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class IptvChannel(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "country") val country: String? = null,
    @Json(name = "languages") val languages: List<String>? = null,
    @Json(name = "categories") val categories: List<String>? = null,
    @Json(name = "logo") val logo: String? = null
)

data class IptvStream(
    @Json(name = "channel") val channel: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "http_referrer") val http_referrer: String? = null,
    @Json(name = "user_agent") val user_agent: String? = null
)

data class IptvBlocklist(
    @Json(name = "channel") val channel: String? = null,
    @Json(name = "reason") val reason: String? = null
)

data class IptvLogo(
    @Json(name = "channel") val channel: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "in_use") val in_use: Boolean? = null,
    @Json(name = "format") val format: String? = null,
    @Json(name = "width") val width: Int? = null
)

/** Preferencia de formatos de logo: raster nítido y estándar antes que SVG. */
private fun formatRank(format: String?): Int = when ((format ?: "").trim().uppercase()) {
    "PNG" -> 0
    "WEBP" -> 1
    "JPG", "JPEG" -> 2
    "SVG" -> 3
    else ->  4
}

interface PanaTVApiService {
    @GET("api/channels.json")
    suspend fun getChannels(): retrofit2.Response<List<IptvChannel>>

    @GET("api/streams.json")
    suspend fun getStreams(): retrofit2.Response<List<IptvStream>>

    @GET("api/blocklist.json")
    suspend fun getBlocklist(): retrofit2.Response<List<IptvBlocklist>>

    @GET("api/logos.json")
    suspend fun getLogos(): retrofit2.Response<List<IptvLogo>>
}

class PanaTVRepository(private val context: Context) {
    private val database = PanaTVDatabase.getDatabase(context)
    private val dao = database.channelDao()
    private val favDao = database.favoriteDao()
    private val prefs = context.getSharedPreferences("panatv_prefs", Context.MODE_PRIVATE)
    private val TAG = "PanaTVRepository"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val apiService: PanaTVApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://iptv-org.github.io/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PanaTVApiService::class.java)
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * channels.json NO publica el idioma por canal (el REST no lo incluye).
     * iptv-org sí publica listas M3U separadas por idioma:
     * https://iptv-org.github.io/iptv/languages/{codigo}.m3u
     * Cada #EXTINF trae tvg-id="<canal>@<calidad>". Normalizamos quitando el
     * sufijo @... para mapearlo al id base de channels.json y devolvemos
     * {"CineHispano.us" -> setOf("spa"), ...}. Si alguna lista falla, no
     * rompe la sincronización: se usa lo que se haya podido descargar.
     */
    private suspend fun fetchLanguageMap(): Map<String, Set<String>> =
        coroutineScope {
            val langs = listOf("spa", "por", "eng", "fra")
            val results = langs.map { lang ->
                async {
                    val url = "https://iptv-org.github.io/iptv/languages/$lang.m3u"
                    val ids = try {
                        val body = okHttp.newCall(
                            okhttp3.Request.Builder().url(url).build()
                        ).execute().use { resp ->
                            if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                        }
                        // Normaliza "@SD"/"@HD" -> id base, igual que channels.json
                        Regex("""tvg-id="([^"]+)"""")
                            .findAll(body)
                            .mapNotNull { m ->
                                m.groupValues.getOrNull(1)?.substringBefore('@')
                            }
                            .toList()
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo descargar $lang.m3u: ${e.message}")
                        emptyList()
                    }
                    ids
                }
            }.mapIndexed { i, def ->
                langs[i] to def.await()
            }
            results.flatMap { (lang, ids) -> ids.map { it to lang } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, v) -> v.toSet() }
        }

    fun getChannels(query: String = "", country: String = "", category: String = "", language: String = ""): Flow<List<PanaTVChannelEntity>> {
        return dao.searchChannels(query, country, category, language)
    }

    fun getCategories(): Flow<List<String>> = dao.getDistinctCategories()

    fun getDistinctLanguages(): Flow<List<String>> = dao.getDistinctLanguages()
    
    fun getFavorites(): Flow<List<PanaTVFavoriteEntity>> = favDao.getFavorites()
    
    suspend fun addFavorite(id: String) = favDao.addFavorite(PanaTVFavoriteEntity(id))
    
    suspend fun removeFavorite(id: String) = favDao.removeFavorite(id)

    suspend fun fetchChannelsIfNeeded(onDebug: (String) -> Unit = {}) {
        val lastSync = prefs.getLong("last_sync_time", 0)
        val currentTime = System.currentTimeMillis()
        val oneDayInMillis = 24 * 60 * 60 * 1000L
        val channelCount = dao.getChannelCount()

        if (channelCount == 0 || currentTime - lastSync > oneDayInMillis || lastSync == 0L) {
            Log.d(TAG, "Iniciando sincronización de canales...")
            onDebug("Sincronizando canales LATAM...")
            forceSyncChannels(onDebug)
        } else {
            Log.d(TAG, "Los canales ya están sincronizados (caché local).")
            onDebug("")
        }
    }

    suspend fun forceSyncChannels(onDebug: (String) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            try {
                onDebug("Descargando data de IPTV-ORG...")
                Log.d(TAG, "Descargando data de canales...")
                
                val blocklistResponse = apiService.getBlocklist()
                if (!blocklistResponse.isSuccessful) {
                    onDebug("blocklist.json: HTTP ${blocklistResponse.code()}")
                    return@withContext
                }
                onDebug("blocklist.json OK")
                val blocklistRes = blocklistResponse.body()?.mapNotNull { it.channel }?.toSet() ?: emptySet()
                
                val logosResponse = apiService.getLogos()
                val logosMap = if (logosResponse.isSuccessful) {
                    // iptv-org publica varias entradas por canal (PNG/SVG, tamaños). El
                    // associate() se quedaría con la ÚLTIMA, que suele ser SVG. Preferimos
                    // PNG/SVG manejable por Coil (ya hay SvgDecoder) y raster ligero.
                    // Un PNG
                    // suele verse mejor en la tarjeta que un SVG enorme sin cache.
                    logosResponse.body()?.let { logos ->
                        val byChannel = logos.groupBy { it.channel ?: "" }
                        byChannel.mapValues { (_, entries) ->
                            entries.sortedWith(
                                compareBy(
                                    // primero los que están en uso
                                    { if (it.in_use == true) 0 else 1 },
                                    // después, un orden de formato preferido: PNG > WEBP > JPG > SVG
                                    { formatRank(it.format) },
                                    // y el más grande (más resolución) dentro del mismo formato
                                    { -(it.width ?: 0) }
                                )
                            ).firstOrNull()?.url ?: ""
                        }
                    } ?: emptyMap()
                } else {
                    onDebug("logos.json: HTTP ${logosResponse.code()}")
                    emptyMap()
                }
                onDebug("logos.json OK (${logosMap.size} logos)")

                val channelsResponse = apiService.getChannels()
                if (!channelsResponse.isSuccessful) {
                    onDebug("channels.json: HTTP ${channelsResponse.code()}")
                    return@withContext
                }
                onDebug("channels.json OK")
                val channelsRes = channelsResponse.body() ?: emptyList()

                val streamsResponse = apiService.getStreams()
                if (!streamsResponse.isSuccessful) {
                    onDebug("streams.json: HTTP ${streamsResponse.code()}")
                    return@withContext
                }
                onDebug("streams.json OK")
                val streamsRes = streamsResponse.body() ?: emptyList()

                onDebug("Channels descargados: ${channelsRes.size}")

                // Los REST channels.json/streams.json NO incluyen el idioma por canal:
                // se descargan las listas M3U por idioma y se mapea el id base -> idiomas.
                val languageMap = fetchLanguageMap()
                onDebug("Idiomas mapeados: ${languageMap.size} canales")

                // 2. Unir channels con streams (Cruce inicial)
                val allChannelsById = channelsRes.filter { it.id != null }.associateBy { it.id!! }
                val initialJoined = streamsRes.mapNotNull { stream ->
                    val channelId = stream.channel
                    val streamUrl = stream.url
                    if (channelId != null && streamUrl != null) {
                        val ch = allChannelsById[channelId]
                        if (ch != null) {
                            // Guardamos el par para filtrar progresivamente
                            Pair(ch, stream)
                        } else null
                    } else null
                }
                onDebug("Con stream asociado: ${initialJoined.size}")
                if (initialJoined.isEmpty()) {
                    onDebug("STOP: 0 canales después del Cruce.")
                    return@withContext
                }

                // 3. Eliminar duplicados (por URL de stream)
                val distinctJoined = initialJoined.distinctBy { it.second.url }
                onDebug("Sin duplicados: ${distinctJoined.size}")

                // 4. Filtro LATAM (Países específicos)
                val latamCountries = setOf(
                    "AR", "BO", "BR", "CL", "CO", "CR", "CU", "DO", "EC", "SV", 
                    "GT", "HN", "MX", "NI", "PA", "PY", "PE", "PR", "PT", "UY", "VE",
                    "ES", "US", "GQ", "PH"
                )
                val latamList = distinctJoined.filter { pair ->
                    pair.first.country != null && latamCountries.contains(pair.first.country)
                }
                onDebug("Después del filtro LATAM: ${latamList.size}")
                if (latamList.isEmpty()) {
                    onDebug("STOP: 0 después del filtro LATAM.")
                    return@withContext
                }

                // 5. No se filtra por idioma al poblar la BD: todos los idiomas
                //    entran y el usuario filtra desde la UI (Español/Portugués/Inglés).
                val spaList = latamList
                onDebug("Idiomas: sin filtro (${spaList.size})")

                // 6. Eliminar Blocklist
                val finalFiltered = spaList.filter { pair ->
                    !blocklistRes.contains(pair.first.id)
                }
                onDebug("Después de Blocklist: ${finalFiltered.size}")

                // 6b. Excluir contenido adulto
                val familySafe = finalFiltered.filter { pair ->
                    pair.first.categories?.contains("xxx") != true
                }

                // 6c. Priorizar categorías de entretenimiento para que películas/series
                // siempre entren dentro del límite del catálogo.
                val categoryPriority = mapOf(
                    "movies" to 0, "entertainment" to 1, "series" to 2, "comedy" to 3,
                    "family" to 4, "kids" to 5, "animation" to 6, "documentary" to 7,
                    "music" to 8, "sports" to 9, "news" to 10
                )
                // Orden: los canales que hablan español primero (los de habla hispana
                // entran dentro del límite antes) y luego prioridad de categoría.
                // Así el catálogo local no se llena con canales de idiomas minoritarios.
                fun langsOf(pair: Pair<IptvChannel, IptvStream>): Set<String> =
                    languageMap[pair.first.id] ?: (pair.first.languages?.toSet() ?: emptySet())
                fun speaksSpanish(pair: Pair<IptvChannel, IptvStream>): Boolean =
                    "spa" in langsOf(pair)
                val prioritized = familySafe.sortedWith(
                    compareBy<Pair<IptvChannel, IptvStream>>(
                        { if (speaksSpanish(it)) 0 else 1 },
                        { categoryPriority[it.first.categories?.firstOrNull()] ?: 20 }
                    )
                )

                // Convertir a entidades finales y limitar a 800
                var entitiesSoFar = 0
                val entities = prioritized.map { (ch, stream) ->
                    val rawLogo = ch.logo ?: ""
                    // Preference: 1. logos.json mapping, 2. channel.logo field if URL, 3. constructed URL, 4. empty
                    val logoFromMap = if (!ch.id.isNullOrBlank()) logosMap[ch.id] else null
                    
                    val finalLogo = when {
                        !logoFromMap.isNullOrBlank() -> logoFromMap
                        rawLogo.startsWith("http") -> rawLogo
                        rawLogo.isNotBlank() -> "https://iptv-org.github.io/images/channels/$rawLogo"
                        else -> ""
                    }
                    
                    if (finalLogo.isNotEmpty()) {
                        val logMsg = "Canal: ${ch.name} | Logo Map: $logoFromMap | Logo Final: $finalLogo"
                        Log.d(TAG, logMsg)
                        if (entitiesSoFar < 5) {
                            onDebug(logMsg)
                        }
                    }
                    entitiesSoFar++
 
                    PanaTVChannelEntity(
                        id = stream.url!!,
                        name = ch.name ?: "Sin nombre",
                        streamUrl = stream.url,
                        logoUrl = finalLogo,
                        country = ch.country ?: "",
                        category = ch.categories?.firstOrNull() ?: "",
                        languages = (languageMap[ch.id] ?: (ch.languages?.toSet() ?: emptySet())).sorted().joinToString(","),
                        userAgent = stream.user_agent,
                        referrer = stream.http_referrer
                    )
                }.take(6000)

                onDebug("Total final para Room: ${entities.size}")

                if (entities.isNotEmpty()) {
                    dao.replaceChannels(entities)
                    prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()
                    Log.d(TAG, "Sincronización exitosa. ${entities.size} canales guardados en la BD local.")
                    onDebug("Sincronización exitosa: ${entities.size} canales.")
                } else {
                    Log.w(TAG, "No se encontraron canales válidos para guardar.")
                    onDebug("Error: No se encontraron canales válidos.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error de red o sincronización al obtener los canales: ${e.message}", e)
                onDebug("Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
