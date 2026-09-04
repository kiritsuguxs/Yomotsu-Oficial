package eu.kanade.translation.detection

/** Single worker owner; a rejected client's cleanup cannot terminate another request. */
class DbnetLease<T : Any> {
    private var owner: T? = null
    fun claim(token: T): Boolean {
        if (owner == null) owner = token
        return owner == token
    }
    fun ownedBy(token: T?): Boolean = token != null && owner == token
}
