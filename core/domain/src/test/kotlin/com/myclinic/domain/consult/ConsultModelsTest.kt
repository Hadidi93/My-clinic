package com.myclinic.domain.consult

import com.myclinic.domain.model.RecordSection
import com.myclinic.domain.record.RecordJson
import com.myclinic.domain.record.RecordTable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Consults, referrals and notifications as the app reads them. Demo data only. */
class ConsultModelsTest {

    private val consults = RecordJson.decodeFromString(
        ListSerializer(ConsultSummary.serializer()),
        """[{"id":"c1","patient_id":"p1","role":"consultant","question":"Demo: fit for surgery?","urgency":"urgent",
             "status":"pending","anonymized":true,"sections":["allergies","investigations"],"created_at":"2026-10-04T08:00:00+00:00",
             "expires_at":"2026-10-11T08:00:00+00:00","revoked_at":null,"closed_at":null,"active":true,
             "other_doctor":{"id":"d1","full_name":"Dr Demo Owner","specialty":"Surgery","hospital":"Demo Hospital"},
             "patient_name":null,"patient_sex":"male","patient_age":54,"last_activity_at":"2026-10-04T08:00:00+00:00","unread":1},
            {"id":"c2","patient_id":"p1","role":"requester","question":"Demo","status":"closed","anonymized":false,
             "sections":["identifiers"],"active":false,"closed_at":"2026-10-03T00:00:00+00:00",
             "other_doctor":{"id":"d2","full_name":"Dr Demo Cardio","specialty":null,"hospital":null},
             "patient_name":"Demo Patient","unread":0}]""",
    )

    @Test
    fun `consult list is read from the server, anonymized patients have no name`() {
        val (asConsultant, asRequester) = consults
        assertTrue(asConsultant.iAmConsultant)
        assertNull(asConsultant.patientName)
        assertEquals(setOf(RecordSection.ALLERGIES, RecordSection.INVESTIGATIONS), asConsultant.sharedSections)
        assertEquals("Surgery · Demo Hospital", asConsultant.otherDoctor.subtitle)
        assertEquals("", asRequester.otherDoctor.subtitle)
    }

    @Test
    fun `who may reply, revoke, close and view the record`() {
        val (asConsultant, closedRequest) = consults
        assertTrue(ConsultRules.canReply(asConsultant))
        assertFalse("only the requester revokes", ConsultRules.canRevoke(asConsultant))
        assertTrue(ConsultRules.canRevoke(asConsultant.copy(role = "requester")))
        assertTrue(ConsultRules.canClose(asConsultant))
        assertTrue(ConsultRules.canViewRecord(asConsultant))
        assertFalse(ConsultRules.canReply(closedRequest))
        assertFalse(ConsultRules.canClose(closedRequest))
        assertFalse("requesters already have the record", ConsultRules.canViewRecord(closedRequest.copy(active = true)))
        assertFalse("no access once revoked", ConsultRules.canViewRecord(asConsultant.copy(active = false)))
    }

    @Test
    fun `the consultant loads only the tables of the shared sections`() {
        val tables = ConsultRules.tablesFor(setOf(RecordSection.ALLERGIES, RecordSection.INVESTIGATIONS))
        assertEquals(
            listOf(RecordTable.ALLERGIES, RecordTable.INVESTIGATION_REQUESTS, RecordTable.INVESTIGATION_RESULTS, RecordTable.ATTACHMENTS),
            tables,
        )
        assertFalse(RecordTable.PATIENTS in ConsultRules.tablesFor(setOf(RecordSection.IDENTIFIERS)))
        assertFalse(RecordTable.ATTACHMENTS in ConsultRules.tablesFor(setOf(RecordSection.MEDICATIONS)))
        assertTrue(RecordTable.ATTACHMENTS in ConsultRules.tablesFor(setOf(RecordSection.SURGICAL_CARE)))
    }

    @Test
    fun `referral actions follow direction and status`() {
        val received = RecordJson.decodeFromString(
            ReferralSummary.serializer(),
            """{"id":"r1","patient_id":"p1","direction":"received","kind":"transfer","status":"pending","note":"Demo",
               "created_at":"2026-10-04T08:00:00+00:00","other_doctor":{"id":"d1","full_name":"Dr Demo"},
               "patient_name":"Demo Patient","patient_sex":"female","patient_age":40,"primary_diagnosis":"Demo gallstones"}""",
        )
        assertTrue(ReferralRules.canRespond(received))
        assertFalse(ReferralRules.canCancel(received))
        val sent = received.copy(direction = "sent")
        assertTrue(ReferralRules.canCancel(sent))
        assertFalse(ReferralRules.canRespond(sent))
        assertFalse("a completed transfer can't be undone", ReferralRules.canEnd(received.copy(status = "accepted")))
        assertTrue(ReferralRules.canEnd(received.copy(status = "accepted", kind = "comanagement")))
    }

    @Test
    fun `doctor cards carry the grade, and every grade and specialty has both languages`() {
        val card = RecordJson.decodeFromString(
            DoctorCard.serializer(),
            """{"id":"d1","full_name":"Dr Demo","specialty":"Cardiology","hospital":null,"photo_path":null,"grade":"consultant"}""",
        )
        assertEquals("consultant", card.grade)
        com.myclinic.domain.model.DoctorGrade.ALL.forEach {
            assertTrue(it, com.myclinic.domain.forms.Vocabulary.OPTIONS.containsKey(it))
        }
        com.myclinic.domain.model.Specialties.ALL.forEach { assertTrue(it.en.isNotBlank() && it.ar.isNotBlank()) }
    }

    @Test
    fun `notifications carry only a kind and an id`() {
        val n = RecordJson.decodeFromString(
            AppNotification.serializer(),
            """{"id":"n1","recipient_id":"d1","kind":"lab_request","ref_id":"x","created_at":"2026-10-04T08:00:00+00:00","read_at":null}""",
        )
        assertFalse(n.isRead)
        assertTrue(n.kind in NotificationKind.ALL)
        assertTrue(NotificationKind.isConsult(NotificationKind.CONSULT_MESSAGE))
        assertTrue(NotificationKind.isReferral(NotificationKind.REFERRAL_RESPONSE))
    }
}
