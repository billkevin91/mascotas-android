package com.billkevin91.mascota

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.os.PowerManager
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
        const val ACTION_TEST_START = "com.billkevin91.mascota.TEST_START"
        const val ACTION_TEST_END = "com.billkevin91.mascota.TEST_END"
        const val ACTION_TEST_CLEAR = "com.billkevin91.mascota.TEST_CLEAR"
        const val ACTION_FORGET = "com.billkevin91.mascota.FORGET"
        const val ACTION_SOUND = "com.billkevin91.mascota.SOUND"
        const val ACTION_FEED = "com.billkevin91.mascota.FEED"
        const val ACTION_TEST_FLOWER = "com.billkevin91.mascota.TEST_FLOWER"
        const val EXTRA_PET = "pet"
        const val EXTRA_SIZE = "size"
        const val EXTRA_ANIMATION = "animation"
        const val EXTRA_LOOP = "loop"
        private const val CHANNEL = "mascotas"
        private const val TAG = "Mascotas"
        private const val MAX_FOOD = 5
        private const val MAX_CHILDREN = 30
        private const val GLIDE_FRAME_MS = 33L
        private const val MAX_FLOWER_MS = 120_000L
        private const val MAX_HEARTS = 8
        /** Con el dedo quieto encima este tiempo, se levanta (como antes al tocarla). */
        private const val HOLD_MS = 250L
        /** Cuántas mascotas normales hay en pantalla (para el botón "Dar de comer"). */
        @Volatile var petsOnScreen = 0
            private set

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

        /** Prueba automática: a la primera mascota de ese tipo le sale una flor (como al comer). */
        fun testFlower(context: Context, pet: String) {
            runCatching { context.startService(Intent(context, PetService::class.java).setAction(ACTION_TEST_FLOWER).putExtra(EXTRA_PET, pet)) }
        }

        /** Cae comida desde arriba; las mascotas van a comérsela. */
        fun feed(context: Context) = send(context, ACTION_FEED)

        fun clear(context: Context) {
            context.startService(Intent(context, PetService::class.java).setAction(ACTION_CLEAR))
        }

        /**
         * Modo prueba aparte: al entrar, las mascotas normales se pausan y se ocultan;
         * al salir, la de prueba se va y las normales siguen donde estaban.
         */
        fun testStart(context: Context) = send(context, ACTION_TEST_START)
        fun testEnd(context: Context) = send(context, ACTION_TEST_END)
        fun testClear(context: Context) = send(context, ACTION_TEST_CLEAR)

        /** Una mascota importada se borró o se reemplazó: fuera de la pantalla y de la memoria. */
        fun forget(context: Context, pet: String) {
            runCatching {
                context.startService(Intent(context, PetService::class.java).setAction(ACTION_FORGET).putExtra(EXTRA_PET, pet))
            }
        }

        private fun send(context: Context, action: String) {
            // Si el servicio no está y la app ya va a segundo plano, Android no deja arrancarlo: no hace falta
            runCatching { context.startService(Intent(context, PetService::class.java).setAction(action)) }
        }
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val pets = ArrayList<PetWindow>()
    private val defs = HashMap<String, PetDef>()
    private val sheets = HashMap<String, Bitmap>()
    private val areas = HashMap<Int, Area>()
    /** La pantalla en píxeles reales (para la comida). */
    private val screen = Area()
    private val foods = ArrayList<FoodWindow>()
    private val hearts = ArrayList<View>()

    // Medir el consumo (sólo para la prueba automática: preferencia "medir")
    private var moves = 0
    private var draws = 0
    private var glideFrames = 0
    private var ticks = 0
    private val measure = object : Runnable {
        override fun run() {
            android.util.Log.i(TAG, "consumo: $moves movimientos de ventana, $draws dibujos, $glideFrames fotogramas de deslizamiento, $ticks pasos en 30 s")
            moves = 0; draws = 0; glideFrames = 0; ticks = 0
            handler.postDelayed(this, 30_000)
        }
    }
    private var speed = 1.0
    private var testSpeed = 1.0
    private var showNames = false
    private var soundOn = false
    private var followClock = true
    private var maxPets = SettingsActivity.MAX_PETS
    /** Ocultarlas cuando otra app ocupa toda la pantalla (Ajustes) y si ahora la ocupa. */
    private var hideFullscreen = true
    private var fullscreen = false
    private var sensor: View? = null
    private lateinit var sounds: PetSounds
    /** Probando movimientos: sólo se ve y se mueve la mascota de prueba. */
    private var testMode = false
    /** Pantalla apagada o bloqueada: nadie las ve, así que se esperan quietas (ahorra batería). */
    private var asleep = false
    /** Reaccionar al teléfono (cargador y batería baja). */
    private var reactOn = true
    /** Android avisó de batería baja (y aún no de que se recuperó). */
    private var lowBattery = false
    private val lastReaction = HashMap<Reaction, Long>()

    enum class Reaction { CHARGE, LOW_BATTERY }

    /** Ahorro de batería (el de la app o el del sistema): sin deslizamiento entre pasos (mueve menos la ventana). */
    private var powerSave = false
    private var saverOn = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> readPowerSave()
                Intent.ACTION_POWER_CONNECTED -> react(Reaction.CHARGE)
                Intent.ACTION_BATTERY_LOW -> { setLowBattery(true); react(Reaction.LOW_BATTERY) }
                Intent.ACTION_BATTERY_OKAY -> setLowBattery(false)
                else -> checkAwake()
            }
        }
    }
    private val awakeCheck = Runnable { checkAwake() }

    /**
     * ¿Se ven las mascotas? No con la pantalla apagada ni con la de bloqueo encima.
     * Se mira el estado real y no el último aviso: al desbloquear con huella o cara los avisos
     * de "pantalla encendida" y "desbloqueado" pueden llegar en cualquier orden.
     */
    private fun checkAwake() {
        val interactive = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        val locked = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
        asleep = !interactive || locked
        handler.removeCallbacks(awakeCheck)
        // Pantalla encendida pero bloqueada: se vuelve a mirar hasta que la desbloquees
        if (interactive && locked) handler.postDelayed(awakeCheck, 500)
        syncPaused()
    }
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, _ -> readPrefs(p) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        measure(screen, 1)
        sounds = PetSounds(this)
        sounds.volume = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getInt(SettingsActivity.PREF_VOLUME, 60) / 100f
        startInForeground()
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        readPrefs(prefs)
        readPowerSave()
        checkAwake()
        setLowBattery(batteryIsLow())
        startSensor()
        if (getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getBoolean("medir", false)) handler.postDelayed(measure, 30_000)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        // Son avisos del sistema: llegan aunque el receptor no sea público
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, filter)
    }

    /** Al arrancar: ¿ya está baja? (Android sólo avisa al cruzar el límite, ~15 %). */
    private fun batteryIsLow(): Boolean {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        val plugged = i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
        return !plugged && level >= 0 && scale > 0 && level * 100 / scale <= 15
    }

    private fun setLowBattery(low: Boolean) {
        lowBattery = low
        for (p in pets) p.brain.sleepy = reactOn && lowBattery && !p.test
    }

    /**
     * Las mascotas reaccionan al teléfono: con el cargador se alegran (⚡), con la batería baja
     * les entra sueño (😴). Sólo si se ven y está activado.
     */
    fun react(kind: Reaction) {
        if (!reactOn || asleep || testMode) return
        val now = SystemClock.uptimeMillis()
        // Enchufar y desenchufar seguido no las vuelve locas
        val gap = 3_000L
        if (now - (lastReaction[kind] ?: -gap) < gap) return
        lastReaction[kind] = now
        android.util.Log.i(TAG, "reacción: $kind")
        for (p in ArrayList(pets)) {
            if (p.test || p.paused || p.brain.isChild) continue
            val reacted = if (kind == Reaction.LOW_BATTERY) p.brain.doze() else p.brain.caress()
            if (!reacted) continue
            p.restart()
            val emoji = when (kind) { Reaction.CHARGE -> "⚡"; Reaction.LOW_BATTERY -> "😴" }
            showHearts(p.centerX().roundToInt(), p.top(), emoji)
        }
    }

    private fun readPowerSave() {
        powerSave = saverOn || (getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode
    }

    private fun readPrefs(prefs: android.content.SharedPreferences) {
        speed = prefs.getFloat(MainActivity.PREF_SPEED, 1f).toDouble()
        testSpeed = prefs.getFloat(MainActivity.PREF_TEST_SPEED, 1f).toDouble()
        showNames = prefs.getBoolean(MainActivity.PREF_NAMES, false)
        val wasOn = soundOn
        soundOn = prefs.getBoolean(MainActivity.PREF_SOUND, false)
        saverOn = prefs.getBoolean(MainActivity.PREF_SAVER, false)
        followClock = prefs.getBoolean(MainActivity.PREF_CLOCK, true)
        reactOn = prefs.getBoolean(MainActivity.PREF_REACT, true)
        maxPets = prefs.getInt(SettingsActivity.PREF_MAX, SettingsActivity.MAX_PETS)
        if (::sounds.isInitialized) sounds.volume = prefs.getInt(SettingsActivity.PREF_VOLUME, 60) / 100f
        val hide = prefs.getBoolean(SettingsActivity.PREF_FULLSCREEN, true)
        if (hide != hideFullscreen) { hideFullscreen = hide; if (::wm.isInitialized) syncPaused() }
        if (::wm.isInitialized) setLowBattery(lowBattery)
        if (::wm.isInitialized) readPowerSave()
        if (!soundOn && ::sounds.isInitialized) sounds.stopAll()
        // El botón de la notificación dice si el sonido está encendido
        if (wasOn != soundOn && ::wm.isInitialized) startInForeground()
        for (p in pets) {
            p.brain.speed = if (p.test) testSpeed else speed
            p.brain.followClock = followClock && !p.test
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
            ACTION_TEST_START -> {
                enterTest()
                if (pets.isEmpty()) stopSelf()
                return START_STICKY
            }
            ACTION_TEST_END -> {
                exitTest()
                if (pets.isEmpty()) stopSelf()
                return START_STICKY
            }
            ACTION_SOUND -> {
                // Botón de la notificación: encender o apagar los sonidos
                val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                prefs.edit().putBoolean(MainActivity.PREF_SOUND, !prefs.getBoolean(MainActivity.PREF_SOUND, false)).apply()
                if (pets.isEmpty()) stopSelf()
                return START_STICKY
            }
            ACTION_FORGET -> {
                val pet = intent.getStringExtra(EXTRA_PET)
                for (p in ArrayList(pets)) if (p.folder == pet) p.remove()
                defs.remove(pet)
                sheets.remove(pet)
                sounds.stopAll()  // y su caché de sonidos
                if (pets.isEmpty()) stopSelf()
                return START_STICKY
            }
            ACTION_TEST_FLOWER -> {
                val pet = intent.getStringExtra(EXTRA_PET)
                pets.firstOrNull { it.folder == pet && !it.brain.isChild && !it.test }?.sproutFlower()
                return START_STICKY
            }
            ACTION_FEED -> {
                if (pets.none { !it.test && !it.brain.isChild }) {
                    android.util.Log.i(TAG, "comida: no hay mascotas")
                    if (pets.isEmpty()) stopSelf()
                    return START_STICKY
                }
                if (foods.size >= MAX_FOOD) foods.first().remove()
                FoodWindow().show()
                return START_STICKY
            }
            ACTION_TEST_CLEAR -> {
                for (p in ArrayList(pets)) if (p.test) p.remove()
                if (pets.isEmpty()) stopSelf()
                return START_STICKY
            }
            ACTION_PLAY -> {
                if (!Settings.canDrawOverlays(this)) {
                    if (pets.isEmpty()) stopSelf()
                    return START_NOT_STICKY
                }
                enterTest()
                val pet = intent.getStringExtra(EXTRA_PET) ?: PetLibrary.BUNDLED.first()
                val id = intent.getIntExtra(EXTRA_ANIMATION, -1)
                val loop = intent.getBooleanExtra(EXTRA_LOOP, false)
                if (id < 0) {
                    // Que se mueva sola (la de prueba): fuera repeticiones y crías sobrantes (la tina...)
                    for (p in ArrayList(pets)) {
                        if (!p.test) continue
                        if (p.brain.isChild) p.dismiss() else p.brain.stopLoop()
                    }
                } else {
                    // Siempre la mascota de prueba (nunca una normal); si es de otro tipo, se cambia
                    val target = pets.lastOrNull {
                        it.test && !it.brain.isChild && (!it.brain.isDying || it.brain.loopId > 0) && it.folder == pet
                    } ?: run {
                        for (p in ArrayList(pets)) if (p.test) p.remove()
                        addPet(pet, getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getFloat(MainActivity.PREF_SIZE, 1f), test = true)
                    }
                    target?.play(id, loop)
                }
                if (pets.isEmpty()) stopSelf()
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
        measure(screen, 1)
        for (f in ArrayList(foods)) f.drop()
    }

    override fun onDestroy() {
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener)
        runCatching { unregisterReceiver(screenReceiver) }
        sensor?.let { runCatching { wm.removeView(it) } }
        removeAll()
        sounds.stopAll()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Mascotas en pantalla", "Mascotas: pets on your screen"), NotificationManager.IMPORTANCE_LOW))
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val more = PendingIntent.getService(this, 1, Intent(this, PetService::class.java).setAction(ACTION_ADD), flags)
        val clear = PendingIntent.getService(this, 2, Intent(this, PetService::class.java).setAction(ACTION_CLEAR), flags)
        val sound = PendingIntent.getService(this, 3, Intent(this, PetService::class.java).setAction(ACTION_SOUND), flags)
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val n = builder
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setContentTitle(tr("Tus mascotas están paseando", "Your pets are out for a walk"))
            .setContentText(tr("Arrástralas, lánzalas o tócalas dos veces para despedirlas", "Drag them, throw them or double-tap to say goodbye"))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null as Icon?, tr("Otra más", "One more"), more).build())
            .addAction(Notification.Action.Builder(null as Icon?, tr("Quitar todas", "Remove all"), clear).build())
            .addAction(Notification.Action.Builder(null as Icon?, if (soundOn) tr("🔊 Sonido: sí", "🔊 Sound: on") else tr("🔇 Sonido: no", "🔇 Sound: off"), sound).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    // ---- Mascotas ----

    private fun enterTest() {
        if (testMode) return
        testMode = true
        syncPaused()
    }

    private fun exitTest() {
        testMode = false
        for (p in ArrayList(pets)) if (p.test) p.remove()
        syncPaused()
    }

    /** Se mueve sólo lo que se ve: nada con la pantalla apagada; en modo prueba, sólo la de prueba. */
    private fun shouldRun(p: PetWindow) = onView() && (!testMode || p.test)

    /**
     * Ventanita invisible de 1 píxel que se entera de si la barra de estado está oculta:
     * así se sabe que un juego o un vídeo ocupa toda la pantalla.
     */
    private fun startSensor() {
        val v = View(this)
        val lp = WindowManager.LayoutParams(
            1, 1, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "Sensor-mascota"
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }
        if (Build.VERSION.SDK_INT >= 30) {
            v.setOnApplyWindowInsetsListener { _, insets ->
                setFullscreen(!insets.isVisible(WindowInsets.Type.statusBars()))
                insets
            }
        } else {
            @Suppress("DEPRECATION")
            v.setOnSystemUiVisibilityChangeListener { vis -> setFullscreen(vis and View.SYSTEM_UI_FLAG_FULLSCREEN != 0) }
        }
        if (runCatching { wm.addView(v, lp) }.isSuccess) sensor = v
    }

    private fun setFullscreen(on: Boolean) {
        if (on == fullscreen) return
        fullscreen = on
        android.util.Log.i(TAG, "pantalla completa: $on")
        syncPaused()
    }

    /** Se ve algo nuestro ahora (no con la pantalla apagada ni con un juego o vídeo a pantalla completa). */
    private fun onView() = !asleep && !(hideFullscreen && fullscreen)

    private fun syncPaused() {
        for (p in ArrayList(pets)) if (shouldRun(p)) p.resume() else p.pause()
        // La comida también se esconde (y los corazones no salen: ver showHearts)
        for (f in foods) f.hidden(!onView())
    }

    private fun addPet(folder: String, size: Float, test: Boolean = false): PetWindow? {
        if (!test && pets.count { !it.brain.isChild && !it.test } >= maxPets) {
            android.widget.Toast.makeText(this, tr("Ya hay $maxPets mascotas: es el máximo (se cambia en Ajustes)",
                "There are already $maxPets pets: that's the maximum (change it in Settings)"), android.widget.Toast.LENGTH_LONG).show()
            return null
        }
        if (!PetLibrary.exists(this, folder)) return null
        val def = defs.getOrPut(folder) { PetLibrary.load(this, folder) }
        val sheet = sheets.getOrPut(folder) { PetLibrary.sheet(def) }
        val density = resources.displayMetrics.density
        val scale = (density * size).roundToInt().coerceAtLeast(1)
        val area = areas.getOrPut(scale) { Area().also { measure(it, scale) } }
        val brain = PetBrain(def, area, sheet.width / def.tilesX, sheet.height / def.tilesY)
        brain.scale = scale
        brain.others = ::allBrains
        brain.speed = if (test) testSpeed else speed
        brain.followClock = followClock && !test
        brain.sleepy = reactOn && lowBattery && !test
        brain.start()
        val w = PetWindow(brain, sheet, scale, folder, test = test).takeIf { it.show() }
        // Una normal que llega mientras se prueban movimientos (p. ej. "Otra más") espera oculta
        if (w != null && !shouldRun(w)) w.pause()
        return w
    }

    /** Las que están en pantalla (las pausadas no chocan con nadie). */
    private fun allBrains(): List<PetBrain> = brainsCache ?: pets.filter { !it.paused }.map { it.brain }.also { brainsCache = it }

    /** La lista de [allBrains] se rehace sólo si cambian las mascotas (antes, en cada paso de cada una). */
    private var brainsCache: List<PetBrain>? = null

    private fun removeAll() {
        for (p in ArrayList(pets)) p.remove()
        for (f in ArrayList(foods)) f.remove()
        for (h in ArrayList(hearts)) removeHearts(h)
    }

    private fun countPets() {
        petsOnScreen = pets.count { !it.test && !it.brain.isChild }
    }

    /** La comida (ya en el suelo) más cercana a esa mascota. */
    /** Algo que se pueden comer: la comida que cae o una flor que tocaste. */
    private interface Snack {
        fun ready(): Boolean
        fun centerX(): Float
        fun bottom(): Float
        fun eaten()
        val isFlower: Boolean
    }

    private fun nearestFood(p: PetWindow): Snack? {
        val cx = p.centerX()
        val flowers = pets.filter { it.treat }.map { it.asSnack }
        return (foods + flowers).filter { it.ready() }.minByOrNull { Math.abs(it.centerX() - cx) }
    }

    /** Corazoncitos (o [emoji], si se da) que suben y se desvanecen: caricias, comida y reacciones. */
    private fun showHearts(cx: Int, top: Int, emoji: String? = null) {
        if (hearts.size >= MAX_HEARTS || !onView()) return
        val d = resources.displayMetrics.density
        val w = (64 * d).toInt()
        val h = (90 * d).toInt()
        val lp = WindowManager.LayoutParams(
            w, h, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cx - w / 2
            y = top - h + (16 * d).toInt()
            title = if (emoji == null) "Corazones-mascota" else "Burbuja-mascota"
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }
        val start = SystemClock.uptimeMillis()
        val view = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 77, 109); textAlign = Paint.Align.CENTER }
            private val path = android.graphics.Path()
            override fun onDraw(canvas: Canvas) {
                val t = (SystemClock.uptimeMillis() - start) / 1400f
                if (t >= 1f) {
                    // Fuera de onDraw: quitar la ventana mientras se dibuja no es seguro
                    post { removeHearts(this) }
                    return
                }
                // Tres corazones que salen uno detrás de otro, suben haciendo eses y se desvanecen
                for (i in 0 until 3) {
                    val k = ((t * 1.4f) - i * 0.2f).coerceIn(0f, 1f)
                    if (k <= 0f) continue
                    val size = width * (0.28f - i * 0.04f)
                    val hx = width * (0.5f + 0.22f * kotlin.math.sin(k * 6f + i * 2f))
                    val hy = height - size - k * (height - size * 1.5f)
                    paint.alpha = ((1f - k) * 255).toInt()
                    if (emoji == null) heart(canvas, hx, hy, size)
                    else {
                        paint.textSize = size * 1.2f
                        canvas.drawText(emoji, hx, hy + size, paint)
                    }
                }
                postInvalidateOnAnimation()
            }

            private fun heart(c: Canvas, cx: Float, top: Float, s: Float) {
                path.reset()
                path.moveTo(cx, top + s * 0.3f)
                path.cubicTo(cx, top, cx - s * 0.5f, top, cx - s * 0.5f, top + s * 0.3f)
                path.cubicTo(cx - s * 0.5f, top + s * 0.6f, cx, top + s * 0.8f, cx, top + s)
                path.cubicTo(cx, top + s * 0.8f, cx + s * 0.5f, top + s * 0.6f, cx + s * 0.5f, top + s * 0.3f)
                path.cubicTo(cx + s * 0.5f, top, cx, top, cx, top + s * 0.3f)
                c.drawPath(path, paint)
            }
        }
        if (runCatching { wm.addView(view, lp) }.isSuccess) hearts.add(view)
    }

    private fun removeHearts(v: View) {
        if (hearts.remove(v)) runCatching { wm.removeView(v) }
    }

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    /**
     * Comida que cae desde arriba y se queda en el suelo. Se puede arrastrar para llevarla
     * donde quieras (las mascotas la siguen) y quitar con doble toque. Si nadie se la come, se va sola.
     */
    private inner class FoodWindow : Snack {
        private val size = (36 * resources.displayMetrics.density *
            getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getFloat(MainActivity.PREF_SIZE, 1f)).roundToInt()
        private val emoji = listOf("🍎", "🍪", "🍓", "🥕", "🍌", "🧀").random()
        private var x = 0f
        private var y = 0f
        private var vy = 0f
        private var landed = false
        private var dragging = false
        private var attached = false
        private var lastFrame = 0L
        private val params = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "Comida-mascota"
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }
        private val fall = object : Runnable {
            override fun run() {
                if (!attached || dragging) return
                val now = SystemClock.uptimeMillis()
                val dt = ((now - lastFrame).coerceIn(1, 50)) / 1000f
                lastFrame = now
                vy += 2500f * resources.displayMetrics.density * dt
                y += vy * dt
                val floor = (screen.bottom - size).toFloat()
                if (y >= floor) { y = floor; landed = true }
                place()
                if (!landed) handler.postDelayed(this, 16)
            }
        }
        private val expire = Runnable { remove() }
        private val view = object : View(this@PetService) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
            private var grabX = 0f
            private var grabY = 0f
            private var lastUp = 0L

            override fun onDraw(canvas: Canvas) {
                paint.textSize = height * 0.8f
                canvas.drawText(emoji, width / 2f, height * 0.8f, paint)
            }

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastUp < ViewConfiguration.getDoubleTapTimeout()) { remove(); return true }
                        dragging = true
                        landed = false
                        handler.removeCallbacks(fall)
                        grabX = e.rawX - x
                        grabY = e.rawY - y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        x = e.rawX - grabX
                        y = e.rawY - grabY
                        place()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        lastUp = SystemClock.uptimeMillis()
                        dragging = false
                        drop()
                    }
                }
                return true
            }
        }

        fun show() {
            val left = screen.left + (screen.right - screen.left - size) * (0.2f + 0.6f * Math.random().toFloat())
            x = left
            y = screen.top.toFloat()
            params.x = x.roundToInt()
            params.y = y.roundToInt()
            attached = runCatching { wm.addView(view, params) }.isSuccess
            android.util.Log.i(TAG, "comida: cae $emoji (${if (attached) "en pantalla" else "no se pudo mostrar"})")
            if (!attached) return
            foods.add(this)
            hidden(!onView())
            handler.postDelayed(expire, 3 * 60_000L)
            drop()
        }

        /** Que caiga desde donde está hasta el suelo. */
        fun drop() {
            if (!attached) return
            landed = false
            vy = 0f
            lastFrame = SystemClock.uptimeMillis()
            handler.removeCallbacks(fall)
            handler.post(fall)
        }

        private fun place() {
            x = x.coerceIn(screen.left.toFloat(), (screen.right - size).toFloat().coerceAtLeast(screen.left.toFloat()))
            params.x = x.roundToInt()
            params.y = y.roundToInt()
            runCatching { wm.updateViewLayout(view, params) }
        }

        fun hidden(h: Boolean) {
            view.visibility = if (h) View.GONE else View.VISIBLE
        }

        /** En el suelo y sin arrastrar: ya se puede ir a por ella. */
        override fun ready() = attached && landed && !dragging
        override fun centerX() = x + size / 2f
        override fun bottom() = y + size
        override fun eaten() = remove()
        override val isFlower = false

        fun remove() {
            if (!attached) return
            attached = false
            handler.removeCallbacks(fall)
            handler.removeCallbacks(expire)
            runCatching { wm.removeView(view) }
            foods.remove(this)
        }
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
        /** Mascota del modo prueba (y sus crías). */
        val test: Boolean = parent?.test ?: false,
    ) {
        var paused = false
            private set

        fun centerX() = params.x + params.width / 2f

        /**
         * Le sale una flor de las que se quedan creciendo (bloom; las "flower" son las que se come al
         * momento), mejor una que salga a su lado. Para la prueba automática.
         */
        fun sproutFlower() {
            val def = brain.def
            val kids = def.children.values.flatten()
            fun named(c: ChildDef, s: String) = def.animations[c.next]?.name?.lowercase()?.contains(s) == true
            val c = kids.firstOrNull { named(it, "bloom") && "imageY" in it.y.text } ?: kids.firstOrNull { named(it, "bloom") }
                ?: kids.firstOrNull { named(it, "flower") } ?: return
            val cb = PetBrain(def, brain.area, brain.frameW, brain.frameH, ChildRequest(c, brain.x, brain.y, !brain.movingLeft, 1, 50))
            cb.scale = scale
            cb.others = ::allBrains
            cb.speed = brain.speed
            cb.start()
            val w = PetWindow(cb, sheet, scale, folder, parent = this)
            if (w.show()) android.util.Log.i(TAG, "flor: aparece")
        }

        /** Una flor que tocaste: ahora es comida para las demás. */
        var treat = false
        private var lastInfo = 0L
        private var landedLogged = false
        private val born = SystemClock.uptimeMillis()

        /** Flores de las ovejas (flower, bloom...): se pueden comer y se marchitan antes. */
        fun isFlower() = brain.isChild && brain.animationName.lowercase().let { "flower" in it || "bloom" in it || "flor" in it }

        val asSnack = object : Snack {
            override fun ready() = attached && !paused && treat && !brain.isDying &&
                brain.y + brain.frameH >= brain.area.bottom - 2
            override fun centerX() = this@PetWindow.centerX()
            override fun bottom() = (params.y + params.height).toFloat()
            override fun eaten() { treat = false; remove() }
            override val isFlower = true
        }
        fun top() = params.y
        fun height() = params.height

        /** Que el cambio de movimiento se vea ya. */
        fun restart() {
            handler.removeCallbacks(tick)
            handler.post(tick)
        }

        init {
            // Las flores son adorno: no la tienen ocupada (sí la tina u otras crías de una escena)
            brain.hasKids = { pets.any { it.parent === this && !it.isFlower() } }
        }
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
        // Lo último que se dibujó
        private var drawnFrame = -1
        private var drawnOpacity = -1.0
        private var drawnLeft = false

        // Deslizamiento entre pasos (las "Smooth movements" de desktopPet). Sin esto, con
        // la mascota ampliada la ventana salta varios píxeles de golpe en cada paso.
        private var fromX = 0f
        private var fromY = 0f
        private var toX = 0f
        private var toY = 0f
        private var glideMs = 0L
        // El deslizamiento va en [glideSteps] saltos iguales, de píxel de mascota en píxel de mascota
        // (cada uno son [scale] píxeles de la pantalla): sólo despierta cuando de verdad toca moverse
        private var glideSteps = 0
        private var glideStep = 0
        private val glide = object : Runnable {
            override fun run() {
                if (!attached) return
                glideFrames++
                glideStep++
                if (glideStep >= glideSteps) { place(toX, toY); return }
                fun snap(d: Float) = (d / scale).roundToInt() * scale.toFloat()
                val k = glideStep.toFloat() / glideSteps
                place(fromX + snap((toX - fromX) * k), fromY + snap((toY - fromY) * k))
                view.postDelayed(this, glideMs / glideSteps)
            }
        }

        private val tick = object : Runnable {
            override fun run() {
                if (!attached || paused) return
                val animBefore = brain.animationId
                val restartsBefore = brain.restarts
                // La comida: no si es de prueba, una cría o está en medio de algo con crías (la tina)
                val food = if (test || brain.isChild) null else nearestFood(this@PetWindow)
                brain.target = food?.let { Target(it.centerX().toDouble() / scale, it.bottom().toDouble() / scale) }
                // Prueba automática: cada 5 s, qué hace cada una respecto a una flor que tocaste
                if (food?.isFlower == true && SystemClock.uptimeMillis() - lastInfo > 5000) {
                    lastInfo = SystemClock.uptimeMillis()
                    android.util.Log.i(TAG, "flor: $folder ${brain.targetInfo()}")
                }
                val delay = brain.tick()
                ticks++
                if (brain.takeArrived()) {
                    android.util.Log.i(TAG, "comida: se la comió $folder" + if (food?.isFlower == true) " (flor)" else "")
                    food?.eaten()
                    showHearts(centerX().roundToInt(), params.y)
                }
                if (soundOn) for (id in brain.takeStarted()) sounds.started(folder, brain.def, id, always = test) else brain.takeStarted()
                if (brain.restarts != restartsBefore) {
                    // Modo prueba: empieza de nuevo, las crías de la vuelta anterior sobran
                    pets.filter { it.parent === this@PetWindow }.forEach { it.dismiss() }
                } else if (brain.animationId != animBefore) {
                    // Cambió de movimiento: si a una cría le falta un suspiro para acabar,
                    // que acabe ya (si no, al salir de la tina se ven dos ovejas un momento)
                    pets.filter { it.parent === this@PetWindow && it.brain.remainingMs() < 300 }.forEach { it.dismiss() }
                }
                for (c in brain.takeChildren()) {
                    // Tope por si un XML hace crías que hacen crías sin parar
                    if (pets.count { it.brain.isChild } >= MAX_CHILDREN) break
                    val cb = PetBrain(brain.def, brain.area, brain.frameW, brain.frameH, c)
                    cb.scale = scale
                    cb.others = ::allBrains
                    cb.speed = brain.speed
                    cb.start()
                    PetWindow(cb, sheet, scale, folder, parent = this@PetWindow).takeIf { it.show() }
                        ?.let { if (!shouldRun(it)) it.pause() }
                }
                if (brain.finished) { remove(); return }
                // En desktopPet una flor puede tardar ~8 minutos en marchitarse: aquí, como mucho 2
                if (isFlower() && SystemClock.uptimeMillis() - born > MAX_FLOWER_MS) brain.kill()
                // Dónde quedó (para la prueba automática, que la toca ahí)
                if (isFlower() && !landedLogged && brain.y + brain.frameH >= brain.area.bottom - 2) {
                    landedLogged = true
                    android.util.Log.i(TAG, "flor: en el suelo en ${centerX().roundToInt()} ${params.y + params.height / 2}")
                }
                update(glideFor = delay.toLong())
                refreshLabel()
                handler.postDelayed(this, delay.toLong())
            }
        }

        private val toss = object : Runnable {
            override fun run() {
                if (!attached || paused || !brain.tossing) return
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
            brainsCache = null
            countPets()
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

        /** Mientras se prueban movimientos o con la pantalla apagada: quieta e invisible (tampoco recibe toques). */
        fun pause() {
            if (paused || !attached) return
            brainsCache = null
            paused = true
            handler.removeCallbacks(tick)
            handler.removeCallbacks(toss)
            view.removeCallbacks(glide)
            view.visibility = View.GONE
            refreshLabel()
        }

        fun resume() {
            if (!paused || !attached) return
            brainsCache = null
            paused = false
            view.visibility = View.VISIBLE
            if (brain.tossing) {
                lastToss = SystemClock.uptimeMillis()
                handler.post(toss)
            }
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
            // El letrero es cosa del modo prueba: sólo en la mascota de prueba
            val want = showNames && attached && !brain.isChild && test && !paused
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
            brainsCache = null
            countPets()
            if (pets.none { !it.brain.isChild }) {
                for (p in ArrayList(pets)) p.remove()
                // Un momento después: al cambiar la mascota de prueba se quita una y llega otra
                handler.post { if (pets.isEmpty()) stopSelf() }
            }
        }

        /** Dibuja el paso actual; con [glideFor] la ventana se desliza hasta su sitio en ese tiempo. */
        private fun update(addNow: Boolean = false, glideFor: Long = 0) {
            val x = (brain.x * scale).toFloat()
            val y = ((brain.y + brain.offsetY) * scale).toFloat()
            // Sólo se vuelve a dibujar si cambia algo (durmiendo, el dibujo se repite mucho)
            if (addNow || brain.frame != drawnFrame || brain.opacity != drawnOpacity || brain.movingLeft != drawnLeft) {
                drawnFrame = brain.frame; drawnOpacity = brain.opacity; drawnLeft = brain.movingLeft
                view.invalidate()
                draws++
            }
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
            if (glideFor > 0 && !powerSave && !brain.dragging && !brain.tossing && !far) {
                fromX = params.x.toFloat(); fromY = params.y.toFloat()
                toX = x; toY = y
                glideMs = glideFor
                // Un salto por píxel de mascota, pero no más de ~30 por segundo
                val pixels = (maxOf(Math.abs(toX - fromX), Math.abs(toY - fromY)) / scale).roundToInt()
                glideSteps = minOf(pixels, (glideFor / GLIDE_FRAME_MS).toInt()).coerceAtLeast(1)
                glideStep = 0
                if (glideSteps <= 1) place(toX, toY) else view.postDelayed(glide, glideMs / glideSteps)
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
            moves++
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
            // x, y y tiempo; Double y no Float: con el teléfono encendido varios días
            // uptimeMillis es tan grande que un Float lo redondea a 16-32 ms
            private val samples = ArrayDeque<DoubleArray>()
            /** Ya se levantó (arrastrando); si no, al soltar fue un toque. */
            private var grabbed = false
            private val hold = Runnable { if (attached && !grabbed) startGrab() }
            private val offerFlower = Runnable {
                if (!attached || treat) return@Runnable
                treat = true
                android.util.Log.i(TAG, "flor: lista para comer")
                showHearts(centerX().roundToInt(), params.y)
            }
            private val caress = Runnable {
                if (attached && !paused && brain.caress()) {
                    android.util.Log.i(TAG, "caricia: $folder hace ${brain.animationName}")
                    showHearts(centerX().roundToInt(), params.y)
                    handler.removeCallbacks(tick)
                    handler.post(tick)
                }
            }

            private fun startGrab() {
                grabbed = true
                handler.removeCallbacks(caress)
                handler.removeCallbacks(toss)
                brain.grab()
                brain.offsetY = 0.0
                // Que el cambio de dibujo se vea ya
                handler.removeCallbacks(tick)
                handler.post(tick)
            }

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
                if (brain.isDying && brain.loopId <= 0) return false
                if (brain.isChild) {
                    // Las crías (oveja negra, flores...) no se arrastran: doble toque y se van.
                    // Una flor con un toque se vuelve comida: la mascota más cercana va a comérsela.
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastUp < ViewConfiguration.getDoubleTapTimeout()) {
                                handler.removeCallbacks(offerFlower)
                                android.util.Log.i(TAG, "cría quitada con doble toque: ${brain.animationName}")
                                brain.kill()
                                handler.removeCallbacks(tick)
                                handler.post(tick)
                            }
                            lastUp = now
                        }
                        MotionEvent.ACTION_UP -> if (isFlower() && !treat) {
                            handler.removeCallbacks(offerFlower)
                            handler.postDelayed(offerFlower, ViewConfiguration.getDoubleTapTimeout().toLong())
                        }
                    }
                    return true
                }
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastUp < ViewConfiguration.getDoubleTapTimeout() &&
                            abs(e.rawX - downX) < slop * 2 && abs(e.rawY - downY) < slop * 2
                        ) {
                            // Doble toque: se despide (y no hay caricia del primer toque)
                            handler.removeCallbacks(caress)
                            handler.removeCallbacks(toss)
                            brain.kill()
                            return true
                        }
                        downX = e.rawX
                        downY = e.rawY
                        grabX = e.rawX - params.x
                        grabY = e.rawY - params.y
                        samples.clear()
                        samples.addLast(doubleArrayOf(e.rawX.toDouble(), e.rawY.toDouble(), now.toDouble()))
                        grabbed = false
                        // Volando se atrapa al momento; si no, se levanta al moverla o al dejar el dedo encima
                        if (brain.tossing) startGrab() else handler.postDelayed(hold, HOLD_MS)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!grabbed) {
                            if (abs(e.rawX - downX) < slop && abs(e.rawY - downY) < slop) return true
                            handler.removeCallbacks(hold)
                            startGrab()
                        }
                        brain.x = ((e.rawX - grabX) / scale).toDouble()
                        brain.y = ((e.rawY - grabY) / scale).toDouble()
                        brain.offsetY = 0.0
                        val now = SystemClock.uptimeMillis()
                        samples.addLast(doubleArrayOf(e.rawX.toDouble(), e.rawY.toDouble(), now.toDouble()))
                        while (samples.size > 2 && samples[1][2] <= now - 80) samples.removeFirst()
                        update()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val now = SystemClock.uptimeMillis()
                        lastUp = now
                        handler.removeCallbacks(hold)
                        if (!grabbed) {
                            // Un toque: caricia, si no llega el segundo toque de la despedida
                            if (e.actionMasked == MotionEvent.ACTION_UP) {
                                handler.removeCallbacks(caress)
                                handler.postDelayed(caress, ViewConfiguration.getDoubleTapTimeout().toLong())
                            }
                            return true
                        }
                        grabbed = false
                        var vx = 0.0
                        var vy = 0.0
                        val first = samples.firstOrNull()
                        val last = samples.lastOrNull()
                        if (first != null && last != null) {
                            val elapsed = last[2] - first[2]
                            if (elapsed >= 8 && now - last[2] <= 80) {
                                // Misma fuerza que desktopPet, en píxeles de mascota
                                vx = (last[0] - first[0]) / scale / elapsed * 10
                                vy = (last[1] - first[1]) / scale / elapsed * 10
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
