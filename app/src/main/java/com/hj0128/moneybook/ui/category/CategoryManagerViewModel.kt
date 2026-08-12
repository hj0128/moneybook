package com.hj0128.moneybook.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.local.entity.CategoryEntity
import com.hj0128.moneybook.data.model.CategoryNode
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.repository.CategoryUsage
import com.hj0128.moneybook.data.repository.LedgerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 편집/추가 다이얼로그가 열려 있을 때의 대상. */
sealed interface CategoryEditTarget {
    /** 새 대분류. */
    data object NewTopLevel : CategoryEditTarget

    /** [parent] 밑에 새 소분류. */
    data class NewChild(val parent: CategoryEntity) : CategoryEditTarget

    /** 기존 카테고리 수정. */
    data class Edit(val category: CategoryEntity) : CategoryEditTarget
}

/** 삭제 다이얼로그 상태. 무엇을 지우는지와, 옮길 곳 후보. */
data class DeleteTarget(
    val category: CategoryEntity,
    val usage: CategoryUsage,
    /** 이전 대상 후보. 옮길 것이 없으면 비어 있다. */
    val candidates: List<CategoryEntity>,
)

data class CategoryManagerUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val nodes: List<CategoryNode> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryManagerViewModel(
    private val repository: LedgerRepository,
) : ViewModel() {

    private val _type = MutableStateFlow(TransactionType.EXPENSE)
    val type: StateFlow<TransactionType> = _type.asStateFlow()

    /** 열어 둔 대분류. null 이면 대분류 목록을 보는 중이다. */
    private val _openParentId = MutableStateFlow<Long?>(null)

    private val _editTarget = MutableStateFlow<CategoryEditTarget?>(null)
    val editTarget: StateFlow<CategoryEditTarget?> = _editTarget.asStateFlow()

    private val _deleteTarget = MutableStateFlow<DeleteTarget?>(null)
    val deleteTarget: StateFlow<DeleteTarget?> = _deleteTarget.asStateFlow()

    /** 다이얼로그에 띄울 오류 한 줄(이름 중복 등). */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val state: StateFlow<CategoryManagerUiState> =
        _type.flatMapLatest { type ->
            repository.observeCategoryTree(type).map { nodes ->
                CategoryManagerUiState(type = type, nodes = nodes)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoryManagerUiState(),
        )

    /**
     * 열어 둔 대분류.
     *
     * 목록에서 찾아 쓰므로, 그 대분류가 지워지면 자연히 null 이 되어 대분류 목록으로 돌아온다.
     */
    val openParent: StateFlow<CategoryNode?> =
        combine(state, _openParentId) { uiState, id ->
            uiState.nodes.firstOrNull { it.category.id == id }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * 순서 변경 중인 목록. null 이면 보통 상태다.
     *
     * DB 대신 이 사본을 화면에 보여준다. 한 칸 옮길 때마다 저장하면 저장 결과가 Flow 로
     * 되돌아오는 사이에 화면이 튄다. 손을 뗄 때 한 번만 저장한다.
     */
    private val _reorderDraft = MutableStateFlow<List<CategoryEntity>?>(null)
    val reorderDraft: StateFlow<List<CategoryEntity>?> = _reorderDraft.asStateFlow()

    fun selectType(type: TransactionType) {
        _type.value = type
        // 종류가 바뀌면 카테고리 목록 자체가 달라지므로 열어 둔 대분류도 닫는다.
        _openParentId.value = null
        stopReorder()
    }

    fun openParent(categoryId: Long) {
        _openParentId.value = categoryId
        stopReorder()
    }

    fun closeParent() {
        _openParentId.value = null
        stopReorder()
    }

    /** 지금 보고 있는 목록(대분류 목록이거나 열어 둔 대분류의 소분류)의 순서를 바꾸기 시작한다. */
    fun startReorder() {
        val parent = openParent.value
        _reorderDraft.value = parent?.children ?: state.value.nodes.map { it.category }
    }

    fun moveInDraft(from: Int, to: Int) {
        val draft = _reorderDraft.value ?: return
        if (from !in draft.indices || to !in draft.indices) return
        _reorderDraft.value = draft.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** 손을 뗐을 때 저장. 모드는 그대로 두어 이어서 다른 줄도 옮길 수 있다. */
    fun commitOrder() {
        val draft = _reorderDraft.value ?: return
        viewModelScope.launch { repository.reorderCategories(draft.map { it.id }) }
    }

    fun stopReorder() {
        _reorderDraft.value = null
    }

    fun startAddTopLevel() {
        _editTarget.value = CategoryEditTarget.NewTopLevel
    }

    fun startAddChild(parent: CategoryEntity) {
        _editTarget.value = CategoryEditTarget.NewChild(parent)
    }

    fun startEdit(category: CategoryEntity) {
        _editTarget.value = CategoryEditTarget.Edit(category)
    }

    fun dismissEdit() {
        _editTarget.value = null
        _errorMessage.value = null
    }

    fun submitEdit(name: String, colorArgb: Int) {
        val target = _editTarget.value ?: return
        viewModelScope.launch {
            val result = when (target) {
                is CategoryEditTarget.NewTopLevel ->
                    repository.addCategory(name, _type.value, colorArgb, parentId = null).map { }

                is CategoryEditTarget.NewChild ->
                    repository.addCategory(name, _type.value, colorArgb, parentId = target.parent.id).map { }

                is CategoryEditTarget.Edit ->
                    repository.renameCategory(target.category.id, name, colorArgb)
            }
            result
                .onSuccess { dismissEdit() }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    /**
     * 삭제 준비. 옮길 것이 있으면 후보를 함께 담는다.
     *
     * 대분류는 다른 '대분류' 로만 옮길 수 있다. 소분류 밑에 소분류를 두면 3뎁스가 된다.
     * 소분류는 자기 자신만 빼고 어디로든 갈 수 있다.
     */
    fun startDelete(category: CategoryEntity) {
        viewModelScope.launch {
            val usage = repository.categoryUsage(category.id)
            val all = state.value.nodes
            val candidates = if (!usage.needsReassign) {
                emptyList()
            } else if (category.isTopLevel) {
                all.map { it.category }.filter { it.id != category.id }
            } else {
                all.flatMap { listOf(it.category) + it.children }.filter { it.id != category.id }
            }
            _deleteTarget.value = DeleteTarget(category, usage, candidates)
        }
    }

    fun dismissDelete() {
        _deleteTarget.value = null
    }

    fun confirmDelete(reassignToId: Long?) {
        val target = _deleteTarget.value ?: return
        viewModelScope.launch {
            repository.deleteCategory(target.category.id, reassignToId)
            _deleteTarget.value = null
        }
    }

    fun clearError() {
        _errorMessage.update { null }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CategoryManagerViewModel(repository = appContainer().ledgerRepository)
            }
        }
    }
}

