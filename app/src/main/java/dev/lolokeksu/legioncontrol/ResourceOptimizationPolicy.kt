package dev.lolokeksu.legioncontrol

/** One in-flight Hub refresh; explicit requests during a read coalesce into one follow-up. */
internal class HubRefreshGate {
    companion object {
        const val AUTO_COALESCE_MS=750L
        const val SEARCH_DELAY_MS=180L
    }
    private var running=false
    private var pending=false
    private var closed=false
    private var lastSuccess:Long?=null

    @Synchronized fun request(now:Long,force:Boolean):Boolean {
        if(closed)return false
        if(running){if(force)pending=true;return false}
        val last=lastSuccess
        if(!force && last!=null && now>=last && now-last<AUTO_COALESCE_MS)return false
        running=true
        return true
    }
    @Synchronized fun complete(now:Long,success:Boolean):Boolean {
        lastSuccess=if(success)now else null
        if(closed){running=false;pending=false;return false}
        if(pending){pending=false;return true}
        running=false
        return false
    }
    @Synchronized fun close(){closed=true;pending=false}
}

/** Consecutive duplicate observations only; transitions and reconnects are retained. */
internal class DistinctObservation<T:Any> {
    private var last:T?=null
    @Synchronized fun accept(next:T):Boolean {
        if(last==next)return false
        last=next
        return true
    }
    @Synchronized fun clearIf(predicate:(T)->Boolean) {
        if(last?.let(predicate)==true)last=null
    }
}
internal object MonotonicProbePolicy {
    fun due(last:Long?,now:Long,period:Long):Boolean =
        now>=0 && period>0 && (last==null || (now>=last && now-last>=period))
}
