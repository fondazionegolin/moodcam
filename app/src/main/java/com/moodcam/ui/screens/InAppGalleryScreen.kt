package com.moodcam.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.moodcam.camera.CameraViewModel
import com.moodcam.gallery.UserSettings
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppGalleryScreen(
    viewModel: CameraViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State for photos (URIs)
    // In a real app, this should come from a MediaStore query in ViewModel
    // For now, we simulate by listing files in app-specific directory or just using recent captures
    // But since we save to MediaStore, we should ideally query it.
    // Simplifying: we'll assume the ViewModel holds specific captured URIs or we query a known folder.
    // For this implementation, I will rely on a list provided by ViewModel or just querying the folder for simplicity.
    
    // Let's implement a simple file walker for the "MoodCam" folder locally for immediate feedback
    var photos by remember { mutableStateOf<List<File>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        // Simple file listing for legacy storage or scoped storage if accessible
        // Note: On Android 10+, robust gallery requires MediaStore querying.
        // Falling back to ViewModel's recent capture list if available, or just scanning the legacy path for now.
        // A robust solution needs a ContentProvider query.
        viewModel.loadGalleryPhotos(context)
        // We'll observe viewModel.galleryPhotos
    }
    
    val galleryPhotos by viewModel.galleryPhotos.collectAsState()
    
    var selectedPhotoIndex by remember { mutableStateOf(-1) }
    var showUsernameDialog by remember { mutableStateOf(false) }
    
    // Main Gallery Grid
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("MoodCam Gallery", style = MaterialTheme.typography.titleMedium)
                        val username = remember { UserSettings.getUsername(context) }
                        Text("as $username", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showUsernameDialog = true }) {
                        Icon(Icons.Default.Edit, "Edit Username")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Black
    ) { padding ->
        
        if (galleryPhotos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No photos yet. Go shoot some film!", color = Color.Gray)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Black band spacer for notch avoidance
                Spacer(modifier = Modifier.height(32.dp).fillMaxWidth().background(Color.Black))
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(galleryPhotos) { uri ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(uri)
                                .crossfade(true)
                                .allowHardware(false) // Ensures EXIF rotation is applied
                                .build(),
                            contentDescription = "Photo",
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { selectedPhotoIndex = galleryPhotos.indexOf(uri) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
    
    // Full Screen Viewer
    AnimatedVisibility(
        visible = selectedPhotoIndex != -1,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (selectedPhotoIndex != -1 && galleryPhotos.isNotEmpty()) {
            PhotoViewer(
                photos = galleryPhotos,
                initialIndex = selectedPhotoIndex,
                onClose = { selectedPhotoIndex = -1 }
            )
        }
    }
    
    // Username Dialog
    if (showUsernameDialog) {
        var newUsername by remember { mutableStateOf(UserSettings.getUsername(context)) }
        
        AlertDialog(
            onDismissRequest = { showUsernameDialog = false },
            title = { Text("Set Username") },
            text = {
                OutlinedTextField(
                    value = newUsername,
                    onValueChange = { newUsername = it },
                    label = { Text("Username") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    UserSettings.setUsername(context, newUsername)
                    showUsernameDialog = false
                    // Ideally force refresh UI
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsernameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewer(
    photos: List<Uri>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    
    BackHandler { onClose() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            // Pinch-to-zoom state
            var scale by remember { mutableFloatStateOf(1f) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }
            
            val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 5f)
                if (scale > 1f) {
                    offsetX += panChange.x
                    offsetY += panChange.y
                } else {
                    offsetX = 0f
                    offsetY = 0f
                }
            }
            
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photos[page])
                    .crossfade(true)
                    .allowHardware(false)
                    .build(),
                contentDescription = "Full photo",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .transformable(state = transformableState),
                contentScale = ContentScale.Fit
            )
        }
        
        // Top Bar - Close button only
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .statusBarsPadding()
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
        ) {
            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
        }
    }
}
