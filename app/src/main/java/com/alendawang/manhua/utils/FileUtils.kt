/**
 * FileUtils.kt - 兼容层/重导出入口
 * 
 * 所有文件工具函数已拆分到以下专职文件：
 * - ImageLoaderUtils.kt: 图片加载 (ZIP/RAR/PDF/文件夹)
 * - CacheUtils.kt: 缓存管理
 * - ScannerUtils.kt: 媒体扫描 (漫画/小说/音频)
 * - NovelUtils.kt: 小说文本处理
 * - EpubUtils.kt: EPUB 解析
 * - Helpers.kt: 通用工具函数 (包含 compareNatural)
 * 
 * 此文件保留作为向后兼容层，现有代码无需修改 import 语句。
 * 
 * compareNatural 函数位于 Helpers.kt 中，所有工具类通过导入使用。
 */
@file:Suppress("unused")

package com.alendawang.manhua.utils

// ============================================================================
// 重导出: ImageLoaderUtils.kt
// ============================================================================
// 以下函数已移至 ImageLoaderUtils.kt:
// - getComicSourceType
// - loadComicImages
// - loadImagesFromZip
// - loadImagesFromRar
// - loadImagesFromPdf
// - countImagesInZip
// - countImagesInRar
// - countPagesInPdf
// - countImagesInFolder
// - countComicPages
// - getCoverFromZip
// - getCoverFromRar
// - getCoverFromPdf

// ============================================================================
// 重导出: CacheUtils.kt
// ============================================================================
// 以下函数已移至 CacheUtils.kt:
// - clearComicImageCache
// - clearAllComicImageCaches

// ============================================================================
// 重导出: ScannerUtils.kt
// ============================================================================
// 以下函数已移至 ScannerUtils.kt:
// - scanComicsFlow
// - scanNovelsFlow
// - scanAudiosFlow
// - computeComicProgress

// ============================================================================
// 重导出: NovelUtils.kt
// ============================================================================
// 以下函数已移至 NovelUtils.kt:
// - loadNovelText
// - readNovelText
// - saveNovelText
// - parseNovelChapters
// - extractChapterText
// - isWholeFileChapter
// - replaceChapterText
// - findSiblingLrcFile

// ============================================================================
// 重导出: EpubUtils.kt
// ============================================================================
// 以下函数已移至 EpubUtils.kt:
// - parseEpubFile
// - getEpubChapterContent
// - parseNcx

// ============================================================================
// 注意: compareNatural 函数已在 Helpers.kt 中定义
// ============================================================================
