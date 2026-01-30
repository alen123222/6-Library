package com.alendawang.manhua.utils

import android.content.Context
import java.io.File

// --- 清理单个漫画的图片缓存 (退出阅读器时调用，保留封面) ---
fun clearComicImageCache(context: Context, chapterUri: android.net.Uri) {
    try {
        val hash = java.security.MessageDigest.getInstance("MD5")
            .digest(chapterUri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        // 删除解压/渲染的图片目录
        val imagesDir = File(context.cacheDir, "comic_images/$hash")
        if (imagesDir.exists() && imagesDir.isDirectory) {
            imagesDir.deleteRecursively()
        }
        
        // 如果是 RAR，也删除压缩包副本
        val archiveFile = File(context.cacheDir, "archives/$hash.rar")
        if (archiveFile.exists()) {
            archiveFile.delete()
        }
        
        // 注意: 保留封面图片 (在 comic_covers/ 目录)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// --- 清理所有漫画图片缓存 (应用启动时调用，保留封面) ---
fun clearAllComicImageCaches(context: Context) {
    try {
        // 删除所有漫画图片缓存
        val comicImagesDir = File(context.cacheDir, "comic_images")
        if (comicImagesDir.exists() && comicImagesDir.isDirectory) {
            comicImagesDir.deleteRecursively()
        }
        
        // 删除所有 RAR 压缩包副本
        val archivesDir = File(context.cacheDir, "archives")
        if (archivesDir.exists() && archivesDir.isDirectory) {
            archivesDir.deleteRecursively()
        }
        
        // 注意: 保留 comic_covers/ 目录中的封面
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
