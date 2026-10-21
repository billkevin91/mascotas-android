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
        const val ACTION_PLAY = "com.billkevin91.mascota.PLAY"
        const val EXTRA_PET = "pet"
        const val EXTRA_SIZE = "size"
        const val EXTRA_ANIMATION = "animation"
        const val EXTRA_LOOP = "loop"
        private const val CHANNEL = "mascotas"
        private const val MAX_PETS = 12

        fun add(context: Context, pet: String, size: Float) {
            val i = Intent(context, PetService::class.java).setAction(ACTION_ADD)
                .putExtra(EXTRA_PET, pet).putExtra(EXTRA_SIZE, size)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        /** Modo prueba: la mascota de ese tipo hace el movimiento [id] (-1 = que vuelva a moverse sola). */
        fun play(context: Context, pet: String, id: Int, loop: Boolean) {
            val i = Intent(context, PetService::class.java).setAction(ACTION_PLAY)
                .putExtra(EXTRA_PET, pet).putExtra(EXTRA_ANIMATION, id).putExtra(EXTRA_LOOP, loop)
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
    private var speed = 1.0
    private var showNames = false
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, _ -> readPrefs(p) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startInForeground()
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        readPrefs(prefs)
    }

    private fun readPrefs(prefs: android.content.SharedPreferences) {
        speed = prefs.getFloat(MainActivity.PREF_SPEED, 1f).toDouble()
        showNames = prefs.getBoolean(MainActivity.PREF_NAMES, false)
        for (p in pets) {
            p.brain.speed = speed
            p.refreshLabel()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        when (intent?.action) {
            ACTION_CLEAR -> {
                removeAll()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY -> {
                if (!Settings.canDrawOverlays(this)) {
                    if (pets.isEmpty()) stopSelf()
                    return START_NOT_STICKY
                }
                val pet = intent.getStringExtra(EXTRA_PET) ?: PetLibrary.BUNDLED.first()
                val id = intent.getIntExtra(EXTRA_ANIMATION, -1)
                val loop = intent.getBooleanExtra(EXTRA_LOOP, false)
                if (id < 0) {
                    // Que se muevan solas: fuera repeticiones y crías sobrantes (la tina...)
                    for (p in ArrayList(pets)) {
                        if (p.brain.isChild) p.dismiss() else p.brain.stopLoop()
                    }
                } else {
                    // La última mascota de ese tipo; si no hay, aparece una
                    val target = pets.lastOrNull { !it.brain.isChild && !it.brain.isDying && it.folder == pet }
                        ?: addPet(pet, getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getFloat(MainActivity.PREF_SIZE, 1f))
                    target?.play(id, loop)
                }
                return START_STICKY
            }
            else -> {
                // Sin intent = Android reinició el servicio: vuelve la última mascota elegida
                val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                val pet = (intent?.getStringExtra(EXTRA_PET) ?: prefs.getString(MainActivity.PREF_PET, null))
                    ?.takeIf { PetLibrary.exists(this, it) } ?: PetLibrary.BUNDLED.first()
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
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener)
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

    private fun addPet(folder: String, size: Float): PetWindow? {
        if (pets.count { !it.brain.isChild } >= MAX_PETS) return null
        if (!PetLibrary.exists(this, folder)) return null
        val def = defs.getOrPut(folder) { PetLibrary.load(this, folder) }
        val sheet = sheets.getOrPut(folder) { PetLibrary.sheet(def) }
        val density = resources.displayMetrics.density
        val scale = (density * size).roundToInt().coerceAtLeast(1)
        val area = areas.getOrPut(scale) { Area().also { measure(it, scale) } }
        val brain = PetBrain(def, area, sheet.width / def.tilesX, sheet.height / def.tilesY)
        brain.scale = scale
        brain.others = ::allBrains
        brain.speed = speed
        brain.start()
        return PetWindow(brain, sheet, scale, folder).takeIf { it.show() }
    }

    private fun allBrains(): List<PetBrain> = pets.map { it.brain }

    private fun removeAll() {
        for (p in ArrayList(pets)) p.remove()
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
    private inner class PetWindow(
        val brain: PetBrain,
        val sheet: Bitmap,
        val scale: Int,
        val folder: String,
        /** La mascota que la creó (sólo en las crías). */
        val parent: PetWindow? = null,
    ) {
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
        }
        private var attached = false
        private var lastToss = 0L

        // Deslizamiento entre pasos (las "Smooth movements" de desktopPet). Sin esto, con
        // la mascota ampliada la ventana salta varios píxeles de golpe en cada paso.
        private var fromX = 0f
        private var fromY = 0f
        private var toX = 0f
        private var toY = 0f
        private var glideStart = 0L
        private var glideMs = 0L
        private val glide = object : Runnable {
            override fun run() {
                if (!attached) return
                val t = if (glideMs <= 0) 1f
                else ((SystemClock.uptimeMillis() - glideStart).toFloat() / glideMs).coerceIn(0f, 1f)
                place(fromX + (toX - fromX) * t, fromY + (toY - fromY) * t)
                if (t < 1f) view.postOnAnimation(this)
            }
        }

        private val tick = object : Runnable {
            override fun run() {
                if (!attached) return
                val animBefore = brain.animationId
                val restartsBefore = brain.restarts
                val delay = brain.tick()
                if (brain.restarts != restartsBefore) {
                    // Modo prueba: empieza de nuevo, las crías de la vuelta anterior sobran
                    pets.filter { it.parent === this@PetWindow }.forEach { it.dismiss() }
                } else if (brain.animationId != animBefore) {
                    // Cambió de movimiento: si a una cría le falta un suspiro para acabar,
                    // que acabe ya (si no, al salir de la tina se ven dos ovejas un momento)
                    pets.filter { it.parent === this@PetWindow && it.brain.remainingMs() < 300 }.forEach { it.dismiss() }
                }
                for (c in brain.takeChildren()) {
                    val cb = PetBrain(brain.def, brain.area, brain.frameW, brain.frameH, c)
                    cb.scale = scale
                    cb.others = ::allBrains
                    cb.speed = speed
                    cb.start()
                    PetWindow(cb, sheet, scale, folder, parent = this@PetWindow).show()
                }
                if (brain.finished) { remove(); return }
                update(glideFor = delay.toLong())
                refreshLabel()
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

        fun show(): Boolean {
            update(addNow = true)
            if (!attached) {
                // Sin permiso para dibujar encima: no hay nada que mostrar
                if (pets.isEmpty()) stopSelf()
                return false
            }
            pets.add(this)
            refreshLabel()
            handler.post(tick)
            return true
        }

        /** Quita una cría ya (sin esperar a su siguiente paso). */
        fun dismiss() {
            brain.kill()
            handler.removeCallbacks(tick)
            handler.post(tick)
        }

        fun play(id: Int, loop: Boolean) {
            handler.removeCallbacks(toss)
            pets.filter { it.parent === this }.forEach { it.dismiss() }
            brain.play(id, loop)
            handler.removeCallbacks(tick)
            handler.post(tick)
        }

        // ---- Letrero con el nombre del movimiento (modo prueba) ----

        private var label: android.widget.TextView? = null
        private val labelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            params.type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }

        fun refreshLabel() {
            val want = showNames && attached && !brain.isChild
            var l = label
            if (!want) {
                if (l != null) { runCatching { wm.removeView(l) }; label = null }
                return
            }
            val text = "${brain.animationId} · ${brain.animationName}" + if (brain.loopId > 0) " ⟳" else ""
            if (l == null) {
                val d = resources.displayMetrics.density
                l = android.widget.TextView(this@PetService).apply {
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    setPadding((6 * d).toInt(), (2 * d).toInt(), (6 * d).toInt(), (2 * d).toInt())
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.argb(190, 0, 0, 0)); cornerRadius = 8 * d
                    }
                }
                l.text = text
                placeLabel(l)
                if (runCatching { wm.addView(l, labelParams) }.isFailure) return
                label = l
            } else {
                if (l.text != text) l.text = text
                placeLabel(l)
                runCatching { wm.updateViewLayout(l, labelParams) }
            }
        }

        private fun placeLabel(l: android.widget.TextView) {
            val w = if (l.width > 0) l.width else l.paint.measureText(l.text.toString()).toInt()
            val h = if (l.height > 0) l.height else (l.textSize * 1.6f).toInt()
            labelParams.x = params.x + params.width / 2 - w / 2
            // Encima de la mascota, o debajo si está arriba del todo
            labelParams.y = if (params.y - h > 0) params.y - h else params.y + params.height
        }

        fun remove() {
            if (!attached) return
            attached = false
            handler.removeCallbacks(tick)
            handler.removeCallbacks(toss)
            view.removeCallbacks(glide)
            runCatching { wm.removeView(view) }
            label?.let { runCatching { wm.removeView(it) } }
            label = null
            pets.remove(this)
            if (pets.none { !it.brain.isChild }) {
                for (p in ArrayList(pets)) p.remove()
                stopSelf()
            }
        }

        /** Dibuja el paso actual; con [glideFor] la ventana se desliza hasta su sitio en ese tiempo. */
        private fun update(addNow: Boolean = false, glideFor: Long = 0) {
            val x = (brain.x * scale).toFloat()
            val y = ((brain.y + brain.offsetY) * scale).toFloat()
            view.invalidate()
            if (addNow) {
                toX = x; toY = y
                params.x = x.roundToInt()
                params.y = y.roundToInt()
                attached = runCatching { wm.addView(view, params) }.isSuccess
                return
            }
            if (!attached) return
            view.removeCallbacks(glide)
            // Al arrastrar, al volar o si reaparece en otro lado, va directo
            val far = Math.hypot((x - toX).toDouble(), (y - toY).toDouble()) > maxOf(params.width, params.height) * 1.5
            if (glideFor > 0 && !brain.dragging && !brain.tossing && !far) {
                fromX = params.x.toFloat(); fromY = params.y.toFloat()
                toX = x; toY = y
                glideStart = SystemClock.uptimeMillis()
                glideMs = glideFor
                glide.run()
            } else {
                toX = x; toY = y
                place(x, y)
            }
        }

        private fun place(x: Float, y: Float) {
            val nx = x.roundToInt()
            val ny = y.roundToInt()
            if (nx == params.x && ny == params.y) return
            params.x = nx
            params.y = ny
            runCatching { wm.updateViewLayout(view, params) }
            if (label != null) refreshLabel()
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
                if (brain.isDying) return false
                if (brain.isChild) {
                    // Las crías (oveja negra, flores...) no se arrastran: doble toque y se van
                    if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastUp < ViewConfiguration.getDoubleTapTimeout()) {
                            brain.kill()
                            handler.removeCallbacks(tick)
                            handler.post(tick)
                        }
                        lastUp = now
                    }
                    return true
                }
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
