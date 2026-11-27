package vn.unicom.thi360.feature.attemptengine

import vn.unicom.thi360.core.network.LogRedactor

object AttemptLogRedactor {
    private const val REDACTED = "[REDACTED]"

    private val attemptSessionHeaderRegex = Regex(
        """(?i)(X-Attempt-Session\s*[:=]\s*)[^\s,;"]+""",
    )
    private val jsonSessionIdRegex = Regex(
        """(?i)"(sessionId)"\s*:\s*"[^"]*"""",
    )
    private val jsonAnswerObjectRegex = Regex(
        """(?i)"(answer)"\s*:\s*\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}""",
    )
    private val jsonFinalAnswersArrayRegex = Regex(
        """(?i)"(finalAnswers)"\s*:\s*\[[^\[\]]*(?:\[[^\[\]]*\][^\[\]]*)*\]""",
    )
    private val jsonAnswerContentFieldRegex = Regex(
        """(?i)"(html|text)"\s*:\s*"(?:\\.|[^"\\])*"""",
    )

    fun redact(input: String): String {
        var result = LogRedactor.redact(input)
        result = attemptSessionHeaderRegex.replace(result) { match ->
            "${match.groupValues[1]}$REDACTED"
        }
        result = jsonSessionIdRegex.replace(result) { match ->
            "\"${match.groupValues[1]}\":\"$REDACTED\""
        }
        result = jsonFinalAnswersArrayRegex.replace(result) { match ->
            "\"${match.groupValues[1]}\":\"$REDACTED\""
        }
        result = jsonAnswerObjectRegex.replace(result) { match ->
            "\"${match.groupValues[1]}\":\"$REDACTED\""
        }
        result = jsonAnswerContentFieldRegex.replace(result) { match ->
            "\"${match.groupValues[1]}\":\"$REDACTED\""
        }
        return result
    }
}
