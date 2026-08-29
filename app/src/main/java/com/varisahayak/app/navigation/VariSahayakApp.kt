package com.varisahayak.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.varisahayak.R
import com.varisahayak.app.MainViewModel
import com.varisahayak.domain.repository.AuthState
import com.varisahayak.feature.auth.SignInScreen
import com.varisahayak.feature.auth.SignInViewModel
import com.varisahayak.feature.auth.SignUpScreen
import com.varisahayak.feature.auth.SignUpViewModel
import com.varisahayak.feature.lostfound.LostFoundScreen
import com.varisahayak.feature.map.IncidentMapScreen
import com.varisahayak.feature.qr.QrScannerScreen

/**
 * Main application entry point for Compose.
 *
 * This hosts the NavHost and manages the top-level navigation state.
 */
@Composable
fun VariSahayakApp(
    viewModel: MainViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val authState by viewModel.authState.collectAsState(initial = AuthState.Unknown)
    val profile by viewModel.profile.collectAsState(initial = null)

    LaunchedEffect(authState, profile) {
        when (val state = authState) {
            is AuthState.SignedIn -> {
                profile?.let { p ->
                    val home = TopLevelDestination.homeRoute(p.role)
                    navController.navigate(home) {
                        popUpTo(Destination.Splash) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            is AuthState.SignedOut -> {
                navController.navigate(Destination.SignIn) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
            is AuthState.SessionExpired -> {
                navController.navigate(Destination.SignIn) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
            AuthState.Unknown -> {
                // Stay on current (likely Splash)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Splash,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Destination.Splash> {
                SplashScreen()
            }
            composable<Destination.SignIn> {
                val signInViewModel: SignInViewModel = hiltViewModel()
                SignInScreen(
                    viewModel = signInViewModel,
                    onNavigateToSignUp = { navController.navigate(Destination.SignUp) }
                )
            }
            composable<Destination.SignUp> {
                val signUpViewModel: SignUpViewModel = hiltViewModel()
                SignUpScreen(
                    viewModel = signUpViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onSignUpSuccess = {
                        // After signup, Supabase might auto-sign-in, or we might need to navigate to sign-in.
                        // Given our AuthState observation in LaunchedEffect, if it auto-signs-in, 
                        // it will navigate to Home. If not, we can manually go to SignIn.
                        navController.navigate(Destination.SignIn) {
                            popUpTo(Destination.SignUp) { inclusive = true }
                        }
                    }
                )
            }
            composable<Destination.VolunteerDashboard> {
                PlaceholderScreen(title = stringResource(R.string.nav_dashboard) + " (Volunteer)")
            }
            composable<Destination.ResponderDashboard> {
                PlaceholderScreen(title = stringResource(R.string.nav_dashboard) + " (Responder)")
            }
            composable<Destination.CommandDashboard> {
                PlaceholderScreen(title = stringResource(R.string.command_title))
            }
            composable<Destination.AdminDashboard> {
                PlaceholderScreen(title = stringResource(R.string.role_admin))
            }
            composable<Destination.IncidentList> {
                PlaceholderScreen(title = stringResource(R.string.nav_incidents))
            }
            // Placeholder until Phase 4 builds the detail screen. It must stay registered:
            // the map taps through to this route, and an unregistered route throws.
            composable<Destination.IncidentDetail> {
                PlaceholderScreen(title = stringResource(R.string.incident_detail_title))
            }
            composable<Destination.IncidentMap> {
                IncidentMapScreen(
                    onIncidentSelected = { clientId ->
                        navController.navigate(Destination.IncidentDetail(clientId))
                    },
                )
            }
            composable<Destination.QrScanner> {
                QrScannerScreen(
                    onTokenAccepted = { token ->
                        // The SOS Bridge reuses the ordinary reporting flow, carrying the
                        // opaque token. There is no separate SOS Bridge pipeline.
                        navController.navigate(
                            Destination.ReportIncident(
                                sosBridgeToken = token.value,
                                isSos = true,
                            ),
                        )
                    },
                )
            }
            composable<Destination.LostAndFound> {
                LostFoundScreen()
            }
            // Placeholder until Phase 4 builds the reporting screen. Must stay registered:
            // the QR scanner routes here after a token is accepted.
            composable<Destination.ReportIncident> {
                PlaceholderScreen(title = stringResource(R.string.report_title))
            }
            composable<Destination.Profile> {
                PlaceholderScreen(title = stringResource(R.string.nav_profile))
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge
        )
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
