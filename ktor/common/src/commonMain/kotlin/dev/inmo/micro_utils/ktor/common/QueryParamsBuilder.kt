package dev.inmo.micro_utils.ktor.common

import kotlin.js.JsExport
import kotlin.js.JsName

typealias QueryParam = Pair<String, String?>
typealias QueryParams = Map<String, String?>

val QueryParams.asUrlQuery: String
    get() = keys.joinToString("&") { "${it}${get(it) ?.let { value -> "=$value" }}" }

val List<QueryParam>.asUrlQuery: String
    get() = joinToString("&") { (key, value) -> "${key}${value ?.let { _ -> "=$value" }}" }

@JsExport
fun String.includeQueryParams(
    queryParams: QueryParams
): String = "$this${if(queryParams.isNotEmpty()) "${if (contains("?")) "&" else "?"}${queryParams.asUrlQuery}" else ""}"

@JsExport
@JsName("includeQueryParamsWithList")
fun String.includeQueryParams(
    queryParams: List<QueryParam>
): String = "$this${if (contains("?")) "&" else "?"}${queryParams.asUrlQuery}"

val String.parseUrlQuery: QueryParams
    get() = split("&").map {
        it.split("=").let { pair ->
            pair.first() to pair.getOrNull(1)
        }
    }.toMap()
