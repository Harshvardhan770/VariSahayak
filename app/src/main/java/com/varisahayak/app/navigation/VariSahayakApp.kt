package com.varisahayak.app.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.varisahayak.R
import com.varisahayak.app.MainViewModel
import com.varisahayak.core.designsystem.Dimens
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.designsystem.component.FloatingTopBar
import com.varisahayak.core.designsystem.component.WalkieTalkieWidget
import com.varisahayak.core.locale.AppLocale
import com.varisahayak.core.permissions.AppPermissions
import com.varisahayak.core.walkie.WalkieUiState
import com.varisahayak.domain.model.UserRole
import com.varisahayak.domain.repository.AuthState
import com.varisahayak.feature.auth.ForgotPasswordScreen
import com.varisahayak.feature.auth.SignInScreen
import com.varisahayak.feature.auth.SignInViewModel
import com.varisahayak.feature.auth.SignUpScreen
import com.varisahayak.feature.auth.SignUpViewModel
import com.varisahayak.feature.communication.CommunicationScreen
import com.varisahayak.feature.dashboard.CommandDashboardScreen
import com.varisahayak.feature.dashboard.DashboardActions
import com.varisahayak.feature.dashboard.ResponderDashboardScreen
import com.varisahayak.feature.dashboard.VolunteerDashboardScreen
import com.varisahayak.feature.incidents.IncidentDetailScreen
import com.varisahayak.feature.incidents.IncidentListScreen
import com.varisahayak.feature.incidents.ReportIncidentScreen
import com.varisahayak.feature.lostfound.LostFoundScreen
import com.varisahayak.feature.lostfound.MatchReviewScreen
import com.varisahayak.feature.map.IncidentMapScreen
import com.varisahayak.feature.profile.ProfileScreen
import com.varisahayak.feature.profile.ProfileViewModel
import com.varisahayak.feature.qr.QrScannerScreen

