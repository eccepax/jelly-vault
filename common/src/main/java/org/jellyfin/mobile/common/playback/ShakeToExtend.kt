package org.jellyfin.mobile.common.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Feels the phone being shaken, calling back on each shake while started. */
interface ShakeSensor {
    fun start(onShake: () -> Unit)
    fun stop()
}

/**
 * While the [timer] counts down (not stopping at a segment's end) and [enabled] says so (a
 * setting), shaking the phone extends it, as [SleepTimer.extend] does from its menu, with a [buzz]
 * to say it was felt. The phone is only felt while that's so.
 */
class ShakeToExtend(
    private val timer: SleepTimer,
    private val sensor: ShakeSensor,
    private val enabled: () -> Boolean,
    private val buzz: () -> Unit,
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        // Read again at every tick of the countdown, so the setting changed while it runs is followed
        timer.state.map { it is SleepTimerState.Counting && enabled() }
            .distinctUntilChanged()
            .collect { feel ->
                if (feel) {
                    sensor.start {
                        timer.extend()
                        buzz()
                    }
                } else {
                    sensor.stop()
                }
            }
    }
}

/** The phone's accelerometer as a [ShakeSensor] (see [ShakeDetector]); nothing on a phone without one. */
class AccelerometerShakeSensor(context: Context) : ShakeSensor, SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val detector = ShakeDetector()
    private var onShake: (() -> Unit)? = null

    override fun start(onShake: () -> Unit) {
        val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        this.onShake = onShake
        sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    override fun stop() {
        onShake = null
        sensors?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        if (detector.onSample(x, y, z, SystemClock.elapsedRealtime())) onShake?.invoke()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** A short buzz, to say a shake was felt (the app needs the VIBRATE permission); nothing without a vibrator. */
fun buzzOnce(context: Context) {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    vibrator.vibrate(VibrationEffect.createOneShot(BUZZ_MS, VibrationEffect.DEFAULT_AMPLITUDE))
}

private const val BUZZ_MS = 80L
