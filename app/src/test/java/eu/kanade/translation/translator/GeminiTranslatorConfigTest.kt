package eu.kanade.translation.translator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class GeminiTranslatorConfigTest {

    @Test
    fun `builds Gemini REST request without unsupported sampling parameters`() {
        val body = buildGeminiRestRequestBody(
            systemInstruction = "Translate comics",
            requestText = "Translate this page",
            maxOutputTokens = 4096,
        )
        val json = Json.parseToJsonElement(body).jsonObject
        val generationConfig = json.getValue("generationConfig").jsonObject

        assertEquals(4096, generationConfig.getValue("maxOutputTokens").jsonPrimitive.int)
        assertFalse("temperature" in generationConfig)
        assertFalse("topP" in generationConfig)
        assertFalse("topK" in generationConfig)
        assertEquals(
            1,
            json.getValue("systemInstruction")
                .jsonObject
                .getValue("parts")
                .jsonArray
                .size,
        )
        assertEquals(
            "Translate this page",
            json.getValue("contents")
                .jsonArray[0]
                .jsonObject
                .getValue("parts")
                .jsonArray[0]
                .jsonObject
                .getValue("text")
                .jsonPrimitive
                .content,
        )
    }

    @Test
    fun `normalizes Gemini model path for REST endpoint`() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent",
            buildGeminiRestUrl("models/gemini-3.6-flash"),
        )
    }
}
