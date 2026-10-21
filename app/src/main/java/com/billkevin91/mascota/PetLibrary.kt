package com.billkevin91.mascota

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import java.io.File
import java.io.InputStream

/** Una mascota que se puede soltar: [id] es la carpeta de assets o "user:<carpeta>". */
class PetEntry(val id: String, val name: String, val bundled: Boolean)

/** Resultado de importar: [replaced] si ya había una importada con ese nombre y se actualizó. */
class ImportResult(val entry: PetEntry, val replaced: Boolean)

/**
 * Todas las mascotas: las que vienen con la app (assets/pets) y las importadas por ti
 * (archivos animations.xml de desktopPet guardados en el teléfono). El editor guardará aquí.
 */
object PetLibrary {
    /** Carpetas en assets/pets (mascotas de desktopPet). */
    val BUNDLED = listOf("esheep64", "blue_sheep", "pink_sheep", "neko", "pink_neko", "fox", "pink_fox")
    private val NAMES = mapOf(
        "esheep64" to "eSheep", "blue_sheep" to "Oveja azul", "pink_sheep" to "Oveja rosa",
        "neko" to "Neko", "pink_neko" to "Neko rosa", "fox" to "Zorrito", "pink_fox" to "Zorrito rosa",
    )
    private const val USER = "user:"

    private fun userDir(context: Context) = File(context.filesDir, "pets")
    private fun folderOf(context: Context, id: String) = File(userDir(context), id.removePrefix(USER))

    fun all(context: Context): List<PetEntry> {
        val user = userDir(context).listFiles().orEmpty()
            .filter { File(it, "animations.xml").isFile }
            .sortedBy { it.lastModified() }
            .map { PetEntry(USER + it.name, File(it, "name.txt").takeIf { f -> f.isFile }?.readText()?.trim() ?: it.name, false) }
        return BUNDLED.map { PetEntry(it, NAMES[it] ?: it, true) } + user
    }

    fun exists(context: Context, id: String) =
        if (id.startsWith(USER)) File(folderOf(context, id), "animations.xml").isFile else id in BUNDLED

    fun name(context: Context, id: String) = all(context).firstOrNull { it.id == id }?.name ?: id

    fun open(context: Context, id: String): InputStream =
        if (id.startsWith(USER)) File(folderOf(context, id), "animations.xml").inputStream()
        else context.assets.open("pets/$id/animations.xml")

    fun load(context: Context, id: String): PetDef = open(context, id).use { PetDef.parse(it) }

    fun icon(context: Context, id: String): Bitmap? = runCatching {
        if (id.startsWith(USER)) BitmapFactory.decodeFile(File(folderOf(context, id), "icon.png").path)
        else context.assets.open("pets/$id/icon.png").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    /**
     * Importa un animations.xml (de desktopPet, de su PetEditor o de esheep.petrucci.ch).
     * Lanza una excepción con un mensaje legible si el archivo no sirve.
     */
    fun import(context: Context, input: InputStream): ImportResult {
        // eSheep y gSheep llevan los mismos arreglos que las incluidas (la tina, el baño)
        val bytes = PetCompat.patch(input.readBytes().toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
        val def = try {
            PetDef.parse(bytes.inputStream())
        } catch (e: Exception) {
            throw IllegalArgumentException("No es un animations.xml de desktopPet (${e.message})")
        }
        require(def.animations.isNotEmpty()) { "El archivo no tiene movimientos" }
        val sheet = try { sheet(def) } catch (e: Exception) { null }
        requireNotNull(sheet) { "No se pudo leer la imagen de la mascota" }
        require(sheet.width >= def.tilesX && sheet.height >= def.tilesY) { "La imagen es más pequeña que su cuadrícula de dibujos" }
        val name = def.header.petName.ifEmpty { def.title }.trim().ifEmpty { "Mascota" }
        // Si ya hay una importada con el mismo nombre, se reemplaza (no se duplica)
        val existing = all(context).firstOrNull { !it.bundled && it.name.equals(name, ignoreCase = true) }
        return ImportResult(save(context, existing?.id, name, bytes, def, sheet), existing != null)
    }

    /** Guarda una mascota tuya (nueva si [id] es null). El editor usará esto. */
    fun save(context: Context, id: String?, def: PetDef): PetEntry {
        val name = def.header.petName.ifEmpty { def.title }.trim().ifEmpty { "Mascota" }
        return save(context, id, name, PetXml.write(def).toByteArray(), def, sheet(def))
    }

    private fun save(context: Context, id: String?, name: String, xml: ByteArray, def: PetDef, sheet: Bitmap): PetEntry {
        val dir = if (id != null && id.startsWith(USER)) folderOf(context, id) else newFolder(context, name)
        dir.mkdirs()
        File(dir, "animations.xml").writeBytes(xml)
        File(dir, "name.txt").writeText(name)
        File(dir, "icon.png").outputStream().use { iconFor(def, sheet).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return PetEntry(USER + dir.name, name, false)
    }

    fun delete(context: Context, id: String) {
        if (id.startsWith(USER)) folderOf(context, id).deleteRecursively()
    }

    private fun newFolder(context: Context, name: String): File {
        val base = name.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").trim('_').take(30).ifEmpty { "mascota" }
        var dir = File(userDir(context), base)
        var n = 2
        while (dir.exists()) dir = File(userDir(context), "${base}_${n++}")
        return dir
    }

    /** Icono: el del XML si Android lo entiende; si no, el primer dibujo de la mascota. */
    private fun iconFor(def: PetDef, sheet: Bitmap): Bitmap {
        if (def.header.icon.isNotEmpty()) {
            val bytes = runCatching { Base64.decode(def.header.icon.filterNot { it.isWhitespace() }, Base64.DEFAULT) }.getOrNull()
            val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            if (bmp != null) return bmp
        }
        val first = def.spawns.firstOrNull()?.next?.let { def.animations[it] }?.frames?.firstOrNull()
            ?: def.animations.values.firstOrNull()?.frames?.firstOrNull() ?: 0
        val fw = sheet.width / def.tilesX
        val fh = sheet.height / def.tilesY
        return Bitmap.createBitmap(sheet, (first % def.tilesX) * fw, (first / def.tilesX) * fh, fw, fh)
    }

    /** La hoja de dibujos, con el color de fondo hecho transparente. */
    fun sheet(def: PetDef): Bitmap {
        val bytes = Base64.decode(def.png.filterNot { it.isWhitespace() }, Base64.DEFAULT)
        val opts = BitmapFactory.Options().apply { inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)) { "imagen no válida" }
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
        "", "transparent" -> null
        else -> runCatching { Color.parseColor(name.trim()) or 0xFF000000.toInt() }.getOrNull()
    }
}
