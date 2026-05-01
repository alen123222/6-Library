package com.alendawang.manhua.model.plugin

import com.google.gson.annotations.SerializedName

/**
 * 对应 Legado (阅读 3.0) 书源的 JSON 结构
 */
data class LegadoSource(
    val bookSourceUrl: String = "",
    val bookSourceName: String = "",
    val bookSourceGroup: String = "",
    val bookSourceType: Int = 0,
    val searchUrl: String = "",
    val exploreUrl: String = "",
    val ruleSearch: RuleSearch? = null,
    val ruleBookInfo: RuleBookInfo? = null,
    val ruleToc: RuleToc? = null,
    val ruleContent: RuleContent? = null
)

data class RuleSearch(
    val bookList: String? = null,
    val name: String? = null,
    val author: String? = null,
    val kind: String? = null,
    val wordCount: String? = null,
    val lastChapter: String? = null,
    val intro: String? = null,
    val coverUrl: String? = null,
    val bookUrl: String? = null
)

data class RuleBookInfo(
    val init: String? = null,
    val name: String? = null,
    val author: String? = null,
    val kind: String? = null,
    val wordCount: String? = null,
    val lastChapter: String? = null,
    val intro: String? = null,
    val coverUrl: String? = null,
    val tocUrl: String? = null
)

data class RuleToc(
    val chapterList: String? = null,
    val chapterName: String? = null,
    val chapterUrl: String? = null,
    val nextTocUrl: String? = null
)

data class RuleContent(
    val content: String? = null,
    val nextContentUrl: String? = null,
    val webJs: String? = null,
    val sourceRegex: String? = null
)
