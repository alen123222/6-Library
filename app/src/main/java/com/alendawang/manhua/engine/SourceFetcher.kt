package com.alendawang.manhua.engine

import com.alendawang.manhua.model.plugin.LegadoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SearchResult(
    val name: String,
    val author: String,
    val coverUrl: String,
    val intro: String,
    val detailUrl: String
)

object SourceFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 执行书源搜索
     */
    suspend fun search(source: LegadoSource, keyword: String, page: Int = 1): List<SearchResult> = withContext(Dispatchers.IO) {
        val searchRule = source.ruleSearch ?: return@withContext emptyList()
        val searchUrlPattern = source.searchUrl
        if (searchUrlPattern.isBlank()) return@withContext emptyList()

        // 构建搜索 URL (简单处理 {{key}} 和 {{page}})
        val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
        val targetUrl = searchUrlPattern
            .replace("{{key}}", encodedKeyword)
            .replace("{{page}}", page.toString())

        // 拼接 baseURL (如果 targetUrl 不是完整的 URL)
        val fullUrl = if (targetUrl.startsWith("http")) {
            targetUrl
        } else {
            source.bookSourceUrl.trimEnd('/') + "/" + targetUrl.trimStart('/')
        }

        // 发起网络请求
        val request = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return@withContext emptyList()
        }

        val htmlContent = response.body?.string() ?: return@withContext emptyList()
        val document = Jsoup.parse(htmlContent, source.bookSourceUrl)

        // 开始解析书籍列表
        val listElements = RuleParser.getElements(document, searchRule.bookList)
        
        val results = mutableListOf<SearchResult>()
        for (element in listElements) {
            val name = RuleParser.getString(element, searchRule.name)
            val author = RuleParser.getString(element, searchRule.author)
            val coverUrl = RuleParser.getString(element, searchRule.coverUrl)
            val intro = RuleParser.getString(element, searchRule.intro)
            val detailUrl = RuleParser.getString(element, searchRule.bookUrl)

            // 如果连名字都没有，说明解析失败或过滤掉了
            if (name.isNotBlank()) {
                results.add(
                    SearchResult(
                        name = name,
                        author = author,
                        coverUrl = coverUrl,
                        intro = intro,
                        detailUrl = detailUrl
                    )
                )
            }
        }

        return@withContext results
    }
}
