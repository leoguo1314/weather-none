package com.skypulse.weather.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.skypulse.weather.ui.components.LucideIcon
import com.skypulse.weather.ui.theme.LocalSecondaryPageTheme
import kotlinx.coroutines.delay

/**
 * 台风雷达图页面。
 *
 * 与城市管理 / 预警详情页面保持一致的布局风格：
 * - 顶部：标题「台风路径」+ 返回箭头（[TopAppBar]）
 * - 下方：整个展示区作为圆角方框（卡片）内嵌 WebView 地图，而非全屏铺开
 *
 * WebView 嵌入外部台风路径地图页面：
 * https://tf.istrongcloud.com/release/index-gjdw2.html
 *
 * 页面为「实时台风路径」完整应用，包含台风路径、雷达图、
 * 卫星云图等图层切换功能，无需本地任何地图实现。
 *
 * ## 性能优化说明（对比真实浏览器体验）
 * 该页面是持续重绘的 JS 地图 SPA（风场动画/瓦片/标注频繁增删 DOM），
 * 为让手势（双指缩放/滑动）与浏览器一样丝滑，做了以下处理：
 * 1. 地图卡片使用原生 View（FrameLayout + 圆角裁剪 + WebView）承载，
 *    顶栏位于卡片上方不与其重叠，除加载遮罩期间外 Compose 不覆盖 WebView，
 *    避免 WebView 走分层合成（先渲染到纹理再合成）造成掉帧；
 * 2. 不再注入常驻 MutationObserver（页面频繁变更 DOM 时会在主线程持续产生回调），
 *    改为纯 CSS `!important` + 几次一次性延迟隐藏，CSS 优先级足以覆盖行内样式；
 * 3. 加载遮罩只在首次主页面加载期间显示，且 12s 兜底强制消失；
 * 4. WebView 显式使用硬件渲染层，防止个别设备回退到软件渲染。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarMapScreen(
    latitude: Double?,
    longitude: Double?,
    locationName: String?,
    onBack: () -> Unit
) {
    val page = LocalSecondaryPageTheme.current
    var isLoading by remember { mutableStateOf(true) }
    val currentOnBack by rememberUpdatedState(onBack)

    // 兜底：即使 onPageFinished 迟迟不触发（页面内部持续异步加载），
    // 也强制隐藏加载遮罩，避免遮罩长期叠加在 WebView 之上。
    LaunchedEffect(Unit) {
        delay(12_000)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(page.background)
    ) {
        // === 顶栏（与城市管理 / 预警详情一致） ===
        TopAppBar(
            title = { Text("台风路径", color = page.textPrimary) },
            navigationIcon = {
                IconButton(onClick = { currentOnBack() }) {
                    LucideIcon(
                        name = "arrow-left",
                        contentDescription = "返回",
                        tint = page.backArrow
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = page.background
            )
        )

        // === 下方：圆角方框（卡片）内展示 WebView 地图 ===
        // 底部额外留出 32dp，让卡片与手机底边保持明显距离（navigationBarsPadding 之上叠加）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    createTyphoonMapContainer(ctx, page.cardBackground) { loading ->
                        isLoading = loading
                    }
                },
                update = { view ->
                    // 深浅色主题切换时同步卡片底色
                    (view as? FrameLayout)?.background?.let { bg ->
                        if (bg is GradientDrawable) bg.setColor(page.cardBackground.toArgb())
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // === 加载指示（仅首次加载期间，覆盖在卡片区域内） ===
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(page.cardBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "加载台风路径...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = page.textSecondary
                    )
                }
            }
        }
    }
}

/**
 * 构建圆角卡片容器 + WebView。
 *
 * 卡片用原生 FrameLayout + 圆角 GradientDrawable + clipToOutline 实现，
 * 与 Compose 卡片外观一致，同时保证 WebView 被正确裁剪到圆角内。
 */
