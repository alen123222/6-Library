package com.alendawang.manhua.engine

import android.util.Log
import com.alendawang.manhua.model.plugin.LegadoSource
import com.jayway.jsonpath.JsonPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

data class SearchResult(
    val name: String,
    val author: String,
    val coverUrl: String,
    val intro: String,
    val detailUrl: String
)

object SourceFetcher {

    private const val TAG = "SourceFetcher"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * 执行书源搜索
     *
     * 参考 Legado 的 AnalyzeUrl + WebBook.searchBookAwait + BookList.analyzeBookList 流程
     */
    suspend fun search(source: LegadoSource, keyword: String, page: Int = 1): List<SearchResult> = withContext(Dispatchers.IO) {
        val searchRule = source.ruleSearch ?: return@withContext emptyList()
        val searchUrlPattern = source.searchUrl
        if (searchUrlPattern.isBlank()) return@withContext emptyList()

        // 参照 Legado AnalyzeUrl.replaceKeyPageJs()
        // {{key}} 在 Legado 中其实是 JS 表达式, key 是变量，直接替换即可
        val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")

        // 解析搜索 URL：Legado 格式为 "url,{json配置}" 逗号分隔
        val parts = splitUrlAndOption(searchUrlPattern)
        var urlTemplate = parts.first
        val optionJson = parts.second

        // 替换模板变量
        urlTemplate = urlTemplate
            .replace("{{key}}", encodedKeyword)
            .replace("{{page}}", page.toString())
            .replace("\${key}", encodedKeyword)
            .replace("\${page}", page.toString())

        // 解析 POST 配置
        var method = "GET"
        var postBody: String? = null
        var charset: String? = null

        if (optionJson != null) {
            try {
                val optionMap = com.google.gson.Gson().fromJson(optionJson, Map::class.java) as? Map<String, Any>
                optionMap?.let { map ->
                    (map["method"] as? String)?.let { if (it.equals("POST", true)) method = "POST" }
                    (map["body"] as? String)?.let { body ->
                        postBody = body
                            .replace("{{key}}", encodedKeyword)
                            .replace("{{page}}", page.toString())
                            .replace("\${key}", encodedKeyword)
                            .replace("\${page}", page.toString())
                    }
                    (map["charset"] as? String)?.let { charset = it }
                }
            } catch (e: Exception) {
                Log.w(TAG, "解析 URL 配置失败: $e")
            }
        }

        // 拼接完整 URL
        val fullUrl = if (urlTemplate.startsWith("http")) {
            urlTemplate
        } else {
            source.bookSourceUrl.trimEnd('/') + "/" + urlTemplate.trimStart('/')
        }

        Log.d(TAG, "🔍 搜索 URL: $fullUrl, method=$method, body=$postBody")

        // 构建请求
        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")

        if (method == "POST" && postBody != null) {
            // 判断 body 是不是 JSON
            val bodyStr = postBody!!
            if (bodyStr.trimStart().startsWith("{") || bodyStr.trimStart().startsWith("[")) {
                requestBuilder.post(bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType()))
            } else {
                // 表单格式 key=value&key2=value2
                val formBuilder = FormBody.Builder()
                bodyStr.split("&").forEach { pair ->
                    val kv = pair.split("=", limit = 2)
                    if (kv.size == 2) {
                        formBuilder.add(kv[0], kv[1])
                    }
                }
                requestBuilder.post(formBuilder.build())
            }
        }

