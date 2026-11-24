package dev.lolokeksu.legioncontrol

/**
 * One immutable export snapshot owned by a ViewModel, not by an Activity.
 * Access is confined to the Android main thread. Process death discards it.
 */
internal class PendingDocument<T:Any> {
    private var snapshot:T?=null
    val hasPending:Boolean get()=snapshot!=null
    fun begin(value:T):Boolean {
        if(snapshot!=null) return false
        snapshot=value
        return true
    }
    fun take():T? = snapshot.also { snapshot=null }
}
