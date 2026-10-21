package com.billkevin91.mascota

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import java.io.File
import kotlin.random.Random

/**
 * Sonidos de las mascotas (como StartSound de desktopPet): al empezar un movimiento que tiene
 * sonido, suena según su probabilidad y se repite [Sound.loop] veces más.
 * Los sonidos van en base64 dentro del XML; se guardan una vez en la caché para reproducirlos.
 */
class PetSounds(private val context: Context) {
    private val playing = ArrayList<MediaPlayer>()
    private val files = HashMap<String, File?>()

    /** [pet] identifica la mascota (para la caché); [animationId] es el movimiento que empezó. */
    fun started(pet: String, def: PetDef, animationId: Int) {
        val sound = def.sounds.firstOrNull { it.animationId == animationId } ?: return
        if (Random.nextInt(100) >= sound.probability) return
        if (playing.size >= MAX_AT_ONCE) return
        val file = files.getOrPut("$pet/$animationId") { save(pet, sound) } ?: return
        runCatching {
            var loops = sound.loop
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(file.path)
            mp.setVolume(VOLUME, VOLUME)
            mp.setOnCompletionListener {
                if (loops-- > 0) it.start() else release(it)
            }
            mp.setOnErrorListener { p, _, _ -> release(p); true }
            mp.prepare()
            mp.start()
            playing.add(mp)
        }
    }

    fun stopAll() {
        for (mp in ArrayList(playing)) release(mp)
        files.clear()
    }

    private fun release(mp: MediaPlayer) {
        runCatching { mp.release() }
        playing.remove(mp)
    }

    private fun save(pet: String, sound: Sound): File? = runCatching {
        // A veces viene como "data:audio/mp3;base64,..."
        val b64 = sound.base64.substringAfter(";base64,").filterNot { it.isWhitespace() }
        val dir = File(context.cacheDir, "sonidos").apply { mkdirs() }
        val name = (pet + "_" + sound.animationId).map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        File(dir, name).apply { writeBytes(Base64.decode(b64, Base64.DEFAULT)) }
    }.getOrNull()

    companion object {
        private const val MAX_AT_ONCE = 4
        private const val VOLUME = 0.6f
    }
}
