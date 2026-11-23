package dev.lolokeksu.legioncontrol

/** Human-readable °C in the UI, existing deci-°C values in SQLite and Smart Rules. */
object SmartProfileTemperature {
    private val pattern=Regex("""^(?:3[0-9]|4[0-9]|5[0-5])(?:[.,][0-9])?$""")
    fun parseDeciC(input:String):Int? {
        val value=input.trim()
        if(!pattern.matches(value)) return null
        val parts=value.replace(',', '.').split('.')
        val result=parts[0].toInt()*10+(parts.getOrNull(1)?.toInt()?:0)
        return result.takeIf { it in 300..550 }
    }
    fun format(deciC:Int):String {
        require(deciC in 300..550)
        return "${deciC/10},${deciC%10}"
    }
}
