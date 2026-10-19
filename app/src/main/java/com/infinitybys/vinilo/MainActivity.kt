package com.infinitybys.vinilo

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale
import java.util.concurrent.Executor

private val Night = Color(0xFF1A1424)
private val Panel = Color(0xFF251D33)
private val Coral = Color(0xFFFF806D)
private val SoftText = Color(0xFFBDB4C9)
private val Cream = Color(0xFFF7F0FA)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val mainExecutor: Executor by lazy { ContextCompat.getMainExecutor(this) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) refreshSongs()
    }

    private var songsState by mutableStateOf<List<Song>>(emptyList())
    private var permissionGranted by mutableStateOf(false)
    private var currentTitle by mutableStateOf("Elige una canción")
    private var currentArtist by mutableStateOf("Tu música, a tu manera")
    private var isPlaying by mutableStateOf(false)
    private var currentIndex by mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) refreshSongs() else permissionLauncher.launch(permission)

        val token = SessionToken(this, ComponentName(this, MusicPlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        currentTitle = mediaItem?.mediaMetadata?.title?.toString() ?: "Elige una canción"
                        currentArtist = mediaItem?.mediaMetadata?.artist?.toString() ?: "Tu música, a tu manera"
                        currentIndex = mediaController?.currentMediaItemIndex ?: -1
                    }
                })
                syncPlayerState()
            } catch (_: Exception) { }
        }, mainExecutor)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Coral,
                    background = Night,
                    surface = Panel,
                    onBackground = Cream,
                    onSurface = Cream
                )
            ) {
                ViniloScreen(
                    songs = songsState,
                    permissionGranted = permissionGranted,
                    currentTitle = currentTitle,
                    currentArtist = currentArtist,
                    isPlaying = isPlaying,
                    currentIndex = currentIndex,
                    onSongClick = { index -> playSong(index) },
                    onToggle = { togglePlayback() },
                    onNext = { skipNext() },
                    onPrevious = { skipPrevious() },
                    onRequestPermission = {
                        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
                        permissionLauncher.launch(p)
                    }
                )
            }
        }
    }

    private fun refreshSongs() {
        permissionGranted = true
        songsState = MusicRepository.loadSongs(this)
    }

    private fun playSong(index: Int) {
        val controller = mediaController ?: return
        if (songsState.isEmpty() || index !in songsState.indices) return
        val items = songsState.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build()
                ).build()
        }
        controller.setMediaItems(items, index, 0L)
        controller.prepare()
        controller.play()
        currentIndex = index
        currentTitle = songsState[index].title
        currentArtist = songsState[index].artist
        isPlaying = true
    }

    private fun togglePlayback() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0 && songsState.isNotEmpty()) {
            playSong(0)
        } else if (controller.isPlaying) controller.pause() else controller.play()
    }

    private fun skipNext() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0 && songsState.isNotEmpty()) playSong(0)
        else controller.seekToNextMediaItem()
    }

    private fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0 && songsState.isNotEmpty()) playSong(0)
        else controller.seekToPreviousMediaItem()
    }

    private fun syncPlayerState() {
        mediaController?.let { controller ->
            isPlaying = controller.isPlaying
            currentTitle = controller.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Elige una canción"
            currentArtist = controller.currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Tu música, a tu manera"
            currentIndex = controller.currentMediaItemIndex
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        super.onDestroy()
    }
}

@Composable
private fun ViniloScreen(
    songs: List<Song>,
    permissionGranted: Boolean,
    currentTitle: String,
    currentArtist: String,
    isPlaying: Boolean,
    currentIndex: Int,
    onSongClick: (Int) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onRequestPermission: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("Canciones") }
    val filtered = remember(songs, search) {
        songs.filter { it.title.contains(search, true) || it.artist.contains(search, true) || it.album.contains(search, true) }
    }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF30223F), Night, Night)))
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(Coral, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = Night)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("VINILO", fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = Cream)
                Text("Tu música. Tu momento.", fontSize = 12.sp, color = SoftText)
            }
            Icon(Icons.Filled.FavoriteBorder, contentDescription = "Favoritos", tint = Coral)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Canciones", "Álbumes", "Artistas").forEach { tab ->
                val selected = selectedTab == tab
                Surface(
                    color = if (selected) Coral else Panel,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f).clickable { selectedTab = tab }
                ) {
                    Text(tab, Modifier.padding(vertical = 11.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = if (selected) Night else SoftText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar canciones o artistas…", color = SoftText) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Coral, unfocusedBorderColor = Color(0xFF493A58),
                focusedTextColor = Cream, unfocusedTextColor = Cream, cursorColor = Coral
            )
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(if (selectedTab == "Canciones") "Tu biblioteca" else selectedTab, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Cream)
                Text("${filtered.size} canciones disponibles", fontSize = 12.sp, color = SoftText)
            }
            Text("● LOCAL", fontSize = 10.sp, color = Coral, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(10.dp))

        if (!permissionGranted) {
            EmptyLibrary("Dale permiso a Vinilo para encontrar la música de tu teléfono.", "Dar permiso", onRequestPermission)
        } else if (songs.isEmpty()) {
            EmptyLibrary("Todavía no encontramos canciones locales. Añade archivos de audio al teléfono y vuelve a intentarlo.", "Volver a cargar", onRequestPermission)
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No encontramos coincidencias.", color = SoftText)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                itemsIndexed(filtered, key = { _, song -> song.id }) { _, song ->
                    val originalIndex = songs.indexOfFirst { it.id == song.id }
                    SongRow(song, originalIndex == currentIndex, onClick = { onSongClick(originalIndex) })
                }
            }
        }

        PlayerPanel(currentTitle, currentArtist, isPlaying, onToggle, onNext, onPrevious)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SongRow(song: Song, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (selected) Color(0xFF382A47) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).background(Panel, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            VinylDisc(isPlaying = selected, size = 38)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = if (selected) Coral else Cream, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Text(song.artist, color = SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
        Text(formatDuration(song.duration), color = SoftText, fontSize = 11.sp)
    }
}

@Composable
private fun PlayerPanel(
    title: String, artist: String, isPlaying: Boolean,
    onToggle: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit
) {
    Surface(color = Color(0xFF2C2238), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VinylDisc(isPlaying = isPlaying, size = 66)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("SONANDO AHORA", color = Coral, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(title, color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist, color = SoftText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", tint = Cream, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.width(22.dp))
                Surface(color = Coral, shape = CircleShape, modifier = Modifier.size(54.dp).clickable(onClick = onToggle)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pausar" else "Reproducir", tint = Night, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.width(22.dp))
                IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", tint = Cream, modifier = Modifier.size(30.dp)) }
            }
        }
    }
}

@Composable
private fun VinylDisc(isPlaying: Boolean, size: Int) {
    val transition = rememberInfiniteTransition(label = "vinyl")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )
    Box(
        Modifier.size(size.dp).rotate(if (isPlaying) rotation else 0f)
            .background(Color(0xFF111015), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.fillMaxSize(.84f).background(Color(0xFF29232F), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize(.72f).background(Color(0xFF16131D), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size((size * .35f).dp).background(Coral, CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size((size * .09f).dp).background(Night, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(message: String, buttonText: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        VinylDisc(isPlaying = false, size = 94)
        Spacer(Modifier.height(18.dp))
        Text(message, color = SoftText, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Night)) { Text(buttonText) }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
