package gts.drivinglicencewallet.data.database.entities

import kotlin.enums.enumEntries

/** The constant named [name], or null if there's none. */
internal inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    enumEntries<T>().firstOrNull { constant -> constant.name == name }

/** Maps every element, or returns null if [transform] returns null for one of them. */
internal inline fun <T, R : Any> List<T>.mapAllOrNull(transform: (T) -> R?): List<R>? {
    val mapped = map(transform)
    return if (null in mapped) null else mapped.filterNotNull()
}
