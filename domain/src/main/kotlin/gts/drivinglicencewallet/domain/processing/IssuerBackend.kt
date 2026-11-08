package gts.drivinglicencewallet.domain.processing

import gts.drivinglicencewallet.domain.applicant.ApplicantDetails
import gts.drivinglicencewallet.domain.application.PhotoReference
import gts.drivinglicencewallet.domain.workflow.DataProblem
import gts.drivinglicencewallet.domain.workflow.PhotoProblem
import gts.drivinglicencewallet.domain.workflow.ReviewReason
import kotlin.time.Instant

/** The issuer's backend, which decides each processing step. */
interface IssuerBackend {

    /** The problems in the details; none means they pass. Ages are measured at [processingStartedAt]. */
    suspend fun checkData(details: ApplicantDetails, processingStartedAt: Instant): BackendResult<Set<DataProblem>>

    /** The problems with the photo; none means it passes. */
    suspend fun checkPhoto(photo: PhotoReference): BackendResult<Set<PhotoProblem>>

    /** The reasons for a manual review; none means automatic approval. Ages are measured at [processingStartedAt]. */
    suspend fun assessRisk(
        details: ApplicantDetails,
        photo: PhotoReference,
        processingStartedAt: Instant,
    ): BackendResult<Set<ReviewReason>>
}

sealed interface BackendResult<out T> {

    data class Answered<out T>(val answer: T) : BackendResult<T>

    /** A temporary failure, such as no network. A later attempt can work. */
    data object Unavailable : BackendResult<Nothing>
}
