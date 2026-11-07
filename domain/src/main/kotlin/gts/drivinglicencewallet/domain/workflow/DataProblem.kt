package gts.drivinglicencewallet.domain.workflow

import gts.drivinglicencewallet.domain.applicant.ApplicantField

/** Why the applicant's details failed data validation. */
sealed interface DataProblem {

    data class MissingField(val field: ApplicantField) : DataProblem

    data object InvalidEmail : DataProblem

    /** Younger than 18 on the day the application was processed. */
    data object Under18 : DataProblem
}