@Composable
fun VariSahayakApp(
    currentLocale: AppLocale,
    onLocaleChange: (AppLocale) -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val authState by viewModel.authState.collectAsState(initial = AuthState.Unknown)
    val profile by viewModel.profile.collectAsState(initial = null)
    val isOnline by viewModel.isOnline.collectAsState(initial = true)
    val walkieState by viewModel.walkieState.collectAsState(initial = WalkieUiState())
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    val currentRoute = currentBackStackEntry?.destination?.route

    // Android 13+ will not deliver a notification until POST_NOTIFICATIONS is granted, and
    // FCM declares the permission without requesting it. Asked once the user is signed in
    // rather than at first launch, so the prompt arrives when there is something to be
    // notified about.
    NotificationPermissionRequest(enabled = profile != null)

    // The device's own position. Nothing collected LocationProvider.locationUpdates()
    // before this, so no volunteer or responder position ever reached the server — which
    // also left the proximity term in match_responder scoring zero for everybody, because
    // responders.last_location_at was never written.
    LocationTrackingEffect(
        enabled = profile != null,
        onStart = viewModel::startLocationTracking,
        onStop = viewModel::stopLocationTracking,
    )

    // A tapped notification names a server incident id; every route here is keyed by the
    // device-generated client id. Resolve, then navigate, then clear — so a configuration
    // change does not replay the tap and yank the user back.
    val pendingNotification by viewModel.pendingNotification.collectAsState()
    LaunchedEffect(pendingNotification, profile) {
        val target = pendingNotification ?: return@LaunchedEffect
        if (profile == null) return@LaunchedEffect

        viewModel.resolveNotificationTarget(target.incidentServerId) { clientId ->
            if (clientId != null) {
                navController.navigate(Destination.IncidentDetail(clientId)) {
                    launchSingleTop = true
                }
            }
            viewModel.consumeNotification()
        }
    }

    // Radio visibility is a user preference for the session, not app state — it survives
    // rotation but is deliberately not persisted across launches.
    var walkieVisible by rememberSaveable { mutableStateOf(false) }
    var walkieExpanded by rememberSaveable { mutableStateOf(true) }

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
        // Transparent so the theme canvas set by MainActivity shows through. A Scaffold
        // that paints its own background would put a second, subtly different surface
        // under every screen.
        containerColor = Color.Transparent,
        topBar = {
            if (!isAuthOrSplash) {
                FloatingTopBar(
                    title = stringResource(currentRoute.titleRes()),
                    role = profile?.role,
                    isOnline = isOnline,
                    locale = currentLocale,
                    onLocaleChange = onLocaleChange,
                    walkieEnabled = walkieVisible,
                    onToggleWalkie = { walkieVisible = !walkieVisible },
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(
                            horizontal = Dimens.FloatingInset,
                            vertical = Dimens.SpaceSm,
                        ),
                )
            }
        },
        bottomBar = {
            if (!isAuthOrSplash && profile != null) {
                // The radio docks above the navigation bar rather than floating over the
                // content.
                //
                // As an overlay it covered whatever happened to be in the bottom-left
                // corner — on the volunteer dashboard that is the SOS button, which is the
                // one control in this app that must never be obscured. Living in the
                // bottomBar slot means Scaffold folds its height into the content inset, so
                // every screen reflows around it and no overlap is possible at any size.
                Column {
                    if (walkieVisible) {
                        WalkieTalkieWidget(
                            state = walkieState,
                            expanded = walkieExpanded,
                            onToggleExpanded = { walkieExpanded = !walkieExpanded },
                            onStartTransmit = viewModel::startTransmit,
                            onStopTransmit = viewModel::stopTransmit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = Dimens.FloatingInset,
                                    end = Dimens.FloatingInset,
                                    bottom = Dimens.SpaceSm,
                                ),
                        )
                    }

                    VariNavigationBar(
                        destinations = TopLevelDestination.forRole(currentRole),
                        currentRoute = currentRoute,
                        onSelect = { dest ->
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        VariNavHost(
            navController = navController,
            walkieVisible = walkieVisible,
            walkieChannelName = walkieState.channel?.name,
            onToggleWalkie = { walkieVisible = !walkieVisible },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * The bottom bar.
 *
 * Kept opaque rather than frosted. Unlike the top bar it is not floating over content, and
 * a translucent navigation bar over a scrolling list produces exactly the shifting,
 * unreadable labels this design is trying to avoid.
 */
@Composable
private fun VariNavigationBar(
    destinations: List<TopLevelDestination>,
    currentRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val colors = VariTheme.colors
    NavigationBar(
        containerColor = colors.cardSurface,
        contentColor = colors.textPrimary,
        tonalElevation = NavigationBarDefaults.Elevation,
    ) {
        destinations.forEach { dest ->
            val isSelected = currentRoute?.contains(dest.route::class.simpleName ?: "") == true
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(dest) },
                icon = {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = null,
                    )
                },
                label = {
                    Text(
                        text = stringResource(dest.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onBrandSubtle,
                    selectedTextColor = colors.onBrandSubtle,
                    indicatorColor = colors.brandSubtle,
                    unselectedIconColor = colors.textMuted,
                    unselectedTextColor = colors.textMuted,
                ),
            )
        }
    }
}

/**
 * Maps the current route to its bar title.
 *
 * Route matching is by substring because the type-safe routes serialise to fully-qualified
 * class names, and matching the simple name is the cheapest thing that stays correct when
 * arguments are appended.
 */
private fun String?.titleRes(): Int = when {
    this == null -> R.string.app_name
    contains("IncidentMap") -> R.string.map_title
    contains("IncidentList") -> R.string.nav_incidents
    contains("IncidentDetail") -> R.string.incident_detail_title
    contains("ReportIncident") -> R.string.report_title
    contains("QrScanner") -> R.string.qr_scan_title
    contains("LostAndFound") -> R.string.lostfound_title
    contains("Communication") -> R.string.comms_title
    contains("Profile") -> R.string.profile_title
    contains("CommandDashboard") || contains("AdminDashboard") -> R.string.command_title
    contains("Dashboard") -> R.string.nav_dashboard
    else -> R.string.app_name
}

@Composable
private fun VariNavHost(
    navController: androidx.navigation.NavHostController,
    walkieVisible: Boolean,
    walkieChannelName: String?,
    onToggleWalkie: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Built once and shared by all five role dashboards: every one of them offers the same
    // navigation verbs, and defining them per-screen is how two roles end up with the same
    // button going to different places.
    val dashboardActions = DashboardActions(
        onReport = { navController.navigate(Destination.ReportIncident()) },
        onScan = { navController.navigate(Destination.QrScanner) },
        onMap = { navController.navigate(Destination.IncidentMap) },
        onLostFound = { navController.navigate(Destination.LostAndFound()) },
        onDetail = { clientId -> navController.navigate(Destination.IncidentDetail(clientId)) },
        onToggleWalkie = onToggleWalkie,
        // Replaced by each screen with its own confirmation flow; never fires as-is.
        onSos = {},
    )

    NavHost(
        navController = navController,
        startDestination = Destination.Splash,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(400)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(400)
            )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(400)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(400)
            )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(400)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(400)
            )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(400)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(400)
            )
        }
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
            VolunteerDashboardScreen(
                viewModel = hiltViewModel(),
                actions = dashboardActions,
                walkieChannelName = walkieChannelName,
                walkieVisible = walkieVisible,
            )
        }

        // One destination for medical, police and NGO: the screen reads the signed-in role
        // and shows that uniform's counters. Three routes would have meant three ways to
        // land on the same back stack entry.
        composable<Destination.ResponderDashboard> {
            ResponderDashboardScreen(
                viewModel = hiltViewModel(),
                actions = dashboardActions,
                walkieChannelName = walkieChannelName,
                walkieVisible = walkieVisible,
            )
        }

        composable<Destination.CommandDashboard> {
            CommandDashboardScreen(
                viewModel = hiltViewModel(),
                actions = dashboardActions,
                walkieChannelName = walkieChannelName,
                walkieVisible = walkieVisible,
            )
        }

        composable<Destination.AdminDashboard> {
            CommandDashboardScreen(
                viewModel = hiltViewModel(),
                actions = dashboardActions,
                walkieChannelName = walkieChannelName,
                walkieVisible = walkieVisible,
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
            // A scan establishes only *where*. The volunteer then chooses what to do with
            // that location, and both paths carry the token forward so the report is filed
            // against the fixed sign rather than a phone fix in a crowd.
            QrScannerScreen(
                onReportEmergency = { result ->
                    navController.navigate(
                        Destination.ReportIncident(
                            qrLocationToken = result.token,
                            qrLocationName = result.location?.locationName,
                            isSos = true,
                        ),
                    )
                },
                onReportFoundPerson = { result ->
                    navController.navigate(
                        Destination.LostAndFound(
                            qrLocationToken = result.token,
                            qrLocationName = result.location?.locationName,
                            kind = "FOUND",
                        ),
                    )
                },
            )
        }

        composable<Destination.LostAndFound> {
            LostFoundScreen(
                onOpenMatches = { navController.navigate(Destination.MatchReview) },
            )
        }

        composable<Destination.Communication> {
            CommunicationScreen()
        }

        composable<Destination.MatchReview> {
            MatchReviewScreen()
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

@Composable
private fun SplashScreen() {
    val colors = VariTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.auth_checking_session),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            modifier = Modifier.padding(top = Dimens.SpaceSm),
        )
    }
}

