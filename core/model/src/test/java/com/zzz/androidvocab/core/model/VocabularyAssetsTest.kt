package com.zzz.androidvocab.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VocabularyAssetsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun publishSafeAssetsMatchExpectedCounts() {
        val assetDir =
            listOf(
                File("app/src/main/assets/vocab"),
                File("../../app/src/main/assets/vocab"),
            ).first { it.exists() }.canonicalFile
        val expected =
            mapOf(
                "cet4.json" to 3846,
                "cet6.json" to 5406,
                "kaoyan.json" to 4801,
                "ielts.json" to 5038,
                "toefl.json" to 6970,
            )

        expected.forEach { (fileName, count) ->
            val rows = json.parseToJsonElement(File(assetDir, fileName).readJsonText()).jsonArray
            assertEquals(fileName, count, rows.size)
            assertFalse(
                rows.any { row ->
                    val flags =
                        (row.jsonObject["sourceFlags"]?.jsonArray ?: emptyList()).map {
                            it.jsonPrimitive.content
                        }
                    "kylebing" in flags || "netem" in flags
                },
            )
        }
        val manifest = json.parseToJsonElement(File(assetDir, "manifest.json").readJsonText()).jsonObject
        assertEquals("publish", manifest["buildTarget"]?.jsonPrimitive?.content)
        assertTrue(File(assetDir, "sources.json").exists())
    }
}

private fun File.readJsonText(): String = readText().removePrefix("\uFEFF")
