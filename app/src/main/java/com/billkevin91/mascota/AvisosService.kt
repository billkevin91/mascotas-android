package com.billkevin91.mascota

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Se entera de que llegó una notificación para que las mascotas reaccionen.
 * No lee su contenido ni guarda nada: sólo avisa a [PetService] de que llegó una.
 * Android sólo lo activa si le das "Acceso a notificaciones" a la app.
 */
class AvisosService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Ni las nuestras, ni las fijas (música, descargas...), ni los resúmenes de grupo
        if (sbn.packageName == packageName || sbn.isOngoing) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        PetService.notified()
    }
}
