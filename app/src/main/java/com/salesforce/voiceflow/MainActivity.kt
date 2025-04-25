/*
 * Copyright (c) 2025 Toni Melisma
 */
package com.salesforce.voiceflow

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.ui.SalesforceActivity
import java.io.UnsupportedEncodingException

class MainActivity : SalesforceActivity() {

    private var client: RestClient? = null
    private lateinit var namesListState: MutableState<List<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileSyncSDKManager.getInstance().setViewNavigationVisibility(this)

        setContent {
            namesListState = remember { mutableStateOf(emptyList()) }

            MaterialTheme {
                MainScreen(
                    names = namesListState.value,
                    onFetchContacts = { fetchData("SELECT Name FROM Contact", namesListState) },
                    onFetchAccounts = { fetchData("SELECT Name FROM Account", namesListState) },
                    onClear = { namesListState.value = emptyList() },
                    onLogout = { SalesforceSDKManager.getInstance().logout(this@MainActivity) }
                )
            }
        }
    }


    override fun onResume(client: RestClient?) {
        this.client = client
    }

    private fun fetchData(soql: String, state: MutableState<List<String>>) {
        val currentClient = this.client
        if (currentClient == null) {
            showError("Salesforce client not available.")
            return
        }

        val restRequest = try {
            RestRequest.getRequestForQuery(ApiVersionStrings.getVersionNumber(this), soql)
        } catch (e: UnsupportedEncodingException) {
            showError("Error creating request: ${e.message}")
            return
        }

        currentClient.sendAsync(restRequest, object : RestClient.AsyncRequestCallback {
            override fun onSuccess(request: RestRequest, result: RestResponse) {
                result.consumeQuietly()
                try {
                    if (result.isSuccess) {
                        val records = result.asJSONObject().getJSONArray("records")
                        val fetchedNames = mutableListOf<String>()
                        for (i in 0..<records.length()) {
                            fetchedNames.add(records.getJSONObject(i).getString("Name"))
                        }
                        runOnUiThread {
                            state.value = fetchedNames
                        }
                    } else {
                        showError("API Error: ${result.statusCode} - ${result.asString()}")
                        runOnUiThread {
                            state.value = emptyList()
                        }
                    }
                } catch (e: Exception) {
                    onError(e)
                }
            }

            override fun onError(exception: Exception) {
                showError("Network Error: ${exception.message}")
                runOnUiThread {
                    state.value = emptyList()
                }
            }
        })
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    names: List<String>,
    onFetchContacts: () -> Unit,
    onFetchAccounts: () -> Unit,
    onClear: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VoiceFlow") },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onFetchContacts, modifier = Modifier.weight(1f)) {
                    Text("Fetch Contacts")
                }
                Button(onClick = onFetchAccounts, modifier = Modifier.weight(1f)) {
                    Text("Fetch Accounts")
                }
                Button(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text("Clear")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (names.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("List is empty.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(names) { name ->
                        Text(
                            text = name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}