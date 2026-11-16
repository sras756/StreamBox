package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.remote.MovieBoxApiClient
import com.example.data.repository.StreamRoomRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.navigation.DiscoverRoute
import com.example.ui.navigation.LiveRoomsRoute
import com.example.ui.navigation.SearchRoute
import com.example.ui.navigation.WatchRoomRoute
import com.example.ui.navigation.WebSitesRoute
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.LiveRoomsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.WatchRoomScreen
import com.example.ui.screens.WebStreamScreen
import com.example.ui.theme.BroadcastCyan
import com.example.ui.theme.ElectricCrimson
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StreamBoxTheme
import com.example.ui.theme.SurfaceElevated
import com.example.ui.viewmodel.CatalogViewModel
import com.example.ui.viewmodel.WatchRoomViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      StreamBoxTheme {
        StreamBoxRoot()
      }
    }
  }
}

@Composable
fun StreamBoxRoot(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by
    auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      StreamBoxMainShell(user = user)
    }
  }
}

@Composable
private fun StreamBoxMainShell(user: FirebaseUser) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val apiClient = remember { MovieBoxApiClient() }
  val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Viewer"
  val photoUrl = user.photoUrl?.toString()

  val catalogViewModel: CatalogViewModel =
    viewModel(
      key = user.uid,
      factory =
        viewModelFactory {
          initializer {
            val app =
              checkNotNull(this[APPLICATION_KEY]) {
                "APPLICATION_KEY missing from CreationExtras"
              }
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            CatalogViewModel(
              apiClient = apiClient,
              roomRepository = StreamRoomRepository(db),
              currentUserId = user.uid,
              displayName = displayName,
              photoUrl = photoUrl,
            )
          }
        },
    )

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val isInsideWatchRoom = currentDestination?.hasRoute<WatchRoomRoute>() == true

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavigationRail = maxWidth >= 600.dp

    Scaffold(
      containerColor = ObsidianBg,
      contentWindowInsets =
        if (isInsideWatchRoom) WindowInsets(0, 0, 0, 0)
        else WindowInsets.statusBars,
      bottomBar = {
        if (!useNavigationRail && !isInsideWatchRoom) {
          NavigationBar(
            containerColor = SurfaceElevated,
            windowInsets = WindowInsets.navigationBars,
          ) {
            NavigationBarItem(
              selected = currentDestination?.hasRoute<DiscoverRoute>() == true,
              onClick = {
                navController.navigate(DiscoverRoute) {
                  popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = {
                Icon(
                  imageVector =
                    if (currentDestination?.hasRoute<DiscoverRoute>() == true)
                      Icons.Filled.Explore
                    else Icons.Outlined.Explore,
                  contentDescription = stringResource(R.string.nav_discover),
                )
              },
              label = { Text(stringResource(R.string.nav_discover)) },
              colors =
                NavigationBarItemDefaults.colors(
                  selectedIconColor = Color.White,
                  selectedTextColor = Color.White,
                  indicatorColor = ElectricCrimson,
                  unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
              modifier = Modifier.testTag("nav_discover"),
            )

            NavigationBarItem(
              selected = currentDestination?.hasRoute<LiveRoomsRoute>() == true,
              onClick = {
                navController.navigate(LiveRoomsRoute) {
                  popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = {
                Icon(
                  imageVector =
                    if (currentDestination?.hasRoute<LiveRoomsRoute>() == true)
                      Icons.Filled.LiveTv
                    else Icons.Outlined.LiveTv,
                  contentDescription = stringResource(R.string.nav_rooms),
                )
              },
              label = { Text(stringResource(R.string.nav_rooms)) },
              colors =
                NavigationBarItemDefaults.colors(
                  selectedIconColor = Color.White,
                  selectedTextColor = Color.White,
                  indicatorColor = ElectricCrimson,
                  unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
              modifier = Modifier.testTag("nav_rooms"),
            )

            NavigationBarItem(
              selected = currentDestination?.hasRoute<SearchRoute>() == true,
              onClick = {
                navController.navigate(SearchRoute) {
                  popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = {
                Icon(
                  imageVector =
                    if (currentDestination?.hasRoute<SearchRoute>() == true)
                      Icons.Filled.Search
                    else Icons.Outlined.Search,
                  contentDescription = stringResource(R.string.nav_search),
                )
              },
              label = { Text(stringResource(R.string.nav_search)) },
              colors =
                NavigationBarItemDefaults.colors(
                  selectedIconColor = Color.White,
                  selectedTextColor = Color.White,
                  indicatorColor = BroadcastCyan,
                  unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
              modifier = Modifier.testTag("nav_search"),
            )

            NavigationBarItem(
              selected = currentDestination?.hasRoute<WebSitesRoute>() == true,
              onClick = {
                navController.navigate(WebSitesRoute) {
                  popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = {
                Icon(
                  imageVector =
                    if (currentDestination?.hasRoute<WebSitesRoute>() == true)
                      Icons.Filled.Language
                    else Icons.Outlined.Language,
                  contentDescription = stringResource(R.string.nav_websites),
                )
              },
              label = { Text(stringResource(R.string.nav_websites)) },
              colors =
                NavigationBarItemDefaults.colors(
                  selectedIconColor = ObsidianBg,
                  selectedTextColor = Color.White,
                  indicatorColor = BroadcastCyan,
                  unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
              modifier = Modifier.testTag("nav_websites"),
            )
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useNavigationRail && !isInsideWatchRoom) {
          NavigationRail(containerColor = SurfaceElevated) {
            NavigationRailItem(
              selected = currentDestination?.hasRoute<DiscoverRoute>() == true,
              onClick = {
                navController.navigate(DiscoverRoute) {
                  popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = {
                Icon(
                  imageVector = Icons.Filled.Explore,
                  contentDescription = stringResource(R.string.nav_discover),
                )
              },
              label = { Text(stringResource(R.string.nav_discover)) },
              colors =
                NavigationRailItemDefaults.colors(
                  selectedIconColor = Color.White,
                  indicatorColor = ElectricCrimson,
                ),
            )
            NavigationRailItem(
              selected = currentDestination?.hasRoute<LiveRoomsRoute>() == true,
              onClick = {
                navController.navigate(LiveRoomsRoute) {
                  popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = {
                Icon(
                  imageVector = Icons.Filled.LiveTv,
                  contentDescription = stringResource(R.string.nav_rooms),
                )
              },
              label = { Text(stringResource(R.string.nav_rooms)) },
              colors =
                NavigationRailItemDefaults.colors(
                  selectedIconColor = Color.White,
                  indicatorColor = ElectricCrimson,
                ),
            )
            NavigationRailItem(
              selected = currentDestination?.hasRoute<SearchRoute>() == true,
              onClick = {
                navController.navigate(SearchRoute) {
                  popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = {
                Icon(
                  imageVector = Icons.Filled.Search,
                  contentDescription = stringResource(R.string.nav_search),
                )
              },
              label = { Text(stringResource(R.string.nav_search)) },
              colors =
                NavigationRailItemDefaults.colors(
                  selectedIconColor = ObsidianBg,
                  indicatorColor = BroadcastCyan,
                ),
            )
            NavigationRailItem(
              selected = currentDestination?.hasRoute<WebSitesRoute>() == true,
              onClick = {
                navController.navigate(WebSitesRoute) {
                  popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = {
                Icon(
                  imageVector = Icons.Filled.Language,
                  contentDescription = stringResource(R.string.nav_websites),
                )
              },
              label = { Text(stringResource(R.string.nav_websites)) },
              colors =
                NavigationRailItemDefaults.colors(
                  selectedIconColor = ObsidianBg,
                  indicatorColor = BroadcastCyan,
                ),
            )
          }
        }

        NavHost(
          navController = navController,
          startDestination = DiscoverRoute,
          modifier = Modifier.weight(1f).fillMaxSize(),
        ) {
          composable<DiscoverRoute> {
            DiscoverScreen(
              viewModel = catalogViewModel,
              userName = displayName,
              userPhotoUrl = photoUrl,
              onOpenStream = { item ->
                navController.navigate(
                  WatchRoomRoute(
                    subjectId = item.subjectId,
                    title = item.title,
                    mediaType = item.mediaType,
                    season = if (item.mediaType == "Series") 1 else 0,
                    episode = if (item.mediaType == "Series") 1 else 0,
                    coverUrl = item.coverUrl,
                    detailPath = item.detailPath,
                  )
                )
              },
              onSignOutClick = {
                signOut(credentialManager = credentialManager, scope = coroutineScope)
              },
            )
          }

          composable<LiveRoomsRoute> {
            LiveRoomsScreen(
              viewModel = catalogViewModel,
              onJoinRoom = { room ->
                navController.navigate(
                  WatchRoomRoute(
                    subjectId = room.subjectId,
                    title = room.title,
                    mediaType = room.mediaType,
                    season = room.season.toInt(),
                    episode = room.episode.toInt(),
                    coverUrl = room.coverUrl,
                    detailPath = room.detailPath,
                  )
                )
              },
              onResumeWatchlist = { item ->
                navController.navigate(
                  WatchRoomRoute(
                    subjectId = item.subjectId,
                    title = item.title,
                    mediaType = item.mediaType,
                    season = item.lastSeason.toInt(),
                    episode = item.lastEpisode.toInt(),
                    coverUrl = item.coverUrl,
                  )
                )
              },
            )
          }

          composable<SearchRoute> {
            SearchScreen(
              viewModel = catalogViewModel,
              onOpenStream = { item ->
                navController.navigate(
                  WatchRoomRoute(
                    subjectId = item.subjectId,
                    title = item.title,
                    mediaType = item.mediaType,
                    season = if (item.mediaType == "Series") 1 else 0,
                    episode = if (item.mediaType == "Series") 1 else 0,
                    coverUrl = item.coverUrl,
                    detailPath = item.detailPath,
                  )
                )
              },
              onOpenById = { subjectId, title, detailPath ->
                navController.navigate(
                  WatchRoomRoute(
                    subjectId = subjectId,
                    title = title,
                    mediaType = "Movie",
                    detailPath = detailPath,
                  )
                )
              },
            )
          }

          composable<WebSitesRoute> {
            WebStreamScreen(
              onPlayDirectStream = { streamUrl, refererUrl, title ->
                val webRoomId = "web_${streamUrl.hashCode().toUInt()}"
                navController.navigate(
                  WatchRoomRoute(
                    subjectId = webRoomId,
                    title = title,
                    mediaType = "Web Stream",
                    directStreamUrl = streamUrl,
                    directStreamReferer = refererUrl,
                  )
                )
              },
            )
          }

          composable<WatchRoomRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<WatchRoomRoute>()
            val roomViewModel: WatchRoomViewModel =
              viewModel(
                key = "${user.uid}_${route.subjectId}_${route.season}_${route.episode}_${route.directStreamUrl.orEmpty()}",
                factory =
                  viewModelFactory {
                    initializer {
                      val app =
                        checkNotNull(this[APPLICATION_KEY]) {
                          "APPLICATION_KEY missing from CreationExtras"
                        }
                      val databaseId = app.getString(R.string.firestore_database_id)
                      val db = FirebaseFirestore.getInstance(databaseId)
                      WatchRoomViewModel(
                        apiClient = apiClient,
                        roomRepository = StreamRoomRepository(db),
                        subjectId = route.subjectId,
                        initialTitle = route.title,
                        mediaType = route.mediaType,
                        initialSeason = route.season,
                        initialEpisode = route.episode,
                        initialCoverUrl = route.coverUrl,
                        initialDetailPath = route.detailPath,
                        directStreamUrl = route.directStreamUrl,
                        directStreamReferer = route.directStreamReferer,
                        currentUserId = user.uid,
                        displayName = displayName,
                        photoUrl = photoUrl,
                      )
                    }
                  },
              )

            WatchRoomScreen(
              viewModel = roomViewModel,
              httpClient = apiClient.httpClient,
              onBack = { navController.popBackStack() },
            )
          }
        }
      }
    }
  }
}