        val request = requestBuilder.build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            Log.w(TAG, "❌ HTTP ${response.code}")
            return@withContext emptyList()
        }

        // 处理编码: 参考 Legado 对 charset 的处理
        val bodyBytes = response.body?.bytes() ?: return@withContext emptyList()
        val detectedCharset = charset
            ?: response.body?.contentType()?.charset()?.name()
            ?: detectCharset(bodyBytes)
        val body = String(bodyBytes, Charset.forName(detectedCharset))

        Log.d(TAG, "📦 响应长度: ${body.length}, 编码: $detectedCharset, 前200字: ${body.take(200)}")

        // 判断内容类型
        val isJson = body.trimStart().let { it.startsWith("{") || it.startsWith("[") }

        if (isJson) {
            Log.d(TAG, "🔀 走 JSON 解析路径")
            parseJsonResults(body, source, searchRule, fullUrl)
        } else {
            Log.d(TAG, "🔀 走 HTML 解析路径")
            parseHtmlResults(body, source, searchRule, fullUrl)
        }
    }

    /**
     * 将 Legado 的 searchUrl 拆分为 URL 和 JSON 配置
     * 格式: "url,{json}" 或者纯 "url"
     * 参考 Legado AnalyzeUrl 中的 paramPattern = "\\s*,\\s*(?=\\{)"
     */
    private fun splitUrlAndOption(searchUrl: String): Pair<String, String?> {
        // 找到第一个 ,{ 的位置
        val regex = Regex("""\s*,\s*(?=\{)""")
        val match = regex.find(searchUrl) ?: return Pair(searchUrl, null)
        val url = searchUrl.substring(0, match.range.first)
        val option = searchUrl.substring(match.range.last + 1)
        return Pair(url, option)
    }

    /**
     * 简单的编码检测: 如果 UTF-8 解析出来有大量乱码, 尝试 GBK
     */
    private fun detectCharset(bytes: ByteArray): String {
        val utf8Str = String(bytes, Charsets.UTF_8)
        val garbledCount = utf8Str.count { it == '\uFFFD' }
        return if (garbledCount > utf8Str.length / 10) "GBK" else "UTF-8"
    }

    /**
     * JSON 模式解析
     */
    private fun parseJsonResults(
        body: String,
        source: LegadoSource,
        searchRule: com.alendawang.manhua.model.plugin.RuleSearch,
        fullUrl: String
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val ctx = JsonPath.parse(body)
            val bookListRule = searchRule.bookList ?: return emptyList()
            val items = RuleParser.getJsonList(ctx, bookListRule)

            Log.d(TAG, "📋 JSON 列表项数: ${items.size}")

            for (itemCtx in items) {
                val name = RuleParser.getStringFromJson(itemCtx, searchRule.name)
                val author = RuleParser.getStringFromJson(itemCtx, searchRule.author)
                val coverUrl = RuleParser.getStringFromJson(itemCtx, searchRule.coverUrl)
                val intro = RuleParser.getStringFromJson(itemCtx, searchRule.intro)
                var detailUrl = RuleParser.getStringFromJson(itemCtx, searchRule.bookUrl)
                
                if (detailUrl.isBlank()) {
                    detailUrl = fullUrl
                } else {
                    detailUrl = makeAbsoluteUrl(source.bookSourceUrl, detailUrl)
                }

                if (name.isNotBlank()) {
                    results.add(
                        SearchResult(
                            name = name,
                            author = author,
                            coverUrl = makeAbsoluteUrl(source.bookSourceUrl, coverUrl),
                            intro = intro,
                            detailUrl = detailUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON 解析异常: ${e.message}")
            e.printStackTrace()
        }
        Log.d(TAG, "✅ JSON 搜索结果数: ${results.size}")
        return results
    }

    /**
     * HTML 模式解析
     */
    private fun parseHtmlResults(
        body: String,
        source: LegadoSource,
        searchRule: com.alendawang.manhua.model.plugin.RuleSearch,
        fullUrl: String
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        // 使用实际请求的 fullUrl 作为基础 URI 解析，方便 abs:href 提取
        val document = Jsoup.parse(body, fullUrl)
        val bookListRule = searchRule.bookList ?: return emptyList()

        val listElements = RuleParser.getElements(document, bookListRule)
        Log.d(TAG, "📋 HTML 列表项数: ${listElements.size}, 规则: $bookListRule")

        for (element in listElements) {
            val name = RuleParser.getString(element, searchRule.name)
            val author = RuleParser.getString(element, searchRule.author)
            val coverUrl = RuleParser.getString(element, searchRule.coverUrl)
            val intro = RuleParser.getString(element, searchRule.intro)
            var detailUrl = RuleParser.getString(element, searchRule.bookUrl)
            
            if (detailUrl.isBlank()) {
                detailUrl = fullUrl
            } else {
                detailUrl = makeAbsoluteUrl(source.bookSourceUrl, detailUrl)
            }

            if (name.isNotBlank()) {
                results.add(
                    SearchResult(
                        name = name,
                        author = author,
                        coverUrl = makeAbsoluteUrl(source.bookSourceUrl, coverUrl),
                        intro = intro,
                        detailUrl = detailUrl
                    )
                )
            }
        }
        Log.d(TAG, "✅ HTML 搜索结果数: ${results.size}")
        return results
    }

    /**
     * 解析发现分类列表
     * 参考 Legado BookSourceExtensions.kt 的 exploreKinds() 方法
     * 格式: "分类名::URL" 用 \n 或 && 分隔，也可以是 JSON 数组 [{"title":"xx","url":"xx"}]
     */
    fun parseExploreKinds(exploreUrl: String): List<Pair<String, String>> {
        if (exploreUrl.isBlank()) return emptyList()
        val trimmed = exploreUrl.trim()

        // JSON 数组格式
        if (trimmed.startsWith("[")) {
            try {
                val list = com.google.gson.Gson().fromJson(
                    trimmed,
                    com.google.gson.reflect.TypeToken.getParameterized(
                        List::class.java,
                        Map::class.java
                    ).type
                ) as? List<Map<String, Any>>
                return list?.mapNotNull { map ->
                    val title = map["title"]?.toString() ?: return@mapNotNull null
                    val url = map["url"]?.toString() ?: return@mapNotNull null
                    Pair(title, url)
                } ?: emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "解析 JSON 格式 exploreUrl 失败: $e")
            }
        }

        // 文本格式: "分类名::URL" 用 \n 或 && 分隔
        return trimmed.split(Regex("(&&|\\n)+")).mapNotNull { kindStr ->
            val parts = kindStr.split("::")
            if (parts.size >= 2) {
                Pair(parts[0].trim(), parts[1].trim())
            } else null
        }
    }

    suspend fun explore(source: LegadoSource, exploreUrl: String, page: Int = 1): List<SearchResult> = withContext(Dispatchers.IO) {
        val rule = source.ruleExplore ?: source.ruleSearch ?: return@withContext emptyList()

        val targetUrl = exploreUrl
            .replace("{{page}}", page.toString())
            .replace("\${page}", page.toString())

        val fullUrl = makeAbsoluteUrl(source.bookSourceUrl, targetUrl)
        Log.d(TAG, "🌍 发现: $fullUrl")

        val body = fetchBody(fullUrl, source)
        val isJson = body.trimStart().let { it.startsWith("{") || it.startsWith("[") }

        if (isJson) {
            parseJsonResults(body, source, rule, fullUrl)
        } else {
            parseHtmlResults(body, source, rule, fullUrl)
        }
    }

    private fun makeAbsoluteUrl(baseUrl: String, url: String): String {
        if (url.isBlank()) return ""
        
        // 提取真正的 URL 部分（过滤掉 Legado 的 ,{...} 选项后缀）用于判断
        val pureUrl = splitUrlAndOption(url).first
        
        if (pureUrl.startsWith("http://", ignoreCase = true) || pureUrl.startsWith("https://", ignoreCase = true)) {
            return url
        }
        if (pureUrl.startsWith("//")) {
            return "https:$url"
        }
        
        // 确保 baseUrl 有 scheme
        val validBaseUrl = if (baseUrl.startsWith("http://", ignoreCase = true) || baseUrl.startsWith("https://", ignoreCase = true)) {
            baseUrl
        } else if (baseUrl.isNotBlank()) {
            "https://$baseUrl"
        } else {
            "https://"
        }
        
        return validBaseUrl.trimEnd('/') + "/" + url.trimStart('/')
    }

    // ======================== 书籍下载引擎 ========================

    data class BookDetail(
        val name: String,
        val author: String,
        val coverUrl: String,
        val intro: String,
        val tocUrl: String  // 目录页 URL（可能和详情页相同）
    )

    data class ChapterInfo(
        val name: String,
        val url: String
    )

    /**
     * 获取书籍详情 — 参照 Legado BookInfo.analyzeBookInfo
     */
    suspend fun fetchBookInfo(source: LegadoSource, detailUrl: String): BookDetail = withContext(Dispatchers.IO) {
        val ruleInfo = source.ruleBookInfo
        val fullUrl = makeAbsoluteUrl(source.bookSourceUrl, detailUrl)

        Log.d(TAG, "📖 获取详情: $fullUrl")
        Log.d(TAG, "📖 ruleBookInfo 规则: $ruleInfo")

        val body = fetchBody(fullUrl, source)
        val isJson = body.trimStart().let { it.startsWith("{") || it.startsWith("[") }

        var name = ""; var author = ""; var coverUrl = ""; var intro = ""; var tocUrl = fullUrl

        if (isJson) {
            val ctx = JsonPath.parse(body)
            name = RuleParser.getStringFromJson(ctx, ruleInfo?.name)
            author = RuleParser.getStringFromJson(ctx, ruleInfo?.author)
            coverUrl = RuleParser.getStringFromJson(ctx, ruleInfo?.coverUrl)
            intro = RuleParser.getStringFromJson(ctx, ruleInfo?.intro)
            val toc = RuleParser.getStringFromJson(ctx, ruleInfo?.tocUrl)
            Log.d(TAG, "📖 提取到的 tocUrl 原始值: '$toc'")
            if (toc.isNotBlank()) tocUrl = makeAbsoluteUrl(source.bookSourceUrl, toc)
        } else {
            val doc = Jsoup.parse(body, fullUrl)
            name = RuleParser.getString(doc, ruleInfo?.name)
            author = RuleParser.getString(doc, ruleInfo?.author)
            coverUrl = RuleParser.getString(doc, ruleInfo?.coverUrl)
            intro = RuleParser.getString(doc, ruleInfo?.intro)
            val toc = RuleParser.getString(doc, ruleInfo?.tocUrl)
            Log.d(TAG, "📖 提取到的 tocUrl 原始值: '$toc'")
            if (toc.isNotBlank()) tocUrl = makeAbsoluteUrl(source.bookSourceUrl, toc)
        }

        // 如果详情页没拿到名字，用搜索结果中的
        BookDetail(
            name = name,
            author = author,
            coverUrl = makeAbsoluteUrl(source.bookSourceUrl, coverUrl),
            intro = intro,
            tocUrl = tocUrl
        )
    }

    /**
     * 获取章节目录 — 参照 Legado BookChapterList.analyzeChapterList
     */
    suspend fun fetchChapterList(source: LegadoSource, tocUrl: String): List<ChapterInfo> = withContext(Dispatchers.IO) {
        val ruleToc = source.ruleToc ?: throw Exception("未配置 ruleToc，无法获取目录")
        val fullUrl = makeAbsoluteUrl(source.bookSourceUrl, tocUrl)

        Log.d(TAG, "📋 获取目录 URL: $fullUrl")

        val body = fetchBody(fullUrl, source)
        val isJson = body.trimStart().let { it.startsWith("{") || it.startsWith("[") }
        val chapters = mutableListOf<ChapterInfo>()

        if (isJson) {
            val ctx = JsonPath.parse(body)
            val listRule = ruleToc.chapterList.takeIf { !it.isNullOrBlank() } ?: "$..*"
            Log.d(TAG, "📋 JSON 解析，使用的章节列表规则: $listRule")
            val items = RuleParser.getJsonList(ctx, listRule)
            Log.d(TAG, "📋 解析出的节点数量: ${items.size}")
            for (itemCtx in items) {
                val name = RuleParser.getStringFromJson(itemCtx, ruleToc.chapterName)
                val url = RuleParser.getStringFromJson(itemCtx, ruleToc.chapterUrl)
                if (name.isNotBlank()) {
                    chapters.add(ChapterInfo(name, makeAbsoluteUrl(source.bookSourceUrl, url)))
                }
            }
        } else {
            val doc = Jsoup.parse(body, fullUrl)
            val listRule = ruleToc.chapterList.takeIf { !it.isNullOrBlank() } ?: "a"
            Log.d(TAG, "📋 HTML 解析，使用的章节列表规则: $listRule")
            val elements = RuleParser.getElements(doc, listRule)
            Log.d(TAG, "📋 解析出的 Element 数量: ${elements.size}")
            for (el in elements) {
                var name = RuleParser.getString(el, ruleToc.chapterName)
                if (name.isBlank() && ruleToc.chapterName.isNullOrBlank()) {
                    name = el.text()
                }
                
                var url = RuleParser.getString(el, ruleToc.chapterUrl)
                if (url.isBlank() && ruleToc.chapterUrl.isNullOrBlank()) {
                    val aTag = if (el.tagName() == "a") el else el.selectFirst("a")
                    url = aTag?.attr("abs:href")?.takeIf { it.isNotBlank() } ?: aTag?.attr("href") ?: ""
                }
                
                if (name.isNotBlank()) {
                    chapters.add(ChapterInfo(name, makeAbsoluteUrl(source.bookSourceUrl, url)))
                }
            }
        }

        Log.d(TAG, "📋 最终获取的目录章节数: ${chapters.size}")
        if (chapters.isEmpty()) {
            val preview = if (body.length > 500) body.substring(0, 500) + "..." else body
            Log.e(TAG, "❌ 章节列表为空! 源码前500字符: $preview")
            throw Exception("未获取到任何章节。列表规则:${ruleToc.chapterList}。你可以查看 Logcat 了解详情。")
        }
        chapters
    }

    /**
     * 获取单章正文 — 参照 Legado BookContent.analyzeContent
     */
    suspend fun fetchContent(source: LegadoSource, chapterUrl: String): String = withContext(Dispatchers.IO) {
        val ruleContent = source.ruleContent ?: return@withContext ""
        val fullUrl = makeAbsoluteUrl(source.bookSourceUrl, chapterUrl)

        val body = fetchBody(fullUrl, source)
        val isJson = body.trimStart().let { it.startsWith("{") || it.startsWith("[") }

        val content = if (isJson) {
            val ctx = JsonPath.parse(body)
            RuleParser.getStringFromJson(ctx, ruleContent.content)
        } else {
            val doc = Jsoup.parse(body, fullUrl)
            RuleParser.getString(doc, ruleContent.content)
        }

        // 简单清洗 HTML 标签
        content.replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<p>"), "")
            .replace(Regex("</p>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    /**
     * 下载完整书籍到文件
     * @param onProgress 进度回调 (当前章节, 总章节, 章节名)
     * @return Pair(txt文件路径, 封面文件路径)
     */
    suspend fun downloadBook(
        source: LegadoSource,
        detailUrl: String,
        bookName: String,
        coverUrl: String,
        outputDir: java.io.File,
        onProgress: (current: Int, total: Int, chapterName: String) -> Unit
    ): Pair<java.io.File, java.io.File?> = withContext(Dispatchers.IO) {
        // 确保目录存在
        if (!outputDir.exists()) outputDir.mkdirs()

        // 清理文件名中的非法字符
        val safeName = bookName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val txtFile = java.io.File(outputDir, "$safeName.txt")
        val coverFile = java.io.File(outputDir, "$safeName.jpg")

        // 1. 获取目录
        val bookDetail = fetchBookInfo(source, detailUrl)
        val chapters = fetchChapterList(source, bookDetail.tocUrl)

        if (chapters.isEmpty()) {
            throw Exception("未获取到任何章节")
        }

        Log.d(TAG, "📥 开始下载: $bookName, ${chapters.size}章")

        // 2. 逐章下载正文并写入 TXT
        txtFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("$bookName\n")
            writer.write("作者：${bookDetail.author}\n\n")

            for ((index, chapter) in chapters.withIndex()) {
                onProgress(index + 1, chapters.size, chapter.name)
                try {
                    val content = fetchContent(source, chapter.url)
                    writer.write("\n\n${chapter.name}\n\n")
                    writer.write(content)
                    writer.write("\n")
                } catch (e: Exception) {
                    Log.w(TAG, "第${index + 1}章获取失败: ${e.message}")
                    writer.write("\n\n${chapter.name}\n\n[内容获取失败]\n")
                }
                // 小延迟避免被封
                kotlinx.coroutines.delay(100)
            }
        }

        // 3. 下载封面
        val actualCoverUrl = coverUrl.ifBlank { bookDetail.coverUrl }
        var coverResult: java.io.File? = null
        if (actualCoverUrl.isNotBlank()) {
            try {
                val coverRequest = Request.Builder().url(actualCoverUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val coverResponse = client.newCall(coverRequest).execute()
                if (coverResponse.isSuccessful) {
                    coverResponse.body?.bytes()?.let { bytes ->
                        coverFile.writeBytes(bytes)
                        coverResult = coverFile
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "封面下载失败: ${e.message}")
            }
        }

        Log.d(TAG, "✅ 下载完成: ${txtFile.absolutePath}")
        Pair(txtFile, coverResult)
    }

    /**
     * 通用 HTTP 请求，获取 body 字符串
     */
    private fun fetchBody(url: String, source: LegadoSource? = null): String {
        val (pureUrl, options) = splitUrlAndOption(url)
        val requestBuilder = Request.Builder().url(pureUrl)
        
        requestBuilder.header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        
        // 1. 解析 source 的全局 header
        if (source?.header != null) {
            try {
                val ctx = JsonPath.parse(source.header)
                val headers = ctx.json() as? Map<*, *> ?: ctx.read<Map<String, String>>("$")
                headers?.forEach { (k, v) -> requestBuilder.header(k.toString(), v.toString()) }
            } catch (e: Exception) {
                Log.w(TAG, "解析全局 header 失败: ${e.message}")
            }
        }

        var method = "GET"
        var postBody: String? = null

        // 2. 解析 options（如果有局部 headers, method, body 等设置）
        if (options != null) {
            try {
                val optionMap = com.google.gson.Gson().fromJson(options, Map::class.java) as? Map<String, Any>
                if (optionMap != null) {
                    val headers = optionMap["headers"] as? Map<String, String>
                    headers?.forEach { (k, v) -> requestBuilder.header(k, v) }
                    
                    val m = optionMap["method"] as? String
                    if (m != null && m.equals("POST", true)) method = "POST"
                    
                    postBody = optionMap["body"] as? String
                }
            } catch (e: Exception) {
                Log.w(TAG, "解析 URL options 失败: ${e.message}")
            }
        }
        
        if (method == "POST") {
            val bodyStr = postBody ?: ""
            if (bodyStr.trimStart().startsWith("{") || bodyStr.trimStart().startsWith("[")) {
                requestBuilder.post(bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType()))
            } else {
                val formBuilder = FormBody.Builder()
                bodyStr.split("&").forEach { pair ->
                    val kv = pair.split("=", limit = 2)
                    if (kv.size == 2) formBuilder.add(kv[0], kv[1])
                    else if (kv.size == 1 && kv[0].isNotEmpty()) formBuilder.add(kv[0], "")
                }
                requestBuilder.post(formBuilder.build())
            }
        }

        val request = requestBuilder.build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        val bodyBytes = response.body?.bytes() ?: throw Exception("Empty response")
        val charset = response.body?.contentType()?.charset()?.name() ?: detectCharset(bodyBytes)
        return String(bodyBytes, Charset.forName(charset))
    }
}

