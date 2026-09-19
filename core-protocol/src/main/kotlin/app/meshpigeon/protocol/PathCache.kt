package app.meshpigeon.protocol

/**
 * Per-destination path cache with the reduced meshcore-open policy (11 §2.3):
 * direct-if-known else flood; on send exhaustion the learned path is CLEARED
 * automatically (retry goes via flood). No scoring weights, no rotation.
 */
data class PathEntry(
    val destPubKeyHash: Int, // 1-byte node hash
    val path: Path,
    val lastUsedAt: Long,
    val successCount: Int,
    val failCount: Int,
)

class PathCache(private val clock: () -> Long) {
    private val paths = HashMap<Int, PathEntry>()

    /** The route to use for `destHash`, or null → flood. */
    fun routeFor(destHash: Int): Path? = paths[destHash]?.path

    fun learn(destHash: Int, path: Path) {
        paths[destHash] = PathEntry(destHash, path, clock(), successCount = 0, failCount = 0)
    }

    /** On ACK: success; optionally upgrade the learned path. */
    fun onAck(destHash: Int, usedPath: Path?) {
        val e = paths[destHash] ?: PathEntry(destHash, usedPath ?: return, clock(), 0, 0)
        paths[destHash] = e.copy(lastUsedAt = clock(), successCount = e.successCount + 1)
    }

    /**
     * On send exhaustion: clear the failed path (11 §2.3 — automatic, so
     * the next send floods and can re-learn).
     */
    fun onExhausted(destHash: Int) {
        val e = paths[destHash] ?: return
        paths[destHash] = e.copy(failCount = e.failCount + 1, path = emptyPath())
    }

    fun isEmptyPath(destHash: Int): Boolean =
        paths[destHash]?.path?.hopCount == 0

    /** Advanced screen data: hops, last used, success count (08 §5). */
    fun entry(destHash: Int): PathEntry? = paths[destHash]

    private fun emptyPath(): Path = Path(PathHashSize.THREE, emptyList())
}
