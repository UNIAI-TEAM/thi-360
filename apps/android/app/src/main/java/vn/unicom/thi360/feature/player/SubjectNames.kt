package vn.unicom.thi360.feature.player

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.generated.model.ChoiceSubjectView
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.theme.LocalAppLocale

object SubjectNames {

    @StringRes
    fun knownCodeResOrNull(code: String?): Int? = when (code?.trim()?.uppercase()) {
        "ANH" -> R.string.attempt_subjects_codes_ANH
        "CNCN" -> R.string.attempt_subjects_codes_CNCN
        "CNNN" -> R.string.attempt_subjects_codes_CNNN
        "DIA" -> R.string.attempt_subjects_codes_DIA
        "ECON_LAW" -> R.string.attempt_subjects_codes_ECON_LAW
        "GDKTPL" -> R.string.attempt_subjects_codes_GDKTPL
        "HOA" -> R.string.attempt_subjects_codes_HOA
        "INFORMATICS" -> R.string.attempt_subjects_codes_INFORMATICS
        "LY" -> R.string.attempt_subjects_codes_LY
        "SCIENTIFIC_THINKING" -> R.string.attempt_subjects_codes_SCIENTIFIC_THINKING
        "SINH" -> R.string.attempt_subjects_codes_SINH
        "SU" -> R.string.attempt_subjects_codes_SU
        "TECH_AGRICULTURE" -> R.string.attempt_subjects_codes_TECH_AGRICULTURE
        "TECH_INDUSTRY" -> R.string.attempt_subjects_codes_TECH_INDUSTRY
        "TIENG_ANH" -> R.string.attempt_subjects_codes_TIENG_ANH
        "TIN" -> R.string.attempt_subjects_codes_TIN
        "TOAN" -> R.string.attempt_subjects_codes_TOAN
        "VAN" -> R.string.attempt_subjects_codes_VAN
        "VIETNAMESE" -> R.string.attempt_subjects_codes_VIETNAMESE
        else -> null
    }

    fun resolve(
        code: String,
        locale: AppLocale,
        subjectsByCode: Map<String, ChoiceSubjectView> = emptyMap(),
        stringResolver: ((Int) -> String)? = null,
    ): String {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return ""
        val meta = subjectsByCode[trimmed] ?: subjectsByCode[trimmed.uppercase()]
        val resId = knownCodeResOrNull(trimmed)
        if (resId != null && stringResolver != null) {
            return stringResolver(resId)
        }
        if (meta != null) {
            val localized = when (locale) {
                AppLocale.EN -> meta.nameEn?.takeIf { it.isNotBlank() }
                    ?: meta.nameVi?.takeIf { it.isNotBlank() }
                AppLocale.VI -> meta.nameVi?.takeIf { it.isNotBlank() }
                    ?: meta.nameEn?.takeIf { it.isNotBlank() }
            }
            if (!localized.isNullOrBlank()) return localized
        }
        return trimmed
    }

    @Composable
    fun resolveLocalized(
        code: String,
        subjectsByCode: Map<String, ChoiceSubjectView> = emptyMap(),
    ): String {
        val locale = LocalAppLocale.current
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return ""
        val resId = knownCodeResOrNull(trimmed)
        if (resId != null) {
            return stringResource(resId)
        }
        return resolve(
            code = trimmed,
            locale = locale,
            subjectsByCode = subjectsByCode,
            stringResolver = null,
        )
    }

    fun resolve(
        context: Context,
        code: String,
        locale: AppLocale,
        subjectsByCode: Map<String, ChoiceSubjectView> = emptyMap(),
    ): String = resolve(
        code = code,
        locale = locale,
        subjectsByCode = subjectsByCode,
        stringResolver = context::getString,
    )
}
