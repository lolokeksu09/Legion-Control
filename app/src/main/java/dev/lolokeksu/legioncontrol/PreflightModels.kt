package dev.lolokeksu.legioncontrol

import java.util.Locale

/** Per-game expectations, not requests to modify Android settings. */
data class PreflightPreset(
    val targetHz: Int = 90,
    val minBattery: Int = 20,
    val maxBatteryTempC: Int = 42,
    val minAvailableRamGiB: Int = 2,
    val requireValidatedNetwork: Boolean = true
) {
    init {
        require(targetHz in listOf(0,60,90,120,144,165))
        require(minBattery in 5..80)
        require(maxBatteryTempC in 35..45)
        require(minAvailableRamGiB in 0..8)
    }
    fun encode(): String = listOf("v1",targetHz,minBattery,maxBatteryTempC,
        minAvailableRamGiB,if(requireValidatedNetwork) 1 else 0).joinToString("|")
    companion object {
        fun decode(value: String?): PreflightPreset? = runCatching {
            val x = requireNotNull(value).split('|')
            require(x.size == 6 && x[0] == "v1" && x[5] in listOf("0","1"))
            PreflightPreset(x[1].toInt(),x[2].toInt(),x[3].toInt(),x[4].toInt(),x[5]=="1")
        }.getOrNull()
    }
}

enum class PreflightLevel(val label: String) {
    PASS("Норма"), WARNING("Внимание"), CRITICAL("Ограничение"),
    UNKNOWN("Нет данных"), SKIPPED("Не требуется"), INFO("Информация")
}
data class PreflightFinding(val key: String, val title: String, val level: PreflightLevel, val detail: String)

data class PreflightReading(
    val battery: Int? = null,
    val temperatureDeciC: Int? = null,
    val thermal: Int? = null,
    val powerSaving: Boolean? = null,
    val displayHz: Float? = null,
    val supportedMaxHz: Float? = null,
    val availableRamBytes: Long? = null,
    val androidLowRam: Boolean? = null,
    val network: String? = null,
    val validatedNetwork: Boolean? = null,
    val launchable: Boolean? = null,
    val captureBusy: Boolean? = null,
    val sessionActive: Boolean? = null,
    val pendingProfile: String? = null,
    val pendingProfileKnown: Boolean = false
)

object PreflightRules {
    const val FRESH_MS = 60_000L
    private const val GIB = 1073741824L
    fun fresh(checkedAtElapsed: Long, nowElapsed: Long): Boolean =
        checkedAtElapsed >= 0 && nowElapsed >= checkedAtElapsed &&
            nowElapsed - checkedAtElapsed <= FRESH_MS

