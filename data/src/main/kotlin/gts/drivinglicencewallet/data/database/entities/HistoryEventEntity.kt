package gts.drivinglicencewallet.data.database.entities

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * One line of an application's history. Lines are only ever added, so a status change is one insert.
 *
 * [position] keeps the lines in the order they were saved. [reasons], [rejectionReason] and [attempts]
 * hold the event's details, and only the events that have such details use them.
 * The mapping to and from the domain is in HistoryMapping.kt.
 */
@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(
            entity = LicenceApplicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["applicationId"]
        ),
    ],
    indices = [Index(value = ["applicationId"])],
)
internal data class HistoryEventEntity(
    @PrimaryKey(autoGenerate = true) val position: Long = 0,
    val applicationId: String,
    /** Milliseconds since 1970-01-01 UTC. */
    val timestamp: Long,
    /** An [EventCode] name. */
    val event: String,
    /** The problems or the review reasons, as codes separated by commas. */
    val reasons: String?,
    val rejectionReason: String?,
    val attempts: Int?,
)

/**
 * How each workflow event is saved. The history stores these names, and the problems and reasons are
 * stored by their names too, as are the licence categories. So renaming any of them needs a migration.
 */
internal enum class EventCode {
    ApplicationSubmitted,
    ProcessingStarted,
    DataValidationPassed,
    DataValidationFailed,
    PhotoCheckPassed,
    PhotoCheckFailed,
    AutoApproved,
    ManualReviewRequired,
    ManuallyApproved,
    ManuallyRejected,
    ProcessingAbandoned,
}
