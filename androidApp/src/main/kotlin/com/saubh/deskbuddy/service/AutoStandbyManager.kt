package com.saubh.deskbuddy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.SensorManager
import android.os.BatteryManager
import android.view.OrientationEventListener
import com.saubh.deskbuddy.prefs.StandbyPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Charging + landscape = docked: standby opens on its own (a clock without a PC; PC controls once connected). Lives in
 * [ConnectionService]; when that process is gone, [DockWakeJob] restarts it on the next plug-in.
 * [onPowerConnected] lets auto-connect try at once instead of after its current backoff.
 */
class AutoStandbyManager(
    private val context: Context,
    private val standbyPrefs: StandbyPrefs,
    private val onPowerConnected: () -> Unit = {},
) {

    private val _isAutoStandby = MutableStateFlow(false)
    val isAutoStandby: StateFlow<Boolean> = _isAutoStandby.asStateFlow()

    private val launcher = DockLauncher(context)
    private var orientationListener: OrientationEventListener? = null
    private var started = false
    private var isCharging = false

    @Volatile
    private var userEnabled = true

    /** Set while the PC types into the phone: jumping to standby would steal focus from the target app. */
    @Volatile
    private var suppressed = false

    /** The app is on screen: its UI follows [isAutoStandby] itself, so nothing needs launching. */
    @Volatile
    private var uiVisible = false

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    isCharging = true
                    onPowerConnected()
                    updateSensor()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    isCharging = false
                    updateSensor()
                    updateState(false)
                    DockWakeJob.schedule(context)
                }
            }
        }
    }

    fun setUserEnabled(enabled: Boolean) {
        userEnabled = enabled
        if (!enabled) {
            updateState(false)
            stopOrientationListener()
        } else if (isCharging) {
            startOrientationListener()
        }
    }

    fun setSuppressed(value: Boolean) {
        suppressed = value
        if (value) updateState(false)
    }

    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        if (visible) launcher.dismiss()
    }

    /** Only while charging and user enabled: the sensor costs nothing worth saving while plugged in. */
    private fun updateSensor() {
        if (isCharging && userEnabled) startOrientationListener() else stopOrientationListener()
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(powerReceiver, filter)

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        isCharging = batteryManager.isCharging
        // Already plugged in: the next unplug schedules the wake-up.
        if (!isCharging) DockWakeJob.schedule(context)

        updateSensor()
    }

    /** Safe to call when [start] never ran (the service gave up before starting it). */
    fun stop() {
        if (!started) return
        started = false
        context.unregisterReceiver(powerReceiver)
        stopOrientationListener()
    }

    private fun startOrientationListener() {
        if (!userEnabled) return
        if (orientationListener == null) {
            orientationListener = object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
                override fun onOrientationChanged(orientation: Int) {
                    val landscape = DockPolicy.isLandscape(orientation) ?: return
                    updateState(DockPolicy.shouldStandby(isCharging, landscape, suppressed))
                }
            }
        }
        orientationListener?.enable()
    }

    private fun stopOrientationListener() {
        orientationListener?.disable()
        orientationListener = null
    }

    private fun updateState(autoStandby: Boolean) {
        val target = autoStandby && userEnabled
        if (target && suppressed) return
        if (_isAutoStandby.value == target) return
        _isAutoStandby.value = target

        when {
            !target -> launcher.dismiss()
            !uiVisible -> launcher.open()
        }
    }
}
