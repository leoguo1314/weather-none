package com.skypulse.weather.ui.screen

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.skypulse.weather.ui.components.LucideIcon

/**
 * 台风雷达图全屏页面。
 *
 * 直接以 WebView 嵌入外部台风路径地图页面：
 * https://tf.istrongcloud.com/release/index-gjdw2.html
 *
 * 页面为「实时台风路径」完整应用，包含台风路径、雷达图、
 * 卫星云图等图层切换功能，无需本地任何地图实现。
 */
@Composable
fun RadarMapScreen(
    latitude: Double?,
    longitude: Double?,
    locationName: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF10161E))) {
        // === WebView 台风地图 ===
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
                        databaseEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        // 允许 HTTPS 页面加载 HTTP 资源（页面部分资源可能是 http）
                        @Suppress("DEPRECATION")
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        // 允许加载本地文件（页面可能需要）
                        @Suppress("DEPRECATION")
                        allowFileAccess = true
                        @Suppress("DEPRECATION")
                        allowContentAccess = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = settings.userAgentString
                    }

                    webChromeClient = WebChromeClient()

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            // 隐藏页面上的广告/版权/标题元素：
                            // - .copyright_div        → 左下角「四创科技」
                            // - .typhoon-TitleNameDiv-mobile → 顶部台风标题编号
                            val hideJs = """
                                (function() {
                                    var css = document.createElement('style');
                                    css.textContent = '.copyright_div, .typhoon-TitleNameDiv-mobile, .current_checked_typhoon_div { display: none !important; }';
                                    document.head.appendChild(css);
                                    // 兜底：直接隐藏已存在的元素
                                    var targets = document.querySelectorAll('.copyright_div, .typhoon-TitleNameDiv-mobile, .current_checked_typhoon_div');
                                    targets.forEach(function(el) { el.style.display = 'none'; });
                                    // 监听 DOM 变化，动态创建的元素也隐藏
                                    var observer = new MutationObserver(function(mutations) {
                                        mutations.forEach(function(m) {
                                            m.addedNodes.forEach(function(node) {
                                                if (node.nodeType === 1) {
                                                    if (node.classList && (node.classList.contains('copyright_div') || node.classList.contains('typhoon-TitleNameDiv-mobile') || node.classList.contains('current_checked_typhoon_div'))) {
                                                        node.style.display = 'none';
                                                    }
                                                }
                                            });
                                        });
                                    });
                                    observer.observe(document.body, { childList: true, subtree: true });
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(hideJs, null)
                        }
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            // 忽略子资源错误，只有主页面失败才提示
                        }
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            // 页面内的链接（如地图、详情页）在 WebView 内打开
                            return false
                        }
                    }

                    // 加载台风路径页面
                    loadUrl("https://tf.istrongcloud.com/release/index-gjdw2.html")
                }
            },
            update = { },
            modifier = Modifier.fillMaxSize()
        )

        // === 顶栏（返回按钮 + 标题） ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                LucideIcon(name = "arrow-left", contentDescription = "返回", tint = Color.White, size = 22.dp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "台风路径",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }

        // === 加载指示 ===
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF10161E).copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "加载台风路径...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
