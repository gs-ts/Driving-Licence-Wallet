package gts.drivinglicencewallet.domain.workflow

import kotlin.time.Instant

/**
 * One line of an application's audit trail: what happened, when, and the status before and after.
 *
 * [fromStatus] is null only for the first entry, when the application was submitted.
 */
data class HistoryEntry(
    val timestamp: Instant,
    val fromStatus: ApplicationStatus?,
    val toStatus: ApplicationStatus,
    val event: WorkflowEvent,
)
