/*
 * Copyright (c) 2017-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.voiceflow

import android.os.Bundle
import androidx.activity.compose.setContent
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
