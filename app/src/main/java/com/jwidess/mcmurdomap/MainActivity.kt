package com.jwidess.mcmurdomap

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

class MainActivity : ComponentActivity() {

    private var webViewRef: WebView? = null
    private var isMapReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BuildingRepository.init(applicationContext)

        // Hold splash screen until map is completely loaded & rendered
        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    return if (isMapReady) {
                        content.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        false
                    }
                }
            }
        )

        // Force status bar icons dark/black (readable on light map)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true
        insetsController.isAppearanceLightNavigationBars = true

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0085CA),            // Primary Blue
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFE7F6F8),   // Ice Tint Container
                    onPrimaryContainer = Color(0xFF162E51), // Navy
                    secondary = Color(0xFFE66F0E),          // Signature Orange
                    onSecondary = Color.White,
                    secondaryContainer = Color(0xFFFDF0E6),
                    onSecondaryContainer = Color(0xFF7A3600),
                    surface = Color.White,
                    onSurface = Color(0xFF171716),          // Charcoal
                    surfaceVariant = Color(0xFFF0F4F8),
                    onSurfaceVariant = Color(0xFF454545),
                    error = Color(0xFFD31245),              // Error Red
                    onError = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFDAECF6) // Ocean Background
                ) {
                    MapAppScreen(
                        onWebViewReady = { webViewRef = it },
                        getWebView = { webViewRef },
                        isMapReady = isMapReady,
                        onMapReady = { isMapReady = true }
                    )
                }
            }
        }
    }
}

