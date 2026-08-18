package com.example.swtichandsavepda.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.swtichandsavepda.data.model.AuthState
import com.example.swtichandsavepda.presentation.ProductArgs
import com.example.swtichandsavepda.presentation.SessionViewModel
import com.example.swtichandsavepda.presentation.screens.adjuststock.AdjustStockScreen
import com.example.swtichandsavepda.presentation.screens.adjuststock.AdjustStockViewModel
import com.example.swtichandsavepda.presentation.screens.barcode.BarcodeScreen
import com.example.swtichandsavepda.presentation.screens.barcode.BarcodeViewModel
import com.example.swtichandsavepda.presentation.screens.barcode.ScannedTarget
import com.example.swtichandsavepda.presentation.screens.credentials.CredentialsScreen
import com.example.swtichandsavepda.presentation.screens.credentials.CredentialsViewModel
import com.example.swtichandsavepda.presentation.screens.menu.MenuScreen
import com.example.swtichandsavepda.presentation.screens.menu.MenuViewModel
import com.example.swtichandsavepda.presentation.screens.purchaseorder.PurchaseOrderScreen
import com.example.swtichandsavepda.presentation.screens.purchaseorder.PurchaseOrderViewModel
import com.example.swtichandsavepda.presentation.screens.purchasereturn.PurchaseReturnScreen
import com.example.swtichandsavepda.presentation.screens.purchasereturn.PurchaseReturnViewModel
import com.example.swtichandsavepda.presentation.screens.splash.SplashScreen
import com.example.swtichandsavepda.presentation.screens.uploadstock.UploadStockScreen
import com.example.swtichandsavepda.presentation.screens.uploadstock.UploadStockViewModel

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Menu : Screen("menu")
    data object Barcode : Screen("barcode")

    /**
     * The document screens optionally take a scanned product (id + name + cost,
     * plus the unit a Multi-UOM barcode resolved to) to pre-select. Navigating to
     * the bare [route] leaves those null and opens the empty form.
     */
    data object AdjustStock : Screen("adjust-stock") {
        val routeWithArgs = withProductArgs(route)
        fun forScan(target: ScannedTarget) = buildProductRoute(route, target)
    }

    data object UploadStock : Screen("upload-stock") {
        val routeWithArgs = withProductArgs(route)
        fun forScan(target: ScannedTarget) = buildProductRoute(route, target)
    }

    data object PurchaseOrder : Screen("purchase-order") {
        val routeWithArgs = withProductArgs(route)
        fun forScan(target: ScannedTarget) = buildProductRoute(route, target)
    }

    data object PurchaseReturn : Screen("purchase-return") {
        val routeWithArgs = withProductArgs(route)
        fun forScan(target: ScannedTarget) = buildProductRoute(route, target)
    }
}

private fun withProductArgs(base: String) =
    "$base?${ProductArgs.ID}={${ProductArgs.ID}}" +
        "&${ProductArgs.NAME}={${ProductArgs.NAME}}" +
        "&${ProductArgs.COST}={${ProductArgs.COST}}" +
        "&${ProductArgs.UNIT_ID}={${ProductArgs.UNIT_ID}}"

private fun buildProductRoute(base: String, target: ScannedTarget) =
    "$base?${ProductArgs.ID}=${target.product.id}" +
        "&${ProductArgs.NAME}=${Uri.encode(target.product.name)}" +
        "&${ProductArgs.COST}=${target.product.cost?.toString().orEmpty()}" +
        "&${ProductArgs.UNIT_ID}=${target.unit?.productUnitId?.toString().orEmpty()}"

private fun optionalStringArg(name: String): NamedNavArgument = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

