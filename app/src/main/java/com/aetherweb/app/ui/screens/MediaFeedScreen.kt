package com.aetherweb.app.ui.screens

import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.aetherweb.app.AetherFeedManager
import com.aetherweb.app.FeedComment
import com.aetherweb.app.FeedPost
import com.aetherweb.app.MeshViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaFeedScreen(
    viewModel: MeshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedMediaTab by remember { mutableIntStateOf(0) } // 0: Reels (Doomscroll), 1: YT Music
    val posts by AetherFeedManager.posts.collectAsState()
    val scope = rememberCoroutineScope()

    // Multi-media picker (videos & photos from device storage)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            AetherFeedManager.addPostFromUri(
                context = context,
                uri = uri,
                caption = "Local drop • Offline Doomscroll 📱🔥",
                authorName = "Me (${viewModel.uiState.value.localUserName.ifBlank { "User" }})"
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // TOP APP BAR / MODE SWITCHER
        Surface(
            color = Color.Black.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Segmented Pill / Tab Switcher (Reels vs Music)
                Row(
                    modifier = Modifier
                        .background(Color(0xFF222222), RoundedCornerShape(20.dp))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selectedMediaTab == 0) Color(0xFFE1306C) else Color.Transparent)
                            .clickable { selectedMediaTab = 0 }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Reels Feed",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selectedMediaTab == 1) Color(0xFFFF0000) else Color.Transparent)
                            .clickable { selectedMediaTab = 1 }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "YT Music",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Add to Feed action button
                if (selectedMediaTab == 0) {
                    IconButton(
                        onClick = { mediaPickerLauncher.launch("*/*") },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF262626), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Media to Feed",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // CONTENT
        if (selectedMediaTab == 0) {
            // DOOMSCROLL REELS PAGER
            DoomscrollReelsView(
                posts = posts,
                viewModel = viewModel,
                onAddMedia = { mediaPickerLauncher.launch("*/*") }
            )
        } else {
            // YT MUSIC TAB VIEW
            MusicTabScreen(modifier = Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoomscrollReelsView(
    posts: List<FeedPost>,
    viewModel: MeshViewModel,
    onAddMedia: () -> Unit
) {
    if (posts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text("No posts in offline feed", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Import videos & images from storage", color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onAddMedia,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C))
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select Media")
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { posts.size })
    var activeCommentPost by remember { mutableStateOf<FeedPost?>(null) }
    var shareNotification by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            key = { index -> if (index in posts.indices) posts[index].id else index.toString() },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val post = posts[page]
            val isCurrentPage = pagerState.currentPage == page

            ReelPageItem(
                post = post,
                isActive = isCurrentPage,
                onLikeToggle = { AetherFeedManager.toggleLike(post.id) },
                onCommentClick = { activeCommentPost = post },
                onShareClick = {
                    shareNotification = "Beamed '${post.caption.take(25)}' to nearby mesh peers! 🚀"
                    viewModel.sendMessage("📱 [Shared Reel]: \"${post.caption}\" by @${post.authorHandle}")
                }
            )
        }

        // Notification Toast for sharing to mesh
        AnimatedVisibility(
            visible = shareNotification != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        ) {
            shareNotification?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E88E5)),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                LaunchedEffect(msg) {
                    delay(2500)
                    shareNotification = null
                }
            }
        }

        // INSTAGRAM-STYLE COMMENTS BOTTOM SHEET
        if (activeCommentPost != null) {
            val post = activeCommentPost!!
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var commentInput by remember { mutableStateOf("") }

            ModalBottomSheet(
                onDismissRequest = { activeCommentPost = null },
                sheetState = sheetState,
                containerColor = Color(0xFF181818),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .size(width = 38.dp, height = 4.dp)
                            .background(Color(0xFF555555), RoundedCornerShape(2.dp))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding()
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Comments (${post.comments.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(onClick = { activeCommentPost = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = Color.Gray)
                        }
                    }

                    // Comments list
                    if (post.comments.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No comments yet. Start the conversation!", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(post.comments) { comment ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                Brush.linearGradient(listOf(Color(0xFFE1306C), Color(0xFFFD1D1D))),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = comment.author.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "@${comment.author.lowercase()}",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("offline peer", color = Color.Gray, fontSize = 10.sp)
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(text = comment.text, color = Color(0xFFDDDDDD), fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Comment Input Bar (with mesh sync)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = commentInput,
                            onValueChange = { commentInput = it },
                            placeholder = { Text("Reply to @${post.authorHandle}...", color = Color.Gray, fontSize = 13.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF262626),
                                unfocusedContainerColor = Color(0xFF262626),
                                focusedBorderColor = Color(0xFFE1306C),
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (commentInput.isNotBlank()) {
                                    val user = viewModel.uiState.value.localUserName.ifBlank { "Me" }
                                    AetherFeedManager.addComment(post.id, user, commentInput)
                                    viewModel.sendMessage("💬 [Reel Reply on @${post.authorHandle}]: $commentInput")
                                    commentInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFE1306C), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReelPageItem(
    post: FeedPost,
    isActive: Boolean,
    onLikeToggle: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val context = LocalContext.current
    var isVideoPlaying by remember { mutableStateOf(true) }
    var showBigHeart by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Spin animation for music disc
    val infiniteTransition = rememberInfiniteTransition(label = "disc_spin")
    val discRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(4000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "disc_angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(post.id) {
                detectTapGestures(
                    onDoubleTap = {
                        if (!post.isLiked) {
                            onLikeToggle()
                        }
                        showBigHeart = true
                    },
                    onTap = {
                        isVideoPlaying = !isVideoPlaying
                    }
                )
            }
    ) {
        // 1. MEDIA PLAYER / BACKGROUND
        if (post.mediaType == "video" && post.isLocalDeviceMedia) {
            val uri = Uri.parse(post.mediaUri)
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(uri)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            if (isActive && isVideoPlaying) start()
                        }
                    }
                },
                update = { videoView ->
                    if (isActive && isVideoPlaying) {
                        if (!videoView.isPlaying) videoView.start()
                    } else {
                        if (videoView.isPlaying) videoView.pause()
                    }
                },
                onRelease = { videoView ->
                    videoView.stopPlayback()
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (post.mediaType == "image" && post.isLocalDeviceMedia) {
            AsyncImage(
                model = Uri.parse(post.mediaUri),
                contentDescription = post.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Atmospheric stylized sample canvas for offline demo reels
            StylizedSampleMediaBackdrop(post = post, isActive = isActive)
        }

        // Top Subtle Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
        )

        // Bottom Heavy Gradient (ensures text readability)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        // 2. DOUBLE-TAP BIG HEART ANIMATION (INSTAGRAM STYLE)
        AnimatedVisibility(
            visible = showBigHeart,
            enter = scaleIn(spring(dampingRatio = 0.4f, stiffness = 400f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color(0xFFFF2A55).copy(alpha = 0.9f),
                modifier = Modifier.size(110.dp)
            )
            LaunchedEffect(showBigHeart) {
                delay(650)
                showBigHeart = false
            }
        }

        // 3. RIGHT ACTION RAIL (LIKE, COMMENT, SHARE, DISC)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Like Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onLikeToggle,
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (post.isLiked) Color(0xFFFF2A55) else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = post.likes.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Comment Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onCommentClick,
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = post.comments.size.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Share to Mesh Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share to Mesh",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = "Beam",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Spinning Music Disc
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .rotate(if (isActive) discRotation else 0f)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFF2B2B2B), Color.Black)),
                        CircleShape
                    )
                    .border(1.5.dp, Color(0xFF666666), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(Color(0xFFE1306C), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        // 4. BOTTOM POST DETAILS (INSTAGRAM METADATA)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.80f)
                .padding(start = 16.dp, bottom = 24.dp)
        ) {
            // Author Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045))),
                            CircleShape
                        )
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = post.author.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Text(
                    text = "@${post.authorHandle}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Peer", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Caption
            Text(
                text = post.caption,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Tags
            if (post.tags.isNotEmpty()) {
                Text(
                    text = post.tags.joinToString(" "),
                    color = Color(0xFF00B0FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Music Ticker
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "♫ ${post.musicTitle} • ${post.musicArtist}",
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StylizedSampleMediaBackdrop(
    post: FeedPost,
    isActive: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "backdrop_anim")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(6000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "wave_offset"
    )

    val gradientColors = when (post.mediaUri) {
        "sample_nebula" -> listOf(Color(0xFF0D0826), Color(0xFF280B45), Color(0xFF6B117B), Color(0xFFFF007F))
        "sample_waves" -> listOf(Color(0xFF001F3F), Color(0xFF003366), Color(0xFF0074D9), Color(0xFF39CCCC))
        else -> listOf(Color(0xFF1A002C), Color(0xFF37004D), Color(0xFF7A006C), Color(0xFFFF0055))
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Dynamic gradient background
        drawRect(
            brush = Brush.verticalGradient(
                colors = gradientColors,
                startY = 0f,
                endY = height
            )
        )

        // Atmospheric glowing orbs
        val orbCenter1 = Offset(width * 0.3f, height * (0.35f + 0.05f * kotlin.math.sin(waveOffset * 6.28f)))
        val orbCenter2 = Offset(width * 0.75f, height * (0.6f + 0.05f * kotlin.math.cos(waveOffset * 6.28f)))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                center = orbCenter1,
                radius = width * 0.45f
            ),
            center = orbCenter1,
            radius = width * 0.45f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFF007F).copy(alpha = 0.35f), Color.Transparent),
                center = orbCenter2,
                radius = width * 0.55f
            ),
            center = orbCenter2,
            radius = width * 0.55f
        )
    }
}
