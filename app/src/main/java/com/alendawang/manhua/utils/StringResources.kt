package com.alendawang.manhua.utils

import com.alendawang.manhua.model.AppLanguage

/**
 * 应用字符串资源
 * 支持中英文双语
 */
object AppStrings {
    
    // --- 主界面 ---
    fun comics(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "漫画"
        AppLanguage.ENGLISH -> "Comics"
    }
    
    fun novels(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "小说"
        AppLanguage.ENGLISH -> "Novels"
    }
    
    fun audio(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "音频"
        AppLanguage.ENGLISH -> "Audio"
    }
    
    fun search(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "搜索"
        AppLanguage.ENGLISH -> "Search"
    }
    
    fun searchHint(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "搜索..."
        AppLanguage.ENGLISH -> "Search..."
    }
    
    fun settings(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "设置"
        AppLanguage.ENGLISH -> "Settings"
    }
    
    fun sort(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "排序"
        AppLanguage.ENGLISH -> "Sort"
    }
    
    fun view(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "视图"
        AppLanguage.ENGLISH -> "View"
    }
    
    fun theme(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "主题"
        AppLanguage.ENGLISH -> "Theme"
    }
    
    fun language(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "语言"
        AppLanguage.ENGLISH -> "Language"
    }
    
    fun scan(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "扫描"
        AppLanguage.ENGLISH -> "Scan"
    }
    
    fun scanFolder(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "扫描文件夹"
        AppLanguage.ENGLISH -> "Scan Folder"
    }
    
    fun scanning(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "扫描中..."
        AppLanguage.ENGLISH -> "Scanning..."
    }
    
    fun favorite(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "收藏"
        AppLanguage.ENGLISH -> "Favorite"
    }
    
    fun unfavorite(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "取消收藏"
        AppLanguage.ENGLISH -> "Unfavorite"
    }
    
    fun hide(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "隐藏"
        AppLanguage.ENGLISH -> "Hide"
    }
    
    fun unhide(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "取消隐藏"
        AppLanguage.ENGLISH -> "Unhide"
    }
    
    fun delete(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "删除"
        AppLanguage.ENGLISH -> "Delete"
    }
    
    fun confirm(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "确认"
        AppLanguage.ENGLISH -> "Confirm"
    }
    
    fun cancel(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "取消"
        AppLanguage.ENGLISH -> "Cancel"
    }
    
    fun ok(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "确定"
        AppLanguage.ENGLISH -> "OK"
    }
    
    fun close(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "关闭"
        AppLanguage.ENGLISH -> "Close"
    }
    
    fun selectAll(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "全选"
        AppLanguage.ENGLISH -> "Select All"
    }
    
    fun clearSelection(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "清除选择"
        AppLanguage.ENGLISH -> "Clear Selection"
    }
    
    fun selected(count: Int, lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "已选择 $count 项"
        AppLanguage.ENGLISH -> "$count selected"
    }
    
    // --- 排序选项 ---
    fun sortRecentRead(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "最近阅读"
        AppLanguage.ENGLISH -> "Recent Read"
    }
    
    fun sortEarliestRead(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "最早阅读"
        AppLanguage.ENGLISH -> "Earliest Read"
    }
    
    fun sortNameAZ(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "名称 (A-Z)"
        AppLanguage.ENGLISH -> "Name (A-Z)"
    }
    
    fun sortNameZA(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "名称 (Z-A)"
        AppLanguage.ENGLISH -> "Name (Z-A)"
    }
    
    // --- 显示模式 ---
    fun listView(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "列表视图"
        AppLanguage.ENGLISH -> "List View"
    }
    
    fun gridView3(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "三列视图"
        AppLanguage.ENGLISH -> "3 Columns"
    }
    
    fun gridView4(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "四列视图"
        AppLanguage.ENGLISH -> "4 Columns"
    }
    
    fun gridView5(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "密集视图"
        AppLanguage.ENGLISH -> "5 Columns"
    }
    
    // --- 主题 ---
    fun themeSakura(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "落樱"
        AppLanguage.ENGLISH -> "Sakura"
    }
    
    fun themeCyberpunk(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "暗黑"
        AppLanguage.ENGLISH -> "Cyberpunk"
    }
    
    fun themeInkStyle(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "水墨"
        AppLanguage.ENGLISH -> "Ink Style"
    }
    
    fun themeMatcha(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "抹茶"
        AppLanguage.ENGLISH -> "Matcha"
    }
    
    // --- 阅读器设置 ---
    fun fontSize(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "字体大小"
        AppLanguage.ENGLISH -> "Font Size"
    }
    
    fun lineHeight(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "行高"
        AppLanguage.ENGLISH -> "Line Height"
    }
    
    fun paragraphSpacing(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "段落间距"
        AppLanguage.ENGLISH -> "Paragraph Spacing"
    }
    
    fun horizontalPadding(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "水平边距"
        AppLanguage.ENGLISH -> "Horizontal Padding"
    }
    
    fun backgroundColor(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "背景颜色"
        AppLanguage.ENGLISH -> "Background Color"
    }
    
    fun fontType(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "字体类型"
        AppLanguage.ENGLISH -> "Font Type"
    }
    
    fun textColor(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "文字颜色"
        AppLanguage.ENGLISH -> "Text Color"
    }
    
    fun customBackground(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "自定义背景"
        AppLanguage.ENGLISH -> "Custom Background"
    }
    
