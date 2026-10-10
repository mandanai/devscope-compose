package dev.journeyscope.export.redaction

internal class ScrubResult(val text: String, val changed: Boolean)

/** Last line of defence for free text: names, labels and allowlisted values can still carry secrets. */
internal object ValueScrubber {
    const val MASK: String = "[REDACTED]"

    private val bearer = Regex("""(?i)\b(bearer|basic)\s+[A-Za-z0-9._~+/=-]{8,}""")
    private val jwt = Regex("""\beyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]*""")
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val longSecret = Regex("""\b[A-Fa-f0-9]{32,}\b|\b[A-Za-z0-9+/_-]{40,}={0,2}""")
    private val phone = Regex("""(?<![\w.])\+?\d[\d\s().-]{8,}\d(?![\w.])""")
    private val querySecret = Regex(
        """(?i)([?&;](?:access_token|refresh_token|id_token|token|api[_-]?key|key|secret|password|passwd|otp|code|sig|signature|x-amz-[a-z-]+|expires|auth|session|sid)=)[^&#\s]*""",
    )
    private val userInfo = Regex("""(?i)(\b[a-z][a-z0-9+.-]*://)[^/\s:@]+:[^/\s@]+@""")

    fun scrub(input: String): ScrubResult {
        var text = input
        text = userInfo.replace(text) { it.groupValues[1] + MASK + "@" }
        text = querySecret.replace(text) { it.groupValues[1] + MASK }
        text = bearer.replace(text, MASK)
        text = jwt.replace(text, MASK)
        text = email.replace(text, MASK)
        text = longSecret.replace(text, MASK)
        text = phone.replace(text, MASK)
        return ScrubResult(text, text != input)
    }
}
