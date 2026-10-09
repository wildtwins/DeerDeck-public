package com.wildtwins.deerdeck.data

import org.json.JSONArray

object SessionTagCodec {
    fun encode(tags: List<String>): String? {
        val clean = tags
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        return clean.takeIf { it.isNotEmpty() }?.let { JSONArray(it).toString() }
    }

    fun decode(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            val json = JSONArray(value)
            List(json.length()) { index -> json.getString(index) }
        }.getOrElse {
            value.split("|", ",")
        }.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }
}
