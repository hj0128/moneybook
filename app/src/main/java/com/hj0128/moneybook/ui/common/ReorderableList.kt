package com.hj0128.moneybook.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.isActive

/** 손가락이 이 거리 안에 들어오면 목록이 알아서 밀린다. 없으면 화면 밖으로는 끌 수 없어 긴 목록을 못 옮긴다. */
private val AutoScrollZone = 72.dp

/** 한 번에 밀 거리. 크면 지나치고, 작으면 답답하다. */
private const val AutoScrollStep = 12f

/**
 * 오른쪽 손잡이를 끌어 줄 순서를 바꾸는 목록.
 *
 * 손잡이에서만 끌리게 한 이유: 줄 전체를 잡게 하면 목록을 위아래로 넘기려는 손짓과 구분이 안 된다.
 * 길게 눌러 잡는 방식도 있지만, 무엇을 어떻게 잡아야 하는지 화면에 드러나지 않는다.
 *
 * 순서는 [onMove] 로 바로 알려주고, 손을 뗄 때 [onDrop] 으로 저장할 때가 됐음을 알린다.
 * 끄는 중에 매번 저장하면 저장 결과가 다시 흘러들어와 화면이 튄다.
 */
@Composable
fun <T> ReorderableList(
    items: List<T>,
    keyOf: (T) -> Long,
    labelOf: (T) -> String,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
    row: @Composable (item: T) -> Unit,
) {
    // 끌고 있는 줄은 key 로 기억한다. 자리가 바뀌면 index 는 달라지지만 key 는 그대로다.
    var draggingKey by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var autoScroll by remember { mutableFloatStateOf(0f) }

    // 제스처 람다는 pointerInput 안에 갇히므로 최신 목록/콜백을 이렇게 들여다본다.
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDrop by rememberUpdatedState(onDrop)

    val autoScrollZonePx = with(LocalDensity.current) { AutoScrollZone.toPx() }

    /**
     * 끌고 있는 줄의 한가운데가 올라탄 이웃과 자리를 맞바꾼다.
     *
     * 손가락이 움직일 때뿐 아니라 목록이 저절로 밀릴 때도 불러야 한다. 안 그러면 화면만
     * 흐르고 줄은 제자리라, 화면 밖으로 옮기려면 손가락을 계속 흔들어야 한다.
     */
    val trySwap = {
        val key = draggingKey
        val info = listState.layoutInfo
        val dragged = info.visibleItemsInfo.firstOrNull { it.key == key }
        if (key != null && dragged != null) {
            // 겹치기만 해도 바꾸면 한 칸 움직이는 동안 두 번 바뀐다. 한가운데를 기준으로 삼는다.
            val center = dragged.offset + dragOffset + dragged.size / 2f
            val target = info.visibleItemsInfo.firstOrNull { other ->
                other.key != key && center >= other.offset && center <= other.offset + other.size
            }
            val from = currentItems.indexOfFirst { keyOf(it) == key }
            if (target != null && from >= 0 && from != target.index) {
                currentOnMove(from, target.index)
                // 자리를 바꾸면 줄의 기준 좌표가 상대 자리로 옮겨간다.
                // 그만큼 빼야 화면에서는 손가락 밑에 그대로 있다.
                dragOffset += (dragged.offset - target.offset)
            }
        }
    }
    val currentTrySwap by rememberUpdatedState(trySwap)

    // 목록이 밀린 만큼 끌던 줄의 기준 좌표도 밀리므로, 밀린 거리를 되돌려줘야 손가락 밑에 붙어 있는다.
    //
    // 한 바퀴에 반드시 한 프레임을 쉰다. 목록 끝에 닿으면 scrollBy 가 0 을 돌려주며 곧바로
    // 끝나는데, 그때 쉬지 않으면 이 루프가 메인 스레드를 통째로 붙잡아 앱이 멈춘다.
    LaunchedEffect(draggingKey) {
        if (draggingKey == null) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            val amount = autoScroll
            if (amount != 0f) {
                dragOffset += listState.scrollBy(amount)
                currentTrySwap()
            }
        }
    }

    LazyColumn(modifier = modifier, state = listState, contentPadding = contentPadding) {
        itemsIndexed(items, key = { _, item -> keyOf(item) }) { _, item ->
            val key = keyOf(item)
            val isDragging = key == draggingKey
            Column(
                modifier = Modifier
                    // 끄는 줄은 다른 줄 위로 떠야 한다. 안 그러면 이웃 줄에 가려진다.
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    .shadow(if (isDragging) 6.dp else 0.dp)
                    // 떠 있는 동안 밑에 깔린 줄이 비쳐 보이지 않게 바탕을 깐다.
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) { row(item) }
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .size(24.dp)
                            .semantics { contentDescription = "${labelOf(item)} 순서 이동" }
                            .pointerInput(key) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingKey = key
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        draggingKey = null
                                        dragOffset = 0f
                                        autoScroll = 0f
                                        currentOnDrop()
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        dragOffset = 0f
                                        autoScroll = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        currentTrySwap()

                                        val info = listState.layoutInfo
                                        val dragged = info.visibleItemsInfo
                                            .firstOrNull { it.key == key }
                                            ?: return@detectDragGestures
                                        val top = dragged.offset + dragOffset
                                        val bottom = top + dragged.size

                                        // 더 밀 수 없는 방향이면 아예 켜지 않는다.
                                        autoScroll = when {
                                            bottom > info.viewportEndOffset - autoScrollZonePx &&
                                                listState.canScrollForward -> AutoScrollStep

                                            top < info.viewportStartOffset + autoScrollZonePx &&
                                                listState.canScrollBackward -> -AutoScrollStep

                                            else -> 0f
                                        }
                                    },
                                )
                            },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
