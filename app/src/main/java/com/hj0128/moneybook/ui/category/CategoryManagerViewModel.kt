package com.hj0128.moneybook.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hj0128.moneybook.appContainer
import com.hj0128.moneybook.data.local.entity.CategoryEntity
import com.hj0128.moneybook.data.model.TransactionType
import com.hj0128.moneybook.data.repository.CategoryUsage
import com.hj0128.moneybook.data.repository.LedgerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 대분류 하나와 그 밑 소분류들. 화면은 이 묶음을 그대로 그린다. */
data class CategoryNode(
    val category: CategoryEntity,
    val children: List<CategoryEntity>,
)

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

    private val _editTarget = MutableStateFlow<CategoryEditTarget?>(null)
    val editTarget: StateFlow<CategoryEditTarget?> = _editTarget.asStateFlow()

    private val _deleteTarget = MutableStateFlow<DeleteTarget?>(null)
    val deleteTarget: StateFlow<DeleteTarget?> = _deleteTarget.asStateFlow()

    /** 다이얼로그에 띄울 오류 한 줄(이름 중복 등). */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val state: StateFlow<CategoryManagerUiState> =
        _type.flatMapLatest { type ->
            repository.observeCategories(type).map { flat ->
                CategoryManagerUiState(type = type, nodes = flat.toNodes())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoryManagerUiState(),
        )

    fun selectType(type: TransactionType) {
        _type.value = type
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

/**
 * 평평한 목록을 대분류 > 소분류로 묶는다.
 *
 * 부모가 사라진 소분류(경쟁 조건 등)는 버리지 않고 대분류로 올려서 보여준다.
 * 화면에서 사라지면 사용자는 지울 방법조차 없어진다.
 */
internal fun List<CategoryEntity>.toNodes(): List<CategoryNode> {
    val byParent = filterNot { it.isTopLevel }.groupBy { it.parentId }
    val topLevelIds = filter { it.isTopLevel }.map { it.id }.toSet()
    val orphans = filterNot { it.isTopLevel }.filter { it.parentId !in topLevelIds }

    return (filter { it.isTopLevel } + orphans).map { parent ->
        CategoryNode(category = parent, children = byParent[parent.id].orEmpty())
    }
}
