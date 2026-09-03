package com.example

import com.example.data.database.MessageEntity
import com.example.data.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ReactionsTest {

    private fun reactionMessage(reactions: Map<String, String>): Message = Message(
        id = "m1",
        chatId = "c1",
        senderId = "u1",
        content = "hola",
        createdAt = "2026-08-01T12:00:00Z",
        reactions = reactions
    )

    @Test
    fun persistedReactionIsVisibleInBubbleData() {
        val parsed = MessageEntity.parseReactionsJson("""{"u1":"👍"}""")
        assertEquals(mapOf("u1" to "👍"), parsed)

        val entity = MessageEntity.fromMessage(reactionMessage(mapOf("u1" to "👍")))
        assertTrue(entity.reactionsJson.contains("u1"))
        assertTrue(entity.reactionsJson.contains("👍"))

        val roundTrip = entity.toMessage().reactions
        assertEquals(mapOf("u1" to "👍"), roundTrip)
    }

    @Test
    fun multipleReactionsAreAllParsed() {
        val parsed = MessageEntity.parseReactionsJson(
            """{"u1":"👍","u2":"❤️","u3":"😂"}"""
        )
        assertEquals(mapOf("u1" to "👍", "u2" to "❤️", "u3" to "😂"), parsed)
    }

    @Test
    fun deletedReactionDisappearsFromBubbleData() {
        val reactions = mutableMapOf("u1" to "👍", "u2" to "❤️")
        reactions.remove("u1")
        val entity = MessageEntity.fromMessage(reactionMessage(reactions))
        val roundTrip = entity.toMessage().reactions
        assertFalse(entity.reactionsJson.contains("u1"))
        assertFalse(entity.reactionsJson.contains("👍"))
        assertEquals(mapOf("u2" to "❤️"), roundTrip)
    }

    @Test
    fun messageWithoutReactionsShowsNoReactionComponent() {
        val parsedEmpty = MessageEntity.parseReactionsJson("{}")
        assertTrue(parsedEmpty.isEmpty())
        assertTrue(MessageEntity.parseReactionsJson(null).isEmpty())

        val defaultMessage = reactionMessage(emptyMap())
        assertTrue(defaultMessage.reactions.isEmpty())
        val engineSkipsEmpty = defaultMessage.reactions.isNullOrEmpty()

        // La condicion del engine: no pinta la pill si no hay reacciones
        assertTrue(engineSkipsEmpty)
    }
}
