package dev.lolokeksu.legioncontrol

import java.util.Locale

enum class CompareMeasure(val label:String,val unit:String) {
    PEAK_TEMP("Пик температуры батареи","°C"),
    TEMP_CHANGE("Изменение температуры батареи","°C"),
    BATTERY_DROP("Снижение заряда","п.п."),
    DISPLAY("Медиана частоты дисплея","Гц"),
    SEVERE("Thermal Status ≥ SEVERE","% замеров"),
    POWER("Энергосбережение","% замеров"),
    NETWORK("Сеть не подтверждена Android","% замеров")
}

data class CompareValue(val value:Double?,val known:Int,val total:Int) {
    val sufficient:Boolean get()=value!=null && known>=3 && known.toDouble()/total>=0.70
}
data class CompareWindow(
    val header:GameSessionHeader,val minutes:Int,val points:Int,val coverage:Double,
    val gaps:Int,val duplicateOrOutside:Int,val issues:List<String>,
    val metrics:Map<CompareMeasure,CompareValue>
)
data class ComparePair(val a:CompareWindow,val b:CompareWindow) {
    val comparable:Boolean get()=a.issues.isEmpty() && b.issues.isEmpty()
    fun delta(m:CompareMeasure):Double? {
        val first=a.metrics.getValue(m)
        val second=b.metrics.getValue(m)
        return if(comparable && first.sufficient && second.sufficient)
            second.value!!-first.value!! else null
    }
}

/** Equal elapsed windows from the beginning of completed sessions, with no interpolation. */
object SessionCompareAnalysis {
    val windows=listOf(5,10,15,30)
    const val MAX_POINTS=2000
    private const val GAP=45000L

