package com.salesforce.voiceflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.salesforce.voiceflow.data.SalesforceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val data: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class MainViewModel(private val repository: SalesforceRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    fun fetchObjects() = fetchData { repository.describeGlobal() }
    fun fetchAccounts() = fetchData { repository.getAccountNames() }

    private fun fetchData(fetch: suspend () -> Result<List<String>>) {
        _uiState.value = MainUiState(isLoading = true)

        viewModelScope.launch {
            val result = fetch()
            result.onSuccess { names ->
                    _uiState.value = MainUiState(data = names)
            }.onFailure { error ->
                _uiState.value = MainUiState(error = error.message ?: "An unknown error occurred.")
            }
        }
    }

    fun clearData() {
        _uiState.value = MainUiState()
    }
}

@Suppress("UNCHECKED_CAST")
class MainViewModelFactory(
    private val repository: SalesforceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 