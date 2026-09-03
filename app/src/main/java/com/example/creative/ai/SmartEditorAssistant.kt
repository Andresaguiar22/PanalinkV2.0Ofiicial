package com.example.creative.ai

import com.example.ai.OpenRouterService as AiService
import com.example.creative.core.CreativeLayer
import com.example.creative.core.CreativeProject

data class EditorSuggestion(
    val id: String,
    val title: String,
    val description: String,
    val actionType: SuggestionActionType,
    val payload: String = ""
)

enum class SuggestionActionType {
    APPLY_FILTER,
    CENTER_TEXT,
    TRIM_CLIP,
    ADD_MUSIC,
    GENERATE_SUBTITLES,
    AUTO_BEAUTY
}

object SmartEditorAssistant {

    fun analyzeProject(project: CreativeProject): List<EditorSuggestion> {
        val local = analyzeProjectLocal(project)
        return local
    }

    /** AI-powered suggestions via OpenRouter (Claude 3 Haiku). Returns remote if OPENROUTER_API_KEY is set, else local. */
    suspend fun analyzeProjectRemote(project: CreativeProject): List<EditorSuggestion> {
        val hasKey = !System.getenv("OPENROUTER_API_KEY").isNullOrBlank()
        if (!hasKey) return analyzeProjectLocal(project)
        return try {
            val summary = "Proyecto ${project.type} con ${project.layers.size} capas."
            val remote = AiService.generateChat(
                "Como asistente de edición, recomienda 2 acciones JSON (id,title,description,actionType,payload) para: $summary",
                model = "anthropic/claude-3-haiku"
            )
            val arr = org.json.JSONArray(remote)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                EditorSuggestion(
                    id = o.optString("id", "ai_$i"),
                    title = o.optString("title", "IA"),
                    description = o.optString("description", ""),
                    actionType = SuggestionActionType.CENTER_TEXT,
                    payload = o.optString("payload", "")
                )
            }
        } catch (e: Exception) { analyzeProjectLocal(project) }
    }

    private fun analyzeProjectLocal(project: CreativeProject): List<EditorSuggestion> {
        val suggestions = mutableListOf<EditorSuggestion>()

        // 1. Filter recommendation
        val hasFilter = project.layers.any { it is CreativeLayer.Filter }
        if (!hasFilter) {
            suggestions.add(
                EditorSuggestion(
                    id = "sug_filter",
                    title = "Mejorar Iluminación",
                    description = "Aplica el filtro 'Cinematic' para balancear la calidez del video.",
                    actionType = SuggestionActionType.APPLY_FILTER,
                    payload = "cinematic"
                )
            )
        }

        // 2. Text positioning warning
        val textLayers = project.layers.filterIsInstance<CreativeLayer.Text>()
        textLayers.forEach { textLayer ->
            if (textLayer.yFraction > 0.85f || textLayer.yFraction < 0.10f) {
                suggestions.add(
                    EditorSuggestion(
                        id = "sug_text_bounds_${textLayer.id}",
                        title = "Centrar Texto",
                        description = "El texto '${textLayer.text.take(15)}...' está muy cerca del borde. Recomendamos centrarlo.",
                        actionType = SuggestionActionType.CENTER_TEXT,
                        payload = textLayer.id
                    )
                )
            }
        }

        // 3. Audio recommendation
        val hasAudio = project.layers.any { it is CreativeLayer.Audio }
        if (!hasAudio) {
            suggestions.add(
                EditorSuggestion(
                    id = "sug_music",
                    title = "Añadir Música de Fondo",
                    description = "Los Reels con música obtienen un 40% más de alcance.",
                    actionType = SuggestionActionType.ADD_MUSIC
                )
            )
        }

        // 4. Subtitle recommendation for video
        val hasSubtitles = project.layers.any { it is CreativeLayer.Text && it.id.startsWith("sub_") }
        if (!hasSubtitles && project.type == com.example.creative.core.CreativeType.REEL) {
            suggestions.add(
                EditorSuggestion(
                    id = "sug_subtitles",
                    title = "Generar Subtítulos IA",
                    description = "Transcribe automáticamente la voz del vídeo para generar subtítulos sincronizados.",
                    actionType = SuggestionActionType.GENERATE_SUBTITLES
                )
            )
        }

        return suggestions
    }
}
