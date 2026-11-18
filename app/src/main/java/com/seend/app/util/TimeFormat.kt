package com.seend.app.util

import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Formatea la "última conexión" igual que el HTML original: hace un
 * momento, hace N min, hoy a las HH:MM, dd/MM HH:MM.
 *
 * Recibe epoch en segundos (ts) como lo manda el servidor Go.
 */
fun formatearUltimaConexion(ts: Long): String {
    if (ts <= 0) return "Desconectado"
    val now = System.currentTimeMillis() / 1000
    val diff = now - ts
    return when {
        diff < 60 -> "Últ. conexión hace un momento"
        diff < 3600 -> "Últ. conexión hace ${diff / 60} min"
        else -> {
            val d = Date(ts * 1000)
            val hoy = Calendar.getInstance().apply { time = Date() }
            val dia = Calendar.getInstance().apply { time = d }
            val hh = String.format(Locale.getDefault(), "%02d", dia.get(Calendar.HOUR_OF_DAY))
            val mm = String.format(Locale.getDefault(), "%02d", dia.get(Calendar.MINUTE))
            if (hoy.get(Calendar.YEAR) == dia.get(Calendar.YEAR) &&
                hoy.get(Calendar.DAY_OF_YEAR) == dia.get(Calendar.DAY_OF_YEAR)) {
                "Últ. conexión hoy a las $hh:$mm"
            } else {
                val dd = String.format(Locale.getDefault(), "%02d", dia.get(Calendar.DAY_OF_MONTH))
                val mon = String.format(Locale.getDefault(), "%02d", dia.get(Calendar.MONTH) + 1)
                "Últ. conexión $dd/$mon $hh:$mm"
            }
        }
    }
}

/** Texto corto de estado: "En línea", "Escribiendo…" o la última conexión. */
fun textoEstado(enLinea: Boolean, escribiendo: Boolean, ultima: Long): String =
    when {
        escribiendo -> "Escribiendo…"
        enLinea -> "En línea"
        else -> formatearUltimaConexion(ultima)
    }
