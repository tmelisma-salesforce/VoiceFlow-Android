package com.salesforce.voiceflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class MainUiState(
    val data: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private var client: RestClient? = null

    fun onClientReady(client: RestClient) {
        this.client = client
    }

    fun fetchData(soql: String) {
        val restClient = client ?: run {
            _uiState.value = MainUiState(error = "Salesforce client not available.")
            return
        }

        _uiState.value = MainUiState(isLoading = true)

        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val request = RestRequest.getRequestForQuery(ApiVersionStrings.getVersionNumber(null), soql)
                    suspendCoroutine { continuation ->
                        restClient.sendAsync(request, object : RestClient.AsyncRequestCallback {
                            override fun onSuccess(request: RestRequest?, response: com.salesforce.androidsdk.rest.RestResponse?) {
                                continuation.resume(response)
                            }

                            override fun onError(exception: java.lang.Exception?) {
                                continuation.resume(null)
                            }
                        })
                    }
                }


                if (response != null && response.isSuccess) {
                    val records = response.asJSONObject().getJSONArray("records")
                    val names = mutableListOf<String>()
                    for (i in 0 until records.length()) {
                        names.add(records.getJSONObject(i).getString("Name"))
                    }
                    _uiState.value = MainUiState(data = names)
                } else {
                    _uiState.value = MainUiState(error = response?.toString() ?: "An error occurred")
                }
            } catch (e: Exception) {
                _uiState.value = MainUiState(error = e.message ?: "An unknown error occurred.")
            }
        }
    }

    fun clearData() {
        _uiState.value = MainUiState()
    }
} 