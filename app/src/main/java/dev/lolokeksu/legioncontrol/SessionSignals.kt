package dev.lolokeksu.legioncontrol

import java.util.Locale

/** Local-only event correlation. Unknown values and gaps never imply a recovery. */
data class SessionSignal(val timeMs:Long,val kind:String,val source:String,val details:String)
data class SessionSignalsReport(
    val session:GameSessionHeader,
    val pointCount:Int,val persistedEventCount:Int,val gapsOver45s:Int,
    val measuredContinuityMs:Long,val unknownTemperatureCount:Int,
    val unknownThermalCount:Int,val unknownNetworkCount:Int,
    val firstBattery:Int?,val lastBattery:Int?,val peakBatteryTempDeciC:Int?,
    val sampleLimitHit:Boolean,val eventLimitHit:Boolean,
    val omittedSignals:Int,val signals:List<SessionSignal>
)

object SessionSignals {
    const val MAX_SAMPLES=10_000
    const val MAX_EVENTS=2_000
    const val MAX_SIGNALS=600
    private const val GAP_MS=45_000L
    fun normalizeMarker(raw:String):String? =
        raw.trim().replace(Regex("[\u0000-\u001F\u007F]+")," ")
            .replace(Regex(" {2,}")," ").trim().take(120).ifBlank { null }

    fun analyze(header:GameSessionHeader, recorded:List<GameTelemetryPoint>,
        events:List<GameSessionEvent>,sampleLimitHit:Boolean=false,
        eventLimitHit:Boolean=false):SessionSignalsReport {
        val samples=recorded.sortedBy { it.timeMs }
        val raw=ArrayList<SessionSignal>()
        var gaps=0
        var continuityMs=0L
        var prior:GameTelemetryPoint?=null
        fun emit(t:Long,kind:String,message:String) {
            raw.add(SessionSignal(t,kind,"android_public_api",message))
        }
        for (p in samples) {
            val prev=prior
            val delta=if(prev==null)0L else p.timeMs-prev.timeMs
            val adjacent=prev!=null && delta in 1..GAP_MS
            if(prev!=null && delta>GAP_MS) {
                gaps++
                emit(p.timeMs,"SAMPLE_GAP","Нет непрерывных измерений: пауза "+(delta/1000)+" с")
            }
            if(adjacent) continuityMs+=delta
            val thermal=p.thermalStatus?.takeIf { it in 0..6 }
            val oldThermal=prev?.thermalStatus?.takeIf { it in 0..6 }
            if(thermal!=null && thermal>=3 && (!adjacent || oldThermal==null || oldThermal<3))
                emit(p.timeMs,"THERMAL_SEVERE","Android Thermal Status "+thermal+" (SEVERE или выше)")
            if(adjacent && thermal!=null && thermal<3 && oldThermal!=null && oldThermal>=3)
                emit(p.timeMs,"THERMAL_RECOVERED","Android Thermal Status "+thermal+" (ниже SEVERE)")
            val temp=p.batteryTempDeciC?.takeIf { it in -400..1200 }
            val oldTemp=prev?.batteryTempDeciC?.takeIf { it in -400..1200 }
            if(temp!=null && temp>=450 && (!adjacent || oldTemp==null || oldTemp<450))
                emit(p.timeMs,"BATTERY_TEMP_HIGH","Батарея "+formatDeci(temp)+" °C (≥45 °C)")
            if(adjacent && temp!=null && temp<450 && oldTemp!=null && oldTemp>=450)
                emit(p.timeMs,"BATTERY_TEMP_RECOVERED","Батарея "+formatDeci(temp)+" °C (ниже 45 °C)")
            if(p.powerSaving==true && (!adjacent || prev?.powerSaving!=true))
                emit(p.timeMs,"POWER_SAVING","Android: энергосбережение активно")
            if(adjacent && prev?.powerSaving==true && p.powerSaving==false)
                emit(p.timeMs,"POWER_SAVING_OFF","Android: энергосбережение неактивно")
            if(p.networkValidated==false && (!adjacent || prev?.networkValidated!=false))
                emit(p.timeMs,"NETWORK_UNVALIDATED","Сеть не подтверждена Android, не ping игры")
            if(adjacent && prev?.networkValidated==false && p.networkValidated==true)
                emit(p.timeMs,"NETWORK_VALIDATED","Сеть снова подтверждена Android")
            if(adjacent && prev!=null && p.networkType!="UNKNOWN" &&
                prev.networkType!="UNKNOWN" && p.networkType!=prev.networkType)
                emit(p.timeMs,"NETWORK_TRANSPORT",
                    "Транспорт: "+prev.networkType+" → "+p.networkType)
            val hz=p.refreshHz?.takeIf { it.isFinite() && it in 1f..500f }
            val oldHz=prev?.refreshHz?.takeIf { it.isFinite() && it in 1f..500f }
            if(adjacent && hz!=null && oldHz!=null && kotlin.math.abs(hz-oldHz)>=15f)
                emit(p.timeMs,"DISPLAY_REFRESH",
                    "Дисплей: "+String.format(Locale.US,"%.1f",oldHz)+" → "+
                    String.format(Locale.US,"%.1f",hz)+" Гц (не FPS игры)")
            prior=p
        }
        for(e in events) raw.add(SessionSignal(e.timeMs,e.kind,"saved_game_event",e.details))
        val ordered=raw.sortedWith(compareBy<SessionSignal>{it.timeMs}
            .thenBy{it.source}.thenBy{it.kind})
        val retained=ordered.takeLast(MAX_SIGNALS)
        val charge=samples.mapNotNull{it.batteryPercent?.takeIf{v->v in 0..100}}
        val peak=samples.mapNotNull{it.batteryTempDeciC?.takeIf{v->v in -400..1200}}.maxOrNull()
        return SessionSignalsReport(header,samples.size,events.size,gaps,continuityMs,
            samples.count{it.batteryTempDeciC==null || it.batteryTempDeciC !in -400..1200},
            samples.count{it.thermalStatus==null || it.thermalStatus !in 0..6},
            samples.count{it.networkValidated==null},
            charge.firstOrNull(),charge.lastOrNull(),peak,sampleLimitHit,eventLimitHit,
            ordered.size-retained.size,retained)
    }

