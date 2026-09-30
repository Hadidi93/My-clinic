package com.myclinic.domain.forms

import com.myclinic.domain.record.RecordTable

/** A label in both app languages. */
data class Localized(val en: String, val ar: String) {
    fun get(language: String): String = if (language == "ar") ar else en
}

/**
 * The clinical vocabulary of the record forms (field names, choices,
 * checklist items, chronic diseases) in English and Arabic.
 *
 * It lives here, next to the form definitions, rather than in Android
 * string resources, so a unit test can prove that every field and option
 * has both translations. General app text (buttons, messages) stays in
 * the Android string resources.
 */
object Vocabulary {

    fun section(table: RecordTable): Localized = SECTIONS.getValue(table)

    fun field(key: String): Localized = FIELDS[key] ?: Localized(key, key)

    fun option(value: String): Localized = OPTIONS[value] ?: Localized(value, value)

    val SECTIONS: Map<RecordTable, Localized> = mapOf(
        RecordTable.PATIENTS to Localized("Personal data", "البيانات الشخصية"),
        RecordTable.PRESENTING_COMPLAINTS to Localized("Presenting complaint & HPI", "الشكوى الحالية وتاريخها"),
        RecordTable.MEDICAL_CONDITIONS to Localized("Past medical history", "التاريخ المرضي السابق"),
        RecordTable.SURGICAL_HISTORY to Localized("Past surgical history", "التاريخ الجراحي السابق"),
        RecordTable.MEDICATIONS to Localized("Drug history", "التاريخ الدوائي"),
        RecordTable.ALLERGIES to Localized("Allergies", "الحساسية"),
        RecordTable.FAMILY_HISTORY to Localized("Family history", "التاريخ العائلي"),
        RecordTable.SOCIAL_HISTORY to Localized("Social history", "التاريخ الاجتماعي"),
        RecordTable.EXAMINATIONS to Localized("Examination & vital signs", "الفحص والعلامات الحيوية"),
        RecordTable.SURGICAL_CASES to Localized("Surgery", "الجراحة"),
        RecordTable.POSTOP_FOLLOWUPS to Localized("Post-op follow-up", "المتابعة بعد العملية"),
    )

