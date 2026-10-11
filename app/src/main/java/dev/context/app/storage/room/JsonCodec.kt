package dev.context.app.storage.room

import kotlinx.serialization.json.Json

/**
 * Central JSON codec. Uses the same settings for storage, import/export and the debug
 * simulator so exported files always round-trip.
 */
object JsonCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}