package com.billkevin91.mascota

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.roundToInt

/** Mantiene las mascotas caminando por encima de todas las apps. */
class PetService : Service() {

    companion object {
        const val ACTION_ADD = "com.billkevin91.mascota.ADD"
        const val ACTION_CLEAR = "com.billkevin91.mascota.CLEAR"
        const val EXTRA_PET = "pet"
        const val EXTRA_SIZE = "size"
        private const val CHANNEL = "mascotas"
        private const val MAX_PETS = 12

        fun add(context: Context, pet: String, size: Float) {
            val i = Intent(context, PetService::class.java).setAction(ACTION_ADD)
                .putExtra(EXTRA_PET, pet).putExtra(EXTRA_SIZE, size)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun clear(context: Context) {
            context.startService(Intent(context, PetService::class.java).setAction(ACTION_CLEAR))
        }
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val pets = ArrayList<PetWindow>()
    private val defs = HashMap<String, PetDef>()
    private val sheets = HashMap<String, Bitmap>()
    private val areas = HashMap<Int, Area>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        when (intent?.action) {
            ACTION_CLEAR -> {
                removeAll()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // Sin intent = Android reinició el servicio: vuelve la última mascota elegida
                val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                val pet = intent?.getStringExtra(EXTRA_PET) ?: prefs.getString(MainActivity.PREF_PET, null) ?: MainActivity.PETS.first()
                val size = if (intent?.hasExtra(EXTRA_SIZE) == true) intent.getFloatExtra(EXTRA_SIZE, 1f)
                else prefs.getFloat(MainActivity.PREF_SIZE, 1f)
                if (!Settings.canDrawOverlays(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                addPet(pet, size)
            }
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Giro de pantalla: nuevas medidas para todas
        for ((scale, area) in areas) measure(area, scale)
    }

    override fun onDestroy() {
        removeAll()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Mascotas en pantalla", NotificationManager.IMPORTANCE_LOW))
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val more = PendingIntent.getService(this, 1, Intent(this, PetService::class.java).setAction(ACTION_ADD), flags)
        val clear = PendingIntent.getService(this, 2, Intent(this, PetService::class.java).setAction(ACTION_CLEAR), flags)
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val n = builder
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setContentTitle("Tus mascotas están paseando")
            .setContentText("Arrástralas, lánzalas o tócalas dos veces para despedirlas")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null as Icon?, "Otra más", more).build())
            .addAction(Notification.Action.Builder(null as Icon?, "Quitar todas", clear).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    // ---- Mascotas ----

    private fun addPet(folder: String, size: Float) {
        if (pets.count { !it.brain.isChild } >= MAX_PETS) return
        val def = defs.getOrPut(folder) { assets.open("pets/$folder/animations.xml").use { PetDef.parse(it) } }
        val sheet = sheets.getOrPut(folder) { loadSheet(def) }
        val density = resources.displayMetrics.density
        val scale = (density * size).roundToInt().coerceAtLeast(1)
        val area = areas.getOrPut(scale) { Area().also { measure(it, scale) } }
        val brain = PetBrain(def, area, sheet.width / def.tilesX, sheet.height / def.tilesY)
        brain.start()
        PetWindow(brain, sheet, scale).show()
    }

    private fun removeAll() {
        for (p in ArrayList(pets)) p.remove()
    }

    private fun loadSheet(def: PetDef): Bitmap {
        val bytes = Base64.decode(def.png.filterNot { it.isWhitespace() }, Base64.DEFAULT)
        val opts = BitmapFactory.Options().apply { inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val key = transparentColor(def.transparency) ?: return bmp
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (i in px.indices) if (px[i] or 0xFF000000.toInt() == key) px[i] = 0
        bmp.setPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return bmp
    }

    /** Nombres de color de .NET que usan los XML para el fondo transparente. */
    private fun transparentColor(name: String): Int? = when (name.trim().lowercase()) {
        "magenta", "fuchsia" -> 0xFFFF00FF.toInt()
        "green" -> 0xFF008000.toInt()
        "lime" -> 0xFF00FF00.toInt()
        "cyan", "aqua" -> 0xFF00FFFF.toInt()
        "black" -> 0xFF000000.toInt()
        "white" -> 0xFFFFFFFF.toInt()
        "red" -> 0xFFFF0000.toInt()
        "blue" -> 0xFF0000FF.toInt()
        "yellow" -> 0xFFFFFF00.toInt()
        else -> runCatching { Color.parseColor(name.trim()) or 0xFF000000.toInt() }.getOrNull()
    }

    /** Mide la pantalla en píxeles de mascota; el suelo queda sobre la barra de navegación. */
    private fun measure(area: Area, scale: Int) {
        val w: Int
        val h: Int
        var insetLeft = 0
        var insetRight = 0
        var insetBottom = 0
        if (Build.VERSION.SDK_INT >= 30) {
            val m = wm.currentWindowMetrics
            w = m.bounds.width()
            h = m.bounds.height()
            val ins = m.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
            insetLeft = ins.left
            insetRight = ins.right
            insetBottom = ins.bottom
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            w = dm.widthPixels
            h = dm.heightPixels
            @SuppressLint("DiscouragedApi", "InternalInsetResource")
            val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (id > 0 && h > w) insetBottom = resources.getDimensionPixelSize(id)
        }
        area.screenW = w / scale
        area.screenH = h / scale
        area.left = insetLeft / scale
        area.right = (w - insetRight) / scale
        area.top = 0
        area.bottom = (h - insetBottom) / scale
    }

    /** Una mascota (o hija) en su propia ventanita flotante. */
    private inner class PetWindow(val brain: PetBrain, val sheet: Bitmap, val scale: Int) {
        private val view = SpriteView()
        private val params = WindowManager.LayoutParams(
            brain.frameW * scale,
            brain.frameH * scale,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 30) {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (brain.isChild) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        private var attached = false
        private var lastToss = 0L

        private val tick = object : Runnable {
            override fun run() {
                if (!attached) return
                val delay = brain.tick()
                for (c in brain.takeChildren()) {
                    val cb = PetBrain(brain.def, brain.area, brain.frameW, brain.frameH, c)
                    cb.start()
                    PetWindow(cb, sheet, scale).show()
                }
                if (brain.finished) { remove(); return }
                update()
                handler.postDelayed(this, delay.toLong())
            }
        }

        private val toss = object : Runnable {
            override fun run() {
                if (!attached || !brain.tossing) return
                val now = SystemClock.uptimeMillis()
                brain.advanceToss((now - lastToss).toDouble())
                lastToss = now
                update()
                if (brain.tossing) handler.postDelayed(this, 16)
            }
        }

        fun show() {
            update(addNow = true)
            if (!attached) {
                // Sin permiso para dibujar encima: no hay nada que mostrar
                if (pets.isEmpty()) stopSelf()
                return
            }
            pets.add(this)
            handler.post(tick)
        }

        fun remove() {
            if (!attached) return
            attached = false
            handler.removeCallbacks(tick)
            handler.removeCallbacks(toss)
            runCatching { wm.removeView(view) }
            pets.remove(this)
            if (pets.none { !it.brain.isChild }) {
                for (p in ArrayList(pets)) p.remove()
                stopSelf()
            }
        }

        private fun update(addNow: Boolean = false) {
            params.x = (brain.x * scale).roundToInt()
            params.y = ((brain.y + brain.offsetY) * scale).roundToInt()
            view.invalidate()
            if (addNow) {
                attached = runCatching { wm.addView(view, params) }.isSuccess
            } else if (attached) {
                runCatching { wm.updateViewLayout(view, params) }
            }
        }

        private inner class SpriteView : View(this@PetService) {
            private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
            private val src = Rect()
            private val dst = Rect()
            private val slop = ViewConfiguration.get(context).scaledTouchSlop
            private var grabX = 0f
            private var grabY = 0f
            private var downX = 0f
            private var downY = 0f
            private var lastUp = 0L
            private val samples = ArrayDeque<FloatArray>()

            override fun onDraw(canvas: Canvas) {
                val fw = brain.frameW
                val fh = brain.frameH
                val col = brain.frame % brain.def.tilesX
                val row = brain.frame / brain.def.tilesX
                src.set(col * fw, row * fh, col * fw + fw, row * fh + fh)
                dst.set(0, 0, width, height)
                paint.alpha = (brain.opacity.coerceIn(0.0, 1.0) * 255).roundToInt()
                // Los dibujos miran a la izquierda; si camina a la derecha se voltean
                if (!brain.movingLeft) {
                    canvas.save()
                    canvas.scale(-1f, 1f, width / 2f, height / 2f)
                    canvas.drawBitmap(sheet, src, dst, paint)
                    canvas.restore()
                } else {
                    canvas.drawBitmap(sheet, src, dst, paint)
                }
            }

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (brain.isChild || brain.isDying) return false
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastUp < ViewConfiguration.getDoubleTapTimeout() &&
                            abs(e.rawX - downX) < slop * 2 && abs(e.rawY - downY) < slop * 2
                        ) {
                            // Doble toque: se despide
                            handler.removeCallbacks(toss)
                            brain.kill()
                            return true
                        }
                        downX = e.rawX
                        downY = e.rawY
                        grabX = e.rawX - params.x
                        grabY = e.rawY - params.y
                        samples.clear()
                        samples.addLast(floatArrayOf(e.rawX, e.rawY, now.toFloat()))
                        handler.removeCallbacks(toss)
                        brain.grab()
                        brain.offsetY = 0.0
                        // Que el cambio de dibujo se vea ya
                        handler.removeCallbacks(tick)
                        handler.post(tick)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        brain.x = ((e.rawX - grabX) / scale).toDouble()
                        brain.y = ((e.rawY - grabY) / scale).toDouble()
                        brain.offsetY = 0.0
                        val now = SystemClock.uptimeMillis()
                        samples.addLast(floatArrayOf(e.rawX, e.rawY, now.toFloat()))
                        while (samples.size > 2 && samples[1][2] <= now - 80) samples.removeFirst()
                        update()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val now = SystemClock.uptimeMillis()
                        lastUp = now
                        var vx = 0.0
                        var vy = 0.0
                        val first = samples.firstOrNull()
                        val last = samples.lastOrNull()
                        if (first != null && last != null) {
                            val elapsed = last[2] - first[2]
                            if (elapsed >= 8 && now - last[2] <= 80) {
                                // Misma fuerza que desktopPet, en píxeles de mascota
                                vx = ((last[0] - first[0]) / scale / elapsed * 10).toDouble()
                                vy = ((last[1] - first[1]) / scale / elapsed * 10).toDouble()
                            }
                        }
                        brain.release(vx, vy)
                        if (brain.tossing) {
                            lastToss = now
                            handler.removeCallbacks(toss)
                            handler.post(toss)
                        }
                        handler.removeCallbacks(tick)
                        handler.post(tick)
                    }
                }
                return true
            }
        }
    }
}