    val FIELDS: Map<String, Localized> = mapOf(
        // Personal data
        "full_name" to Localized("Full name", "الاسم الكامل"),
        "sex" to Localized("Sex", "الجنس"),
        "age_years" to Localized("Age (years)", "العمر (بالسنوات)"),
        "date_of_birth" to Localized("Date of birth", "تاريخ الميلاد"),
        "file_number" to Localized("File number", "رقم الملف"),
        "national_id" to Localized("National ID", "الرقم القومي"),
        "phone" to Localized("Phone", "الهاتف"),
        "address" to Localized("Address", "العنوان"),
        "emergency_contact_name" to Localized("Emergency contact", "جهة الاتصال للطوارئ"),
        "emergency_contact_phone" to Localized("Emergency contact phone", "هاتف الطوارئ"),
        "primary_diagnosis" to Localized("Main diagnosis", "التشخيص الرئيسي"),
        "tags" to Localized("Tags", "الوسوم"),
        // Complaint
        "complaint" to Localized("Presenting complaint", "الشكوى"),
        "hpi" to Localized("History of present illness", "تاريخ المرض الحالي"),
        "onset_date" to Localized("Onset date", "تاريخ البداية"),
        "recorded_at" to Localized("Recorded at", "وقت التسجيل"),
        // Medical conditions
        "name" to Localized("Name", "الاسم"),
        "is_chronic" to Localized("Chronic", "مزمن"),
        "diagnosed_on" to Localized("Diagnosed on", "تاريخ التشخيص"),
        "notes" to Localized("Notes", "ملاحظات"),
        // Surgical history
        "procedure" to Localized("Procedure", "العملية"),
        "performed_on" to Localized("Date", "التاريخ"),
        "hospital" to Localized("Hospital", "المستشفى"),
        "complications" to Localized("Complications", "المضاعفات"),
        // Medications
        "dose" to Localized("Dose", "الجرعة"),
        "route" to Localized("Route", "طريقة الإعطاء"),
        "frequency" to Localized("Frequency", "عدد المرات"),
        "started_on" to Localized("Started", "تاريخ البدء"),
        "stopped_on" to Localized("Stopped", "تاريخ الإيقاف"),
        "is_current" to Localized("Currently taking", "يتناوله حاليًا"),
        // Allergies
        "allergen" to Localized("Allergen", "مسبب الحساسية"),
        "reaction" to Localized("Reaction", "رد الفعل"),
        "severity" to Localized("Severity", "الشدة"),
        // Family
        "relation" to Localized("Relative", "صلة القرابة"),
        "condition" to Localized("Condition", "المرض"),
        // Social
        "smoking" to Localized("Smoking", "التدخين"),
        "pack_years" to Localized("Pack-years", "باكيت/سنة"),
        "alcohol" to Localized("Alcohol", "الكحول"),
        "occupation" to Localized("Occupation", "المهنة"),
        "marital_status" to Localized("Marital status", "الحالة الاجتماعية"),
        // Examination
        "examined_at" to Localized("Examined at", "وقت الفحص"),
        "systolic_mmhg" to Localized("Systolic BP (mmHg)", "الضغط الانقباضي (مم زئبق)"),
        "diastolic_mmhg" to Localized("Diastolic BP (mmHg)", "الضغط الانبساطي (مم زئبق)"),
        "pulse_bpm" to Localized("Pulse (bpm)", "النبض (نبضة/دقيقة)"),
        "resp_rate" to Localized("Respiratory rate (/min)", "معدل التنفس (/دقيقة)"),
        "temperature_c" to Localized("Temperature (°C)", "الحرارة (°م)"),
        "spo2_percent" to Localized("SpO₂ (%)", "تشبع الأكسجين (%)"),
        "pain_score" to Localized("Pain score (0–10)", "درجة الألم (0–10)"),
        "weight_kg" to Localized("Weight (kg)", "الوزن (كجم)"),
        "height_cm" to Localized("Height (cm)", "الطول (سم)"),
        "findings" to Localized("Examination findings", "نتائج الفحص"),
        // Surgical case
        "diagnosis" to Localized("Diagnosis", "التشخيص"),
        "planned_operation" to Localized("Planned operation", "العملية المخططة"),
        "status" to Localized("Status", "الحالة"),
        "planned_date" to Localized("Planned date", "الموعد المخطط"),
        "preop_checklist" to Localized("Pre-op checklist", "قائمة التحضير قبل العملية"),
        "operation_date" to Localized("Operation date", "تاريخ العملية"),
        "operative_notes" to Localized("Operative notes", "تقرير العملية"),
        // Follow-up
        "surgical_case_id" to Localized("Operation", "العملية"),
        "visit_date" to Localized("Visit date", "تاريخ الزيارة"),
        "wound_status" to Localized("Wound", "حالة الجرح"),
    )

