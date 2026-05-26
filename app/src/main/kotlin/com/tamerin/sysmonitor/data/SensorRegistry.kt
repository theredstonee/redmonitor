package com.tamerin.sysmonitor.data

import android.hardware.Sensor

object SensorRegistry {
    fun typeLabel(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "Beschleunigung"
        Sensor.TYPE_MAGNETIC_FIELD -> "Magnetfeld"
        Sensor.TYPE_GYROSCOPE -> "Gyroskop"
        Sensor.TYPE_LIGHT -> "Lichtsensor"
        Sensor.TYPE_PRESSURE -> "Luftdruck"
        Sensor.TYPE_PROXIMITY -> "Annäherung"
        Sensor.TYPE_GRAVITY -> "Gravitation"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Lineare Beschleunigung"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotationsvektor"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "Luftfeuchte"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Umgebungstemperatur"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "Spiel-Rotationsvektor"
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Gyroskop (unkal.)"
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Magnetfeld (unkal.)"
        Sensor.TYPE_SIGNIFICANT_MOTION -> "Signifikante Bewegung"
        Sensor.TYPE_STEP_DETECTOR -> "Schritterkennung"
        Sensor.TYPE_STEP_COUNTER -> "Schrittzähler"
        Sensor.TYPE_HEART_RATE -> "Herzfrequenz"
        Sensor.TYPE_STATIONARY_DETECT -> "Stillstand"
        Sensor.TYPE_MOTION_DETECT -> "Bewegung"
        Sensor.TYPE_HEART_BEAT -> "Herzschlag"
        else -> "Sensor #$type"
    }

    fun unit(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY,
        Sensor.TYPE_LINEAR_ACCELERATION -> "m/s²"
        Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "rad/s"
        Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "μT"
        Sensor.TYPE_LIGHT -> "lx"
        Sensor.TYPE_PRESSURE -> "hPa"
        Sensor.TYPE_PROXIMITY -> "cm"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
        Sensor.TYPE_STEP_COUNTER -> "Schritte"
        Sensor.TYPE_HEART_RATE -> "BPM"
        else -> ""
    }
}
