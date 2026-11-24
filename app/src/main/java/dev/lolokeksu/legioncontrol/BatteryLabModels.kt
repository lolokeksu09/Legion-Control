package dev.lolokeksu.legioncontrol

import java.util.Locale
import java.util.UUID
import kotlin.math.abs

internal enum class BatteryLabPlan(val durationMs:Long,val label:String) {
    QUICK(120_000,"2 мин"), STANDARD(300_000,"5 мин"), LONG(600_000,"10 мин")
}
internal enum class BatteryLabEnd { COMPLETE, USER_STOP, BACKGROUND, HOT, READ_FAILED }
internal enum class BatteryLabStatus { CHARGING, DISCHARGING, FULL, NOT_CHARGING, UNKNOWN }
internal enum class BatteryLabPlug { NONE, AC, USB, WIRELESS, DOCK, OTHER, UNKNOWN }

internal data class BatteryLabPoint(
    val elapsedMs:Long,
    val percent:Int?,
    val tempDeciC:Int?,
    val currentMicroA:Int?,
    val voltageMv:Int?,
    val chargeMicroAh:Int?,
    val remainingEnergyNwh:Long?,
    val etaMs:Long?,
    val status:BatteryLabStatus,
    val plug:BatteryLabPlug,
    val thermal:Int?
) {
    val watts:Double? get() {
        val i=currentMicroA?.takeIf{it in -50_000_000..50_000_000} ?: return null
        val v=voltageMv?.takeIf{it in 500..20_000} ?: return null
        // Never reverse the OEM sign or silently treat an inconsistent reading as valid.
        if(status==BatteryLabStatus.UNKNOWN || (status==BatteryLabStatus.CHARGING && i<0) ||
            (status==BatteryLabStatus.DISCHARGING && i>0) || (plug==BatteryLabPlug.NONE && i>0)) return null
        return i.toDouble()*v/1_000_000_000.0
    }
}

