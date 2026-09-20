package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.CdnManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URI
import java.security.MessageDigest

/**
 * Cobertura del almacen de media persistente usado por el chat.
 *
 * Dos invariantes que un cambio inocente puede romper:
 *  1. La clave de fichero debe seguir siendo SHA-256 en hexadecimal: si cambia el
 *     formato, todas las caches existentes en los dispositivos se invalidan.
 *  2. `adoptLocalFile` debe dejar la miniatura disponible via `existingUri` con la
 *     clave del mediaUrl remoto, que es como la busca el chat.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OfflineMediaCacheAdoptionTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun referenceKey(url: String): String {
        val identity = if (CdnManager.isCdnRelated(url)) {
            val path = try { URI(url).path.orEmpty() } catch (_: Exception) { url }
            "cdn:${path.substringAfterLast('/')}"
        } else {
            url
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `construye el nombre del fichero con sha256 hexadecimal`() {
        // URL de B2: isCdnRelated siempre es false para este host, asi que la clave es
        // el SHA-256 directo de la URL.
        val url = "https://s3.us-west-002.backblazeb2.com/file/panalink-media/clip-final.jpg"
        val name = OfflineMediaCache.fileFor(context, url, "image/jpeg").name
        assertEquals(referenceKey(url) + ".jpg", name)
    }

    @Test
    fun `deriva la extension del mime cuando la url no la trae`() {
        val url = "https://s3.us-west-002.backblazeb2.com/file/panalink-media/sin-extension"
        val name = OfflineMediaCache.fileFor(context, url, "image/png").name
        assertTrue("debe terminar en .png", name.endsWith(".png"))
        assertEquals(referenceKey(url) + ".png", name)
    }

    @Test
    fun `adoptLocalFile deja la miniatura disponible por la clave del mediaUrl`() {
        val mediaUrl = "https://cdn.example.com/media/foto-adoptada-${System.nanoTime()}.jpg"
        val source = File(context.cacheDir, "thumb-origen.jpg").apply {
            writeBytes(ByteArray(2048) { 0x7A })
        }

        assertNull(
            "no debe existir antes de adoptarla",
            OfflineMediaCache.existingUri(context, mediaUrl, "image/jpeg")
        )

        val adopted = OfflineMediaCache.adoptLocalFile(context, mediaUrl, "image/jpeg", source)

        assertNotNull("adoptLocalFile debe devolver una URI", adopted)
        assertTrue("la URI debe apuntar al archivo persistente", adopted!!.startsWith("file:"))
        assertEquals(
            "existingUri debe encontrar la miniatura adoptada",
            adopted,
            OfflineMediaCache.existingUri(context, mediaUrl, "image/jpeg")
        )
        assertTrue("el archivo adoptado debe existir", File(URI(adopted).path).isFile)
    }

    @Test
    fun `adoptLocalFile sobrevive al borrado del archivo de origen`() {
        val mediaUrl = "https://cdn.example.com/media/video-tapado-${System.nanoTime()}.jpg"
        val source = File(context.cacheDir, "thumb-temp.jpg").apply {
            writeBytes(ByteArray(1024) { 0x11 })
        }

        val adopted = OfflineMediaCache.adoptLocalFile(context, mediaUrl, "image/jpeg", source)
        assertNotNull(adopted)
        source.delete()

        assertEquals(
            "la miniatura debe seguir accesible tras borrar el origen",
            adopted,
            OfflineMediaCache.existingUri(context, mediaUrl, "image/jpeg")
        )
    }

    @Test
    fun `adoptLocalFile rechaza origenes invalidos sin lanzar`() {
        val mediaUrl = "https://cdn.example.com/media/invalido.jpg"
        val directorio = File(context.cacheDir, "es-un-directorio").apply { mkdirs() }
        assertNull(OfflineMediaCache.adoptLocalFile(context, mediaUrl, "image/jpeg", null))
        assertNull(OfflineMediaCache.adoptLocalFile(context, null, "image/jpeg", File(context.cacheDir, "x.jpg")))
        assertNull(
            OfflineMediaCache.adoptLocalFile(context, mediaUrl, "image/jpeg", File(context.cacheDir, "no-existe.jpg"))
        )
        assertNull("un directorio no es un archivo valido", OfflineMediaCache.adoptLocalFile(context, mediaUrl, "image/jpeg", directorio))
    }

    @Test
    fun `existingUri ignora archivos vacios`() {
        val mediaUrl = "https://cdn.example.com/media/vacio-${System.nanoTime()}.jpg"
        val empty = File(context.cacheDir, "vacio.jpg").apply { writeBytes(ByteArray(0)) }
        assertNull(OfflineMediaCache.adoptLocalFile(context, mediaUrl, "image/jpeg", empty))
        assertNull(OfflineMediaCache.existingUri(context, mediaUrl, "image/jpeg"))
    }
}
