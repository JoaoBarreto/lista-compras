package com.example.listacompras.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.listacompras.domain.model.AuthState
import com.example.listacompras.ui.login.LoginRoute
import com.example.listacompras.ui.shopping.ShoppingRoute

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val authState by sessionViewModel.authState.collectAsStateWithLifecycle()
    // Wait for the stored session to load so the login screen does not flash before redirecting.
    if (authState is AuthState.Loading) return

    val startDestination = remember { if (authState is AuthState.LoggedIn) Routes.SHOPPING else Routes.LOGIN }

    // Login, logout and (later) a failed token refresh all just change the auth state.
    LaunchedEffect(authState) {
        val target = if (authState is AuthState.LoggedIn) Routes.SHOPPING else Routes.LOGIN
        if (navController.currentDestination?.route != target) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.LOGIN) { LoginRoute() }
        composable(Routes.SHOPPING) { ShoppingRoute(onLogout = sessionViewModel::logout) }
    }
}
