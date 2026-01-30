package com.alendawang.manhua.utils

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.alendawang.manhua.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

// --- 计算漫画阅读进度 ---
suspend fun computeComicProgress(context: Context, history: ComicHistory): Pair<Int, Int> {
    val chapterCounts = mutableListOf<Int>()
    for (chapter in history.chapters) {
        chapterCounts.add(countComicPages(context, chapter.uriString.toUri(), chapter.sourceType, chapter.internalPath))
    }
    val totalPages = chapterCounts.sum()
    if (totalPages <= 0) return 0 to 0

    val safeChapterIndex = history.lastReadChapterIndex.coerceIn(0, maxOf(0, history.chapters.size - 1))
    val pagesBefore = chapterCounts.take(safeChapterIndex).sum()
    val currentChapterPages = chapterCounts.getOrNull(safeChapterIndex) ?: 0
    val isUnread = history.lastReadChapterIndex == 0 && history.lastReadIndex <= 0
    val currentInChapter = if (isUnread) {
        0
    } else {
        (history.lastReadIndex + 1).coerceIn(0, currentChapterPages)
    }
    val currentPage = (pagesBefore + currentInChapter).coerceAtMost(totalPages)
    return totalPages to currentPage
}

// --- 漫画扫描 (支持增量更新, 支持文件夹/ZIP/RAR/PDF) ---
fun scanComicsFlow(
    context: Context, 
    rootTreeUri: Uri, 
    existingComics: Map<String, ComicHistory>,
    onFolderScanning: (String) -> Unit
): Flow<ComicHistory> = flow {
    val rootDoc = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return@flow
    val level1Files = rootDoc.listFiles()
    val currentTime = System.currentTimeMillis()
    
    val archiveExtensions = setOf("zip", "cbz", "rar", "cbr", "pdf")
    
    for (level1File in level1Files) {
        currentCoroutineContext().ensureActive()
        
        val fileName = level1File.name ?: continue
        val fileUri = level1File.uri.toString()
        val existingComic = existingComics[fileUri]
        val fileLastModified = level1File.lastModified()
        
        // 增量扫描：如果文件未变化且已扫描过，跳过
        if (existingComic != null && 
            existingComic.lastScannedAt > 0 && 
            fileLastModified <= existingComic.lastScannedAt) {
            continue
        }
        
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (level1File.isFile && ext in archiveExtensions) {
            withContext(Dispatchers.Main) { onFolderScanning(fileName) }
            
            val sourceType = getComicSourceType(fileName)
            val comicName = fileName.substringBeforeLast('.')
            
            // PDF 不支持章节检测
            if (sourceType == ComicSourceType.PDF) {
                val coverUri = getCoverFromPdf(context, level1File.uri)
                val totalPages = countPagesInPdf(context, level1File.uri)
                if (totalPages > 0) {
                    val chapters = listOf(ComicChapter("全一册", fileUri, sourceType))
                    val comic = if (existingComic != null) {
                        existingComic.copy(
                            name = comicName,
                            coverUriString = coverUri ?: existingComic.coverUriString,
                            chapters = chapters,
                            cachedTotalPages = totalPages,
                            lastScannedAt = currentTime
                        )
                    } else {
                        ComicHistory(
                            id = fileUri,
                            name = comicName,
                            uriString = fileUri,
                            coverUriString = coverUri,
                            timestamp = currentTime,
                            chapters = chapters,
                            cachedTotalPages = totalPages,
                            cachedCurrentPage = 0,
                            lastScannedAt = currentTime
                        )
                    }
                    emit(comic)
                }
                continue
            }
            
            // ZIP/RAR: 收集所有条目名称，检测章节
            val allEntries = when (sourceType) {
                ComicSourceType.ZIP -> collectZipEntries(context, level1File.uri)
                ComicSourceType.RAR -> collectRarEntries(context, level1File.uri)
                else -> emptyList()
            }
            
            val leafDirs = findLeafDirectoriesInArchive(allEntries)
            
            val chapters: List<ComicChapter>
            var totalPages = 0
            var coverUri: String? = null
            
            if (leafDirs.isEmpty()) {
                val pageCount = when (sourceType) {
                    ComicSourceType.ZIP -> countImagesInZip(context, level1File.uri)
                    ComicSourceType.RAR -> countImagesInRar(context, level1File.uri)
                    else -> 0
                }
                if (pageCount > 0) {
                    chapters = listOf(ComicChapter("全一册", fileUri, sourceType))
                    totalPages = pageCount
                    coverUri = when (sourceType) {
                        ComicSourceType.ZIP -> getCoverFromZip(context, level1File.uri)
                        ComicSourceType.RAR -> getCoverFromRar(context, level1File.uri)
                        else -> null
                    }
                } else {
                    continue
                }
            } else {
                val chapterList = mutableListOf<ComicChapter>()
                for (dir in leafDirs) {
                    val chapterName = dir.trimEnd('/').substringAfterLast('/')
                    val pageCount = when (sourceType) {
                        ComicSourceType.ZIP -> countImagesInZip(context, level1File.uri, dir)
                        ComicSourceType.RAR -> countImagesInRar(context, level1File.uri, dir)
                        else -> 0
                    }
                    if (pageCount > 0) {
                        chapterList.add(ComicChapter(chapterName, fileUri, sourceType, dir))
                        totalPages += pageCount
                    }
                }
                if (chapterList.isEmpty()) continue
                chapters = chapterList
                
                val firstChapterPath = chapters.first().internalPath
                coverUri = when (sourceType) {
                    ComicSourceType.ZIP -> getCoverFromZip(context, level1File.uri, firstChapterPath)
                    ComicSourceType.RAR -> getCoverFromRar(context, level1File.uri, firstChapterPath)
                    else -> null
                }
            }
            
            val comic = if (existingComic != null) {
                existingComic.copy(
                    name = comicName,
                    coverUriString = coverUri ?: existingComic.coverUriString,
                    chapters = chapters,
                    cachedTotalPages = totalPages,
                    lastScannedAt = currentTime
                )
            } else {
                ComicHistory(
                    id = fileUri,
                    name = comicName,
                    uriString = fileUri,
                    coverUriString = coverUri,
                    timestamp = currentTime,
                    chapters = chapters,
                    cachedTotalPages = totalPages,
                    cachedCurrentPage = 0,
                    lastScannedAt = currentTime
                )
            }
            emit(comic)
            continue
        }

        // 处理文件夹
        if (!level1File.isDirectory) continue
        
        val comicName = fileName
        withContext(Dispatchers.Main) { onFolderScanning(comicName) }

        try {
            if (level1File.findFile(".nomedia") == null) {
                level1File.createFile("application/octet-stream", ".nomedia")
            }
        } catch (e: Exception) { }

        val subFiles = level1File.listFiles()
        val chapters = mutableListOf<ComicChapter>()
        var coverUri: String? = null
        var totalPages = 0
        
        val folderChapters = subFiles.filter { it.isDirectory }
        if (folderChapters.isNotEmpty()) {
            folderChapters.forEach { chFolder -> 
                chapters.add(ComicChapter(chFolder.name ?: "Chapter", chFolder.uri.toString(), ComicSourceType.FOLDER))
                val chapterImageCount = chFolder.listFiles().count { it.isFile && it.type?.startsWith("image/") == true }
                totalPages += chapterImageCount
            }
            val firstChapterImages = folderChapters.first().listFiles().filter { it.isFile && it.type?.startsWith("image/") == true }
            if (firstChapterImages.isNotEmpty()) coverUri = firstChapterImages.sortedWith { a, b -> compareNatural(a.name?:"", b.name?:"") }.first().uri.toString()
        } else {
            val images = subFiles.filter { it.isFile && it.type?.startsWith("image/") == true }
            if (images.isNotEmpty()) {
                chapters.add(ComicChapter("全一册", level1File.uri.toString(), ComicSourceType.FOLDER))
                coverUri = images.sortedWith { a, b -> compareNatural(a.name?:"", b.name?:"") }.first().uri.toString()
                totalPages = images.size
            }
        }
        chapters.sortWith { c1, c2 -> compareNatural(c1.name, c2.name) }
        
        if (chapters.isNotEmpty()) {
            val comic = if (existingComic != null) {
                existingComic.copy(
                    name = comicName,
                    coverUriString = coverUri ?: existingComic.coverUriString,
                    chapters = chapters,
                    cachedTotalPages = totalPages,
                    lastScannedAt = currentTime
                )
            } else {
                ComicHistory(
                    id = fileUri, 
                    name = comicName, 
                    uriString = fileUri, 
                    coverUriString = coverUri, 
                    timestamp = currentTime, 
                    chapters = chapters,
                    cachedTotalPages = totalPages,
                    cachedCurrentPage = 0,
                    lastScannedAt = currentTime
                )
            }
            emit(comic)
        }
    }
}.flowOn(Dispatchers.IO)


