package com.aetherweb.app

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class FeedComment(
    val id: String = UUID.randomUUID().toString(),
    val author: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class FeedPost(
    val id: String = UUID.randomUUID().toString(),
    val mediaUri: String, // "content://...", "file://...", or "sample_..."
    val mediaType: String, // "video", "image", "audio"
    val author: String,
    val authorHandle: String,
    val caption: String,
    val tags: List<String> = emptyList(),
    val musicTitle: String = "Original Sound",
    val musicArtist: String = "Offline Creator",
    val likes: Int = 0,
    val isLiked: Boolean = false,
    val comments: List<FeedComment> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isLocalDeviceMedia: Boolean = false
)

object AetherFeedManager {
    private val _posts = MutableStateFlow<List<FeedPost>>(emptyList())
    val posts: StateFlow<List<FeedPost>> = _posts.asStateFlow()

    init {
        // Seed curated offline doomscroll posts so the feed is lively and interactive immediately
        _posts.value = listOf(
            FeedPost(
                id = "sample_reel_1",
                mediaUri = "sample_cyberpunk",
                mediaType = "image",
                author = "Mesh Pioneer",
                authorHandle = "aether_nomad",
                caption = "Testing the decentralized offline network in the mountains. Zero cell towers, zero internet, pure P2P mesh vibes! 📡✨",
                tags = listOf("#aetherweb", "#offlinemesh", "#doomscroll", "#cyberpunk"),
                musicTitle = "Midnight Synth Echoes",
                musicArtist = "Aether Audio",
                likes = 142,
                isLiked = true,
                comments = listOf(
                    FeedComment(author = "Elena_K", text = "Wait, we are swiping reels without WiFi?! 🤯"),
                    FeedComment(author = "Dave_Mesh", text = "This is revolutionary for off-grid festivals!"),
                    FeedComment(author = "Sara_T", text = "Audio sync works like a charm!")
                )
            ),
            FeedPost(
                id = "sample_reel_2",
                mediaUri = "sample_nebula",
                mediaType = "image",
                author = "Aether DJ",
                authorHandle = "dj_aether",
                caption = "Streaming this live audio-visual set directly through the peer-to-peer Wi-Fi hotspot. Swipe up for more offline tunes! 🎧🔥",
                tags = listOf("#silentdisco", "#ytmusic", "#p2paudio", "#localbeats"),
                musicTitle = "Deep Sub Bassline 128BPM",
                musicArtist = "DJ Aether",
                likes = 389,
                isLiked = false,
                comments = listOf(
                    FeedComment(author = "Alex99", text = "The bass is unreal on this one!"),
                    FeedComment(author = "DJ_Kovacs", text = "Added this to my Silent Disco party queue.")
                )
            ),
            FeedPost(
                id = "sample_reel_3",
                mediaUri = "sample_waves",
                mediaType = "image",
                author = "Offline Nomad",
                authorHandle = "solar_punk",
                caption = "Sunset drone footage saved on local storage, now streaming effortlessly to everyone connected to my AetherWeb portal 🌅🏖️",
                tags = listOf("#solarpunk", "#drone4k", "#meshcommunity"),
                musicTitle = "Golden Hour Ambient",
                musicArtist = "Lofi Sunset",
                likes = 527,
                isLiked = true,
                comments = listOf(
                    FeedComment(author = "Maya_W", text = "Looks gorgeous! Can you beam the high-res file?"),
                    FeedComment(author = "Solar_Punk", text = "Beaming it to the room right now via CDN tab!")
                )
            )
        )
    }

    fun addPostFromUri(context: Context, uri: Uri, caption: String, authorName: String) {
        val cr = context.contentResolver
        val mimeType = cr.getType(uri) ?: ""
        val type = when {
            mimeType.startsWith("video") -> "video"
            mimeType.startsWith("image") -> "image"
            mimeType.startsWith("audio") -> "audio"
            else -> "image"
        }

        val newPost = FeedPost(
            id = UUID.randomUUID().toString(),
            mediaUri = uri.toString(),
            mediaType = type,
            author = authorName.ifBlank { "You (Local)" },
            authorHandle = authorName.lowercase().replace(" ", "_").ifBlank { "local_host" },
            caption = caption.ifBlank { "Shared from device storage 📱✨" },
            tags = listOf("#localstorage", "#offlinefeed", "#mesh"),
            musicTitle = "Device Audio Track",
            musicArtist = authorName.ifBlank { "Local" },
            likes = 0,
            isLiked = false,
            comments = emptyList(),
            isLocalDeviceMedia = true
        )

        _posts.value = listOf(newPost) + _posts.value
    }

    fun toggleLike(postId: String) {
        _posts.value = _posts.value.map { post ->
            if (post.id == postId) {
                val newLiked = !post.isLiked
                val newCount = if (newLiked) post.likes + 1 else (post.likes - 1).coerceAtLeast(0)
                post.copy(isLiked = newLiked, likes = newCount)
            } else {
                post
            }
        }
    }

    fun addComment(postId: String, author: String, text: String): FeedComment? {
        if (text.isBlank()) return null
        val comment = FeedComment(author = author.ifBlank { "Me" }, text = text.trim())
        _posts.value = _posts.value.map { post ->
            if (post.id == postId) {
                post.copy(comments = post.comments + comment)
            } else {
                post
            }
        }
        return comment
    }

    /**
     * Seamlessly connects chat messages from the mesh network into the Feed comments.
     * When any peer sends a reel reply or comment over the mesh, it dynamically attaches
     * to the corresponding reel in the Doomscroll feed.
     */
    fun handleIncomingChatMessage(text: String, senderName: String) {
        if (text.startsWith("💬 [Reel Reply on @") || text.startsWith("💬 [Reel Reply:")) {
            val endIdx = text.indexOf("]:")
            if (endIdx != -1) {
                val handlePart = if (text.startsWith("💬 [Reel Reply on @")) {
                    text.substring("💬 [Reel Reply on @".length, endIdx).trim()
                } else {
                    text.substring("💬 [Reel Reply:".length, endIdx).trim()
                }
                val commentContent = text.substring(endIdx + 2).trim()
                
                // Match to the right post by authorHandle, or attach to the active/first post
                val matchedPost = _posts.value.find { 
                    it.authorHandle.equals(handlePart, ignoreCase = true) || it.author.contains(handlePart, ignoreCase = true)
                } ?: _posts.value.firstOrNull()

                matchedPost?.let { post ->
                    addComment(post.id, senderName, commentContent)
                }
            }
        }
    }
}
