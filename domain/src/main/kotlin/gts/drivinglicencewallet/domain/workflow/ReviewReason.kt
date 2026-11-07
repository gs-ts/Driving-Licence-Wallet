package gts.drivinglicencewallet.domain.workflow

/** Why risk assessment sent the application to a reviewer instead of approving it. */
enum class ReviewReason {

    /** Younger than 21 on the day the application was processed. */
    Under21,

    CategoryNotB,

    /** The national ID is on the watchlist. */
    OnWatchlist,

    /** The photo passed the check, but its quality score is below the review threshold. */
    LowPhotoQuality,
}