// 兼容旧接口
fun scanComicsFlow(context: Context, rootTreeUri: Uri, existingUris: Set<String>, onFolderScanning: (String) -> Unit): Flow<ComicHistory> {
    val emptyMap = existingUris.associateWith { uri -> 
        ComicHistory(id = uri, name = "", uriString = uri, coverUriString = null, timestamp = 0, lastScannedAt = Long.MAX_VALUE)
    }
    return scanComicsFlow(context, rootTreeUri, emptyMap, onFolderScanning)
}

// --- 小说扫描 ---
fun scanNovelsFlow(context: Context, rootTreeUri: Uri, existingUris: Set<String>, onFolderScanning: (String) -> Unit): Flow<NovelHistory> = flow {
    val rootDoc = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return@flow
    withContext(Dispatchers.Main) { onFolderScanning(rootDoc.name ?: "小说") }

    try {
        if (rootDoc.findFile(".nomedia") == null) {
            rootDoc.createFile("application/octet-stream", ".nomedia")
        }
    } catch (_: Exception) { }

    val files = rootDoc.listFiles()
    
    val novelFiles = files
        .filter { 
            it.isFile && (
                it.name?.endsWith(".txt", ignoreCase = true) == true || 
                it.name?.endsWith(".epub", ignoreCase = true) == true ||
                it.type?.startsWith("text/") == true ||
                it.type == "application/epub+zip"
            )
        }
        .sortedWith { a, b -> compareNatural(a.name ?: "", b.name ?: "") }

    val coverUri = files
        .filter { it.isFile && it.type?.startsWith("image/") == true }
        .sortedWith { a, b -> compareNatural(a.name ?: "", b.name ?: "") }
        .firstOrNull()
        ?.uri
        ?.toString()

    if (novelFiles.isNotEmpty()) {
        novelFiles.forEach { file ->
            val fileUri = file.uri.toString()
            if (existingUris.contains(fileUri)) return@forEach
            val fileName = file.name ?: "Chapter"
            
            val isEpub = fileName.endsWith(".epub", ignoreCase = true)
            
            if (isEpub) {
                val epubResult = parseEpubFile(context, file.uri)
                if (epubResult != null) {
                    val (parsedChapters, epubCoverUri) = epubResult
                    emit(
                        NovelHistory(
                            id = fileUri,
                            name = fileName.removeSuffix(".epub").removeSuffix(".EPUB"),
                            uriString = fileUri,
                            coverUriString = epubCoverUri ?: coverUri,
                            timestamp = System.currentTimeMillis(),
                            chapters = parsedChapters
                        )
                    )
                }
            } else {
                val rawText = readNovelText(context, file.uri)
                val parsedChapters = rawText?.let { parseNovelChapters(it, fileUri, fileName) }
                    ?: listOf(NovelChapter(fileName, fileUri))
                emit(
                    NovelHistory(
                        id = fileUri,
                        name = fileName,
                        uriString = fileUri,
                        coverUriString = coverUri,
                        timestamp = System.currentTimeMillis(),
                        chapters = parsedChapters
                    )
                )
            }
        }
    }
}.flowOn(Dispatchers.IO)

