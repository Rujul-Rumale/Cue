package dev.context.app.triggers.activity

/** Small process-wide cache of the latest known activity, updated by the activity provider. */
object ActivityStateCache {
    @Volatile
    var last: String? = null
        private set

    fun update(activity: String?) {
        last = activity
    }
}