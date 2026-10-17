package com.hinnka.mycamera.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Vitesse angulaire lissée du téléphone (rad/s) mesurée au gyroscope, pour que le mode Nuit
 * choisisse la pose : longue si le téléphone est posé ou calé, courte s'il est tenu à la main.
 */
internal class NightMotionMonitor(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    @Volatile
    private var smoothedRadPerSecond = Float.NaN

    /** Null tant qu'aucune mesure n'est disponible (pas de gyroscope, ou capteur juste démarré). */
    val angularSpeedRadS: Float?
        get() = smoothedRadPerSecond.takeIf { it.isFinite() }

    fun start() {
        val sensor = gyroscope ?: return
        smoothedRadPerSecond = Float.NaN
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        smoothedRadPerSecond = Float.NaN
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val speed = sqrt(x * x + y * y + z * z)
        val previous = smoothedRadPerSecond
        smoothedRadPerSecond = if (previous.isNaN()) speed else previous + SMOOTHING * (speed - previous)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        // ~0,5 s de mémoire à 50 Hz : un tremblement ponctuel ne suffit pas à tout bloquer.
        const val SMOOTHING = 0.08f
    }
}