data class CategoryFilter(
    val key: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapAppScreen(
    onWebViewReady: (WebView) -> Unit,
    getWebView: () -> WebView?,
    isMapReady: Boolean,
    onMapReady: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Fallback safety timeout in case WebView callback is delayed
    LaunchedEffect(Unit) {
        delay(2200)
        onMapReady()
    }

    var fullScreenPhotoBuilding by remember { mutableStateOf<Building?>(null) }

    // Keep status bar and navigation bar icons dark across screen states (light when in full-screen photo)
    val view = LocalView.current
    if (!view.isInEditMode) {
        val isFullScreenPhoto = fullScreenPhotoBuilding != null
        SideEffect {
            val activityWindow = (view.context as? Activity)?.window
            if (activityWindow != null) {
                val insets = WindowCompat.getInsetsController(activityWindow, view)
                insets.isAppearanceLightStatusBars = !isFullScreenPhoto
                insets.isAppearanceLightNavigationBars = !isFullScreenPhoto
            }
        }
    }

    var selectedBuilding by remember { mutableStateOf<Building?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var activeCategory by remember { mutableStateOf<String?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    val categories = remember {
        listOf(
            CategoryFilter("aed", "AEDs", Icons.Default.Favorite, Color(0xFFD31245)),
            CategoryFilter("dining", "Food & Drink", Icons.Default.Restaurant, Color(0xFFE66F0E)),
            CategoryFilter("dorms", "Dorms", Icons.Default.Bed, Color(0xFF8168B3)),
            CategoryFilter("science", "Science & Labs", Icons.Default.Science, Color(0xFF0085CA)),
            CategoryFilter("medical", "Medical / Clinic", Icons.Default.LocalHospital, Color(0xFFD31245)),
            CategoryFilter("fitness", "Gym & Fitness", Icons.Default.FitnessCenter, Color(0xFF162E51)),
            CategoryFilter("smoking", "Smoking", Icons.Default.SmokingRooms, Color(0xFFF59E0B))
        )
    }

    val searchResults by remember(searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) emptyList()
            else BuildingRepository.search(searchQuery)
        }
    }

    fun selectAndZoom(bldg: Building) {
        selectedBuilding = bldg
        isSearchExpanded = false
        searchQuery = ""
        focusManager.clearFocus()
        getWebView()?.evaluateJavascript("window.zoomToBuilding('${bldg.id}')", null)
    }

    fun toggleCategory(key: String) {
        val next = if (activeCategory == key) null else key
        activeCategory = next
        val wv = getWebView() ?: return

        if (next == null) {
            wv.evaluateJavascript("window.clearCategoryHighlights()", null)
        } else {
            val ids = BuildingRepository.getCategoryIds(next)
            val jsonArray = JSONArray(ids).toString()
            wv.evaluateJavascript("window.highlightCategory('$next', $jsonArray)", null)
        }
    }

    // Hardware back button behavior
    BackHandler(enabled = fullScreenPhotoBuilding != null || isSearchExpanded || selectedBuilding != null || activeCategory != null || showHelpDialog) {
        when {
            fullScreenPhotoBuilding != null -> fullScreenPhotoBuilding = null
            showHelpDialog -> showHelpDialog = false
            isSearchExpanded -> {
                isSearchExpanded = false
                focusManager.clearFocus()
            }
            selectedBuilding != null -> {
                selectedBuilding = null
            }
            activeCategory != null -> toggleCategory(activeCategory!!)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Vector Map WebView
        MapWebView(
            onWebViewCreated = onWebViewReady,
            onBuildingTapped = { bldgId ->
                coroutineScope.launch(Dispatchers.Main) {
                    val bldg = BuildingRepository.getById(bldgId)
                    if (bldg != null) {
                        focusManager.clearFocus()
                        isSearchExpanded = false
                        selectedBuilding = bldg
                    }
                }
            },
            onBuildingDeselected = {
                coroutineScope.launch(Dispatchers.Main) {
                    selectedBuilding = null
                }
            },
            onHelpRequested = {
                coroutineScope.launch(Dispatchers.Main) {
                    showHelpDialog = true
                }
            },
            onMapReady = onMapReady
        )

        // 2. Top Controls Overlay
        Column(
            modifier = if (isLandscape) {
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 6.dp, bottom = 12.dp)
                    .width(360.dp)
                    .fillMaxHeight()
            } else {
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 6.dp)
            }
        ) {
            // Search Bar & Help Button Row
            Row(
                modifier = if (isLandscape) {
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(46.dp)
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Bar
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(23.dp),
                    color = Color.White.copy(alpha = 0.98f),
                    border = BorderStroke(1.5.dp, Color(0xFF162E51).copy(alpha = 0.25f)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF162E51),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                isSearchExpanded = it.isNotBlank()
                            },
                            singleLine = true,
                            cursorBrush = SolidColor(Color(0xFF0085CA)),
                            textStyle = TextStyle(
                                color = Color(0xFF171716),
                                fontSize = 13.5.sp
                            ),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search (e.g. 155, Galley, Crary)...",
                                            fontSize = 13.5.sp,
                                            color = Color(0xFF757575)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    isSearchExpanded = false
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF5D5D52),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Help Button
                Surface(
                    onClick = { showHelpDialog = true },
                    modifier = Modifier.size(if (isLandscape) 42.dp else 46.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.98f),
                    border = BorderStroke(1.5.dp, Color(0xFF162E51).copy(alpha = 0.25f)),
                    shadowElevation = 6.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "?",
                            fontSize = if (isLandscape) 19.sp else 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF162E51)
                        )
                    }
                }
            }

            // Search Results Dropdown
            AnimatedVisibility(
                visible = isSearchExpanded && searchResults.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = if (isLandscape) {
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.98f),
                    border = BorderStroke(1.5.dp, Color(0xFF162E51).copy(alpha = 0.20f)),
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (isLandscape) 180.dp else 260.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        searchResults.forEach { bldg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectAndZoom(bldg) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color(0xFF162E51),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = bldg.id,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                if (bldg.hasPhoto) {
                                    val thumbBitmap = remember(bldg.photo) {
                                        try {
                                            val photoFile = bldg.photo?.trim() ?: ""
                                            val assetPath = if (photoFile.contains("/")) photoFile else "assets/img/buildings/$photoFile"
                                            val stream = try {
                                                context.assets.open(assetPath)
                                            } catch (e: Exception) {
                                                context.assets.open("assets/img/$photoFile")
                                            }
                                            stream.use { BitmapFactory.decodeStream(it) }
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    if (thumbBitmap != null) {
                                        Image(
                                            bitmap = thumbBitmap.asImageBitmap(),
                                            contentDescription = bldg.displayName,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .border(1.dp, Color(0xFF8A8980), RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bldg.displayName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color(0xFF171716)
                                    )
                                    if (!bldg.description.isNullOrBlank()) {
                                        Text(
                                            text = bldg.description,
                                            fontSize = 11.sp,
                                            color = Color(0xFF5D5D52),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (bldg.hasAeds) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = "AED",
                                        tint = Color(0xFFD31245),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                        }
                    }
                }
            }

            // Category Filter Chips: Vertical in landscape, horizontal edge-to-edge in portrait
            if (isLandscape) {
                LazyColumn(
                    modifier = Modifier
                        .width(155.dp)
                        .padding(top = 6.dp)
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = activeCategory == cat.key
                        val chipShape = RoundedCornerShape(20.dp)
                        Surface(
                            onClick = { toggleCategory(cat.key) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(chipShape),
                            shape = chipShape,
                            color = if (isSelected) Color(0xFFE66F0E) else Color.White.copy(alpha = 0.96f),
                            border = BorderStroke(
                                1.5.dp,
                                if (isSelected) Color(0xFFE66F0E) else Color(0xFF162E51).copy(alpha = 0.28f)
                            ),
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = cat.label,
                                    tint = if (isSelected) Color.White else cat.color,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cat.label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else Color(0xFF162E51),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = activeCategory == cat.key
                        val chipShape = RoundedCornerShape(20.dp)
                        Surface(
                            onClick = { toggleCategory(cat.key) },
                            modifier = Modifier.clip(chipShape),
                            shape = chipShape,
                            color = if (isSelected) Color(0xFFE66F0E) else Color.White.copy(alpha = 0.96f),
                            border = BorderStroke(
                                1.5.dp,
                                if (isSelected) Color(0xFFE66F0E) else Color(0xFF162E51).copy(alpha = 0.28f)
                            ),
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = cat.label,
                                    tint = if (isSelected) Color.White else cat.color,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cat.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else Color(0xFF162E51)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Bottom Sheet for Selected Building (covers max ~60% of screen)
        selectedBuilding?.let { building ->
            ModalBottomSheet(
                onDismissRequest = {
                    selectedBuilding = null
                },
                containerColor = Color.White,
                scrimColor = Color(0xFF162E51).copy(alpha = 0.30f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                dragHandle = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Orange top border
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color(0xFFE66F0E))
                        )
                        BottomSheetDefaults.DragHandle(
                            color = Color(0xFFCBD5E1),
                            height = 4.dp,
                            width = 36.dp
                        )
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = screenHeight * 0.58f)
                ) {
                    BuildingDetailContent(
                        building = building,
                        onOpenPdf = { pdfName ->
                            PdfHelper.openBuildingPdf(context, pdfName)
                        },
                        onPhotoClick = { bldg ->
                            fullScreenPhotoBuilding = bldg
                        }
                    )
                }
            }
        }

        // 4. Station Help & Info Dialog (Scrollable & compact in landscape)
        if (showHelpDialog) {
            val syncInfo = remember { BuildingRepository.getSyncInfo(context) }
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                containerColor = Color.White,
                titleContentColor = Color(0xFF162E51),
                textContentColor = Color(0xFF171716),
                icon = if (!isLandscape) {
                    {
                        Image(
                            painter = painterResource(id = R.drawable.usap_logo),
                            contentDescription = "USAP Insignia",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else null,
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isLandscape) {
                            Image(
                                painter = painterResource(id = R.drawable.usap_logo),
                                contentDescription = "USAP Insignia",
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = "McMurdo Station Map",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isLandscape) 16.sp else 18.sp,
                            color = Color(0xFF162E51)
                        )
                    }
                },
                text = {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = if (isLandscape) (screenHeight * 0.55f) else 460.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = if (isLandscape) 6.dp else 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "App Version:",
                                            fontSize = 11.sp,
                                            color = Color(0xFF5D5D52)
                                        )
                                        val editionLabel = if (BuildConfig.IS_SLIM_BUILD) " (Slim)" else " (Full)"
                                        Text(
                                            text = "v${BuildConfig.VERSION_NAME}$editionLabel",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0085CA)
                                        )
                                    }

                                    when (val state = updateState) {
                                        is UpdateState.Idle -> {
                                            OutlinedButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        updateState = UpdateState.Checking
                                                        updateState = UpdateHelper.checkForUpdate(BuildConfig.VERSION_NAME)
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(30.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = Color(0xFF162E51)
                                                ),
                                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Check for Updates",
                                                    modifier = Modifier.size(13.dp),
                                                    tint = Color(0xFF162E51)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Check for Updates",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        is UpdateState.Checking -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(13.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color(0xFF0085CA)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Connecting to GitHub...",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF5D5D52)
                                                )
                                            }
                                        }
                                        is UpdateState.Available -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFEFF6FF), RoundedCornerShape(6.dp))
                                                    .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(6.dp))
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.SystemUpdate,
                                                        contentDescription = "Update available",
                                                        modifier = Modifier.size(15.dp),
                                                        tint = Color(0xFF2563EB)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Update Available: ${state.version}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF1E40AF)
                                                    )
                                                }
                                                Button(
                                                    onClick = {
                                                        try {
                                                            val url = state.releaseUrl.ifBlank { "https://github.com/jwidess/mcmurdo-map/releases/latest" }
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {}
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(28.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                    shape = RoundedCornerShape(4.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF2563EB),
                                                        contentColor = Color.White
                                                    )
                                                ) {
                                                    Text(
                                                        text = "Download on GitHub",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                Text(
                                                    text = "Select Full (~30MB) or Slim (~16MB) edition on GitHub.",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF475569),
                                                    lineHeight = 13.sp
                                                )
                                            }
                                        }
                                        is UpdateState.UpToDate -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Up to date",
                                                        modifier = Modifier.size(13.dp),
                                                        tint = Color(0xFF059669)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Up to date (${state.version})",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFF059669)
                                                    )
                                                }
                                                Text(
                                                    text = "Check again",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF0085CA),
                                                    textDecoration = TextDecoration.Underline,
                                                    modifier = Modifier.clickable {
                                                        coroutineScope.launch {
                                                            updateState = UpdateState.Checking
                                                            updateState = UpdateHelper.checkForUpdate(BuildConfig.VERSION_NAME)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                        is UpdateState.Error -> {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = state.message,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFFDC2626)
                                                )
                                                Text(
                                                    text = "Retry",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF0085CA),
                                                    fontWeight = FontWeight.SemiBold,
                                                    textDecoration = TextDecoration.Underline,
                                                    modifier = Modifier.clickable {
                                                        coroutineScope.launch {
                                                            updateState = UpdateState.Checking
                                                            updateState = UpdateHelper.checkForUpdate(BuildConfig.VERSION_NAME)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = if (isLandscape) 6.dp else 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Last Data Sync:",
                                        fontSize = 11.sp,
                                        color = Color(0xFF5D5D52)
                                    )
                                    Text(
                                        text = syncInfo.lastSyncedFormatted,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669)
                                    )
                                    val docsLabel = if (BuildConfig.IS_SLIM_BUILD) {
                                        "${syncInfo.totalPhotos} photos (PDFs omitted in Slim build)"
                                    } else {
                                        "${syncInfo.totalPhotos} photos - ${syncInfo.totalDocs} PDFs"
                                    }
                                    Text(
                                        text = docsLabel,
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF5D5D52)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = if (isLandscape) 6.dp else 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Map Data Source:",
                                        fontSize = 11.sp,
                                        color = Color(0xFF5D5D52)
                                    )
                                    Text(
                                        text = "USAP McMurdo Station Web Map",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF162E51)
                                    )
                                    Text(
                                        text = "https://www.mcmurdo.usap.gov/map/",
                                        fontSize = 11.sp,
                                        color = Color(0xFF0085CA),
                                        textDecoration = TextDecoration.Underline,
                                        modifier = Modifier.clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.mcmurdo.usap.gov/map/")).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {}
                                        }
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = if (isLandscape) 6.dp else 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Project & Source Code:",
                                        fontSize = 11.sp,
                                        color = Color(0xFF5D5D52)
                                    )
                                    Text(
                                        text = "GitHub Repository",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF162E51)
                                    )
                                    Text(
                                        text = "https://github.com/jwidess/mcmurdo-map",
                                        fontSize = 11.sp,
                                        color = Color(0xFF0085CA),
                                        textDecoration = TextDecoration.Underline,
                                        modifier = Modifier.clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jwidess/mcmurdo-map")).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {}
                                        }
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = if (isLandscape) 6.dp else 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "Disclaimer:",
                                        fontSize = 11.sp,
                                        color = Color(0xFF5D5D52)
                                    )
                                    Text(
                                        text = "This is an independent open-source project and is not affiliated with, endorsed by, or officially associated with the United States Antarctic Program (USAP) or the National Science Foundation (NSF).",
                                        fontSize = 11.sp,
                                        color = Color(0xFF334155),
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showHelpDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0085CA),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(if (isLandscape) 36.dp else 40.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        // 5. Splash Overlay (fades out when map is rendered)
        AnimatedVisibility(
            visible = !isMapReady,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(durationMillis = 280))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.splash_icon),
                    contentDescription = "USAP Insignia",
                    modifier = Modifier.size(150.dp)
                )
            }
        }

        // 5. Full Screen Photo Viewer (Dialog rendered on top of ModalBottomSheet)
        if (fullScreenPhotoBuilding != null) {
            FullScreenPhotoViewer(
                building = fullScreenPhotoBuilding!!,
                onDismiss = { fullScreenPhotoBuilding = null }
            )
        }
    }
}

