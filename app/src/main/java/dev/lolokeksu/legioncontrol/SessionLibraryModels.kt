package dev.lolokeksu.legioncontrol

enum class LibraryStatus(val label:String) {
    ALL("Все"), FINISHED("Завершённые пользователем"),
    INTERRUPTED("Прерванные"), OPEN("Без записи завершения")
}
enum class LibraryPeriod(val label:String,val days:Int?) {
    ALL("Весь период",null), WEEK("7 дней",7), MONTH("30 дней",30), QUARTER("90 дней",90)
}
data class LibraryFilter(val packageName:String?=null,val period:LibraryPeriod=LibraryPeriod.ALL,
    val status:LibraryStatus=LibraryStatus.ALL)
data class LibraryCursor(val startedAtMs:Long,val id:String)
data class LibraryPage(val rows:List<GameSessionHeader>,val next:LibraryCursor?)
data class LibraryQuery(val where:String,val args:List<String>)

object SessionLibraryPolicy {
    const val PAGE_SIZE=20
    const val MAX_SAMPLES=10000
    const val MAX_EVENTS=2000
    fun query(filter:LibraryFilter,now:Long,cursor:LibraryCursor?):LibraryQuery {
        require(now>0)
        val clauses=mutableListOf<String>()
        val args=mutableListOf<String>()
        filter.packageName?.let {
            require(GameLaunchProfilePolicy.validPackage(it))
            clauses+="package_name=?";args+=it
        }
        filter.period.days?.let {
            clauses+="started_at_ms>=? AND started_at_ms<=?"
            args+=(now-it*86400000L).coerceAtLeast(0L).toString();args+=now.toString()
        }
        when(filter.status) {
            LibraryStatus.ALL -> Unit
            LibraryStatus.FINISHED -> clauses+="ended_at_ms IS NOT NULL AND end_reason='USER_STOP'"
            LibraryStatus.INTERRUPTED -> clauses+="ended_at_ms IS NOT NULL AND (end_reason IS NULL OR end_reason<>'USER_STOP')"
            LibraryStatus.OPEN -> clauses+="ended_at_ms IS NULL"
        }
        cursor?.let {
            require(it.startedAtMs>0 && it.id.isNotBlank())
            clauses+="(started_at_ms<? OR (started_at_ms=? AND session_id<?))"
            args+=it.startedAtMs.toString();args+=it.startedAtMs.toString();args+=it.id
        }
        return LibraryQuery(if(clauses.isEmpty())"" else " WHERE "+clauses.joinToString(" AND "),args)
    }
    fun page(rows:List<GameSessionHeader>):LibraryPage {
        val page=rows.take(PAGE_SIZE)
        return LibraryPage(page,if(rows.size>PAGE_SIZE)page.last().let { LibraryCursor(it.startedAtMs,it.id) } else null)
    }
}
data class LibraryDetail(val header:GameSessionHeader,val points:List<GameTelemetryPoint>,
    val events:List<GameSessionEvent>,val sampleLimited:Boolean,val eventLimited:Boolean)
