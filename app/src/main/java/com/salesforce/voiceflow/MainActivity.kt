package com.salesforce.voiceflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.voiceflow.data.SalesforceRepository
import com.salesforce.voiceflow.ui.screens.MainScreen
import com.salesforce.voiceflow.ui.theme.VoiceflowTheme

object AppDestinations {
    const val MAIN_ROUTE = "main"
}

class MainActivity : ComponentActivity() {

    private val repository = SalesforceRepository()
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(repository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val accountType = SalesforceSDKManager.getInstance().accountType

        ClientManager(
            this,
            accountType,
            SalesforceSDKManager.getInstance().shouldLogoutWhenTokenRevoked()
        ).getRestClient(this, object : ClientManager.RestClientCallback {
            override fun authenticatedRestClient(client: RestClient?) {
                if (client == null) {
                    SalesforceSDKManager.getInstance().logout(this@MainActivity)
                    return
                }

                repository.setClient(client)

        setContent {
            val navController = rememberNavController()
            val uiState by viewModel.uiState.collectAsState()

            VoiceflowTheme {
                        NavHost(navController = navController, startDestination = AppDestinations.MAIN_ROUTE) {
                    composable(AppDestinations.MAIN_ROUTE) {
                        MainScreen(
                            uiState = uiState,
                            onFetchObjects = { viewModel.fetchObjects() },
                            onFetchAccounts = { viewModel.fetchAccounts() },
                            onClear = { viewModel.clearData() },
                            onLogout = {
                                SalesforceSDKManager.getInstance().logout(this@MainActivity)
                                        // You might want to navigate to a dedicated login screen
                                        // or finish the activity after logout.
                                        finish()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        })
    }
}
