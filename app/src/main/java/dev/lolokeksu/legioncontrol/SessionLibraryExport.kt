package dev.lolokeksu.legioncontrol

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A single selected session only. Not a restore-compatible full backup. */
object SessionLibraryExport {
    fun write(detail:LibraryDetail,stream:OutputStream) {
        val h=detail.header
        require(h.endedAtMs != null) { "Only ended records can be archived" }
        fun cell(v:Any?):String=when(v) {
            null -> ""
            is String -> CsvEscaper.cell(v)
            else -> v.toString()
        }
        ZipOutputStream(stream).use { zip ->
            // The ZIP owns the stream. Flush each entry, never close the writer between entries.
            val writer=OutputStreamWriter(zip,Charsets.UTF_8).buffered()
            fun row(vararg values:Any?) {
                writer.write(values.joinToString(",") { cell(it) })
                writer.write("\r\n")
            }
            fun entry(name:String,body:()->Unit) {
                zip.putNextEntry(ZipEntry(name))
                body()
                writer.flush()
                zip.closeEntry()
            }
            entry("session.csv") {
                writer.write("session_id,package_name,app_label,started_at_ms,ended_at_ms,end_reason,tcp_opt_in,sample_limit_reached,event_limit_reached\r\n")
                row(h.id,h.packageName,h.appLabel,h.startedAtMs,h.endedAtMs,h.endReason,
                    h.tcpOptIn,detail.sampleLimited,detail.eventLimited)
            }
            entry("samples.csv") {
                writer.write("session_id,recorded_at_ms,battery_percent,battery_temp_deci_c,battery_current_ua,thermal_status,power_saving,refresh_hz,network_type,network_validated\r\n")
                for(p in detail.points) row(h.id,p.timeMs,p.batteryPercent,p.batteryTempDeciC,
                    p.currentMicroA,p.thermalStatus,p.powerSaving,p.refreshHz,p.networkType,p.networkValidated)
            }
            entry("events.csv") {
                writer.write("session_id,recorded_at_ms,event_type,details\r\n")
                for(e in detail.events) row(h.id,e.timeMs,e.kind,e.details)
            }
            entry("README.txt") {
                writer.write("Legion Control: single session export (not a full backup).\n"+
                    "Samples: ${detail.points.size}; events: ${detail.events.size}.\n"+
                    "Sample limit reached: ${detail.sampleLimited}; event limit reached: ${detail.eventLimited}.\n"+
                    "Limits may mean partial data. Null values are empty CSV cells. Display Hz is not game FPS.\n")
            }
        }
    }
}
