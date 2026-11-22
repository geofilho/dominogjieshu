package com.geofilho.dominogjieshu.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/** Foto do jogador: guardada pequena (quadrada) no aparelho e enviada ao amigo pelo Bluetooth. */
object Avatar {
    private const val SIZE = 160

    private fun file(ctx: Context) = File(ctx.filesDir, "avatar.jpg")

    fun load(ctx: Context): Bitmap? = file(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun save(ctx: Context, src: Bitmap): Bitmap {
        val square = squareScaled(src)
        file(ctx).outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return square
    }

    fun delete(ctx: Context) { file(ctx).delete() }

    /** Lê a imagem escolhida na galeria já reduzida (e na orientação certa no Android 9+). */
    fun decode(ctx: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                val s = info.size
                val factor = maxOf(1, minOf(s.width, s.height) / (SIZE * 2))
                dec.setTargetSize(s.width / factor, s.height / factor)
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE * 2) sample *= 2
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }.getOrNull()

    private fun squareScaled(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val crop = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        return Bitmap.createScaledBitmap(crop, SIZE, SIZE, true)
    }

    /** Versão bem pequena em texto (base64) para mandar numa linha do protocolo Bluetooth. */
    fun encode(b: Bitmap): String {
        val small = Bitmap.createScaledBitmap(b, 112, 112, true)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 75, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun decodeBase64(s: String): Bitmap? = runCatching {
        val bytes = Base64.decode(s, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
