package com.alendawang.manhua.engine

import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.ReadContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 参考 Legado 的 AnalyzeRule 架构，实现多模式规则解析器。
 *
 * 支持的规则模式:
 * - @CSS:  → Jsoup 原生 CSS 选择器 (如 @CSS:div.item > a@href)
 * - @Json: → JSONPath (如 @Json:$.data[*].title)
 * - $. 或 $[ 开头 → 自动识别为 JSONPath
 * - Legado Default → class. / id. / tag. 前缀 + @ 分隔的 Legado 原始语法
 */
object RuleParser {

    /**
     * 判断规则的解析模式
     */
    private enum class Mode { Json, CssSelector, LegadoDefault }

    private fun detectMode(rule: String, isJsonContent: Boolean): Mode {
        return when {
            rule.startsWith("@Json:", ignoreCase = true) -> Mode.Json
            rule.startsWith("$.") || rule.startsWith("$[") -> Mode.Json
            rule.startsWith("@CSS:", ignoreCase = true) -> Mode.CssSelector
            isJsonContent -> Mode.Json
            else -> Mode.LegadoDefault
        }
    }

    private fun stripPrefix(rule: String): String {
        return when {
            rule.startsWith("@Json:", ignoreCase = true) -> rule.substring(6)
            rule.startsWith("@CSS:", ignoreCase = true) -> rule.substring(5)
            else -> rule
        }
    }

    // ==========  对外暴露的统一入口  ==========

    /**
     * 从 HTML Element 中提取字符串
     */
    fun getString(element: Element, rule: String?): String {
        if (rule.isNullOrBlank()) return ""
        val rules = rule.split("||")
        for (r in rules) {
            val result = parseSingleRuleHtml(element, r.trim())
            if (result.isNotBlank()) return result
        }
        return ""
    }

    /**
     * 从 JSON 上下文中提取字符串
     */
    fun getStringFromJson(ctx: ReadContext, rule: String?): String {
        if (rule.isNullOrBlank()) return ""
        val cleanRule = stripPrefix(rule)
        
        // 支持 Legado 的 {{...}} 模板替换
        if (cleanRule.contains("{{") && cleanRule.contains("}}")) {
            val regex = Regex("""\{\{(.*?)\}\}""")
            return regex.replace(cleanRule) { matchResult ->
                val innerPath = matchResult.groupValues[1].trim()
                var path = innerPath
                if (!path.startsWith("$") && !path.startsWith("@")) {
                    path = "$.$path"
                }
                try {
                    val obj = ctx.read<Any>(path)
                    if (obj is List<*>) obj.joinToString("\n") else obj.toString()
                } catch (e: Exception) {
                    ""
                }
            }
        }

        val rules = cleanRule.split("||")
        for (r in rules) {
            var path = r.trim()
            if (!path.startsWith("$") && !path.startsWith("@")) {
                path = "$.$path"
            }
            try {
                val obj = ctx.read<Any>(path)
                val result = if (obj is List<*>) obj.joinToString("\n") else obj.toString()
                if (result.isNotBlank()) return result
            } catch (_: Exception) {}
        }
        return ""
    }

    /**
     * 从 HTML Element 获取子节点列表
     */
    fun getElements(element: Element, rule: String?): List<Element> {
        if (rule.isNullOrBlank()) return emptyList()
        val cleanRule = stripPrefix(rule)
        val rules = cleanRule.split("||")
        for (r in rules) {
            try {
                val mode = detectMode(r.trim(), false)
                val selector = if (mode == Mode.CssSelector) {
                    stripPrefix(r.trim())
                } else {
                    convertLegadoToCSS(r.trim())
                }
                val elements = element.select(selector)
                if (elements.isNotEmpty()) return elements
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    /**
     * 从 JSON 上下文获取列表 (每个元素也是一个 ReadContext)
     */
    fun getJsonList(ctx: ReadContext, rule: String?): List<ReadContext> {
        if (rule.isNullOrBlank()) return emptyList()
        var cleanRule = stripPrefix(rule).trim()
        if (!cleanRule.startsWith("$") && !cleanRule.startsWith("@")) {
            cleanRule = "$.$cleanRule"
        }
        try {
            val list = ctx.read<List<Any>>(cleanRule)
            return list.map { JsonPath.parse(it) }
        } catch (_: Exception) {}
        return emptyList()
    }

    // ==========  内部 HTML 解析逻辑  ==========

    private fun parseSingleRuleHtml(element: Element, rule: String): String {
        val mode = detectMode(rule, false)
        val cleanRule = stripPrefix(rule)

        return when (mode) {
            Mode.CssSelector -> parseCssRule(element, cleanRule)
            Mode.LegadoDefault -> parseLegadoDefaultRule(element, cleanRule)
            Mode.Json -> "" // HTML 元素不应该走 JSON 解析
        }
    }

    /**
     * @CSS: 模式 - 格式为 "css选择器@属性" ，最后一个 @ 之后是提取动作
     * 例如：@CSS:div.item > a@href
     */
    private fun parseCssRule(element: Element, rule: String): String {
        val lastAt = rule.lastIndexOf('@')
        if (lastAt <= 0) {
            // 没有 @ 或开头就是 @，视为整个就是选择器取文本
            val els = element.select(rule)
            return els.joinToString("\n") { it.text() }.trim()
        }
        val cssSelector = rule.substring(0, lastAt)
        val action = rule.substring(lastAt + 1)
        val els = element.select(cssSelector)
        if (els.isEmpty()) return ""
        return els.joinToString("\n") { extractAction(it, action) }.trim()
    }

    /**
     * Legado Default 模式 - 使用 @ 作为层级分隔符
     * 例如: class.title@text, tag.a@href
     * 最后一个 @ 后面是提取动作(text/href/src/html/attr)
     * 前面的部分用 @ 切割后逐层深入
     */
    private fun parseLegadoDefaultRule(element: Element, rule: String): String {
        val parts = rule.split("@")
        if (parts.isEmpty()) return ""

        // 判断最后一段是不是动作(text, href, src, html, all, textNodes, ownText, 或属性名)
        val lastPart = parts.last().trim()
        val isAction = lastPart in listOf("text", "textNodes", "ownText", "html", "all", "href", "src")
                || lastPart.startsWith("data-") || lastPart.startsWith("style")

        val selectorParts: List<String>
        val action: String

        if (isAction && parts.size >= 2) {
            selectorParts = parts.dropLast(1)
            action = lastPart
        } else {
            // 全是选择器，默认取 text
            selectorParts = parts
            action = "text"
        }

        // 按层级逐步筛选
        var currentElements = listOf(element)
        for (selectorPart in selectorParts) {
            val css = convertSingleLegadoPart(selectorPart.trim())
            if (css.isBlank()) continue
            val next = mutableListOf<Element>()
            for (el in currentElements) {
                try {
                    next.addAll(el.select(css))
                } catch (_: Exception) {}
            }
            if (next.isEmpty()) return ""
            currentElements = next
        }

        return currentElements.joinToString("\n") { extractAction(it, action) }.trim()
    }

    /**
     * 将单段 Legado 选择器 (如 "class.title", "id.main", "tag.div.0") 转成 Jsoup CSS
     */
    private fun convertSingleLegadoPart(part: String): String {
        if (part.isBlank()) return ""

        // 处理 children
        if (part == "children") return "> *"

        // 处理 class.xxx, id.xxx, tag.xxx 格式
        val dotIndex = part.indexOf('.')
        if (dotIndex > 0) {
            val prefix = part.substring(0, dotIndex)
            val rest = part.substring(dotIndex + 1)
            when (prefix) {
                "class" -> {
                    // class.name.0 → .name:eq(0)
                    val segments = rest.split(".")
                    val className = segments[0]
                    return if (segments.size > 1) {
                        val index = segments.last().toIntOrNull()
                        if (index != null) ".$className:eq($index)" else ".$className"
                    } else {
                        ".$className"
                    }
                }
                "id" -> return "#$rest"
                "tag" -> {
                    val segments = rest.split(".")
                    val tagName = segments[0]
                    return if (segments.size > 1) {
                        val index = segments.last().toIntOrNull()
                        if (index != null) "$tagName:eq($index)" else tagName
                    } else {
                        tagName
                    }
                }
                "text" -> return ":containsOwn($rest)"
            }
        }

        // 如果无法识别，尝试直接作为 CSS 选择器使用
        return part
    }

    /**
     * 将完整的 Legado 规则 (不含 @CSS: 前缀的) 整体转换成 CSS，用于 getElements
     */
    private fun convertLegadoToCSS(rule: String): String {
        // 如果规则看起来已经是合法的 CSS 选择器，直接返回
        if (!rule.contains("class.") && !rule.contains("id.") && !rule.contains("tag.") && !rule.contains("@")) {
            return rule
        }
        // 用 @ 分割，逐段转换后拼接
        val parts = rule.split("@")
        return parts.joinToString(" ") { convertSingleLegadoPart(it.trim()) }.trim()
    }

    /**
     * 从元素中提取指定动作对应的内容
     */
    private fun extractAction(element: Element, action: String): String {
        return when (action) {
            "text" -> element.text()
            "textNodes" -> element.textNodes().joinToString("\n") { it.text().trim() }
            "ownText" -> element.ownText()
            "html" -> element.html()
            "all" -> element.outerHtml()
            "href" -> element.attr("abs:href").takeIf { it.isNotBlank() } ?: element.attr("href")
            "src" -> element.attr("abs:src").takeIf { it.isNotBlank() } ?: element.attr("src")
            else -> element.attr(action) // 任意属性
        }
    }
}