private fun productArgs(): List<NamedNavArgument> = listOf(
    optionalStringArg(ProductArgs.ID),
    optionalStringArg(ProductArgs.NAME),
    optionalStringArg(ProductArgs.COST),
    optionalStringArg(ProductArgs.UNIT_ID),
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // Hoisted at the host so one instance drives startup routing and mid-session
    // forced logout (a 401 clears the session -> authState flips to
    // Unauthenticated -> we bounce back to login from any protected screen).
    val sessionViewModel: SessionViewModel = hiltViewModel()
    val authState by sessionViewModel.authState.collectAsStateWithLifecycle()

    ForcedLogoutWatcher(navController = navController, authState = authState)

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
    ) {
        composable(Screen.Splash.route) {
            LaunchedEffect(authState) {
                when (authState) {
                    is AuthState.Authenticated -> navController.navigate(Screen.Menu.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }

                    AuthState.Unauthenticated -> navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }

                    AuthState.Unknown -> Unit // still validating the stored token
                }
            }
            SplashScreen()
        }

        composable(Screen.Login.route) {
            val viewModel: CredentialsViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            CredentialsScreen(
                uiState = uiState,
                onEmailChange = viewModel::updateEmail,
                onPasswordChange = viewModel::updatePassword,
                onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
                onSignInClick = {
                    viewModel.signIn {
                        navController.navigate(Screen.Menu.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(Screen.Menu.route) {
            val viewModel: MenuViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            MenuScreen(
                uiState = uiState,
                onAcknowledgeUnresolved = viewModel::acknowledge,
                onAdjustStock = { navController.navigate(Screen.AdjustStock.route) },
                onUploadNewStock = { navController.navigate(Screen.UploadStock.route) },
                onAddLinesToPo = { navController.navigate(Screen.PurchaseOrder.route) },
                onPurchaseReturn = { navController.navigate(Screen.PurchaseReturn.route) },
                onScan = { navController.navigate(Screen.Barcode.route) },
                onLogout = {
                    viewModel.logout {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(Screen.AdjustStock.routeWithArgs, arguments = productArgs()) {
            val viewModel: AdjustStockViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            AdjustStockScreen(
                uiState = uiState,
                onSelectMode = viewModel::selectMode,
                onSearchProducts = viewModel::searchProducts,
                onSelectProduct = viewModel::selectProduct,
                onSelectUnit = viewModel::selectUnit,
                onRetryUnits = viewModel::retryUnits,
                onQuantityChange = viewModel::setQuantity,
                onSearchLocations = viewModel::searchLocations,
                onSelectSourceLocation = viewModel::selectSourceLocation,
                onSelectDestinationLocation = viewModel::selectDestinationLocation,
                onDestinationShopChange = viewModel::setDestinationShop,
                onUnitCostChange = viewModel::setUnitCost,
                onReasonChange = viewModel::setReason,
                onSubmit = viewModel::submit,
                onScanProduct = { navController.navigate(Screen.Barcode.route) },
                onCancelRecent = viewModel::cancel,
                onRefresh = viewModel::loadRecent,
                onDismissMessages = viewModel::dismissMessages,
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(Screen.UploadStock.routeWithArgs, arguments = productArgs()) {
            val viewModel: UploadStockViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            UploadStockScreen(
                uiState = uiState,
                onSearchProducts = viewModel::searchProducts,
                onSelectProduct = viewModel::selectProduct,
                onSelectUnit = viewModel::selectUnit,
                onRetryUnits = viewModel::retryUnits,
                onQuantityChange = viewModel::setQuantity,
                onUnitCostChange = viewModel::setUnitCost,
                onReasonChange = viewModel::setReason,
                onSubmit = viewModel::submit,
                onScanProduct = { navController.navigate(Screen.Barcode.route) },
                onDismissMessages = viewModel::dismissMessages,
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(Screen.PurchaseOrder.routeWithArgs, arguments = productArgs()) {
            val viewModel: PurchaseOrderViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            PurchaseOrderScreen(
                uiState = uiState,
                onSearchSuppliers = viewModel::searchSuppliers,
                onSelectSupplier = viewModel::selectSupplier,
                onDeliveryDateChange = viewModel::setDeliveryDate,
                onSearchProducts = viewModel::searchProducts,
                onSelectLineProduct = viewModel::selectLineProduct,
                onSelectLineUnit = viewModel::selectLineUnit,
                onRetryLineUnits = viewModel::retryLineUnits,
                onLineQuantityChange = viewModel::setLineQuantity,
                onLineUnitCostChange = viewModel::setLineUnitCost,
                onAddLine = viewModel::addLine,
                onRemoveLine = viewModel::removeLine,
                onSubmit = viewModel::submit,
                onScanProduct = { navController.navigate(Screen.Barcode.route) },
                onStartReceive = viewModel::startReceive,
                onReceiveQuantityChange = viewModel::setReceiveQuantity,
                onReceiveDeliveryNoteChange = viewModel::setReceiveDeliveryNote,
                onFillReceiveRemaining = viewModel::fillReceiveRemaining,
                onConfirmReceive = viewModel::confirmReceive,
                onCancelReceive = viewModel::cancelReceive,
                onCancelOrder = viewModel::cancelOrder,
                onRefresh = viewModel::loadOrders,
                onDismissMessages = viewModel::dismissMessages,
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(Screen.PurchaseReturn.routeWithArgs, arguments = productArgs()) {
            val viewModel: PurchaseReturnViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            PurchaseReturnScreen(
                uiState = uiState,
                onSearchPurchaseOrders = viewModel::searchPurchaseOrders,
                onSelectPurchaseOrder = viewModel::selectPurchaseOrder,
                onClearPurchaseOrder = viewModel::clearPurchaseOrder,
                onSelectReturnableLine = viewModel::selectReturnableLine,
                onSearchSuppliers = viewModel::searchSuppliers,
                onSelectSupplier = viewModel::selectSupplier,
                onReferenceChange = viewModel::setReference,
                onReturnReasonChange = viewModel::setReturnReason,
                onSearchProducts = viewModel::searchProducts,
                onSelectLineProduct = viewModel::selectLineProduct,
                onSelectLineUnit = viewModel::selectLineUnit,
                onRetryLineUnits = viewModel::retryLineUnits,
                onLineQuantityChange = viewModel::setLineQuantity,
                onLineCostPriceChange = viewModel::setLineCostPrice,
                onLineReasonChange = viewModel::setLineReason,
                onAddLine = viewModel::addLine,
                onRemoveLine = viewModel::removeLine,
                onSubmit = viewModel::submit,
                onScanProduct = { navController.navigate(Screen.Barcode.route) },
                onCancelReturn = viewModel::cancelReturn,
                onRefresh = viewModel::loadReturns,
                onDismissMessages = viewModel::dismissMessages,
                onBackClick = { navController.popBackStack() },
            )
        }

        composable(Screen.Barcode.route) {
            val viewModel: BarcodeViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // Each action clears the result sheet, then routes to the feature
            // with the scanned product pre-selected. The scanner stays on the
            // back stack, so finishing an action returns here for the next scan.
            fun go(route: String) {
                viewModel.dismissOutcome()
                navController.navigate(route)
            }

            BarcodeScreen(
                uiState = uiState,
                onScanDetected = viewModel::onBarcodeDetected,
                onLookup = viewModel::lookup,
                onDismissOutcome = viewModel::dismissOutcome,
                onSelectMatch = viewModel::selectMatch,
                onAdjustStock = { target -> go(Screen.AdjustStock.forScan(target)) },
                onUploadStock = { target -> go(Screen.UploadStock.forScan(target)) },
                onAddToPo = { target -> go(Screen.PurchaseOrder.forScan(target)) },
                onReturn = { target -> go(Screen.PurchaseReturn.forScan(target)) },
                onBackClick = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Bounces the operator to login whenever the session is cleared (logout, or a
 * 401 on an authenticated call) while they are on any screen other than the
 * splash / login screens.
 */
@Composable
private fun ForcedLogoutWatcher(
    navController: NavHostController,
    authState: AuthState,
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val route = currentEntry?.destination?.route
    LaunchedEffect(authState, route) {
        val onAuthScreen = route == null || route == Screen.Splash.route || route == Screen.Login.route
        if (authState is AuthState.Unauthenticated && !onAuthScreen) {
            navController.navigate(Screen.Login.route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }
}
