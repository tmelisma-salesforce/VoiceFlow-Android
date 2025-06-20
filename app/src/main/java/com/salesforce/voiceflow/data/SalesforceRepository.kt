package com.salesforce.voiceflow.data

import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import org.json.JSONException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SalesforceRepository {

    private var client: RestClient? = null

    fun setClient(client: RestClient) {
        this.client = client
    }

    suspend fun getContactNames(): Result<List<String>> {
        return fetchData("SELECT Name FROM Contact")
    }

    suspend fun getAccountNames(): Result<List<String>> {
        return fetchData("SELECT Name FROM Account")
    }

    private suspend fun fetchData(soql: String): Result<List<String>> {
        val restClient = client ?: return Result.failure(IllegalStateException("Salesforce client not available."))

        return try {
            val request = RestRequest.getRequestForQuery(ApiVersionStrings.getVersionNumber(null), soql)
            val response = restClient.sendAsync(request)

            if (response.isSuccess) {
                val records = response.asJSONObject().getJSONArray("records")
                val names = mutableListOf<String>()
                for (i in 0 until records.length()) {
                    names.add(records.getJSONObject(i).getString("Name"))
                }
                Result.success(names)
            } else {
                Result.failure(Exception(response.toString()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private suspend fun RestClient.sendAsync(request: RestRequest): RestResponse = suspendCoroutine { continuation ->
    this.sendAsync(request, object : RestClient.AsyncRequestCallback {
        override fun onSuccess(request: RestRequest?, response: RestResponse) {
            continuation.resume(response)
        }

        override fun onError(exception: Exception) {
            continuation.resumeWith(Result.failure(exception))
        }
    })
} 