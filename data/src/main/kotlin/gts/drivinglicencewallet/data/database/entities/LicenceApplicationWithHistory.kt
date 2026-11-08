package gts.drivinglicencewallet.data.database.entities

import androidx.room3.Embedded
import androidx.room3.Relation
import gts.drivinglicencewallet.domain.application.ApplicationId
import gts.drivinglicencewallet.domain.application.LicenceApplication
import gts.drivinglicencewallet.domain.application.PhotoReference

/** An application with all its history lines, read together in one transaction. */
internal data class LicenceApplicationWithHistory(
    @Embedded val application: LicenceApplicationEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["applicationId"])
    val history: List<HistoryEventEntity>,
)

/**
 * Rebuilds the application from what's saved. Returns null if any of it can't be read: a value that
 * doesn't pass its check, an unknown code, or a history that doesn't follow the workflow.
 */
internal fun LicenceApplicationWithHistory.toApplicationOrNull(): LicenceApplication? {
    val details = application.toDetailsOrNull() ?: return null
    val events = history
        .sortedBy { saved -> saved.position }
        .mapAllOrNull { saved -> saved.toSavedEventOrNull() } ?: return null
    return LicenceApplication.restore(
        id = ApplicationId(value = application.id),
        details = details,
        photo = PhotoReference(value = application.photoReference),
        events = events
    )
}
