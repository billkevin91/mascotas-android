package com.billkevin91.mascota

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Widget de la pantalla de inicio: soltar otra mascota, darles de comer o quitarlas todas. */
class MascotasWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget)
        // Cada botón abre AccionActivity, que hace la acción sin mostrar pantalla
        for ((i, pair) in listOf(
            R.id.widget_soltar to AccionActivity.OTRA,
            R.id.widget_comida to AccionActivity.COMER,
            R.id.widget_quitar to AccionActivity.QUITAR,
        ).withIndex()) {
            val intent = Intent(context, AccionActivity::class.java).setAction(pair.second)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(context, 100 + i, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(pair.first, pending)
        }
        manager.updateAppWidget(ids, views)
    }
}