private fun createTyphoonMapContainer(
    context: Context,
    cardColor: Color,
    onLoadingChanged: (Boolean) -> Unit
): FrameLayout {
    val density = context.resources.displayMetrics.density

    val container = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            cornerRadius = 14f * density
            setColor(cardColor.toArgb())
        }
        clipToOutline = true
    }

    // === WebView 台风地图 ===
    val webView = WebView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // 与地图页面一致的深色底，避免加载初期白屏闪烁
        setBackgroundColor(0xFF10161E.toInt())

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
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
        }
        // 明确要求硬件渲染层（避免个别设备回退到软件渲染导致手势卡顿）
        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webChromeClient = WebChromeClient()

        webViewClient = object : WebViewClient() {
            private var mainPageFinished = false

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                // 只对首次主页面加载显示遮罩；页面内部异步加载不再触发遮罩闪回
                if (!mainPageFinished) onLoadingChanged(true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (mainPageFinished) return
                mainPageFinished = true
                onLoadingChanged(false)
                // 隐藏页面上的广告/版权/标题/源标注元素：
                // - .copyright_div    → 左下角「四创科技」
                // - .typhoon-TitleNameDiv-mobile → 顶部台风标题编号
                // - .current_checked_typhoon_div → 当前台风选中标识
                // - .map_source_div   → 右下角「高德地图 - GS(2016)710号」及图层时间编号（雷达图/云图/风场时间戳）
                // 另外将右侧 6 个功能按钮（图例/测距/风场/雷达/云图/台风列表）替换为
                // App 风格：半透明深色圆角 + 白色线性图标（lucide 风格 SVG data-URI）。
                // 说明：纯 CSS !important 优先级高于行内样式，无需常驻 MutationObserver；
                // 这里再加几次一次性延迟隐藏兜底动态创建的元素（开销可忽略，不拖慢手势）。
                val injectJs = """
                    (function() {
                        var css = [
                            '.copyright_div{display:none !important;}',
                            '.typhoon-TitleNameDiv-mobile{display:none !important;}',
                            '.current_checked_typhoon_div{display:none !important;}',
                            '.map_source_div{display:none !important;}',
                            '.right_btn_div{top:10px !important;padding:4px !important;}',
                            '.tf_menu_list_div{width:42px !important;height:44px !important;margin-top:8px !important;padding-top:5px !important;background:rgba(13,20,28,0.55) !important;border:1px solid rgba(255,255,255,0.12) !important;border-radius:12px !important;box-shadow:0 2px 10px rgba(0,0,0,0.30) !important;opacity:1 !important;}',
                            '.tf_menu_list_div .tf_menu_list_img{width:22px !important;height:22px !important;margin:0 auto 3px auto !important;background-size:22px 22px !important;background-repeat:no-repeat !important;background-position:center !important;}',
                            '.tf_menu_list_font{font-size:10px !important;line-height:1 !important;color:#ffffff !important;text-align:center !important;}',
                            '.tf_menu_list_div_tl .tf_menu_list_img,.tf_menu_list_div_tl.img_hover .tf_menu_list_img{background-image:url(\'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyMiIgaGVpZ2h0PSIyMiIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNmZmZmZmYiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48cGF0aCBkPSJtMTIuODMgMi4xOGEyIDIgMCAwIDAtMS42NiAwTDIuNiA2LjA4YTEgMSAwIDAgMCAwIDEuODNsOC41OCAzLjkxYTIgMiAwIDAgMCAxLjY2IDBsOC41OC0zLjlhMSAxIDAgMCAwIDAtMS44M1oiLz48cGF0aCBkPSJtMjIgMTcuNjUtOS4xNyA0LjE2YTIgMiAwIDAgMS0xLjY2IDBMMiAxNy42NSIvPjxwYXRoIGQ9Im0yMiAxMi42NS05LjE3IDQuMTZhMiAyIDAgMCAxLTEuNjYgMEwyIDEyLjY1Ii8+PC9zdmc+\') !important;}',
                            '.tf_menu_list_div_cscj .tf_menu_list_img,.tf_menu_list_div_cscj.img_hover .tf_menu_list_img{background-image:url(\'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyMiIgaGVpZ2h0PSIyMiIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNmZmZmZmYiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48cGF0aCBkPSJNMjEuMyAxNS4zYTIuNCAyLjQgMCAwIDEgMCAzLjRsLTIuNiAyLjZhMi40IDIuNCAwIDAgMS0zLjQgMEwyLjcgOC43YTIuNDEgMi40MSAwIDAgMSAwLTMuNGwyLjYtMi42YTIuNDEgMi40MSAwIDAgMSAzLjQgMFoiLz48cGF0aCBkPSJtMTQuNSAxMi41IDItMiIvPjxwYXRoIGQ9Im0xMS41IDkuNSAyLTIiLz48cGF0aCBkPSJtOC41IDYuNSAyLTIiLz48cGF0aCBkPSJtMTcuNSAxNS41IDItMiIvPjwvc3ZnPg==\') !important;}',
                            '.tf_menu_list_div_xsfc .tf_menu_list_img,.tf_menu_list_div_xsfc.img_hover .tf_menu_list_img{background-image:url(\'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyMiIgaGVpZ2h0PSIyMiIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNmZmZmZmYiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48cGF0aCBkPSJNMTIuOCAxOS42QTIgMiAwIDEgMCAxNCAxNkgyIi8+PHBhdGggZD0iTTE3LjUgOGEyLjUgMi41IDAgMSAxIDIgNEgyIi8+PHBhdGggZD0iTTkuOCA0LjRBMiAyIDAgMSAxIDExIDhIMiIvPjwvc3ZnPg==\') !important;}',
                            '.tf_menu_list_div_qxld .tf_menu_list_img,.tf_menu_list_div_qxld.img_hover .tf_menu_list_img{background-image:url(\'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyMiIgaGVpZ2h0PSIyMiIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNmZmZmZmYiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48Y2lyY2xlIGN4PSIxMiIgY3k9IjEyIiByPSIxMCIvPjxsaW5lIHgxPSIyMiIgeDI9IjE4IiB5MT0iMTIiIHkyPSIxMiIvPjxsaW5lIHgxPSI2IiB4Mj0iMiIgeTE9IjEyIiB5Mj0iMTIiLz48bGluZSB4MT0iMTIiIHgyPSIxMiIgeTE9IjYiIHkyPSIyIi8+PGxpbmUgeDE9IjEyIiB4Mj0iMTIiIHkxPSIyMiIgeTI9IjE4Ii8+PC9zdmc+\') !important;}',
                            '.tf_menu_list_div_wxyt .tf_menu_list_img,.tf_menu_list_div_wxyt.img_hover .tf_menu_list_img{background-image:url(\'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyMiIgaGVpZ2h0PSIyMiIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNmZmZmZmYiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48cGF0aCBkPSJNMTcuNSAxOUg5YTcgNyAwIDEgMSA2LjcxLTloMS43OWE0LjUgNC41IDAgMSAxIDAgOVoiLz48L3N2Zz4=\') !important;}',
                            '.tf_menu_list_div_tflb .tf_menu_list_img,.tf_menu_list_div_tflb.img_hover .tf_menu_list_img{background-image:url(\'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyMiIgaGVpZ2h0PSIyMiIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiNmZmZmZmYiIHN0cm9rZS13aWR0aD0iMiIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIiBzdHJva2UtbGluZWpvaW49InJvdW5kIj48cGF0aCBkPSJNMyAxMmguMDEiLz48cGF0aCBkPSJNMyAxOGguMDEiLz48cGF0aCBkPSJNMyA2aC4wMSIvPjxwYXRoIGQ9Ik04IDEyaDEzIi8+PHBhdGggZD0iTTggMThoMTMiLz48cGF0aCBkPSJNOCA2aDEzIi8+PC9zdmc+\') !important;}'
                        ].join(' ');
                        var style = document.createElement('style');
                        style.textContent = css;
                        document.head.appendChild(style);
                        [1500, 3000, 5000].forEach(function(ms) {
                            setTimeout(function() {
                                var els = document.querySelectorAll(
                                    '.copyright_div, .typhoon-TitleNameDiv-mobile, .current_checked_typhoon_div, .map_source_div'
                                );
                                for (var i = 0; i < els.length; i++) {
                                    els[i].style.display = 'none';
                                }
                            }, ms);
                        });
                    })();
                """.trimIndent()
                view?.evaluateJavascript(injectJs, null)
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
        loadUrl(TYPHOON_PAGE_URL)
    }
    container.addView(webView)

    return container
}

private const val TYPHOON_PAGE_URL = "https://tf.istrongcloud.com/release/index-gjdw2.html"
