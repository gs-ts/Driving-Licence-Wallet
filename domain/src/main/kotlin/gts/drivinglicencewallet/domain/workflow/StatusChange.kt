package gts.drivinglicencewallet.domain.workflow

import kotlin.time.Instant

/**
 * A move from one [ApplicationStatus] to the next, with the [WorkflowEvent] that caused it.
 *
 * Only the workflow in this module can create one, so other modules can save a change
 * but can't make up an illegal one. [from] is null only for a new application.
 */
class StatusChange<out S : ApplicationStatus> internal constructor(
    val from: ApplicationStatus?,
    val to: S,
    val event: WorkflowEvent,
) {

    fun toHistoryEntry(timestamp: Instant): HistoryEntry = HistoryEntry(
        timestamp = timestamp,
        fromStatus = from,
        toStatus = to,
        event = event
    )
}
