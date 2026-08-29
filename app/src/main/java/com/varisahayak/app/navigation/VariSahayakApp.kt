package com.varisahayak.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.varisahayak.R
import com.varisahayak.app.MainViewModel
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.component.RoleBadge
import com.varisahayak.domain.model.UserRole
import com.varisahayak.domain.repository.AuthState
import com.varisahayak.feature.auth.SignInScreen
import com.varisahayak.feature.auth.SignInViewModel
import com.varisahayak.feature.auth.SignUpScreen
import com.varisahayak.feature.auth.SignUpViewModel
import com.varisahayak.feature.dashboard.CommandDashboardScreen
import com.varisahayak.feature.dashboard.DashboardViewModel
import com.varisahayak.feature.dashboard.ResponderDashboardScreen
import com.varisahayak.feature.dashboard.VolunteerDashboardScreen
import com.varisahayak.feature.auth.ForgotPasswordScreen
import com.varisahayak.feature.incidents.IncidentDetailScreen
import com.varisahayak.feature.incidents.IncidentListScreen
import com.varisahayak.feature.incidents.ReportIncidentScreen
import com.varisahayak.feature.lostfound.LostFoundScreen
import com.varisahayak.feature.map.IncidentMapScreen
import com.varisahayak.feature.profile.ProfileScreen
import com.varisahayak.feature.profile.ProfileViewModel
import com.varisahayak.feature.qr.QrScannerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariSahayakApp(
    viewModel: MainViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val authState by viewModel.authState.collectAsState(initial = AuthState.Unknown)
    val profile by viewModel.profile.collectAsState(initial = null)
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    val currentRoute = currentBackStackEntry?.destination?.route

    // Auth navigation state machine
    LaunchedEffect(authState, profile) {
        when (authState) {
            is AuthState.SignedIn -> {
                profile?.let { p ->
                    val home = TopLevelDestination.homeRoute(p.role)
                    // Only navigate to home if currently on splash or auth screens
                    if (currentRoute == null || currentRoute.contains("Splash") || currentRoute.contains("SignIn") || currentRoute.contains("SignUp")) {
                        navController.navigate(home) {
                            popUpTo(Destination.Splash) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }
            is AuthState.SignedOut, is AuthState.SessionExpired -> {
                if (currentRoute == null || !currentRoute.contains("SignIn") && !currentRoute.contains("SignUp")) {
                    navController.navigate(Destination.SignIn) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            AuthState.Unknown -> {
                // Stay on splash
            }
        }
    }

    val isAuthOrSplash = currentRoute != null && (
        currentRoute.contains("Splash") ||
        currentRoute.contains("SignIn") ||
        currentRoute.contains("SignUp")
    )

    val currentRole = profile?.role ?: UserRole.VOLUNTEER

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (!isAuthOrSplash) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    actions = {
                        profile?.role?.let { role ->
                            RoleBadge(role = role)
                        }

                        IconButton(onClick = { navController.navigate(Destination.Profile) }) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = stringResource(R.string.nav_profile),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        },
        bottomBar = {
            if (!isAuthOrSplash && profile != null) {
                val destinations = TopLevelDestination.forRole(currentRole)
                NavigationBar {
                    destinations.forEach { dest ->
                        val isSelected = currentRoute?.contains(dest.route::class.simpleName ?: "") == true
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = dest.icon,
                                    contentDescription = stringResource(dest.labelRes),
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Splash,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<Destination.Splash> {
                SplashScreen()
            }

            composable<Destination.SignIn> {
                val signInViewModel: SignInViewModel = hiltViewModel()
                SignInScreen(
                    viewModel = signInViewModel,
                    onNavigateToSignUp = { navController.navigate(Destination.SignUp) },
                )
            }

            composable<Destination.SignUp> {
                val signUpViewModel: SignUpViewModel = hiltViewModel()
                SignUpScreen(
                    viewModel = signUpViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onSignUpSuccess = {
                        navController.navigate(Destination.SignIn) {
                            popUpTo(Destination.SignUp) { inclusive = true }
                        }
                    },
                )
            }

            composable<Destination.ForgotPassword> {
                ForgotPasswordScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable<Destination.VolunteerDashboard> {
                val dashboardViewModel: DashboardViewModel = hiltViewModel()
                VolunteerDashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToReport = { navController.navigate(Destination.ReportIncident()) },
                    onNavigateToScan = { navController.navigate(Destination.QrScanner) },
                    onNavigateToMap = { navController.navigate(Destination.IncidentMap) },
                    onNavigateToLostFound = { navController.navigate(Destination.LostAndFound) },
                    onNavigateToDetail = { clientId ->
                        navController.navigate(Destination.IncidentDetail(clientId))
                    },
                )
            }

            composable<Destination.ResponderDashboard> {
                val dashboardViewModel: DashboardViewModel = hiltViewModel()
                ResponderDashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToDetail = { clientId ->
                        navController.navigate(Destination.IncidentDetail(clientId))
                    },
                )
            }

            composable<Destination.CommandDashboard> {
                val dashboardViewModel: DashboardViewModel = hiltViewModel()
                CommandDashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToDetail = { clientId ->
                        navController.navigate(Destination.IncidentDetail(clientId))
                    },
                )
            }

            composable<Destination.AdminDashboard> {
                val dashboardViewModel: DashboardViewModel = hiltViewModel()
                CommandDashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToDetail = { clientId ->
                        navController.navigate(Destination.IncidentDetail(clientId))
                    },
                )
            }

            composable<Destination.IncidentList> {
                IncidentListScreen(
                    onIncidentSelected = { clientId ->
                        navController.navigate(Destination.IncidentDetail(clientId))
                    },
                )
            }

            composable<Destination.IncidentDetail> {
                IncidentDetailScreen()
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

            composable<Destination.ReportIncident> {
                ReportIncidentScreen(
                    onSaved = { clientId ->
                        // Replace the form in the back stack: pressing back after filing a
                        // report must not reopen a form that was already submitted.
                        navController.navigate(Destination.IncidentDetail(clientId)) {
                            popUpTo(Destination.ReportIncident()) { inclusive = true }
                        }
                    },
                )
            }

            composable<Destination.Profile> {
                val profileViewModel: ProfileViewModel = hiltViewModel()
                ProfileScreen(
                    viewModel = profileViewModel,
                    onSignOut = {
                        navController.navigate(Destination.SignIn) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}