// --- 音频扫描 ---
fun scanAudiosFlow(context: Context, rootTreeUri: Uri, existingUris: Set<String>, onFolderScanning: (String) -> Unit): Flow<AudioHistory> = flow {
    val rootDoc = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return@flow
    withContext(Dispatchers.Main) { onFolderScanning(rootDoc.name ?: "音频") }

    try {
        if (rootDoc.findFile(".nomedia") == null) {
            rootDoc.createFile("application/octet-stream", ".nomedia")
        }
    } catch (_: Exception) { }

    val files = rootDoc.listFiles()
    
    // 扫描根目录下的单曲
    val rootAudioFiles = files
        .filter { it.isFile && it.type?.startsWith("audio/") == true }
        .sortedWith { a, b -> compareNatural(a.name ?: "", b.name ?: "") }
    
    val rootCoverUri = files
        .filter { it.isFile && it.type?.startsWith("image/") == true }
        .sortedWith { a, b -> compareNatural(a.name ?: "", b.name ?: "") }
        .firstOrNull()?.uri?.toString()

    rootAudioFiles.forEach { file ->
        val fileUri = file.uri.toString()
        if (existingUris.contains(fileUri)) return@forEach
        
        val coverUri = extractEmbeddedCover(context, file.uri, file.name ?: "audio") ?: rootCoverUri
        emit(
            AudioHistory(
                id = fileUri,
                name = file.name ?: "Track",
                uriString = fileUri,
                coverUriString = coverUri,
                timestamp = System.currentTimeMillis(),
                tracks = listOf(AudioTrack(file.name ?: "Track", fileUri))
            )
        )
    }

    // 扫描子文件夹（专辑）
    val subfolders = files.filter { it.isDirectory }
    
    subfolders.forEach { subfolder ->
        val subfiles = subfolder.listFiles()
        val albumAudioFiles = subfiles
            .filter { it.isFile && it.type?.startsWith("audio/") == true }
            .sortedWith { a, b -> compareNatural(a.name ?: "", b.name ?: "") }
        
        if (albumAudioFiles.isEmpty()) return@forEach
        
        val albumId = subfolder.uri.toString()
        if (existingUris.contains(albumId)) return@forEach
        
        val folderCoverUri = subfiles
            .filter { it.isFile && it.type?.startsWith("image/") == true }
            .sortedWith { a, b -> compareNatural(a.name ?: "", b.name ?: "") }
            .firstOrNull()?.uri?.toString()
        
        val firstAudioCover = extractEmbeddedCover(context, albumAudioFiles.first().uri, albumAudioFiles.first().name ?: "audio")
        val albumCoverUri = firstAudioCover ?: folderCoverUri
        
        val tracks = albumAudioFiles.map { audioFile ->
            AudioTrack(audioFile.name ?: "Track", audioFile.uri.toString())
        }
        
        emit(
            AudioHistory(
                id = albumId,
                name = subfolder.name ?: "Album",
                uriString = albumId,
                coverUriString = albumCoverUri,
                timestamp = System.currentTimeMillis(),
                tracks = tracks
            )
        )
    }
}.flowOn(Dispatchers.IO)

// 提取音频文件的内嵌封面
private fun extractEmbeddedCover(context: Context, audioUri: Uri, fileName: String): String? {
    return try {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, audioUri)
            val coverBytes = retriever.embeddedPicture
            if (coverBytes != null) {
                val cacheDir = File(context.cacheDir, "audio_covers")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                
                val hash = fileName.hashCode().toString(16)
                val coverFile = File(cacheDir, "$hash.jpg")
                
                if (!coverFile.exists()) {
                    coverFile.writeBytes(coverBytes)
                }
                
                coverFile.absolutePath
            } else {
                null
            }
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    } catch (e: Exception) {
        null
    }
}
