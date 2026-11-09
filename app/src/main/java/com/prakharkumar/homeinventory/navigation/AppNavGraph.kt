package com.prakharkumar.homeinventory.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.prakharkumar.homeinventory.AppContainer
import com.prakharkumar.homeinventory.ui.camera.CaptureScreen
import com.prakharkumar.homeinventory.ui.home.HomeScreen
import com.prakharkumar.homeinventory.ui.itemdetails.ItemDetailScreen
import com.prakharkumar.homeinventory.ui.locations.LocationDetailScreen
import com.prakharkumar.homeinventory.ui.locations.LocationTreeScreen
import com.prakharkumar.homeinventory.ui.recognition.ReviewScreen
import com.prakharkumar.homeinventory.ui.search.SearchScreen
import com.prakharkumar.homeinventory.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val LOCATIONS = "locations"
    const val LOCATION_DETAIL = "location/{locationId}"
    const val CAPTURE = "capture?locationId={locationId}"
    const val REVIEW = "review?photoPath={photoPath}&locationId={locationId}"
    const val SEARCH = "search"
    const val ITEM_DETAIL = "item/{itemId}"
    const val SETTINGS = "settings"

    fun locationDetail(id: String) = "location/$id"
    fun capture(locationId: String?) = "capture?locationId=${locationId.orEmpty()}"
    fun review(photoPath: String, locationId: String?) =
        "review?photoPath=${Uri.encode(photoPath)}&locationId=${locationId.orEmpty()}"
    fun itemDetail(id: String) = "item/$id"
}

@Composable
fun AppNavGraph(
    container: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onSearch = { navController.navigate(Routes.SEARCH) },
                onBrowseLocations = { navController.navigate(Routes.LOCATIONS) },
                onScan = { navController.navigate(Routes.capture(null)) },
                onOpenItem = { navController.navigate(Routes.itemDetail(it)) },
                onOpenLocation = { navController.navigate(Routes.locationDetail(it)) },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.LOCATIONS) {
            LocationTreeScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenLocation = { navController.navigate(Routes.locationDetail(it)) }
            )
        }

        composable(
            route = Routes.LOCATION_DETAIL,
            arguments = listOf(navArgument("locationId") { type = NavType.StringType })
        ) { entry ->
            val locationId = entry.arguments?.getString("locationId").orEmpty()
            LocationDetailScreen(
                container = container,
                locationId = locationId,
                onBack = { navController.popBackStack() },
                onOpenLocation = { navController.navigate(Routes.locationDetail(it)) },
                onOpenItem = { navController.navigate(Routes.itemDetail(it)) },
                onAddItems = { navController.navigate(Routes.capture(locationId)) }
            )
        }

        composable(
            route = Routes.CAPTURE,
            arguments = listOf(navArgument("locationId") { defaultValue = "" })
        ) { entry ->
            val locationId = entry.arguments?.getString("locationId").orEmpty()
            CaptureScreen(
                onBack = { navController.popBackStack() },
                onPhotoReady = { path ->
                    navController.navigate(Routes.review(path, locationId.ifBlank { null })) {
                        popUpTo(Routes.CAPTURE) { inclusive = true }
                    }
                },
                photoStorage = container.photoStorage
            )
        }

        composable(
            route = Routes.REVIEW,
            arguments = listOf(
                navArgument("photoPath") { type = NavType.StringType },
                navArgument("locationId") { defaultValue = "" }
            )
        ) { entry ->
            val photoPath = Uri.decode(entry.arguments?.getString("photoPath").orEmpty())
            val locationId = entry.arguments?.getString("locationId").orEmpty()
            ReviewScreen(
                container = container,
                photoPath = photoPath,
                initialLocationId = locationId.ifBlank { null },
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenItem = { navController.navigate(Routes.itemDetail(it)) }
            )
        }

        composable(
            route = Routes.ITEM_DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { entry ->
            ItemDetailScreen(
                container = container,
                itemId = entry.arguments?.getString("itemId").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenLocation = { navController.navigate(Routes.locationDetail(it)) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(container = container, onBack = { navController.popBackStack() })
        }
    }
}