    fun evaluate(p: PreflightPreset, r: PreflightReading): List<PreflightFinding> = buildList {
        fun add(key:String,title:String,level:PreflightLevel,detail:String) {
            add(PreflightFinding(key,title,level,detail))
        }
        val battery = r.battery?.takeIf { it in 0..100 }
        val temp = r.temperatureDeciC?.takeIf { it in -400..1200 }
        val thermal = r.thermal?.takeIf { it in 0..6 }
        val hz = r.displayHz?.takeIf { it.isFinite() && it > 0f && it <= 1000f }
        val maxHz = r.supportedMaxHz?.takeIf { it.isFinite() && it > 0f && it <= 1000f }
        val ram = r.availableRamBytes?.takeIf { it >= 0 }
        fun fmt(x:Double) = String.format(Locale.US,"%.1f",x)

        add("thermal","Тепловой статус Android", when {
            thermal == null -> PreflightLevel.UNKNOWN
            thermal >= 3 -> PreflightLevel.CRITICAL
            thermal == 2 -> PreflightLevel.WARNING
            else -> PreflightLevel.PASS
        }, when {
            thermal == null -> "Android не предоставил показатель."
            thermal >= 3 -> "Статус $thermal (SEVERE или выше). Отложите нагрузку и дайте устройству остыть."
            else -> "Системный статус: $thermal из 6."
        })
        add("temperature","Температура батареи", when {
            temp == null -> PreflightLevel.UNKNOWN
            temp >= 450 -> PreflightLevel.CRITICAL
            temp >= p.maxBatteryTempC * 10 -> PreflightLevel.WARNING
            else -> PreflightLevel.PASS
        }, "Измерение: " + (temp?.let { fmt(it / 10.0) + " °C" } ?: "н/д") +
            "; порог игры: ${p.maxBatteryTempC} °C. Это не температура CPU/GPU.")
        add("battery","Заряд батареи", when {
            battery == null -> PreflightLevel.UNKNOWN
            battery < p.minBattery -> PreflightLevel.WARNING
            else -> PreflightLevel.PASS
        }, "Сейчас: " + (battery?.let { "$it%" } ?: "н/д") + "; минимум: ${p.minBattery}%.")
        add("power","Энергосбережение", when(r.powerSaving) {
            true -> PreflightLevel.WARNING
            false -> PreflightLevel.PASS
            null -> PreflightLevel.UNKNOWN
        }, when(r.powerSaving) {
            true -> "Включено. Android или прошивка могут ограничивать частоту дисплея и производительность."
            false -> "Выключено."
            null -> "Состояние недоступно."
        })
        add("display","Частота дисплея", when {
            p.targetHz == 0 -> PreflightLevel.SKIPPED
            hz == null -> PreflightLevel.UNKNOWN
            hz + 0.5f < p.targetHz -> PreflightLevel.WARNING
            else -> PreflightLevel.PASS
        }, buildString {
            append("Сейчас: ").append(hz?.let { fmt(it.toDouble())+" Гц" } ?: "н/д")
            append("; цель: ").append(if(p.targetHz==0) "не задана" else "${p.targetHz} Гц")
            append("; максимум режимов Android: ").append(maxHz?.let { fmt(it.toDouble())+" Гц" } ?: "н/д")
            if (maxHz != null && p.targetHz > 0 && maxHz + 0.5f < p.targetHz)
                append(". Android не сообщил режим с такой частотой")
            append(". Это частота дисплея, не FPS игры.")
        })
        add("ram","Доступная RAM", when {
            r.androidLowRam == true -> PreflightLevel.WARNING
            p.minAvailableRamGiB == 0 -> PreflightLevel.SKIPPED
            ram == null -> PreflightLevel.UNKNOWN
            ram < p.minAvailableRamGiB * GIB -> PreflightLevel.WARNING
            else -> PreflightLevel.PASS
        }, "Сейчас: " + (ram?.let { fmt(it.toDouble()/GIB) + " ГиБ" } ?: "н/д") +
            "; минимум: ${p.minAvailableRamGiB} ГиБ. Память всего устройства." +
            if (r.androidLowRam == true) " Android сообщает о нехватке RAM." else "")
        add("network","Подключение", when {
            !p.requireValidatedNetwork -> PreflightLevel.SKIPPED
            r.network == "NONE" || r.validatedNetwork == false -> PreflightLevel.WARNING
            r.validatedNetwork == true -> PreflightLevel.PASS
            else -> PreflightLevel.UNKNOWN
        }, "Тип: ${r.network ?: "н/д"}; валидация Android: " +
            (r.validatedNetwork?.let { if(it) "да" else "нет" } ?: "н/д") +
            ". Ping игрового сервера не измерялся.")
        add("app","Запуск игры",when(r.launchable) {
            true -> PreflightLevel.PASS
            false -> PreflightLevel.WARNING
            null -> PreflightLevel.UNKNOWN
        }, when(r.launchable) {
            true -> "Android предоставил Launcher Intent."
            false -> "Launcher Intent сейчас недоступен. Это не доказательство удаления игры."
            null -> "Не удалось проверить Launcher Intent."
        })
        add("capture","Захват экрана", when(r.captureBusy) {
            true -> PreflightLevel.WARNING
            false -> PreflightLevel.INFO
            null -> PreflightLevel.UNKNOWN
        }, when(r.captureBusy) {
            true -> "Capture Studio занят захватом, согласием или обслуживанием."
            false -> "В Legion Control нет активной операции захвата."
            null -> "Состояние неизвестно."
        })
        add("session","Запись игровой сессии", when(r.sessionActive) {
            true -> PreflightLevel.INFO
            false -> PreflightLevel.INFO
            null -> PreflightLevel.UNKNOWN
        }, when(r.sessionActive) {
            true -> "Game Session уже записывает данные."
            false -> "Game Session не запущен."
            null -> "Состояние неизвестно."
        })
        add("undo","Незавершённый игровой профиль", when {
            !r.pendingProfileKnown -> PreflightLevel.UNKNOWN
            r.pendingProfile != null -> PreflightLevel.WARNING
            else -> PreflightLevel.INFO
        }, if (!r.pendingProfileKnown) "Состояние неизвестно."
            else r.pendingProfile?.let { "Есть сохранённые глобальные настройки: $it. Проверьте возврат в Game Toolbox." }
                ?: "Нет незавершённого возврата Game Toolbox.")
    }
}

data class PreflightReport(val packageName: String, val label: String,
    val preset: PreflightPreset, val checkedAtElapsed: Long, val checkedAtWall: Long,
    val findings: List<PreflightFinding>) {
    val critical get() = findings.count { it.level == PreflightLevel.CRITICAL }
    val warnings get() = findings.count { it.level == PreflightLevel.WARNING }
    val unknown get() = findings.count { it.level == PreflightLevel.UNKNOWN }
    fun text(): String = buildString {
        append("GAME PREFLIGHT · ").append(label).append(" (").append(packageName).append(")\n")
        append("Время снимка (Unix ms): ").append(checkedAtWall).append('\n')
        append("Ограничения: ").append(critical).append(" · предупреждения: ").append(warnings)
            .append(" · неизвестно: ").append(unknown).append("\n\n")
        for (f in findings) append(f.level.label).append(" · ").append(f.title)
            .append(": ").append(f.detail).append('\n')
        append("\nДиагностический снимок Android. Не рейтинг производительности, не измерение FPS или ping игры.")
    }
}
