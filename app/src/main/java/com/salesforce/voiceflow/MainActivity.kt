package com.salesforce.voiceflow

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
// Import Salesforce base Activity and related classes
import com.salesforce.androidsdk.app.SalesforceSDKManager // Keep for logout
import com.salesforce.androidsdk.rest.ApiVersionStrings
// No ClientManager import needed here as we receive client via callback
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.ui.SalesforceActivity // Use SalesforceActivity base class
import com.salesforce.speechsdk.tts.SynthesizerModel
import com.salesforce.speechsdk.tts.TextToSpeechManager
import org.json.JSONObject
import java.io.UnsupportedEncodingException

// Extend SalesforceActivity for proper SDK lifecycle management
// It implements SalesforceActivityInterface
class MainActivity : SalesforceActivity() {

    // Member variable to hold the RestClient instance once obtained via onResume(client)
    private var client: RestClient? = null

    // Hold the TTS Manager instance
    private lateinit var ttsManager: TextToSpeechManager

    // Compose state for the list of names
    private val namesListState = mutableStateOf<List<String>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize TTS Manager
        ttsManager = TextToSpeechManager(
            context = this,
            model = SynthesizerModel.OS // Use OS default TTS engine
        )

        // Set the Compose content
        setContent {
            MaterialTheme {
                // Pass the current state and event handlers to the Composable
                MainScreen(
                    names = namesListState.value, // Use the state variable
                    onFetchContacts = { fetchData("SELECT Name FROM Contact") },
                    onFetchAccounts = { fetchData("SELECT Name FROM Account") },
                    onClear = { namesListState.value = emptyList() }, // Update state directly
                    onLogout = { SalesforceSDKManager.getInstance().logout(this@MainActivity) },
                    onSayHello = { speakHello() }
                )
            }
        }
    }

    // This method IS CALLED BY the SalesforceActivity base class framework
    // automatically after login/passcode checks and after the RestClient is ready
    // because isBuildRestClientOnResumeEnabled defaults to true.
    // We implement it to receive and store the client.
    override fun onResume(client: RestClient) {
        // Store the client instance provided by the base activity framework
        this.client = client

        // You COULD trigger an initial data load here if you wanted data immediately
        // after the client becomes available on resume.
        // Example: if (namesListState.value.isEmpty()) {
        //             fetchData("SELECT Name FROM Contact")
        //          }
    }

    // No need for the parameterless onResume override or manual fetchRestClient method

    private fun fetchData(soql: String) {
        // Use the stored client instance. Check if it's initialized by onResume(client).
        val currentClient = this.client
        if (currentClient == null) {
            // This might happen if fetchData is called before onResume(client) completes,
            // or if authentication fails silently.
            showError("Salesforce client not available. Please wait or try logging in again.")
            return
        }

        val restRequest: RestRequest = try {
            // Use ApiVersionStrings.getVersionNumber(this) which reads from context/manifest
            RestRequest.getRequestForQuery(ApiVersionStrings.getVersionNumber(this), soql)
        } catch (e: UnsupportedEncodingException) {
            showError("Error creating request: ${e.message}")
            return
        } catch (e: Exception) {
            // Catch other potential exceptions during request creation
            showError("Error preparing request: ${e.message}")
            return
        }


        // Call sendAsync using the stored client instance
        currentClient.sendAsync(restRequest, object : RestClient.AsyncRequestCallback {
            override fun onSuccess(request: RestRequest, result: RestResponse) {
                result.consumeQuietly() // Consume before potentially switching threads
                try {
                    if (result.isSuccess) {
                        val records = result.asJSONObject().getJSONArray("records")
                        val fetchedNames = mutableListOf<String>()
                        for (i in 0..<records.length()) {
                            // Defensive check for "Name" field
                            if (records.getJSONObject(i).has("Name")) {
                                fetchedNames.add(records.getJSONObject(i).getString("Name"))
                            }
                        }
                        // Update Compose state on the UI thread
                        runOnUiThread {
                            namesListState.value = fetchedNames
                        }
                    } else {
                        // Handle API errors (e.g., bad SOQL, permissions)
                        showError("API Error: ${result.statusCode} - ${result.asString()}")
                        runOnUiThread {
                            namesListState.value = emptyList()
                        }
                    }
                } catch (e: Exception) {
                    // Handle JSON parsing or other exceptions from the success block
                    onError(e) // Delegate to onError for consistent handling
                }
            }

            override fun onError(exception: Exception) {
                // Handle network errors or exceptions from onSuccess block
                showError("Data Fetch Error: ${exception.message}")
                runOnUiThread {
                    // Clear list on error
                    namesListState.value = emptyList()
                }
            }
        })
    }

    private fun speakHello() {
        try {
            ttsManager.speak("Hello Salesforce Developer")
        } catch (e: Exception) {
            showError("TTS Error: ${e.message ?: "Failed to speak"}")
        }
    }

    private fun showError(message: String) {
        // Ensure Toast is shown on the UI thread
        runOnUiThread {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    // onPause might be automatically handled by SalesforceActivity regarding client state.
    // No specific action needed here unless you have other resources to release.
    // override fun onPause() {
    //     super.onPause()
    // }

    override fun onDestroy() {
        // Clean up TTS Manager
        if (::ttsManager.isInitialized) {
            ttsManager.shutdown()
        }
        super.onDestroy()
        // Client cleanup is likely handled by SDKManager/base class on logout/destroy
    }

    // These methods from SalesforceActivityInterface are required by the base class
    // but you may not need to add custom logic unless your app requires specific
    // actions on logout completion or user switch.
    override fun onLogoutComplete() {
        // Add any specific cleanup logic needed AFTER logout finishes
    }

    override fun onUserSwitched() {
        // Add any logic needed when the user account changes
        // May need to clear cached data and reset UI state.
        this.client = null // Clear the old client
        runOnUiThread { namesListState.value = emptyList() }
        // The base class onResume will likely trigger getting the new user's client.
    }
}


// --- Compose UI (MainScreen Composable remains the same) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    names: List<String>,
    onFetchContacts: () -> Unit,
    onFetchAccounts: () -> Unit,
    onClear: () -> Unit,
    onLogout: () -> Unit,
    onSayHello: () -> Unit // Callback for the new button
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
            // Action Buttons Row
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

            Spacer(modifier = Modifier.height(8.dp)) // Space between rows

            // Say Hello Button
            Button(onClick = onSayHello, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Say Hello")
            }


            Spacer(modifier = Modifier.height(16.dp)) // Space before list

            // Results List
            if (names.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) { // Use weight to fill remaining space
                    Text("List is empty.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { // Use weight to fill remaining space
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