/**
 * Requests POST_NOTIFICATIONS once, on Android 13+.
 *
 * A denial is not handled with a blocking dialog on purpose. Push is best-effort: the
 * `notifications` table is the authoritative record and the incident list always shows the
 * work, so a volunteer who says no still sees everything — they just have to look.
 */
/**
 * Runs position publishing while the app is in front of the user and somebody is signed in.
 *
 * Tied to the resumed state rather than to composition. [LocationTracker] is a plain
 * singleton with no foreground service behind it, so leaving it running once the app is
 * backgrounded would ask the system for updates the app is no longer entitled to receive
 * — and would drain a volunteer's battery for a whole day on the route.
 *
 * The permission is re-read on every resume, which is what makes returning from the system
 * settings screen start tracking without needing anything else to notice.
 */
@Composable
private fun LocationTrackingEffect(
    enabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current

    LifecycleResumeEffect(enabled) {
        val granted = AppPermissions.LOCATION.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
        if (enabled && granted) onStart()

        onPauseOrDispose { onStop() }
    }
}

@Composable
private fun NotificationPermissionRequest(enabled: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    var requested by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Either way the app works. Nothing to do. */ },
    )

    LaunchedEffect(enabled, requested) {
        if (!enabled || requested) return@LaunchedEffect

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        requested = true
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
