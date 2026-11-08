package gts.drivinglicencewallet.data.database.entities

import gts.drivinglicencewallet.domain.applicant.ApplicantField
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.SavedEvent
import gts.drivinglicencewallet.domain.workflow.DataProblem
import gts.drivinglicencewallet.domain.workflow.HistoryEntry
import gts.drivinglicencewallet.domain.workflow.PhotoProblem
import gts.drivinglicencewallet.domain.workflow.RejectionReason
import gts.drivinglicencewallet.domain.workflow.ReviewReason
import gts.drivinglicencewallet.domain.workflow.WorkflowEvent
import kotlin.time.Instant

internal fun HistoryEntry.toEntity(applicationId: ApplicationId): HistoryEventEntity {
    val columns = event.toColumns()
    return HistoryEventEntity(
        applicationId = applicationId.value,
        timestamp = timestamp.toEpochMilliseconds(),
        event = columns.code.name,
        reasons = columns.reasons,
        rejectionReason = columns.rejectionReason,
        attempts = columns.attempts
    )
}

/** The saved event, or null if its code or one of its details isn't known. */
internal fun HistoryEventEntity.toSavedEventOrNull(): SavedEvent? =
    toWorkflowEventOrNull()?.let { workflowEvent ->
        SavedEvent(event = workflowEvent, timestamp = Instant.fromEpochMilliseconds(epochMilliseconds = timestamp))
    }

/** An event as it's saved: its code, and the details that only some events have. */
private data class EventColumns(
    val code: EventCode,
    val reasons: String? = null,
    val rejectionReason: String? = null,
    val attempts: Int? = null,
)

private fun WorkflowEvent.toColumns(): EventColumns {
    return when (this) {
        WorkflowEvent.ApplicationSubmitted -> EventColumns(code = EventCode.ApplicationSubmitted)
        WorkflowEvent.ProcessingStarted -> EventColumns(code = EventCode.ProcessingStarted)
        WorkflowEvent.DataValidationPassed -> EventColumns(code = EventCode.DataValidationPassed)
        is WorkflowEvent.DataValidationFailed -> EventColumns(
            code = EventCode.DataValidationFailed,
            reasons = problems.joinToString(separator = CODE_SEPARATOR) { problem -> problem.code }
        )
        WorkflowEvent.PhotoCheckPassed -> EventColumns(code = EventCode.PhotoCheckPassed)
        is WorkflowEvent.PhotoCheckFailed -> EventColumns(
            code = EventCode.PhotoCheckFailed,
            reasons = problems.joinToString(separator = CODE_SEPARATOR) { problem -> problem.name }
        )
        WorkflowEvent.AutoApproved -> EventColumns(code = EventCode.AutoApproved)
        is WorkflowEvent.ManualReviewRequired -> EventColumns(
            code = EventCode.ManualReviewRequired,
            reasons = reasons.joinToString(separator = CODE_SEPARATOR) { reason -> reason.name }
        )
        WorkflowEvent.ManuallyApproved -> EventColumns(code = EventCode.ManuallyApproved)
        is WorkflowEvent.ManuallyRejected -> EventColumns(code = EventCode.ManuallyRejected, rejectionReason = reason.text)
        is WorkflowEvent.ProcessingAbandoned -> EventColumns(code = EventCode.ProcessingAbandoned, attempts = attempts)
    }
}

private fun HistoryEventEntity.toWorkflowEventOrNull(): WorkflowEvent? {
    return when (enumOrNull<EventCode>(name = event)) {
        EventCode.ApplicationSubmitted -> WorkflowEvent.ApplicationSubmitted
        EventCode.ProcessingStarted -> WorkflowEvent.ProcessingStarted
        EventCode.DataValidationPassed -> WorkflowEvent.DataValidationPassed
        EventCode.DataValidationFailed -> reasonsOrNull(parse = ::dataProblemOrNull)
            ?.let { problems -> WorkflowEvent.DataValidationFailed(problems = problems) }
        EventCode.PhotoCheckPassed -> WorkflowEvent.PhotoCheckPassed
        EventCode.PhotoCheckFailed -> reasonsOrNull { code -> enumOrNull<PhotoProblem>(name = code) }
            ?.let { problems -> WorkflowEvent.PhotoCheckFailed(problems = problems) }
        EventCode.AutoApproved -> WorkflowEvent.AutoApproved
        EventCode.ManualReviewRequired -> reasonsOrNull { code -> enumOrNull<ReviewReason>(name = code) }
            ?.let { reasons -> WorkflowEvent.ManualReviewRequired(reasons = reasons) }
        EventCode.ManuallyApproved -> WorkflowEvent.ManuallyApproved
        EventCode.ManuallyRejected -> rejectionReason
            ?.let { text -> RejectionReason.parse(text = text) }
            ?.let { reason -> WorkflowEvent.ManuallyRejected(reason = reason) }
        EventCode.ProcessingAbandoned -> attempts
            ?.takeIf { count -> count > 0 }
            ?.let { count -> WorkflowEvent.ProcessingAbandoned(attempts = count) }
        null -> null
    }
}

/** Every code in [HistoryEventEntity.reasons] parsed, or null if there are none or one of them isn't known. */
private fun <T : Any> HistoryEventEntity.reasonsOrNull(parse: (String) -> T?): Set<T>? =
    reasons
        ?.split(CODE_SEPARATOR)
        ?.mapAllOrNull(transform = parse)
        ?.toSet()

/** A missing field is saved as "Missing" and the field's name, such as MissingEmail. */
private val DataProblem.code: String
    get() = when (this) {
        is DataProblem.MissingField -> "Missing${field.name}"
        DataProblem.InvalidEmail -> "InvalidEmail"
        DataProblem.Under18 -> "Under18"
    }

/** Every data problem there is, so a saved code can be looked up. A new kind of problem must be added here too. */
private val allDataProblems: List<DataProblem> =
    ApplicantField.entries.map { field -> DataProblem.MissingField(field = field) } + DataProblem.InvalidEmail + DataProblem.Under18

private fun dataProblemOrNull(code: String): DataProblem? = allDataProblems.firstOrNull { problem -> problem.code == code }

private const val CODE_SEPARATOR = ","
