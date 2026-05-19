package com.zzz.androidvocab.core.common

import java.nio.charset.StandardCharsets
import java.util.UUID

fun stableId(
    prefix: String,
    value: String,
): String {
    val uuid = UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))
    return "$prefix-$uuid"
}

fun wordId(word: String): String = stableId("word", word.trim().lowercase())

fun cardId(
    wordId: String,
    bookCode: String,
): String = "card|$bookCode|$wordId"

fun logId(
    cardId: String,
    reviewedAt: String,
): String = stableId("log", "$cardId|$reviewedAt")