    val OPTIONS: Map<String, Localized> = mapOf(
        "male" to Localized("Male", "ذكر"),
        "female" to Localized("Female", "أنثى"),
        "unknown" to Localized("Unknown", "غير معروف"),
        // routes
        "oral" to Localized("Oral", "بالفم"),
        "iv" to Localized("IV", "وريدي"),
        "im" to Localized("IM", "عضلي"),
        "sc" to Localized("SC", "تحت الجلد"),
        "topical" to Localized("Topical", "موضعي"),
        "inhaled" to Localized("Inhaled", "استنشاق"),
        "rectal" to Localized("Rectal", "شرجي"),
        "other" to Localized("Other", "أخرى"),
        // severity
        "mild" to Localized("Mild", "خفيفة"),
        "moderate" to Localized("Moderate", "متوسطة"),
        "severe" to Localized("Severe", "شديدة"),
        "life_threatening" to Localized("Life-threatening", "مهددة للحياة"),
        // relations
        "father" to Localized("Father", "الأب"),
        "mother" to Localized("Mother", "الأم"),
        "brother" to Localized("Brother", "الأخ"),
        "sister" to Localized("Sister", "الأخت"),
        "son" to Localized("Son", "الابن"),
        "daughter" to Localized("Daughter", "الابنة"),
        "grandparent" to Localized("Grandparent", "الجد / الجدة"),
        // smoking
        "never" to Localized("Never", "لا يدخن"),
        "former" to Localized("Ex-smoker", "مدخن سابق"),
        "current" to Localized("Current smoker", "مدخن حاليًا"),
        // marital
        "single" to Localized("Single", "أعزب"),
        "married" to Localized("Married", "متزوج"),
        "divorced" to Localized("Divorced", "مطلق"),
        "widowed" to Localized("Widowed", "أرمل"),
        // surgical case status
        "planned" to Localized("Planned", "مخطط لها"),
        "scheduled" to Localized("Scheduled", "محدد موعدها"),
        "done" to Localized("Done", "تمت"),
        "cancelled" to Localized("Cancelled", "أُلغيت"),
        // wound
        "healing_well" to Localized("Healing well", "يلتئم جيدًا"),
        "erythema" to Localized("Erythema", "احمرار"),
        "discharge" to Localized("Discharge", "إفرازات"),
        "infected" to Localized("Infected", "ملتهب"),
        "dehiscence" to Localized("Dehiscence", "تفتق الجرح"),
        "healed" to Localized("Healed", "التأم"),
        // pre-op checklist
        "consent_signed" to Localized("Consent signed", "الإقرار موقّع"),
        "npo_confirmed" to Localized("Fasting (NPO) confirmed", "الصيام مؤكد"),
        "labs_reviewed" to Localized("Labs reviewed", "التحاليل رُوجعت"),
        "blood_crossmatched" to Localized("Blood cross-matched", "الدم متوافق ومحجوز"),
        "anaesthesia_review" to Localized("Anaesthesia review done", "تقييم التخدير تم"),
        "site_marked" to Localized("Site marked", "مكان العملية محدد"),
        "antibiotic_prophylaxis" to Localized("Antibiotic prophylaxis", "مضاد حيوي وقائي"),
        "dvt_prophylaxis" to Localized("DVT prophylaxis", "وقاية من الجلطات"),
        "imaging_available" to Localized("Imaging available", "الأشعة متاحة"),
        "anticoagulants_reviewed" to Localized("Anticoagulants reviewed", "مضادات التجلط رُوجعت"),
    )
}

/** Common chronic diseases for quick picking. Free text is always allowed too. */
object ChronicDiseases {
    data class Disease(val code: String, val name: Localized)

    val ALL: List<Disease> = listOf(
        Disease("diabetes_t2", Localized("Type 2 diabetes", "السكري من النوع الثاني")),
        Disease("diabetes_t1", Localized("Type 1 diabetes", "السكري من النوع الأول")),
        Disease("hypertension", Localized("Hypertension", "ارتفاع ضغط الدم")),
        Disease("ischemic_heart_disease", Localized("Ischaemic heart disease", "قصور الشريان التاجي")),
        Disease("heart_failure", Localized("Heart failure", "هبوط القلب")),
        Disease("atrial_fibrillation", Localized("Atrial fibrillation", "الرجفان الأذيني")),
        Disease("asthma", Localized("Asthma", "الربو")),
        Disease("copd", Localized("COPD", "الانسداد الرئوي المزمن")),
        Disease("chronic_kidney_disease", Localized("Chronic kidney disease", "الفشل الكلوي المزمن")),
        Disease("chronic_liver_disease", Localized("Chronic liver disease", "مرض الكبد المزمن")),
        Disease("hepatitis_c", Localized("Hepatitis C", "الالتهاب الكبدي الوبائي سي")),
        Disease("hepatitis_b", Localized("Hepatitis B", "الالتهاب الكبدي الوبائي بي")),
        Disease("hypothyroidism", Localized("Hypothyroidism", "قصور الغدة الدرقية")),
        Disease("hyperthyroidism", Localized("Hyperthyroidism", "فرط نشاط الغدة الدرقية")),
        Disease("stroke", Localized("Previous stroke", "سكتة دماغية سابقة")),
        Disease("epilepsy", Localized("Epilepsy", "الصرع")),
        Disease("dvt_pe", Localized("Previous DVT / PE", "جلطة وريدية أو رئوية سابقة")),
        Disease("bleeding_disorder", Localized("Bleeding disorder", "اضطراب النزف")),
        Disease("anaemia", Localized("Chronic anaemia", "أنيميا مزمنة")),
        Disease("rheumatoid_arthritis", Localized("Rheumatoid arthritis", "الروماتويد")),
        Disease("cancer", Localized("Cancer", "ورم خبيث")),
        Disease("obesity", Localized("Obesity", "السمنة")),
    )

    fun byCode(code: String?): Disease? = ALL.firstOrNull { it.code == code }
}
