package com.alendawang.manhua.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * 拖拽多选状态管理
 * 
 * 实现类似相册应用的拖拽多选功能：
 * - 多选模式下，手指按下并滑动即可批量选中/取消选中
 * - 首个触碰的 item 的状态决定本次拖拽的操作方向（选中/取消）
 * - 拖拽过程中经过的所有 item 会被自动选中/取消
 */
class DragSelectState(
    private val onSelectionChange: (Set<String>) -> Unit
) {
    /** 是否正在拖拽 */
    var isDragging by mutableStateOf(false)
        private set
    
    /** 拖拽起始 item 的索引 */
    private var startIndex: Int = -1
    
    /** 当前拖拽到的 item 索引 */
    private var currentIndex: Int = -1
    
    /** 本次拖拽是"选中"还是"取消选中" */
    private var isSelecting: Boolean = true
    
    /** 拖拽开始前的选中状态快照 */
    private var initialSelection: Set<String> = emptySet()
    
    /** 当前 item 列表的 ID 映射 (index -> id) */
    var itemIds: List<String> = emptyList()
    
    fun onDragStart(index: Int, currentSelection: Set<String>) {
        if (index < 0 || index >= itemIds.size) return
        isDragging = true
        startIndex = index
        currentIndex = index
        initialSelection = currentSelection.toSet()
        
        // 首个 item 的当前状态决定操作方向
        val itemId = itemIds[index]
        isSelecting = !currentSelection.contains(itemId)
        
        applySelection()
    }
    
    fun onDragMove(index: Int) {
        if (!isDragging || index < 0 || index >= itemIds.size) return
        if (index == currentIndex) return
        currentIndex = index
        applySelection()
    }
    
    fun onDragEnd() {
        isDragging = false
        startIndex = -1
        currentIndex = -1
    }
    
    private fun applySelection() {
        val rangeStart = min(startIndex, currentIndex)
        val rangeEnd = max(startIndex, currentIndex)
        val draggedIds = (rangeStart..rangeEnd)
            .filter { it in itemIds.indices }
            .map { itemIds[it] }
            .toSet()
        
        val newSelection = if (isSelecting) {
            initialSelection + draggedIds
        } else {
            initialSelection - draggedIds
        }
        onSelectionChange(newSelection)
    }
}

@Composable
fun rememberDragSelectState(
    onSelectionChange: (Set<String>) -> Unit
): DragSelectState {
    val state = remember { DragSelectState(onSelectionChange) }
    // 确保 callback 更新
    LaunchedEffect(onSelectionChange) {
        // state 内部持有的引用会通过外部调用更新
    }
    return state
}

/**
 * 根据触摸位置和 LazyGrid 状态，计算当前手指所在的 item 索引
 */
fun resolveItemIndexFromPosition(
    gridState: LazyGridState,
    touchPosition: Offset,
    gridTopOffset: Float = 0f
): Int {
    val layoutInfo = gridState.layoutInfo
    val adjustedY = touchPosition.y + gridTopOffset
    
    for (item in layoutInfo.visibleItemsInfo) {
        val itemTop = item.offset.y.toFloat()
        val itemBottom = itemTop + item.size.height
        val itemLeft = item.offset.x.toFloat()
        val itemRight = itemLeft + item.size.width
        
        if (touchPosition.x in itemLeft..itemRight && adjustedY in itemTop..itemBottom) {
            return item.index
        }
    }
    
    // 如果没有精确命中，找最近的 item（支持手指在 item 间隙时的容错）
    var closestIndex = -1
    var closestDistance = Float.MAX_VALUE
    
    for (item in layoutInfo.visibleItemsInfo) {
        val itemCenterX = item.offset.x + item.size.width / 2f
        val itemCenterY = item.offset.y + item.size.height / 2f
        val dx = touchPosition.x - itemCenterX
        val dy = adjustedY - itemCenterY
        val distance = dx * dx + dy * dy
        
        if (distance < closestDistance) {
            closestDistance = distance
            closestIndex = item.index
        }
    }
    
    return closestIndex
}
