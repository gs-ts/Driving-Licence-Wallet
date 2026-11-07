package gts.drivinglicencewallet.domain.workflow

/** Why the photo failed the photo check. */
enum class PhotoProblem {
    Missing,
    UnsupportedType,

    /** The file size is below the minimum. */
    TooSmall,

    /** The quality score is below the minimum, so the photo can't be used. */
    LowQuality,
}