    fun render(r:SessionSignalsReport):String=buildString {
        append("SESSION SIGNALS · ").append(r.session.appLabel).append('\n')
        append("Пакет: ").append(r.session.packageName).append('\n')
        append("Замеров: ").append(r.pointCount).append(" · событий SQLite: ")
            .append(r.persistedEventCount).append('\n')
        append("Связные интервалы: ").append(r.measuredContinuityMs/1000)
            .append(" с · пропуски >45 с: ").append(r.gapsOver45s).append('\n')
        append("Неизвестные поля: температура ").append(r.unknownTemperatureCount)
            .append(", Thermal ").append(r.unknownThermalCount)
            .append(", сеть ").append(r.unknownNetworkCount).append('\n')
        append("Заряд: ").append(r.firstBattery?.toString()?.plus("%")?:"н/д")
            .append(" → ").append(r.lastBattery?.toString()?.plus("%")?:"н/д")
            .append(" · пик температуры батареи: ")
            .append(r.peakBatteryTempDeciC?.let{formatDeci(it)+" °C"}?:"н/д").append('\n')
        if(r.sampleLimitHit||r.eventLimitHit||r.omittedSignals>0) {
            append("ОГРАНИЧЕНИЕ: ")
            if(r.sampleLimitHit)append("первые 10000 замеров; ")
            if(r.eventLimitHit)append("первые 2000 событий; ")
            if(r.omittedSignals>0)append("скрыто старых сигналов: ").append(r.omittedSignals)
            append('\n')
        }
        if(r.signals.isEmpty())append("Событий и переходов не выявлено.\n")
        else for(signal in r.signals.takeLast(45)) {
            append(signal.timeMs).append(" · ").append(signal.kind).append(" · ")
                .append(signal.details.replace("\n"," ")).append('\n')
        }
        append("Привязка к игре — метка пользователя, не причина лагов. ")
        append("Термометр — батарея, Гц — дисплей, не FPS. Пропуски не интерполируются.")
    }

    fun csv(r:SessionSignalsReport):String=buildString {
        append("session_id,recorded_at_ms,signal_kind,source,details\n")
        for(s in r.signals) {
            append(csvCell(r.session.id)).append(',').append(s.timeMs).append(',')
                .append(csvCell(s.kind)).append(',').append(csvCell(s.source)).append(',')
                .append(csvCell(s.details)).append('\n')
        }
    }
    private fun csvCell(text:String):String {
        val safe=if(text.firstOrNull() in listOf('=','+','-','@','\t','\r'))"'"+text else text
        return "\""+safe.replace("\"","\"\"")+"\""
    }
    private fun formatDeci(t:Int)=String.format(Locale.US,"%.1f",t/10.0)
}
