package com.myclinic.domain.record

import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditModelsTest {
    @Test
    fun `entries without a person (system actions) are read, not rejected`() {
        val list = AuditJson.decodeFromString(
            ListSerializer(AuditEntry.serializer()),
            """[{"id":1,"occurred_at":"2026-10-06T08:00:00+00:00","action":"create","table_name":"doctors","record_id":"d1",
                 "patient_id":null,"actor_id":null,"actor_name":null,"actor_grade":null,"actor_is_staff":false,
                 "actor_is_me":null,"via":null,"section":null,"details":{}},
                {"id":2,"occurred_at":"2026-10-06T09:00:00+00:00","action":"view","table_name":"patients","record_id":"p1",
                 "patient_id":"p1","actor_id":"d2","actor_name":"Dr Demo","actor_grade":"consultant","actor_is_staff":false,
                 "actor_is_me":true,"via":"consult","section":"allergies","details":{"via_consult":"c1"}}]""",
        )
        assertEquals(2, list.size)
        assertFalse(list[0].actorIsMe)
        assertNull(list[0].actorName)
        assertTrue(list[1].actorIsMe)
        assertEquals("consult", list[1].via)
    }
}
