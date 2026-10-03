package com.myclinic.app.data.facilities

import com.myclinic.domain.record.Facility
import com.myclinic.domain.record.RecordJson
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** Lab/radiology departments that investigation requests can be sent to. Anyone signed in may add one. */
interface FacilityRepository {
    /** Last loaded list (kept in memory so the request form works offline once loaded). */
    val facilities: StateFlow<List<Facility>>
    suspend fun refresh(): Result<List<Facility>>
    suspend fun add(name: String, kind: String, hospital: String?): Result<Facility>
}

@Singleton
class SupabaseFacilityRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : FacilityRepository {

    private val list = MutableStateFlow<List<Facility>>(emptyList())
    override val facilities: StateFlow<List<Facility>> = list.asStateFlow()

    override suspend fun refresh(): Result<List<Facility>> = call {
        val result = supabase.from(TABLE).select { order("name", Order.ASCENDING) }
        RecordJson.decodeFromString(ListSerializer(Facility.serializer()), result.data).also { list.value = it }
    }

    override suspend fun add(name: String, kind: String, hospital: String?): Result<Facility> = call {
        val result = supabase.from(TABLE).insert(
            buildJsonObject {
                put("name", name.trim())
                put("kind", kind)
                put("hospital", hospital?.trim()?.takeIf { it.isNotEmpty() })
            },
        ) { select() }
        val added = RecordJson.decodeFromString(ListSerializer(Facility.serializer()), result.data).single()
        list.value = (list.value + added).sortedBy { it.name.lowercase() }
        added
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private companion object {
        const val TABLE = "facilities"
    }
}