    // --- 背景色 ---
    fun bgEyeCare(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "羊皮纸"
        AppLanguage.ENGLISH -> "Eye Care"
    }
    
    fun bgNight(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "夜间模式"
        AppLanguage.ENGLISH -> "Night Mode"
    }
    
    fun bgPureWhite(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "纯白"
        AppLanguage.ENGLISH -> "Pure White"
    }
    
    fun bgGray(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "灰度"
        AppLanguage.ENGLISH -> "Gray"
    }
    
    fun bgParchment(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "少女"
        AppLanguage.ENGLISH -> "Parchment"
    }
    
    // --- 字体类型 ---
    fun fontSystem(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "系统字体"
        AppLanguage.ENGLISH -> "System"
    }
    
    fun fontSerif(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "宋体"
        AppLanguage.ENGLISH -> "Serif"
    }
    
    fun fontSansSerif(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "黑体"
        AppLanguage.ENGLISH -> "Sans Serif"
    }
    
    fun fontMonospace(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "等宽"
        AppLanguage.ENGLISH -> "Monospace"
    }
    
    // --- 音频播放器 ---
    fun playbackSpeed(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "播放速度"
        AppLanguage.ENGLISH -> "Playback Speed"
    }
    
    fun albums(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "专辑模式"
        AppLanguage.ENGLISH -> "Albums"
    }
    
    fun singles(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "纯单曲"
        AppLanguage.ENGLISH -> "Singles"
    }
    
    // --- 空状态 ---
    fun noContent(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "暂无内容"
        AppLanguage.ENGLISH -> "No Content"
    }
    
    fun noResults(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "无搜索结果"
        AppLanguage.ENGLISH -> "No Results"
    }
    
    fun tapToScan(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "点击扫描文件夹添加内容"
        AppLanguage.ENGLISH -> "Tap to scan folders to add content"
    }
    
    // --- 密码 ---
    fun enterPassword(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "输入密码"
        AppLanguage.ENGLISH -> "Enter Password"
    }
    
    fun setPassword(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "设置密码"
        AppLanguage.ENGLISH -> "Set Password"
    }
    
    fun confirmPassword(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "确认密码"
        AppLanguage.ENGLISH -> "Confirm Password"
    }
    
    fun passwordMismatch(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "密码不匹配"
        AppLanguage.ENGLISH -> "Passwords don't match"
    }
    
    fun wrongPassword(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "密码错误"
        AppLanguage.ENGLISH -> "Wrong password"
    }
    
    // --- 删除确认 ---
    fun deleteConfirmTitle(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "确认删除"
        AppLanguage.ENGLISH -> "Confirm Delete"
    }
    
    fun deleteConfirmMessage(count: Int, lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "确定要删除 $count 项吗？"
        AppLanguage.ENGLISH -> "Delete $count item(s)?"
    }
    
    // --- 进度 ---
    fun page(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "页"
        AppLanguage.ENGLISH -> "Page"
    }
    
    fun chapter(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "章节"
        AppLanguage.ENGLISH -> "Chapter"
    }
    
    fun track(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "曲目"
        AppLanguage.ENGLISH -> "Track"
    }
    
    fun progress(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "进度"
        AppLanguage.ENGLISH -> "Progress"
    }
    
    // --- 底部栏 ---
    fun home(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "首页"
        AppLanguage.ENGLISH -> "Home"
    }
    
    fun library(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "书架"
        AppLanguage.ENGLISH -> "Library"
    }
    
    fun history(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "历史"
        AppLanguage.ENGLISH -> "History"
    }
    
    // --- 详情页 ---
    fun details(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "详情"
        AppLanguage.ENGLISH -> "Details"
    }
    
    fun continueReading(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "继续阅读"
        AppLanguage.ENGLISH -> "Continue"
    }
    
    fun startReading(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "开始阅读"
        AppLanguage.ENGLISH -> "Start Reading"
    }
    
    fun chapters(count: Int, lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "$count 章"
        AppLanguage.ENGLISH -> "$count Chapters"
    }
    
    fun pages(count: Int, lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "$count 页"
        AppLanguage.ENGLISH -> "$count Pages"
    }
    
    fun tracks(count: Int, lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "$count 首"
        AppLanguage.ENGLISH -> "$count Tracks"
    }
    
    // --- 指纹解锁 ---
    fun fingerprintUnlock(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "指纹解锁"
        AppLanguage.ENGLISH -> "Fingerprint Unlock"
    }
    
    fun chooseDefaultUnlockMethod(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "选择默认解锁方式"
        AppLanguage.ENGLISH -> "Choose Default Unlock Method"
    }
    
    fun usePassword(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "使用密码"
        AppLanguage.ENGLISH -> "Use Password"
    }
    
    fun useFingerprint(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "使用指纹"
        AppLanguage.ENGLISH -> "Use Fingerprint"
    }
    
    fun fingerprintAuthFailed(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "指纹验证失败"
        AppLanguage.ENGLISH -> "Fingerprint authentication failed"
    }
    
    fun fingerprintNotRecognized(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "指纹未识别，请重试"
        AppLanguage.ENGLISH -> "Fingerprint not recognized, try again"
    }
    
    fun touchSensor(lang: AppLanguage) = when (lang) {
        AppLanguage.CHINESE -> "请触摸指纹传感器"
        AppLanguage.ENGLISH -> "Touch the fingerprint sensor"
    }
}
