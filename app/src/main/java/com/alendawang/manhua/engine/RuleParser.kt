package com.alendawang.manhua.engine

import org.jsoup.nodes.Element

object RuleParser {

    /**
     * 根据 Legado 规则从 HTML 元素中提取字符串内容
     * @param element Jsoup 解析出的元素节点
     * @param rule Legado 的选择器规则 (例如 "class.name@text||tag.a@href")
     */
    fun getString(element: Element, rule: String?): String {
        if (rule.isNullOrBlank()) return ""

        // 处理多规则回退 (||)
        val rules = rule.split("||")
        for (r in rules) {
            val result = parseSingleRule(element, r.trim())
            if (result.isNotBlank()) {
                return result
            }
        }
        return ""
    }

    /**
     * 根据 Legado 规则获取子节点列表 (通常用于 bookList 或 chapterList)
     */
    fun getElements(element: Element, rule: String?): List<Element> {
        if (rule.isNullOrBlank()) return emptyList()
        
        // 简单处理，取第一个有效的列表规则
        val rules = rule.split("||")
        for (r in rules) {
            val selector = convertToCssSelector(r.trim())
            val elements = element.select(selector)
            if (elements.isNotEmpty()) {
                return elements
            }
        }
        return emptyList()
    }

    private fun parseSingleRule(element: Element, rule: String): String {
        // 分割动作和选择器, 比如 class.title@text
        val parts = rule.split("@")
        if (parts.isEmpty()) return ""

        val action = parts.last().trim()
        val selectorParts = parts.dropLast(1)
        
        val targetElements = if (selectorParts.isEmpty()) {
            listOf(element) // 如果没有选择器，只有动作 (比如单纯的 "text")
        } else {
            val cssSelector = convertToCssSelector(selectorParts.joinToString(" "))
            element.select(cssSelector)
        }

        if (targetElements.isEmpty()) return ""

        // 提取内容
        return targetElements.joinToString("\n") { el ->
            when (action) {
                "text", "textNodes" -> el.text()
                "html", "all" -> el.html()
                "href" -> el.attr("abs:href").takeIf { it.isNotBlank() } ?: el.attr("href")
                "src" -> el.attr("abs:src").takeIf { it.isNotBlank() } ?: el.attr("src")
                else -> {
                    // 尝试作为普通属性获取，比如 @data-url
                    if (action.startsWith("data-")) {
                        el.attr(action)
                    } else {
                        el.text()
                    }
                }
            }
        }.trim()
    }

    /**
     * 将 Legado 的特色语法转化为标准 Jsoup CSS 选择器
     * 例如 class.title 转化为 .title
     */
    private fun convertToCssSelector(legadoRule: String): String {
        var css = legadoRule
        // 移除前缀
        if (css.startsWith("@css:", ignoreCase = true)) {
            css = css.substring(5)
        }
        
        // 简单替换 Legado 历史遗留前缀
        css = css.replace("class.", ".")
        css = css.replace("id.", "#")
        css = css.replace("tag.", "")
        
        // 如果包含一些不兼容的伪类，直接剔除（例如 :eq(0) 在现代 Jsoup 中是 eq(0) 甚至支持不同的写法）
        // 这是一个极简的实现，针对基础书源足够了
        return css.trim()
    }
}
