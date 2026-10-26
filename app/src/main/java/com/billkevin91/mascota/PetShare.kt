package com.billkevin91.mascota

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * Compartir una mascota: su animations.xml, que sirve en esta app (Importar) y en desktopPet
 * de Windows. "Compartir" lo manda a otra app (WhatsApp, correo...); "Guardar" lo deja donde elijas.
 */
object PetShare {
    const val SAVE_REQUEST = 41
    private const val AUTHORITY = "com.billkevin91.mascota.compartir"

    /** Nombre del archivo: "Oveja azul" → "oveja_azul.xml". */
    fun fileName(name: String) =
        name.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").trim('_').take(40).ifEmpty { "mascota" } + ".xml"

    /** Abre el menú de Android para mandarla a otra app. */
    fun share(activity: Activity, id: String) {
        val name = PetLibrary.name(activity, id)
        val dir = File(activity.cacheDir, "compartir").apply { mkdirs() }
        // Sólo una a la vez en la carpeta: la anterior ya se mandó
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName(name))
        PetLibrary.open(activity, id).use { input -> file.outputStream().use { input.copyTo(it) } }
        val uri = Uri.parse("content://$AUTHORITY/${file.name}")
        android.util.Log.i("Mascotas", "compartir: ${file.name} (${file.length()} bytes)")
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/xml")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, name)
            .putExtra(Intent.EXTRA_TEXT, Lang.t(
                "$name, una mascota para Mascotas en pantalla (Android) o desktopPet (Windows).",
                "$name, a pet for Mascotas (Android) or desktopPet (Windows).",
            ))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, Lang.t("Compartir $name", "Share $name")))
    }

    /** Pregunta dónde guardar el archivo; luego llamar a [saveTo] con lo que devuelva. */
    fun askWhere(activity: Activity, id: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/xml")
            .putExtra(Intent.EXTRA_TITLE, fileName(PetLibrary.name(activity, id)))
        activity.startActivityForResult(intent, SAVE_REQUEST)
    }

    fun saveTo(context: Context, id: String, uri: Uri) {
        val out = requireNotNull(context.contentResolver.openOutputStream(uri)) { tr("No se pudo escribir ahí", "Couldn't write there") }
        out.use { o -> PetLibrary.open(context, id).use { it.copyTo(o) } }
    }
}

/** Da a la app elegida el archivo de [PetShare.share] (sólo ese, sólo para leer). */
class CompartirProvider : ContentProvider() {
    private fun file(uri: Uri): File? {
        val name = uri.lastPathSegment ?: return null
        // Nada de rutas: sólo un nombre de archivo dentro de la carpeta de compartir
        if ("/" in name || name.startsWith(".")) return null
        val f = File(File(context!!.cacheDir, "compartir"), name)
        return f.takeIf { it.isFile }
    }

    override fun onCreate() = true
    override fun getType(uri: Uri) = "text/xml"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = file(uri) ?: throw java.io.FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
        val f = file(uri) ?: return null
        // Nombre y tamaño: algunas apps (WhatsApp, Gmail) los piden para mostrar el archivo
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols).apply {
            addRow(cols.map { if (it == OpenableColumns.SIZE) f.length() else if (it == OpenableColumns.DISPLAY_NAME) f.name else null })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
