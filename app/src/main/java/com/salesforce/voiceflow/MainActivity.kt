package com.salesforce.voiceflow

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.ui.SalesforceActivity
import com.salesforce.voiceflow.ui.screens.MainScreen
import com.salesforce.voiceflow.ui.theme.VoiceflowTheme

object AppDestinations {
    const val AUTH_ROUTE = "auth"
    const val MAIN_ROUTE = "main"
}

/**
 * Main activity
 */
class MainActivity : SalesforceActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var client: RestClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val navController = rememberNavController()
            val uiState by viewModel.uiState.collectAsState()

            VoiceflowTheme {
                NavHost(navController = navController, startDestination = AppDestinations.AUTH_ROUTE) {
                    composable(AppDestinations.AUTH_ROUTE) {
                        // Placeholder for the login screen handled by the Salesforce SDK
                    }
                    composable(AppDestinations.MAIN_ROUTE) {
                        MainScreen(
                            uiState = uiState,
                            onFetchContacts = { viewModel.fetchData("SELECT Name FROM Contact") },
                            onFetchAccounts = { viewModel.fetchData("SELECT Name FROM Account") },
                            onClear = { viewModel.clearData() },
                            onLogout = {
                                SalesforceSDKManager.getInstance().logout(this@MainActivity)
                                navController.navigate(AppDestinations.AUTH_ROUTE) {
                                    popUpTo(AppDestinations.MAIN_ROUTE) {
                                        inclusive = true
                                    }
                                }
                            }
                        )
                    }
                }

                LaunchedEffect(client) {
                    client?.let {
                        viewModel.onClientReady(it)
                        navController.navigate(AppDestinations.MAIN_ROUTE) {
                            popUpTo(AppDestinations.AUTH_ROUTE) {
                                inclusive = true
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume(client: RestClient?) {
        this.client = client
    }
}
