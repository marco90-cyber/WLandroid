package com.marco.wlandroid

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs

const val REQ_BIND = 4241
const val REQ_CONFIG = 4242

class WidgetController(private val activity: Activity) {
    val host = AppWidgetHost(activity, 1024)
    val awm: AppWidgetManager = AppWidgetManager.getInstance(activity)
    private var callback: ((Boolean) -> Unit)? = null

    fun providers(): List<AppWidgetProviderInfo> =
        awm.installedProviders.sortedBy { it.loadLabel(activity.packageManager).lowercase() }

    /** Alloca, collega (chiedendo il permesso se serve) e configura un widget. done(null) = annullato. */
    fun begin(provider: AppWidgetProviderInfo, done: (Int?) -> Unit) {
        val id = host.allocateAppWidgetId()

        fun configureOrFinish() {
            val info = awm.getAppWidgetInfo(id)
            if (info?.configure != null) {
                callback = { ok ->
                    if (ok) {
                        done(id)
                    } else {
                        remove(id)
                        done(null)
                    }
                }
                try {
                    host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIG, null)
                } catch (e: Exception) {
                    callback = null
                    remove(id)
                    done(null)
                }
            } else {
                done(id)
            }
        }

        if (awm.bindAppWidgetIdIfAllowed(id, provider.provider)) {
            configureOrFinish()
        } else {
            callback = { ok ->
                if (ok) {
                    configureOrFinish()
                } else {
                    remove(id)
                    done(null)
                }
            }
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            try {
                @Suppress("DEPRECATION")
                activity.startActivityForResult(i, REQ_BIND)
            } catch (e: Exception) {
                callback = null
                remove(id)
                done(null)
            }
        }
    }

    fun handleResult(requestCode: Int, resultCode: Int) {
        if (requestCode == REQ_BIND || requestCode == REQ_CONFIG) {
            val cb = callback
            callback = null
            cb?.invoke(resultCode == Activity.RESULT_OK)
        }
    }

    fun remove(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (e: Exception) {
        }
    }

    fun start() {
        try {
            host.startListening()
        } catch (e: Exception) {
        }
    }

    fun stop() {
        try {
            host.stopListening()
        } catch (e: Exception) {
        }
    }
}

/** Dimensione iniziale della tile in base alla dimensione minima richiesta dal widget. */
fun sizeForProvider(info: AppWidgetProviderInfo, density: Float): Pair<Int, Int> {
    val wDp = info.minWidth / density
    val hDp = info.minHeight / density
    val cw = ((wDp + 30) / 70).toInt().coerceIn(1, 4)
    val ch = ((hDp + 30) / 70).toInt().coerceIn(1, 8)
    return cw to ch
}

/** Contenitore che intercetta la pressione lunga sul widget, così si può selezionare la tile. */
class LongPressFrame(ctx: Context) : FrameLayout(ctx) {
    var onLong: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var fired = false
    private var sx = 0f
    private var sy = 0f
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val fire = Runnable {
        fired = true
        onLong?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                sx = ev.x
                sy = ev.y
                handler.postDelayed(fire, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (!fired && (abs(ev.x - sx) > slop || abs(ev.y - sy) > slop)) handler.removeCallbacks(fire)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(fire)
        }
        return fired
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            handler.removeCallbacks(fire)
            val was = fired
            fired = false
            return was
        }
        return fired
    }
}

@Composable
fun WidgetTile(ctl: WidgetController, id: Int, widthDp: Int, heightDp: Int, onLongPress: () -> Unit) {
    val info = remember(id) { ctl.awm.getAppWidgetInfo(id) }
    val lp by rememberUpdatedState(onLongPress)
    if (info == null) {
        Text("Widget non disponibile", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
    } else {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                LongPressFrame(ctx).also { f ->
                    f.addView(ctl.host.createView(ctx, id, info))
                    f.onLong = { lp() }
                }
            },
            update = { f ->
                (f.getChildAt(0) as? AppWidgetHostView)
                    ?.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
            }
        )
    }
}

@Composable
fun WidgetPickerDialog(
    ctl: WidgetController,
    builtins: List<Pair<String, String>>,
    onBuiltin: (String) -> Unit,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val entries = remember {
        ctl.providers().map { p ->
            Triple(
                p,
                p.loadLabel(ctx.packageManager).toString(),
                try {
                    p.loadIcon(ctx, ctx.resources.displayMetrics.densityDpi).toBitmap(96, 96).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            )
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
        title = { Text("Aggiungi widget") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(builtins) { (bKey, bLabel) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onBuiltin(bKey) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(bLabel, fontSize = 16.sp)
                    }
                }
                items(entries) { (p, label, icon) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(p) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (icon != null) Image(icon, label, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(label, fontSize = 16.sp)
                    }
                }
            }
        }
    )
}
