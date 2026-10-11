package dev.context.app.triggers.celltower

/** Pure cell-identity helpers: parsing, keys and fingerprint matching. Unit-tested. */
object CellFingerprint {

    /**
     * Stable key for a cell identity snapshot: "mcc-mnc-area-id".
     * Returns null when the identity has no usable cell id yet.
     */
    fun key(mcc: String?, mnc: String?, area: String?, cellId: String?): String? {
        if (cellId.isNullOrBlank() || cellId == "2147483647" || cellId == "-1") return null
        val mccN = mcc?.takeIf { it.isNotBlank() } ?: "?"
        val mncN = mnc?.takeIf { it.isNotBlank() } ?: "?"
        val areaN = area?.takeIf { it.isNotBlank() } ?: "?"
        return "$mccN-$mncN-$areaN-$cellId"
    }

    /** True when any currently visible cell belongs to the place fingerprint. */
    fun matches(fingerprint: Collection<String>, visible: Collection<String>): Boolean =
        fingerprint.isNotEmpty() && visible.any { it in fingerprint }

    /** Merges newly observed cells into a fingerprint, keeping insertion order. */
    fun merge(fingerprint: List<String>, observed: Collection<String>): List<String> {
        if (observed.isEmpty()) return fingerprint
        val merged = fingerprint.toMutableList()
        for (cell in observed) {
            if (cell.isNotBlank() && cell !in merged) merged.add(cell)
        }
        return merged
    }
}