@Composable
fun BuildingDetailContent(
    building: Building,
    onOpenPdf: (String) -> Unit,
    onPhotoClick: (Building) -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            // Building Photo
            if (building.hasPhoto) {
                val photoBitmap = remember(building.photo) {
                    try {
                        val photoFile = building.photo?.trim() ?: ""
                        val assetPath = if (photoFile.contains("/")) photoFile else "assets/img/buildings/$photoFile"
                        val stream = try {
                            context.assets.open(assetPath)
                        } catch (e: Exception) {
                            context.assets.open("assets/img/$photoFile")
                        }
                        stream.use { BitmapFactory.decodeStream(it) }
                    } catch (e: Exception) {
                        null
                    }
                }

                if (photoBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF8A8980), RoundedCornerShape(8.dp))
                            .clickable { onPhotoClick(building) }
                    ) {
                        Image(
                            bitmap = photoBitmap.asImageBitmap(),
                            contentDescription = building.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Fullscreen button in bottom right
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            contentColor = Color.White
                        ) {
                            Box(
                                modifier = Modifier.padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "View full image",
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // Title Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF162E51),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = building.id,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = building.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF171716),
                    modifier = Modifier.weight(1f)
                )
            }

            // Badges Row
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!building.occupancy.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.People,
                                contentDescription = "Occupancy",
                                tint = Color(0xFF0085CA),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Max: ${building.occupancy}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF162E51)
                            )
                        }
                    }
                }
                if (!building.construction.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CalendarToday,
                                contentDescription = "Construction",
                                tint = Color(0xFF0085CA),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Built: ${building.construction}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF162E51)
                            )
                        }
                    }
                }
            }

            // AED Section
            if (building.hasAeds) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFECACA))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "AED",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AED Defibrillator On-Site",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF991B1B)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        building.aeds.forEach { aedLocation ->
                            Text(
                                text = "• $aedLocation",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = Color(0xFF7F1D1D),
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Muster Section
            if (building.musterPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF0F9FF),
                    border = BorderStroke(1.dp, Color(0xFFBAE6FD))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = "Muster",
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Emergency Muster Station",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF0369A1)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        building.musterPoints.forEach { pt ->
                            Text(
                                text = "• $pt",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = Color(0xFF0C4A6E),
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Description
            if (!building.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Description",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF0085CA)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = building.description,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFF171716)
                )
            }

            // PDF Info Sheet Button
            if (building.hasInfoSheet) {
                Spacer(modifier = Modifier.height(14.dp))
                if (BuildConfig.IS_SLIM_BUILD) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clickable {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Info PDFs are omitted in the Slim build to reduce app size.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.PictureAsPdf,
                                    contentDescription = "PDF Unavailable",
                                    modifier = Modifier.size(18.dp),
                                    tint = Color(0xFF94A3B8)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Building Information Sheet (PDF)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "PDFs omitted in Slim build to reduce size (download Full build for PDFs)", 
                            fontSize = 10.5.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 14.sp
                        )
                    }
                } else {
                    Button(
                        onClick = { onOpenPdf(building.infoSheet!!) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0085CA),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = "PDF",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Building Information Sheet (PDF)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapWebView(
    onWebViewCreated: (WebView) -> Unit,
    onBuildingTapped: (String) -> Unit,
    onBuildingDeselected: () -> Unit,
    onHelpRequested: () -> Unit,
    onMapReady: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(true)
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }

                // Add bridge for bi-directional communication
                addJavascriptInterface(
                    AndroidBridge(
                        onBuildingSelectedCallback = onBuildingTapped,
                        onBuildingDeselectedCallback = onBuildingDeselected,
                        onHelpRequestedCallback = onHelpRequested,
                        onMapReadyCallback = {
                            coroutineScope.launch(Dispatchers.Main) {
                                onMapReady()
                            }
                        }
                    ),
                    "AndroidBridge"
                )

                webViewClient = LocalAssetWebViewClient(ctx) {
                    coroutineScope.launch(Dispatchers.Main) {
                        delay(100)
                        onMapReady()
                    }
                }
                loadUrl("https://mcmurdo.internal/map_view.html")

                onWebViewCreated(this)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun FullScreenPhotoViewer(
    building: Building,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val photoBitmap = remember(building.photo) {
        try {
            val photoFile = building.photo?.trim() ?: ""
            val assetPath = if (photoFile.contains("/")) photoFile else "assets/img/buildings/$photoFile"
            val stream = try {
                context.assets.open(assetPath)
            } catch (e: Exception) {
                context.assets.open("assets/img/$photoFile")
            }
            stream.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        SideEffect {
            val dialogWindow = (view.parent as? DialogWindowProvider)?.window
            dialogWindow?.let { win ->
                win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                win.setBackgroundDrawableResource(android.R.color.black)
                win.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                win.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                win.statusBarColor = android.graphics.Color.BLACK
                win.navigationBarColor = android.graphics.Color.BLACK
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    win.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                val insets = WindowCompat.getInsetsController(win, view)
                insets.isAppearanceLightStatusBars = false
                insets.isAppearanceLightNavigationBars = false
            }
        }

        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Main Zoomable Photo
            if (photoBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.2f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale <= 1.05f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = photoBitmap.asImageBitmap(),
                        contentDescription = building.displayName,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            },
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                Text(
                    text = "No photo available",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Top Header Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color(0xFF162E51),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = building.id,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = building.displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close full image",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