internal data class BatteryLabRun(val id:String,val startedAt:Long,val plan:BatteryLabPlan,
    val end:BatteryLabEnd,val points:List<BatteryLabPoint>) {
    init {
        require(UUID.fromString(id).toString()==id && startedAt>0)
        require(points.size in 1..121 && points.all{it.elapsedMs>=0})
        require(points.zipWithNext().all{(a,b)->a.elapsedMs<b.elapsedMs})
    }
}
internal data class BatteryLabStats(
    val receivedWh:Double?,val spentWh:Double?,val coveredMs:Long,val observedMs:Long,
    val minWatts:Double?,val maxWatts:Double?,val averageWatts:Double?,
    val tempMin:Double?,val tempMax:Double?,val chargeDelta:Int?,val validPoints:Int,
    val excludedIntervals:Int
)
internal object BatteryLabPolicy {
    const val INTERVAL_MS=5000L
    const val MAX_GAP_MS=12_000L
    fun hot(p:BatteryLabPoint)=p.tempDeciC?.let{it>=450}==true || p.thermal?.let{it in 3..6}==true
    fun statistics(points:List<BatteryLabPoint>):BatteryLabStats {
        var incoming=0.0
        var outgoing=0.0
        var covered=0L
        var excluded=0
        points.zipWithNext().forEach{(a,b)->
            val aw=a.watts;val bw=b.watts
            val dt=b.elapsedMs-a.elapsedMs
            if(aw==null || bw==null || dt !in 1..MAX_GAP_MS || a.plug!=b.plug ||
                a.plug==BatteryLabPlug.UNKNOWN || a.status!=b.status ||
                (aw<0 && bw>0) || (aw>0 && bw<0)) { excluded++;return@forEach }
            val wh=(aw+bw)*0.5*dt/3_600_000.0
            if(wh>=0) incoming+=wh else outgoing-=wh
            covered+=dt
        }
        val observed=if(points.size<2) 0L else (points.last().elapsedMs-points.first().elapsedMs).coerceAtLeast(0L)
        val power=points.mapNotNull{it.watts}
        val temp=points.mapNotNull{it.tempDeciC?.takeIf{v->v in -400..1200}?.div(10.0)}
        val first=points.firstOrNull()?.percent?.takeIf{it in 0..100}
        val last=points.lastOrNull()?.percent?.takeIf{it in 0..100}
        return BatteryLabStats(incoming.takeIf{covered>0},outgoing.takeIf{covered>0},covered,observed,
            power.minOrNull(),power.maxOrNull(),if(covered>0) (incoming-outgoing)*3_600_000.0/covered else null,
            temp.minOrNull(),temp.maxOrNull(),if(first!=null && last!=null) last-first else null,power.size,excluded)
    }
    fun comparable(a:BatteryLabRun,b:BatteryLabRun):Boolean {
        if(a.id==b.id || a.plan!=b.plan || a.end!=BatteryLabEnd.COMPLETE || b.end!=BatteryLabEnd.COMPLETE) return false
        fun uniform(r:BatteryLabRun):Boolean {
            val p=r.points.first()
            val stats=statistics(r.points)
            return p.plug!=BatteryLabPlug.UNKNOWN && p.status in setOf(BatteryLabStatus.CHARGING,BatteryLabStatus.DISCHARGING) &&
                r.points.all{it.plug==p.plug && it.status==p.status} && stats.observedMs>=r.plan.durationMs-INTERVAL_MS &&
                stats.coveredMs.toDouble()/stats.observedMs>=0.8
        }
        val x=a.points.first();val y=b.points.first()
        return uniform(a)&&uniform(b)&&x.status==y.status&&x.percent!=null&&y.percent!=null&&
            x.tempDeciC!=null&&y.tempDeciC!=null&&abs(x.percent-y.percent)<=5&&abs(x.tempDeciC-y.tempDeciC)<=30
    }
}
internal object BatteryLabReport {
    fun n(v:Double?,digits:Int=2)=v?.let{String.format(Locale.US,"%.${digits}f",it)} ?: "н/д"
    fun report(points:List<BatteryLabPoint>):String {
        val s=BatteryLabPolicy.statistics(points);val p=points.lastOrNull()
        return buildString {
            append("Заряд: ").append(p?.percent?.let{"$it %"} ?: "н/д").append(" · ").append(p?.plug ?: "UNKNOWN").append(" · ").append(p?.status ?: "UNKNOWN").append('\n')
            append("Мощность батареи: ").append(n(p?.watts)).append(" Вт · ток: ").append(n(p?.currentMicroA?.div(1000.0),0)).append(" мА\n")
            append("Напряжение: ").append(n(p?.voltageMv?.div(1000.0),3)).append(" В · температура: ").append(n(p?.tempDeciC?.div(10.0),1)).append(" °C\n")
            append("До полной зарядки (Android): ").append(p?.etaMs?.let{"${(it+59_999)/60_000} мин"} ?: "н/д").append('\n')
            append("Остаток заряда (датчик): ").append(n(p?.chargeMicroAh?.div(1000.0),0)).append(" мА·ч · энергия: ").append(n(p?.remainingEnergyNwh?.div(1_000_000_000.0))).append(" Вт·ч\n")
            append("Получено / отдано: ").append(n(s.receivedWh,3)).append(" / ").append(n(s.spentWh,3)).append(" Вт·ч\n")
            append("Средняя мощность: ").append(n(s.averageWatts)).append(" Вт · мин/макс: ").append(n(s.minWatts)).append(" / ").append(n(s.maxWatts)).append(" Вт\n")
            append("Температура мин/макс: ").append(n(s.tempMin,1)).append(" / ").append(n(s.tempMax,1)).append(" °C · Δ заряд: ").append(s.chargeDelta ?: "н/д").append(" п.п.\n")
            append("Покрытие интеграла: ").append(s.coveredMs/1000).append('/').append(s.observedMs/1000).append(" с · исключено интервалов: ").append(s.excludedIntervals).append('\n')
            if(p?.currentMicroA!=null && p.voltageMv!=null && p.watts==null) append("Показания направления противоречат состоянию или неизвестны.\n")
            append("Расчёт по Android: не мощность адаптера, не ёмкость батареи и не расход отдельной игры.")
        }
    }
    fun compare(a:BatteryLabRun,b:BatteryLabRun):String {
        require(BatteryLabPolicy.comparable(a,b))
        val x=BatteryLabPolicy.statistics(a.points);val y=BatteryLabPolicy.statistics(b.points)
        fun diff(v:Double?,w:Double?)=if(v!=null&&w!=null) w-v else null
        return "B − A: средняя мощность "+n(diff(x.averageWatts,y.averageWatts))+" Вт; энергия вход "+n(diff(x.receivedWh,y.receivedWh),3)+
            " Вт·ч; энергия выход "+n(diff(x.spentWh,y.spentWh),3)+" Вт·ч; максимальная температура "+n(diff(x.tempMax,y.tempMax),1)+
            " °C.\nУсловия близки по начальным заряду/температуре и режиму, но нагрузка и адаптер не контролируются. Причинный вывод невозможен.\n\nA:\n"+
            report(a.points)+"\n\nB:\n"+report(b.points)
    }
    fun csv(run:BatteryLabRun):String=buildString {
        append("elapsed_ms,battery_percent,temp_deci_c,current_micro_a,voltage_mv,charge_micro_ah,remaining_energy_nwh,eta_ms,status,plug,thermal,battery_watts\r\n")
        run.points.forEach{p->append(listOf(p.elapsedMs,p.percent,p.tempDeciC,p.currentMicroA,p.voltageMv,p.chargeMicroAh,
            p.remainingEnergyNwh,p.etaMs,p.status,p.plug,p.thermal,p.watts).joinToString(","){it?.toString().orEmpty()}).append("\r\n")}
    }
}
