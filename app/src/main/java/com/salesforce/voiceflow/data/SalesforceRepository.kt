package com.salesforce.voiceflow.data

import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import org.json.JSONException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import android.util.Log

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

    suspend fun describeGlobal(): Result<List<String>> {
        val restClient = client ?: return Result.failure(IllegalStateException("Salesforce client not available."))

        return try {
            val request = RestRequest.getRequestForDescribeGlobal(ApiVersionStrings.getVersionNumber(null))
            val response = restClient.sendAsync(request)

            if (response.isSuccess) {
                val jsonResponse = response.asJSONObject()
                val sobjects = jsonResponse.getJSONArray("sobjects")
                val objectLabels = mutableSetOf<String>() // Use a Set to handle potential duplicates

                for (i in 0 until sobjects.length()) {
                    val sobject = sobjects.getJSONObject(i)
                    val apiName = sobject.getString("name")
                    val isCreatable = sobject.optBoolean("createable", false)
                    val isCustomSetting = sobject.optBoolean("customSetting", false)
                    val isLayoutable = sobject.optBoolean("layoutable", false)
                    val isSearchable = sobject.optBoolean("searchable", false)

                    // --- CORRECTED LOGIC ---
                    val isLikelyUserObject = (isLayoutable && isSearchable)

                    // Filter out system-generated objects
                    val isSystemObject = apiName.endsWith("__Share") ||
                            apiName.endsWith("__History") ||
                            apiName.endsWith("__Rule") ||
                            apiName.endsWith("__ChangeEvent") ||
                            isCustomSetting

                    if (isCreatable && !isSystemObject && isLikelyUserObject) {
                        objectLabels.add(sobject.getString("labelPlural"))
                    }
                }

                Log.d("SalesforceRepository", "Found ${objectLabels.size} relevant objects.")
                objectLabels.chunked(20).forEachIndexed { index, chunk ->
                    Log.d("SalesforceRepository", "Relevant Objects (${index + 1}): ${chunk.joinToString()}")
                }

                Result.success(objectLabels.sorted())
            } else {
                Log.e("SalesforceRepository", "Describe Global Error: $response")
                Result.failure(Exception(response.toString()))
            }
        } catch (e: Exception) {
            Log.e("SalesforceRepository", "Describe Global Exception", e)
            Result.failure(e)
        }
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