package com.localstream.app.ui.player

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("UnusedPrivateMember", "LongMethod")
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerView(
    videoId: String,
    initialPositionMs: Long,
    isPlaying: Boolean,
    positionMs: Long,
    onPositionChanged: (positionMs: Long, durationMs: Long) -> Unit,
    onPlayingStateChanged: (isPlaying: Boolean) -> Unit,
    onEnded: () -> Unit,
    onError: (error: String?) -> Unit,
    onBuffering: (isBuffering: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isJsReady by remember { mutableStateOf(false) }
    var lastWebPositionMs by remember { mutableLongStateOf(initialPositionMs) }

    val startSeconds = (initialPositionMs / 1000).coerceAtLeast(0)

    val htmlContent = remember(videoId, startSeconds) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                body, html { margin: 0; padding: 0; width: 100%; height: 100%; background-color: #000000; overflow: hidden; }
                #player { width: 100%; height: 100%; }
            </style>
        </head>
        <body>
            <div id="player"></div>
            <script>
                var tag = document.createElement('script');
                tag.src = "https://www.youtube.com/iframe_api";
                var firstScriptTag = document.getElementsByTagName('script')[0];
                firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                var player;
                function onYouTubeIframeAPIReady() {
                    player = new YT.Player('player', {
                        height: '100%',
                        width: '100%',
                        videoId: '$videoId',
                        playerVars: {
                            'autoplay': 1,
                            'controls': 0,
                            'rel': 0,
                            'fs': 0,
                            'playsinline': 1,
                            'start': $startSeconds,
                            'enablejsapi': 1,
                            'modestbranding': 1
                        },
                        events: {
                            'onReady': onPlayerReady,
                            'onStateChange': onPlayerStateChange,
                            'onError': onPlayerError
                        }
                    });
                }
                function onPlayerReady(event) {
                    if (window.AndroidBridge) {
                        window.AndroidBridge.onPlayerReady();
                    }
                    setInterval(function() {
                        if (player && player.getPlayerState && player.getPlayerState() === 1) {
                            var curTime = player.getCurrentTime() || 0;
                            var dur = player.getDuration() || 0;
                            if (window.AndroidBridge) {
                                window.AndroidBridge.onProgress(curTime, dur);
                            }
                        }
                    }, 500);
                }
                function onPlayerStateChange(event) {
                    if (window.AndroidBridge) {
                        var curTime = player && player.getCurrentTime ? player.getCurrentTime() : 0;
                        var dur = player && player.getDuration ? player.getDuration() : 0;
                        window.AndroidBridge.onStateChange(event.data, curTime, dur);
                    }
                }
                function onPlayerError(err) {
                    if (window.AndroidBridge) {
                        window.AndroidBridge.onError("" + err.data);
                    }
                }
                function playVideo() { if(player && player.playVideo) player.playVideo(); }
                function pauseVideo() { if(player && player.pauseVideo) player.pauseVideo(); }
                function seekTo(sec) { if(player && player.seekTo) player.seekTo(sec, true); }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    DisposableEffect(videoId) {
        onDispose {
            webViewRef?.destroy()
        }
    }

    LaunchedEffect(isPlaying, isJsReady) {
        if (isJsReady) {
            if (isPlaying) {
                webViewRef?.evaluateJavascript("playVideo()", null)
            } else {
                webViewRef?.evaluateJavascript("pauseVideo()", null)
            }
        }
    }

    LaunchedEffect(positionMs, isJsReady) {
        if (isJsReady && kotlin.math.abs(positionMs - lastWebPositionMs) > 1500L) {
            val sec = positionMs / 1000f
            lastWebPositionMs = positionMs
            webViewRef?.evaluateJavascript("seekTo($sec)", null)
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = object : WebViewClient() {}
                webChromeClient = object : WebChromeClient() {}
                val mainScope = CoroutineScope(Dispatchers.Main)
                addJavascriptInterface(
                    YouTubeBridge(
                        mainScope = mainScope,
                        onReady = { isJsReady = true },
                        onStateChange = { state, currentTimeSec, durationSec ->
                            when (state) {
                                1 -> { // PLAYING
                                    onBuffering(false)
                                    onError(null)
                                    onPlayingStateChanged(true)
                                    val pos = (currentTimeSec * 1000).toLong()
                                    lastWebPositionMs = pos
                                    onPositionChanged(pos, (durationSec * 1000).toLong())
                                }
                                2 -> { // PAUSED
                                    onBuffering(false)
                                    onPlayingStateChanged(false)
                                    val pos = (currentTimeSec * 1000).toLong()
                                    lastWebPositionMs = pos
                                    onPositionChanged(pos, (durationSec * 1000).toLong())
                                }
                                3 -> { // BUFFERING
                                    onBuffering(true)
                                }
                                0 -> { // ENDED
                                    onBuffering(false)
                                    onPlayingStateChanged(false)
                                    onEnded()
                                }
                                else -> Unit
                            }
                        },
                        onProgress = { currentTimeSec, durationSec ->
                            val pos = (currentTimeSec * 1000).toLong()
                            lastWebPositionMs = pos
                            onPositionChanged(pos, (durationSec * 1000).toLong())
                        },
                        onError = { errorCode ->
                            onBuffering(false)
                            onError("Erreur de lecture YouTube (code $errorCode)")
                        },
                    ),
                    "AndroidBridge",
                )

                loadDataWithBaseURL("https://www.youtube.com", htmlContent, "text/html", "UTF-8", null)
                webViewRef = this
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

class YouTubeBridge(
    private val mainScope: CoroutineScope,
    private val onReady: () -> Unit,
    private val onStateChange: (state: Int, currentTimeSec: Float, durationSec: Float) -> Unit,
    private val onProgress: (currentTimeSec: Float, durationSec: Float) -> Unit,
    private val onError: (errorCode: String) -> Unit,
) {
    @JavascriptInterface
    fun onPlayerReady() {
        mainScope.launch { onReady() }
    }

    @JavascriptInterface
    fun onStateChange(state: Int, currentTimeSec: Float, durationSec: Float) {
        mainScope.launch { onStateChange(state, currentTimeSec, durationSec) }
    }

    @JavascriptInterface
    fun onProgress(currentTimeSec: Float, durationSec: Float) {
        mainScope.launch { onProgress(currentTimeSec, durationSec) }
    }

    @JavascriptInterface
    fun onError(errorCode: String) {
        mainScope.launch { onError(errorCode) }
    }
}
