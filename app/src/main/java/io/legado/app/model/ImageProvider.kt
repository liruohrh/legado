package io.legado.app.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Size
import androidx.collection.LruCache
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppLog.putDebug
import io.legado.app.constant.PageAnim
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isEpub
import io.legado.app.help.book.isPdf
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.localBook.EpubFile
import io.legado.app.model.localBook.PdfFile
import io.legado.app.utils.BitmapCache
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.SvgUtils
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

object ImageProvider {

    private val errorBitmap: Bitmap by lazy {
        BitmapFactory.decodeResource(appCtx.resources, R.drawable.image_loading_error)
    }

    /**
     * 缓存bitmap LruCache实现
     * filePath bitmap
     */
    private const val M = 1024 * 1024
    val cacheSize: Int
        get() {
            if (AppConfig.bitmapCacheSize <= 0) {
                AppConfig.bitmapCacheSize = 50
            }
            return AppConfig.bitmapCacheSize * M
        }
    private val maxCacheSize = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
        min(128 * M, Runtime.getRuntime().maxMemory().toInt())
    } else {
        256 * M
    }
    private val asyncLoadingImages = ConcurrentHashMap.newKeySet<String>()
    val bitmapLruCache = object : LruCache<String, Bitmap>(cacheSize) {

        override fun sizeOf(filePath: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }

        override fun entryRemoved(
            evicted: Boolean,
            filePath: String,
            oldBitmap: Bitmap,
            newBitmap: Bitmap?
        ) {
            //错误图片不能释放,占位用,防止一直重复获取图片
            if (oldBitmap != errorBitmap) {
                BitmapCache.add(oldBitmap)
                //oldBitmap.recycle()
                //putDebug("ImageProvider: trigger bitmap recycle. URI: $filePath")
                //putDebug("ImageProvider : cacheUsage ${size()}bytes / ${maxSize()}bytes")
            }
        }

    }

    fun put(key: String, bitmap: Bitmap) {
        bitmapLruCache.put(key, bitmap)
    }

    fun get(key: String): Bitmap? {
        return bitmapLruCache.get(key)
    }

    private fun getNotRecycled(key: String): Bitmap? {
        val bitmap = bitmapLruCache.get(key) ?: return null
        if (bitmap.isRecycled) {
            bitmapLruCache.remove(key)
            return null
        }
        return bitmap
    }

    /**
     *缓存网络图片和epub图片
     */
    suspend fun cacheImage(
        book: Book,
        src: String,
        bookSource: BookSource?
    ): File {
        return withContext(IO) {
            val vFile = BookHelp.getImage(book, src)
            if (!vFile.exists()) {
                if (book.isEpub) {
                    EpubFile.getImage(book, src)?.use { input ->
                        val newFile = FileUtils.createFileIfNotExist(vFile.absolutePath)
                        FileOutputStream(newFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } else if (book.isPdf) {
                    PdfFile.getImage(book, src)?.use { input ->
                        val newFile = FileUtils.createFileIfNotExist(vFile.absolutePath)
                        FileOutputStream(newFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    BookHelp.saveImage(bookSource, book, src)
                }
            }
            return@withContext vFile
        }
    }

    /**
     *获取图片宽度高度信息
     */
    suspend fun getImageSize(
        book: Book,
        src: String,
        bookSource: BookSource?
    ): Size {
        val file = cacheImage(book, src, bookSource)
        val op = BitmapFactory.Options()
        // inJustDecodeBounds如果设置为true,仅仅返回图片实际的宽和高,宽和高是赋值给opts.outWidth,opts.outHeight;
        op.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, op)
        if (op.outWidth < 1 && op.outHeight < 1) {
            //svg size
            val size = SvgUtils.getSize(file.absolutePath)
            if (size != null) return size
            putDebug("ImageProvider: $src Unsupported image type")
            //file.delete() 重复下载
            return Size(errorBitmap.width, errorBitmap.height)
        }
        return Size(op.outWidth, op.outHeight)
    }

    /**
     * <p>
     * 从章节缓存中任取一张作为ImageSize
     * <p>
     * 注意：可能在大小不太一样时有一点点问题？不太知道，因为没怎么看明白 {@link io.legado.app.ui.book.read.page.provider.ChapterProvider.getTextChapter}
     * <p>
     *     但是应该不会怎么样
     */
    suspend fun getAnyImageSize(
        book: Book
    ): Size {
        val file =  BookHelp.getAnyImage(book)
        val op = BitmapFactory.Options()
        // inJustDecodeBounds如果设置为true,仅仅返回图片实际的宽和高,宽和高是赋值给opts.outWidth,opts.outHeight;
        op.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, op)
        if (op.outWidth < 1 && op.outHeight < 1) {
            //svg size
            val size = SvgUtils.getSize(file.absolutePath)
            if (size != null) return size
            putDebug("ImageProvider: $file Unsupported image type")
            //file.delete() 重复下载
            return Size(errorBitmap.width, errorBitmap.height)
        }
        return Size(op.outWidth, op.outHeight)
    }

    /**
     *获取bitmap 使用LruCache缓存
     */
    fun getImage(
        book: Book,
        src: String,
        width: Int,
        height: Int? = null,
        block: (() -> Unit)? = null
    ): Bitmap? {
        //src为空白时 可能被净化替换掉了 或者规则失效
        if (book.getUseReplaceRule() && src.isBlank()) {
            book.setUseReplaceRule(false)
            appCtx.toastOnUi(R.string.error_image_url_empty)
        }
        val vFile = BookHelp.getImage(book, src)
        if (!vFile.exists()) return errorBitmap
        //epub文件提供图片链接是相对链接，同时阅读多个epub文件，缓存命中错误
        //bitmapLruCache的key同一改成缓存文件的路径
        val cacheBitmap = getNotRecycled(vFile.absolutePath)
        if (cacheBitmap != null) return cacheBitmap
        if (height != null && AppConfig.asyncLoadImage && ReadBook.pageAnim() == PageAnim.scrollPageAnim) {
            if (asyncLoadingImages.contains(vFile.absolutePath)) {
                return null
            }
            asyncLoadingImages.add(vFile.absolutePath)
            Coroutine.async {
                BitmapUtils.decodeBitmap(vFile.absolutePath, width, height)
                    ?: SvgUtils.createBitmap(vFile.absolutePath, width, height)
                    ?: throw NoStackTraceException(appCtx.getString(R.string.error_decode_bitmap))
            }.onSuccess {
                bitmapLruCache.run {
                    if (maxSize() < maxCacheSize && size() + it.byteCount > maxSize() && putCount() - evictionCount() < 5) {
                        resize(min(maxCacheSize, maxSize() + it.byteCount))
                        AppLog.put("图片缓存太小，自动扩增至${(maxSize() / M)}MB。")
                    }
                }
                bitmapLruCache.put(vFile.absolutePath, it)
            }.onError {
                //错误图片占位,防止重复获取
                bitmapLruCache.put(vFile.absolutePath, errorBitmap)
            }.onFinally {
                asyncLoadingImages.remove(vFile.absolutePath)
                block?.invoke()
            }
            return null
        }
        return kotlin.runCatching {
            val bitmap = BitmapUtils.decodeBitmap(vFile.absolutePath, width, height)
                ?: SvgUtils.createBitmap(vFile.absolutePath, width, height)
                ?: throw NoStackTraceException(appCtx.getString(R.string.error_decode_bitmap))
            bitmapLruCache.put(vFile.absolutePath, bitmap)
            bitmap
        }.onFailure {
            //错误图片占位,防止重复获取
            bitmapLruCache.put(vFile.absolutePath, errorBitmap)
        }.getOrDefault(errorBitmap)
    }

}
