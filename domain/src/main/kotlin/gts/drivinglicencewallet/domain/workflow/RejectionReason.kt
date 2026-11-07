package gts.drivinglicencewallet.domain.workflow

/** Why a reviewer rejected an application, in the reviewer's own words. Never blank. */
@JvmInline
value class RejectionReason private constructor(val text: String) {

    companion object {
        /** Returns null when [text] is blank. Removes surrounding spaces. */
        fun parse(text: String): RejectionReason? {
            val trimmedText = text.trim()
            return if (trimmedText.isEmpty()) null else RejectionReason(text = trimmedText)
        }
    }
}
