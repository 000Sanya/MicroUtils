package dev.inmo.micro_utils.repos.ktor.client.key_value

import dev.inmo.micro_utils.ktor.client.*
import dev.inmo.micro_utils.ktor.common.buildStandardUrl
import dev.inmo.micro_utils.repos.WriteStandardKeyValueRepo
import dev.inmo.micro_utils.repos.ktor.common.key_value.*
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.*

class KtorWriteStandardKeyValueRepo<K, V> (
    private var baseUrl: String,
    private var client: HttpClient = HttpClient(),
    private var keySerializer: KSerializer<K>,
    private var valueSerializer: KSerializer<V>,
) : WriteStandardKeyValueRepo<K, V> {
    private val keyValueMapSerializer = MapSerializer(keySerializer, valueSerializer)
    private val keysListSerializer = ListSerializer(keySerializer)
    override val onNewValue: Flow<Pair<K, V>> = client.createStandardWebsocketFlow(
        buildStandardUrl(baseUrl, onNewValueRoute),
        deserializer = PairSerializer(keySerializer, valueSerializer)
    )

    override val onValueRemoved: Flow<K> = client.createStandardWebsocketFlow(
        buildStandardUrl(baseUrl, onValueRemovedRoute),
        deserializer = keySerializer
    )

    override suspend fun set(toSet: Map<K, V>) = client.unipost(
        buildStandardUrl(
            baseUrl,
            setRoute
        ),
        BodyPair(keyValueMapSerializer, toSet),
        Unit.serializer()
    )
    override suspend fun set(k: K, v: V) = set(mapOf(k to v))

    override suspend fun unset(toUnset: List<K>) = client.unipost(
        buildStandardUrl(
            baseUrl,
            unsetRoute,
        ),
        BodyPair(keysListSerializer, toUnset),
        Unit.serializer()
    )
    override suspend fun unset(k: K) = unset(listOf(k))
}

@Deprecated("Renamed", ReplaceWith("KtorWriteStandardKeyValueRepo", "dev.inmo.micro_utils.repos.ktor.client.key_value.KtorWriteStandardKeyValueRepo"))
typealias KtorStandartWriteKeyValueRepo<K, V> = KtorWriteStandardKeyValueRepo<K, V>
