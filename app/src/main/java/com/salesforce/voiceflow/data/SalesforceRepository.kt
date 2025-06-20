package com.salesforce.voiceflow.data

import android.util.Log
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestRequest.RestMethod
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

    suspend fun describeGlobal(): Result<List<String>> {
        val restClient = client ?: return Result.failure(IllegalStateException("Salesforce client not available."))

        // --- STEP 1: Get the list of ALL relevant objects from their global tab visibility ---
        val accessibleTabsResult = getLightningSalesAppObjects()
        val accessibleTabs = accessibleTabsResult.getOrElse {
            Log.e("SalesforceRepository", "Could not get accessible tabs.", it)
            return Result.failure(it) // Fail fast if we can't get the tabs
        }

        // If we couldn't determine which objects are in the app, it's better to stop.
        if (accessibleTabs.isEmpty() && accessibleTabsResult.isFailure) {
            return Result.failure(accessibleTabsResult.exceptionOrNull() ?: IllegalStateException("No objects found in Sales App."))
        }

        // --- STEP 2: Get ALL objects and their permissions from describeGlobal ---
        return try {
            val request = RestRequest.getRequestForDescribeGlobal(ApiVersionStrings.getVersionNumber(null))
            val response = restClient.sendAsync(request)

            if (response.isSuccess) {
                val jsonResponse = response.asJSONObject()
                val sobjects = jsonResponse.getJSONArray("sobjects")
                val finalObjectLabels = mutableSetOf<String>()

                for (i in 0 until sobjects.length()) {
                    val sobject = sobjects.getJSONObject(i)
                    val apiName = sobject.getString("name")

                    // Combine the results: Is the object in the LightningSales app AND is it creatable?
                    if (accessibleTabs.contains(apiName) && sobject.optBoolean("createable", false)) {
                        finalObjectLabels.add(sobject.getString("labelPlural"))
                    }
                }
                Result.success(finalObjectLabels.sorted())
            } else {
                Result.failure(Exception(response.toString()))
            }
        } catch (e: Exception) {
            Log.e("SalesforceRepository", "Describe Global Exception", e)
            Result.failure(e)
        }
    }

    /**
     * Gets the SObject tabs specifically from the 'LightningSales' application
     * by fetching its ID and then calling the direct UI API endpoint for that app.
     *
     * This function targets only 'LightningSales' as requested.
     *
     * @return A Result containing a Set of SObject API names from the LightningSales app.
     */
    private suspend fun getLightningSalesAppObjects(): Result<Set<String>> {
        val restClient = client ?: return Result.failure(IllegalStateException("Salesforce client not available."))

        try {
            // STEP 1: Fetch all available apps to find the correct ID for "LightningSales".
            // The previous SOQL query on AppDefinition proved unreliable.
            val path = "/services/data/${ApiVersionStrings.getVersionNumber(null)}/ui-api/apps?formFactor=Large"
            val request = RestRequest(RestMethod.GET, path)
            val response = restClient.sendAsync(request)

            if (!response.isSuccess) {
                Log.e("SalesforceRepository", "Could not fetch the list of apps: $response")
                return Result.failure(Exception("Could not fetch the list of apps: $response"))
            }

            val apps = response.asJSONObject().getJSONArray("apps")
            var salesApp: org.json.JSONObject? = null
            for (i in 0 until apps.length()) {
                val app = apps.getJSONObject(i)
                if (app.getString("developerName") == "LightningSales") {
                    salesApp = app
                    break
                }
            }

            if (salesApp == null) {
                Log.e("SalesforceRepository", "The 'LightningSales' app was not found in the user's available apps.")
                return Result.failure(Exception("The 'LightningSales' app was not found."))
            }

            val salesAppId = salesApp.getString("appId")
            Log.d("SalesforceRepository", "Found 'LightningSales' app with ID: $salesAppId")

            // STEP 2: Use the valid App ID to fetch the app's metadata.
            val appPath = "/services/data/${ApiVersionStrings.getVersionNumber(null)}/ui-api/apps/$salesAppId?formFactor=Large"
            val appRequest = RestRequest(RestMethod.GET, appPath)
            val appResponse = restClient.sendAsync(appRequest)

            if (appResponse.isSuccess) {
                val app = appResponse.asJSONObject()
                val navItems = app.optJSONArray("navItems")

                if (navItems == null || navItems.length() == 0) {
                    Log.w("SalesforceRepository", "Request for 'LightningSales' app was successful but contained no navigation items.")
                    return Result.success(emptySet())
                }

                val sobjectApiNames = mutableSetOf<String>()
                for (j in 0 until navItems.length()) {
                    navItems.getJSONObject(j).optString("objectApiName", null)?.let {
                        sobjectApiNames.add(it)
                    }
                }
                Log.d("SalesforceRepository", "Found ${sobjectApiNames.size} objects in the 'LightningSales' App: ${sobjectApiNames.joinToString()}")
                return Result.success(sobjectApiNames)
            } else {
                Log.e("SalesforceRepository", "Direct UI API /apps/{appId} Error: $appResponse")
                return Result.failure(Exception(appResponse.toString()))
            }
        } catch (e: Exception) {
            Log.e("SalesforceRepository", "Direct app fetch Exception", e)
            return Result.failure(e)
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