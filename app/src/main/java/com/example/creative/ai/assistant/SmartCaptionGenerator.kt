package com.example.creative.ai.assistant

import com.example.ai.OpenRouterService as AiService

enum class CaptionTone(val displayName: String) {
    INSPIRATIONAL("Inspiracional"),
    FUNNY("Divertido / Humo"),
    PROFESSIONAL("Profesional"),
    ROMANTIC("Romántico"),
    VIRAL("Viral / Enganche"),
    STORYTELLING("Storytelling")
}

data class GeneratedCaption(
    val text: String,
    val tone: CaptionTone,
    val suggestedHashtags: List<String>
)

/**
 * P6.6.5 - Smart Caption Generator
 * Local AI caption generator providing contextual hooks, storytelling, and hashtag recommendations.
 */
object SmartCaptionGenerator {

    fun generateCaptions(topic: String, categoryName: String): List<GeneratedCaption> {
        val baseTopic = if (topic.isBlank()) "momentos increíbles" else topic
        // Local fallback
        val local = listOf(
            GeneratedCaption("Crea la vida que no puedes esperar para vivir ✨ $baseTopic.", CaptionTone.INSPIRATIONAL, listOf("PanaLink", "Inspiracion", "EstiloDeVida", "Motivacion")),
            GeneratedCaption("Guarda este post antes de que se pierda en el feed 🚀 $baseTopic.", CaptionTone.VIRAL, listOf("Viral", "Tendencia", "PanaLinkCreative", "ParaTi")),
            GeneratedCaption("Elevando los estándares día a día. $baseTopic 💼", CaptionTone.PROFESSIONAL, listOf("Pro", "Creador", "Business", "Innovacion")),
            GeneratedCaption("Un pequeño resumen de lo que ha sido $baseTopic 📖", CaptionTone.STORYTELLING, listOf("Diario", "Experiencias", "Recuerdos"))
        )
        return local
    }

    /** AI-powered caption via OpenRouter (GPT-3.5). Returns remote result if OPENROUTER_API_KEY is configured, else local fallback. */
    suspend fun generateCaptionsRemote(topic: String, categoryName: String, locale: String = "es"): List<GeneratedCaption> {
        val hasKey = !System.getenv("OPENROUTER_API_KEY").isNullOrBlank()
        if (!hasKey) return generateCaptions(topic, categoryName)
        return try {
            val prompt = "Genera 3 captions cortos en español para $categoryName sobre '$topic'. Devuelve JSON con campos text, tone, suggestedHashtags (3-5)."
            val raw = AiService.generateChat(prompt, model = "openai/gpt-3.5-turbo")
            val parsed = org.json.JSONArray(raw)
            (0 until parsed.length()).map { i ->
                val o = parsed.getJSONObject(i)
                GeneratedCaption(
                    text = o.optString("text"),
                    tone = CaptionTone.VIRAL,
                    suggestedHashtags = o.optJSONArray("suggestedHashtags")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()
                )
            }
        } catch (e: Exception) { generateCaptions(topic, categoryName) }
    }
}
