package com.alendawang.manhua.model

import android.net.Uri

/**
 * 代表压缩包内的一张图片引用（轻量级，不包含实际图片数据）。
 * 用于替代从压缩包解压到磁盘后的文件 URI，让 Coil 按需从压缩包直接读取。
 *
 * @param archiveUri  压缩包文件的 SAF URI
 * @param entryName   压缩包内的条目路径 (如 "chapter1/001.jpg")
 * @param sortIndex   排序后的页码索引
 * @param sourceType  ZIP 或 RAR
 */
data class ArchiveImageRef(
    val archiveUri: Uri,
    val entryName: String,
    val sortIndex: Int,
    val sourceType: ComicSourceType = ComicSourceType.ZIP
) {
    /** 用于 Coil 缓存 key 和 LazyColumn item key */
    fun cacheKey(): String = "${archiveUri}#${entryName}"
}
