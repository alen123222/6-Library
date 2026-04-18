package com.alendawang.manhua

import android.content.Context
import coil.ImageLoader
import coil.request.CachePolicy
import com.alendawang.manhua.utils.ArchiveImageFetcherFactory
import com.alendawang.manhua.utils.ArchiveImageKeyer

/**
 * ImageLoader 单例，避免每次组合/加载时重复创建实例。
 * 内部管理内存缓存和磁盘缓存。
 * 注册了自定义 ArchiveImageFetcher，支持直接从 ZIP/RAR 按需加载图片。
 */
object ImageLoaderSingleton {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .components {
                    add(ArchiveImageFetcherFactory())
                    add(ArchiveImageKeyer())
                }
                .build()
                .also { instance = it }
        }
    }
}
