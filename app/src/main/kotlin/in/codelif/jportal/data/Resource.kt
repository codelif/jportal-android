package `in`.codelif.jportal.data

/**
 * what a screen renders: whatever data we have, how old it is, and whether a
 * refresh is in flight or just failed. data and error can both be set, that's
 * the "offline, showing yesterday's numbers" case.
 */
data class Resource<out T>(
    val data: T? = null,
    val fetchedAt: Long? = null,
    val refreshing: Boolean = false,
    val error: Throwable? = null,
    /** false until the disk cache has been looked at, nothing is worth drawing before that */
    val checked: Boolean = false,
) {
    val isEmptyLoading: Boolean get() = data == null && refreshing

    fun <R> map(f: (T) -> R): Resource<R> = Resource(data?.let(f), fetchedAt, refreshing, error, checked)

    companion object {
        fun <T> loading(): Resource<T> = Resource(refreshing = true)
    }
}
