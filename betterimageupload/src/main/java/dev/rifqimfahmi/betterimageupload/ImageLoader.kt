package dev.rifqimfahmi.betterimageupload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*
import kotlin.math.min

object ImageLoader {

    fun loadBitmap(
        context: Context,
        imageUri: Uri,
        maxWidth: Float,
        maxHeight: Float,
        useMaxScale: Boolean
    ): Bitmap? {
        val bmOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
        BitmapFactory.decodeStream(inputStream, null, bmOptions)
        val photoW = bmOptions.outWidth.toFloat()
        val photoH = bmOptions.outHeight.toFloat() // original photo. add log below
        var scaleFactor = if (useMaxScale) Math.max(
            photoW / maxWidth,
            photoH / maxHeight
        ) else min(photoW / maxWidth, photoH / maxHeight)
        if (scaleFactor < 1) {
            scaleFactor = 1f
        }
        bmOptions.inJustDecodeBounds = false
        bmOptions.inSampleSize = scaleFactor.toInt()
        if (bmOptions.inSampleSize % 2 != 0) { // check if sample size is divisible by 2
            var sample = 1
            while (sample * 2 < bmOptions.inSampleSize) {
                sample *= 2
            }
            bmOptions.inSampleSize = sample
        }
        var matrix: Matrix? = null
        try {
            val exif = ExifInterface(inputStream!!)
            val orientation: Int = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                1
            )
            matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(
                    90f
                )
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(
                    180f
                )
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(
                    270f
                )
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        scaleFactor /= bmOptions.inSampleSize.toFloat()
        if (scaleFactor > 1) {
            if (matrix == null) {
                matrix = Matrix()
            }
            matrix.postScale(1.0f / scaleFactor, 1.0f / scaleFactor)
        }
        var bitmap: Bitmap? = null
        inputStream?.close()
        val inputStream2: InputStream? = context.contentResolver.openInputStream(imageUri)
            try {
                bitmap = BitmapFactory.decodeStream(inputStream2, null, bmOptions)
                if (bitmap != null) {
                    val newBitmap: Bitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                    if (newBitmap != bitmap) {
                        bitmap.recycle()
                        bitmap = newBitmap
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                inputStream?.close()
            }
        return bitmap
    }

    fun scaleAndSaveImage(
        bitmap: Bitmap?,
        maxWidth: Float,
        maxHeight: Float,
        progressive: Boolean,
        quality: Int,
        cache: Boolean,
        minWidth: Int,
        minHeight: Int
    ): String? {
        return scaleAndSaveImage(
            bitmap,
            CompressFormat.JPEG,
            progressive,
            maxWidth,
            maxHeight,
            quality,
            cache,
            minWidth,
            minHeight,
            false
        )
    }

    fun scaleAndSaveImage(
//        photoSize: TLRPC.PhotoSize?,
        bitmap: Bitmap?,
        compressFormat: CompressFormat?,
        progressive: Boolean,
        maxWidth: Float,
        maxHeight: Float,
        quality: Int,
        cache: Boolean,
        minWidth: Int,
        minHeight: Int,
        forceCacheDir: Boolean
    ): String? {
        if (bitmap == null) {
            return null
        }
        val photoW = bitmap.width.toFloat()
        val photoH = bitmap.height.toFloat()
        if (photoW == 0f || photoH == 0f) {
            return null
        }
        var scaleAnyway = false
        var scaleFactor = Math.max(photoW / maxWidth, photoH / maxHeight)
        if (minWidth != 0 && minHeight != 0 && (photoW < minWidth || photoH < minHeight)) {
            scaleFactor = if (photoW < minWidth && photoH > minHeight) {
                photoW / minWidth
            } else if (photoW > minWidth && photoH < minHeight) {
                photoH / minHeight
            } else {
                Math.max(photoW / minWidth, photoH / minHeight)
            }
            scaleAnyway = true
        }
        val w = (photoW / scaleFactor).toInt()
        val h = (photoH / scaleFactor).toInt()
        if (h == 0 || w == 0) {
            null
        } else try {
            return scaleAndSaveImageInternal(
//                photoSize,
                bitmap,
                compressFormat,
                progressive,
                w,
                h,
                photoW,
                photoH,
                scaleFactor,
                quality,
                cache,
                scaleAnyway,
                forceCacheDir
            )
        } catch (e: Throwable) {
            e.printStackTrace()
//            ImageLoader.getInstance().clearMemory()
            System.gc()
            try {
//                ImageLoader.scaleAndSaveImageInternal(
//                    photoSize,
//                    bitmap,
//                    compressFormat,
//                    progressive,
//                    w,
//                    h,
//                    photoW,
//                    photoH,
//                    scaleFactor,
//                    quality,
//                    cache,
//                    scaleAnyway,
//                    forceCacheDir
//                )
            } catch (e2: Throwable) {
                e.printStackTrace()
                null
            }
        }

        return ""
    }

    private fun scaleAndSaveImageInternal(
//        photoSize: TLRPC.PhotoSize,
        bitmap: Bitmap,
        compressFormat: CompressFormat?,
        progressive: Boolean,
        w: Int,
        h: Int,
        photoW: Float,
        photoH: Float,
        scaleFactor: Float,
        quality: Int,
        cache: Boolean,
        scaleAnyway: Boolean,
        forceCacheDir: Boolean
    ): String? {
//        var photoSize: TLRPC.PhotoSize? = photoSize
        val scaledBitmap: Bitmap = if (scaleFactor > 1 || scaleAnyway) {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }
//        val check = photoSize != null
//        val location: TLRPC.TL_fileLocationToBeDeprecated
//        if (photoSize == null || photoSize.location !is TLRPC.TL_fileLocationToBeDeprecated) {
//            location = TL_fileLocationToBeDeprecated()
//            location.volume_id = Int.MIN_VALUE
//            location.dc_id = Int.MIN_VALUE
//            location.local_id = SharedConfig.getLastLocalId()
//            location.file_reference = ByteArray(0)
//            photoSize = TL_photoSize()
//            photoSize.location = location
//            photoSize.w = scaledBitmap.width
//            photoSize.h = scaledBitmap.height
//            if (photoSize.w <= 100 && photoSize.h <= 100) {
//                photoSize.type = "s"
//            } else if (photoSize.w <= 320 && photoSize.h <= 320) {
//                photoSize.type = "m"
//            } else if (photoSize.w <= 800 && photoSize.h <= 800) {
//                photoSize.type = "x"
//            } else if (photoSize.w <= 1280 && photoSize.h <= 1280) {
//                photoSize.type = "y"
//            } else {
//                photoSize.type = "w"
//            }
//        } else {
//            location = photoSize.location as TLRPC.TL_fileLocationToBeDeprecated
//        }
        var uniqueID = UUID.randomUUID().toString()
        val fileName: String = "test_optim_$uniqueID.jpg"
        val fileDir: File = File("/storage/emulated/0/Download/")
//        fileDir = if (forceCacheDir) {
//            FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE)
//        } else {
//            if (location.volume_id !== Int.MIN_VALUE)
//                FileLoader.getDirectory(FileLoader.MEDIA_DIR_IMAGE)
//            else
//                FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE)
//        }
        val cacheFile = File(fileDir, fileName)
//        if (compressFormat == CompressFormat.JPEG && progressive && BuildVars.DEBUG_VERSION) { // not using native
//            photoSize.size = Utilities.saveProgressiveJpeg(
//                scaledBitmap,
//                scaledBitmap.width,
//                scaledBitmap.height,
//                scaledBitmap.rowBytes,
//                quality,
//                cacheFile.absolutePath
//            )
//        } else {
            val stream = FileOutputStream(cacheFile)
            scaledBitmap.compress(compressFormat, quality, stream) // compress bitmap here
//            if (!cache) {
//                photoSize.size = stream.channel.size().toInt()
//            }
            stream.close()
//        }
//        if (cache) {
//            val stream2 = ByteArrayOutputStream()
//            scaledBitmap.compress(compressFormat, quality, stream2)
//            photoSize.bytes = stream2.toByteArray()
//            photoSize.size = photoSize.bytes.length
//            stream2.close()
//        }
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        return cacheFile.absolutePath
    }
}