package moe.matsuri.nb4a.proxy.config

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

internal fun fullSingBoxConfig(contents: String): JsonObject? = try {
    JsonParser.parseString(contents).takeIf { it.isJsonObject }?.asJsonObject?.takeIf { root ->
        root.get("inbounds")?.isJsonArray == true &&
            (root.get("outbounds")?.isJsonArray == true || root.get("endpoints")?.isJsonArray == true)
    }
} catch (_: JsonParseException) {
    null
}

internal fun fullSingBoxNodeNames(contents: String): List<String> {
    val config = fullSingBoxConfig(contents) ?: return emptyList()
    val nonNodeTypes = setOf("direct", "block", "dns", "selector", "urltest")
    return listOf("outbounds", "endpoints")
        .flatMap { key -> config.get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty() }
        .mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject }
        .mapNotNull { node ->
            val type = node.stringValue("type")
            if (type == null || type in nonNodeTypes) null
            else node.stringValue("tag")?.takeIf(String::isNotBlank)
        }
        .distinct()
}

private fun JsonObject.stringValue(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
