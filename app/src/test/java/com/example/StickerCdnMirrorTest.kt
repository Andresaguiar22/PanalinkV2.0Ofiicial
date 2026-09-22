package com.example

import com.example.util.StickerCdnMirror
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StickerCdnMirror: las URLs de proveedores externos (static.klipy.com,
 * media.giphy.com) deben espejarse a nuestro CDN, mientras que la
 * infraestructura propia (CDN activo, Supabase Storage, vCDN y B2) nunca
 * se toca. Este test fija ese contrato de [isCdnOwned].
 *
 * [isCdnOwned] es puro (sin Context y sin Android), así que se puede
 * testear en JVM sin mocking.» Nota: [CdnManager.isCdnRelated] no requiere
 * inicialización para rutas de media (concuerde con los tests existentes de
 * CdnManager en la suite).
 */
class StickerCdnMirrorTest {

    @Test
    fun urlsDeKlipyYGiphySonExternas() {
        assertFalse(StickerCdnMirror.isCdnOwned("https://static.klipy.com/ii/abc/def/sticker.gif"))
        assertFalse(StickerCdnMirror.isCdnOwned("https://media.giphy.com/media/abc/giphy.gif"))
        assertFalse(StickerCdnMirror.isCdnOwned("https://media2.giphy.com/media/v1.token/xyz/giphy.gif"))
    }

    @Test
    fun urlsDeNuestraInfraestructuraNoSeEspejan() {
        // Supabase (el host viene de SupabaseClient.supabaseUrl)
        assertTrue(StickerCdnMirror.isCdnOwned("https://abcdefgh.supabase.co/storage/v1/object/public/images/a.png"))

        // vCDN
        assertTrue(StickerCdnMirror.isCdnOwned("https://cdn.vcdn.me/cdn/p1/xyz/stream.m3u8"))
        assertTrue(StickerCdnMirror.isCdnOwned("https://embed.vcdn.me/api/bff/player-config/abc"))
        assertTrue(StickerCdnMirror.isCdnOwned("vcdn://video/abc"))

        // B2 firmado
        assertTrue(StickerCdnMirror.isCdnOwned("https://panalink.s3.us-east-005.backblazeb2.com/video/abc.mp4?X-Amz-Signature=token"))
    }

    @Test
    fun rutasLocalesNoSeEspejan() {
        assertTrue(StickerCdnMirror.isCdnOwned("/data/user/0/com.example/cache/sticker.webp"))
        assertTrue(StickerCdnMirror.isCdnOwned("file:///data/local/tmp/sticker.webp"))
        assertTrue(StickerCdnMirror.isCdnOwned("content://media/external/images/1"))
    }
}