    fun analyze(h:GameSessionHeader,raw:List<GameTelemetryPoint>,minutes:Int,
        limited:Boolean=false):CompareWindow {
        require(minutes in windows)
        require(h.startedAtMs>0 && h.startedAtMs<=Long.MAX_VALUE-minutes*60000L)
        val duration=minutes*60000L
        val end=h.startedAtMs+duration
        val actualEnd=h.endedAtMs?.coerceAtMost(end) ?: end
        val bounded=raw.take(MAX_POINTS)
        val inWindow=bounded.filter { it.timeMs in h.startedAtMs..actualEnd }
        val points=inWindow.sortedBy { it.timeMs }.distinctBy { it.timeMs }
        val gaps=points.zipWithNext().count { (a,b)-> b.timeMs-a.timeMs>GAP }
        val observed=points.zipWithNext().sumOf { (a,b)->
            (b.timeMs-a.timeMs).takeIf { it in 1..GAP } ?: 0L
        }
        val coverage=(observed.toDouble()/duration).coerceIn(0.0,1.0)
        val issues=buildList {
            if(h.endedAtMs==null || h.endReason!="USER_STOP") add("Нет штатного завершения записи.")
            if(h.endedAtMs==null || h.endedAtMs<end) add("Запись короче выбранного окна.")
            if(limited || raw.size>MAX_POINTS) add("Достигнут лимит чтения.")
            if(points.size<6) add("Менее 6 измерений.")
            if(coverage<0.70) add("Покрытие окна менее 70%.")
            if(gaps>0) add("Есть разрывы более 45 секунд.")
            if(points.firstOrNull()?.let { it.timeMs-h.startedAtMs<=GAP }!=true ||
                points.lastOrNull()?.let { end-it.timeMs<=GAP }!=true)
                add("Начало или конец окна не покрыты.")
        }
        val temps=points.mapNotNull { it.batteryTempDeciC?.takeIf { t->t in -400..1200 } }
        val batteries=points.mapNotNull { it.batteryPercent?.takeIf { b->b in 0..100 } }
        val refresh=points.mapNotNull { it.refreshHz?.takeIf { f-> f.isFinite() && f in 1f..500f } }.sorted()
        fun temp(p:GameTelemetryPoint?)=p?.batteryTempDeciC?.takeIf { it in -400..1200 }
        fun battery(p:GameTelemetryPoint?)=p?.batteryPercent?.takeIf { it in 0..100 }
        fun endpoints(first:Int?,last:Int?,factor:Double):Double? =
            if(first==null || last==null) null else (last-first)*factor
        fun ratio(values:List<Boolean>):CompareValue = CompareValue(
            if(values.isEmpty()) null else 100.0*values.count { it }/values.size,values.size,points.size)
        fun value(v:Double?,known:Int)=CompareValue(v,known,points.size)
        val metrics=mapOf(
            CompareMeasure.PEAK_TEMP to value(temps.maxOrNull()?.div(10.0),temps.size),
            CompareMeasure.TEMP_CHANGE to value(endpoints(temp(points.firstOrNull()),temp(points.lastOrNull()),0.1),temps.size),
            CompareMeasure.BATTERY_DROP to value(endpoints(battery(points.firstOrNull()),battery(points.lastOrNull()),-1.0),batteries.size),
            CompareMeasure.DISPLAY to value(if(refresh.isEmpty()) null else
                (refresh[(refresh.size-1)/2].toDouble()+refresh[refresh.size/2])/2,refresh.size),
            CompareMeasure.SEVERE to ratio(points.mapNotNull { it.thermalStatus?.takeIf { t->t in 0..6 }?.let { t->t>=3 } }),
            CompareMeasure.POWER to ratio(points.mapNotNull { it.powerSaving }),
            CompareMeasure.NETWORK to ratio(points.mapNotNull { it.networkValidated?.let { v->!v } })
        )
        return CompareWindow(h,minutes,points.size,coverage,gaps,inWindow.size-points.size+
            bounded.count { it.timeMs<h.startedAtMs },issues,metrics)
    }
    fun compare(a:CompareWindow,b:CompareWindow):ComparePair {
        require(a.header.packageName==b.header.packageName && a.header.id!=b.header.id)
        require(a.minutes==b.minutes)
        return ComparePair(a,b)
    }
    fun format(v:Double?):String=v?.takeIf { it.isFinite() }?.let { String.format(Locale.US,"%.2f",it) } ?: "н/д"
    fun report(pair:ComparePair):String=buildString {
        append("SESSION COMPARE · ").append(pair.a.header.packageName).append('\n')
        append("Первые ").append(pair.a.minutes).append(" мин каждой записи.\n")
        for((label,w) in listOf("A" to pair.a,"B" to pair.b)) {
            append(label).append(": ").append(w.header.appLabel).append(" · ").append(w.header.startedAtMs)
                .append(" · точек ").append(w.points).append(" · покрытие ").append(format(w.coverage*100)).append("%\n")
            w.issues.forEach { append("• ").append(it).append('\n') }
        }
        for(m in CompareMeasure.entries) {
            val a=pair.a.metrics.getValue(m); val b=pair.b.metrics.getValue(m)
            append(m.label).append(": A ").append(format(a.value)).append(" (").append(a.known).append('/').append(a.total)
                .append("), B ").append(format(b.value)).append(" (").append(b.known).append('/').append(b.total)
                .append("), Δ B−A ").append(format(pair.delta(m))).append(' ').append(m.unit).append('\n')
        }
        append("Доля замеров не равна доле времени. Заряд может расти при зарядке; условия нагрузки неизвестны. ")
        append("Дельты скрываются при неполном окне или недостатке значений. Гц дисплея не FPS, валидация сети не ping.")
    }
    fun csv(pair:ComparePair):String=buildString {
        append("package,session_a,session_b,window_minutes,metric,unit,a_value,a_known,a_total,b_value,b_known,b_total,delta_b_minus_a,a_coverage,b_coverage,a_issues,b_issues\r\n")
        for(m in CompareMeasure.entries) {
            val a=pair.a.metrics.getValue(m);val b=pair.b.metrics.getValue(m)
            fun num(v:Double?)=v?.let(::format).orEmpty()
            append(listOf(CsvEscaper.cell(pair.a.header.packageName),CsvEscaper.cell(pair.a.header.id),
                CsvEscaper.cell(pair.b.header.id),pair.a.minutes.toString(),CsvEscaper.cell(m.name),
                CsvEscaper.cell(m.unit),num(a.value),a.known.toString(),a.total.toString(),
                num(b.value),b.known.toString(),b.total.toString(),num(pair.delta(m)),
                format(pair.a.coverage),format(pair.b.coverage),
                CsvEscaper.cell(pair.a.issues.joinToString(" | ")),
                CsvEscaper.cell(pair.b.issues.joinToString(" | "))).joinToString(",")).append("\r\n")
        }
    }
}
