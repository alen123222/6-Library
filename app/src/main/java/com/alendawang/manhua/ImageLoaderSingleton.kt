package com.alendawang.manhua

import android.content.Context
import coil.ImageLoader
import coil.request.CachePolicy

/**
 * ImageLoader 单例，避免每次组合/加载时重复创建实例。
 * 内部管理内存缓存和磁盘缓存。
 */
object ImageLoaderSingleton {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
                .also { instance = it }
        }
    }
}
