package com.tamerin.sysmonitor.data

import android.content.Context
import android.hardware.Sensor
import com.tamerin.sysmonitor.R

object SensorRegistry {
    fun typeLabel(context: Context, type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> context.getString(R.string.data_sensor_accelerometer)
        Sensor.TYPE_MAGNETIC_FIELD -> context.getString(R.string.data_sensor_magnetic)
        Sensor.TYPE_GYROSCOPE -> context.getString(R.string.data_sensor_gyroscope)
        Sensor.TYPE_LIGHT -> context.getString(R.string.data_sensor_light)
        Sensor.TYPE_PRESSURE -> context.getString(R.string.data_sensor_pressure)
        Sensor.TYPE_PROXIMITY -> context.getString(R.string.data_sensor_proximity)
        Sensor.TYPE_GRAVITY -> context.getString(R.string.data_sensor_gravity)
        Sensor.TYPE_LINEAR_ACCELERATION -> context.getString(R.string.data_sensor_linear_accel)
        Sensor.TYPE_ROTATION_VECTOR -> context.getString(R.string.data_sensor_rotation_vector)
        Sensor.TYPE_RELATIVE_HUMIDITY -> context.getString(R.string.data_sensor_humidity)
        Sensor.TYPE_AMBIENT_TEMPERATURE -> context.getString(R.string.data_sensor_ambient_temp)
        Sensor.TYPE_GAME_ROTATION_VECTOR -> context.getString(R.string.data_sensor_game_rotation)
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> context.getString(R.string.data_sensor_gyro_uncal)
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> context.getString(R.string.data_sensor_magnetic_uncal)
        Sensor.TYPE_SIGNIFICANT_MOTION -> context.getString(R.string.data_sensor_significant_motion)
        Sensor.TYPE_STEP_DETECTOR -> context.getString(R.string.data_sensor_step_detector)
        Sensor.TYPE_STEP_COUNTER -> context.getString(R.string.data_sensor_step_counter)
        Sensor.TYPE_HEART_RATE -> context.getString(R.string.data_sensor_heart_rate)
        Sensor.TYPE_STATIONARY_DETECT -> context.getString(R.string.data_sensor_stationary)
        Sensor.TYPE_MOTION_DETECT -> context.getString(R.string.data_sensor_motion)
        Sensor.TYPE_HEART_BEAT -> context.getString(R.string.data_sensor_heartbeat)
        else -> context.getString(R.string.data_sensor_unknown, type)
    }

    fun unit(context: Context, type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY,
        Sensor.TYPE_LINEAR_ACCELERATION -> "m/s²"
        Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "rad/s"
        Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "μT"
        Sensor.TYPE_LIGHT -> "lx"
        Sensor.TYPE_PRESSURE -> "hPa"
        Sensor.TYPE_PROXIMITY -> "cm"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
        Sensor.TYPE_STEP_COUNTER -> context.getString(R.string.data_sensor_unit_steps)
        Sensor.TYPE_HEART_RATE -> "BPM"
        else -> ""
    }
}
