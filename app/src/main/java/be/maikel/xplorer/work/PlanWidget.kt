package be.maikel.xplorer.work

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import be.maikel.xplorer.MainActivity
import be.maikel.xplorer.R

/**
 * Widget d'écran d'accueil : le plan DCA de la semaine.
 * Le contenu est écrit par l'app après chaque synchronisation ([save]) ; le widget ne fait aucun appel réseau.
 */
class PlanWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        private const val PREFS = "snipex_widget"

        /** Enregistre le texte du widget et met à jour tous les widgets posés. */
        fun save(ctx: Context, title: String, body: String, foot: String) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", title).putString("body", body).putString("foot", foot).apply()
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, PlanWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { m.updateAppWidget(it, views(ctx)) }
        }

        private fun views(ctx: Context): RemoteViews {
            val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val v = RemoteViews(ctx.packageName, R.layout.widget_plan)
            v.setTextViewText(R.id.widget_title, sp.getString("title", null) ?: ctx.getString(R.string.widget_loading))
            v.setTextViewText(R.id.widget_body, sp.getString("body", "") ?: "")
            v.setTextViewText(R.id.widget_foot, sp.getString("foot", "") ?: "")
            val pi = PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_root, pi)
            return v
        }
    }
}
