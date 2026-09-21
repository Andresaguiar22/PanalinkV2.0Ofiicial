package com.example

import com.example.data.model.KlipyItem
import com.example.data.model.KlipyResponse
import com.example.data.repository.StickerRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Klipy: el payload nativo (api/v1/...) usa "file" con formatos hd/md/sm/xs
 * y "id" numérico. Este test fija ese contrato para que un cambio de forma en
 * el JSON no rompa el parseo en silencio.
 */
class KlipyGifMappingTest {

    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val payload = """
    {
      "result": true,
      "data": {
        "data": [
          {
            "id": 2484942301552561,
            "slug": "cat-one",
            "title": "Cat One",
            "type": "gif",
            "file": {
              "hd": {"gif": {"url": "https://static.klipy.com/hd1.gif", "width": 498, "height": 487, "size": 5525120}},
              "md": {"gif": {"url": "https://static.klipy.com/md1.gif", "width": 640, "height": 626, "size": 1024000}},
              "sm": {"gif": {"url": "https://static.klipy.com/sm1.gif", "width": 220, "height": 215, "size": 819200},
                     "jpg": {"url": "https://static.klipy.com/sm1.jpg", "width": 220, "height": 215, "size": 9000}},
              "xs": {"gif": {"url": "https://static.klipy.com/xs1.gif", "width": 92, "height": 90, "size": 186000}}
            }
          },
          {
            "id": 281,
            "slug": "big-two",
            "title": "Big Two",
            "type": "gif",
            "file": {
              "hd": {"gif": {"url": "https://static.klipy.com/hd2.gif", "width": 281, "height": 498, "size": 10100000}},
              "md": {"gif": {"url": "https://static.klipy.com/md2.gif", "width": 211, "height": 374, "size": 3369984}},
              "sm": {"gif": {"url": "https://static.klipy.com/sm2.gif", "width": 165, "height": 294, "size": 886784}}
            }
          },
          {
            "id": 333,
            "slug": "sponsored",
            "title": "Sponsored",
            "type": "ad",
            "file": {"md": {"gif": {"url": "https://static.klipy.com/ad.gif", "width": 100, "height": 100, "size": 1000}}}
          }
        ],
        "current_page": 1,
        "per_page": 24,
        "has_next": true
      }
    }
    """.trimIndent()

    private fun parse(): KlipyResponse {
        val adapter = moshi.adapter(KlipyResponse::class.java)
        return adapter.fromJson(payload)!!
    }

    @Test
    fun parseaPayloadNativoDeKlipy() {
        val response = parse()
        assertTrue(response.result)
        val data = response.data
        assertNotNull(data)
        assertEquals(3, data!!.items.size)
        assertEquals(1, data.currentPage)
        assertEquals(24, data.perPage)
        assertEquals(true, data.hasNext)
        assertEquals(2484942301552561L, data.items[0].id)
        assertEquals("gif", data.items[0].type)
    }

    @Test
    fun mapeaUsandoMdYPreviewAnimadoSm() {
        val item = parse().data!!.items[0]
        val mapped = with(StickerRepository) { item.toStickerResult() }
        assertNotNull(mapped)
        assertEquals("2484942301552561", mapped!!.id)
        assertEquals("Cat One", mapped.title)
        assertEquals("https://static.klipy.com/md1.gif", mapped.url)
        assertEquals("https://static.klipy.com/sm1.gif", mapped.preview)
        assertEquals(640, mapped.width)
        assertEquals(626, mapped.height)
    }

    @Test
    fun descartaPiezasMarcadasComoPublicidad() {
        val adItem = parse().data!!.items[2]
        assertEquals("ad", adItem.type)
        assertNull(with(StickerRepository) { adItem.toStickerResult() })
    }

    @Test
    fun caeASmCuandoElMdSuperaElLimiteDePeso() {
        val heavy = parse().data!!.items[1]
        val mapped = with(StickerRepository) { heavy.toStickerResult() }
        assertNotNull(mapped)
        assertEquals("https://static.klipy.com/sm2.gif", mapped!!.url)
        assertEquals(165, mapped.width)
    }

    @Test
    fun ignorarItemSinUrlDevuelveNull() {
        val empty = KlipyItem(id = 1L, type = "gif")
        assertNull(with(StickerRepository) { empty.toStickerResult() })
    }
}
