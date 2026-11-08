package gts.drivinglicencewallet.simulatedbackend

import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The issuer's settings. The defaults are for the demo; tests set their own.
 *
 * Quality scores go from 0 to 100. A photo under [minQualityScore] fails the photo check,
 * and one under [reviewQualityScore] goes to a reviewer.
 */
data class IssuerConfig(
    /** National IDs that always go to a reviewer. Letter case doesn't matter. */
    val watchlist: Set<String> = setOf(element = "XX000000"),
    /** The smallest photo file that passes the photo check. */
    val minPhotoBytes: Int = 20_000,
    val minQualityScore: Int = 40,
    val reviewQualityScore: Int = 70,
    /** The issuer's time zone. The age rules use its local date. */
    val timeZone: ZoneId = ZoneId.of("Europe/Brussels"),
    /** How long each call takes, like a real network. */
    val responseDelay: Duration = 2.seconds,
)
