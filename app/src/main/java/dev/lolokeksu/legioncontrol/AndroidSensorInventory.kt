package dev.lolokeksu.legioncontrol

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

/** Reports public SensorManager visibility only; availability is NOT a live reading. */
object AndroidSensorInventory {
    fun inspect(context: Context): List<Pair<String, Boolean?>> {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val supported = listOf(
            "Акселерометр" to Sensor.TYPE_ACCELEROMETER,
            "Гироскоп" to Sensor.TYPE_GYROSCOPE,
            "Освещённость" to Sensor.TYPE_LIGHT,
            "Приближение" to Sensor.TYPE_PROXIMITY,
            "Окружающая температура" to Sensor.TYPE_AMBIENT_TEMPERATURE
        )
        return supported.map { (label, type) ->
            label to if (manager == null) null else runCatching {
                manager.getDefaultSensor(type) != null
            }.getOrNull()
        }
    }
}
