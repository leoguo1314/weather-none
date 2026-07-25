package com.skypulse.weather.data

import com.skypulse.weather.util.FileLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 定位请求协调者 — 请求级去重和短时缓存层。
 *
 * 位于 WeatherSyncManager 和 LocationManager 之间，解决多路调用者
 * （主页、小组件、后台校准）之间重复发起 AMAP 定位请求的问题。
 *
 * 核心能力：
 * 1. 合并并发请求：同一时间窗口内的多个请求共享一次 AMAP 调用
 * 2. 短时缓存：30s 内的定位结果直接返回，不重复调用 AMAP
 * 3. 失败回退：定位失败后 60s 内不再重试
 */
@Singleton
class LocationRequestCoordinator @Inject constructor(
    private val locationManager: LocationManager
) {

    /**
     * 调用者标识，用于日志和未来扩展。
     */
    enum class Caller {
        FOREGROUND_REFRESH,   // refreshWeatherWithLocation()
        WIDGET_REFRESH,       // refreshWeatherWithLocationForWidget()
        LOCATION_CALIBRATION  // calibrateCurrentLocation()
    }

    // ============ 常量 ============

    companion object {
        private const val TAG = "LocCoordinator"

        /** 定位成功后缓存有效期 */
        private const val CACHE_TTL_MS = 30_000L

        /** 定位失败后再试最小间隔 */
        private const val FAILURE_BACKOFF_MS = 60_000L
    }

    // ============ 内部状态 ============

    private val mutex = Mutex()

    /** 上次成功定位 + 时间戳 */
    private var cacheEntry: CacheEntry? = null

    /** 上次失败的 elapsedRealtime()，0 表示无失败 */
    private var lastFailureTimeMs: Long = 0L

    /** 正在进行的请求 + 等待者列表 */
    private var pendingRequest: PendingRequest? = null

    // ============ 数据类 ============

    private data class CacheEntry(
        val location: LocationManager.CachedLocation,
        val timestampMs: Long,
        val highAccuracy: Boolean
    )

    private data class PendingRequest(
        val highAccuracy: Boolean,
        val deferreds: MutableList<CompletableDeferred<LocationManager.CachedLocation?>>,
        val startTimeMs: Long
    )

    // ============ 决策结果 ============

    private sealed class Decision {
        /** 缓存命中，直接返回 */
        data class Cached(val location: LocationManager.CachedLocation) : Decision()

        /** 合并到正在进行的请求，等待 deferred */
        data class Merged(val deferred: CompletableDeferred<LocationManager.CachedLocation?>) : Decision()

        /** 需要发起新的定位请求 */
        data class StartNew(val deferred: CompletableDeferred<LocationManager.CachedLocation?>) : Decision()

        /** 失败回退中，返回 null */
        data object RateLimited : Decision()
    }

    // ============ 公共 API ============

    /**
     * 请求定位。
     *
     * 三阶段门控：
     * 1. 快速路径（mutex 内）：检查缓存命中 / 失败回退
     * 2. 合并路径（mutex 内）：检查是否有 pending 请求可合并
     * 3. 执行路径（mutex 外）：发起实际 AMAP 调用，完成后通知所有等待者
     *
     * @param caller 调用者标识
     * @param highAccuracy 是否需要高精度定位
     * @return CachedLocation 或 null（定位失败 / 权限不足 / 回退中）
     */
    suspend fun requestLocation(
        caller: Caller,
        highAccuracy: Boolean = false
    ): LocationManager.CachedLocation? {
        val callerTag = caller.name
        locI("request_start: caller=$callerTag, highAccuracy=$highAccuracy")

        // Phase 1: 快速路径 — 缓存检查（mutex 内）
        val decision = mutex.withLock {
            // 1a. 检查时间窗口缓存
            cacheEntry?.let { entry ->
                val age = android.os.SystemClock.elapsedRealtime() - entry.timestampMs
                if (age < CACHE_TTL_MS) {
                    if (!highAccuracy || entry.highAccuracy) {
                        locI("cache_hit: caller=$callerTag, age=${age}ms, " +
                            "accuracy=${entry.location.accuracy}m, name=${entry.location.name}")
                        return@withLock Decision.Cached(entry.location)
                    }
                    locI("cache_upgrade_needed: caller=$callerTag, age=${age}ms, " +
                        "cachedHighAcc=${entry.highAccuracy}, requestedHighAcc=$highAccuracy")
                }
            }

            // 1b. 检查失败回退
            if (lastFailureTimeMs > 0L) {
                val failureAge = android.os.SystemClock.elapsedRealtime() - lastFailureTimeMs
                if (failureAge < FAILURE_BACKOFF_MS) {
                    locW("failure_backoff: caller=$callerTag, age=${failureAge}ms")
                    return@withLock Decision.RateLimited
                }
            }

            // 1c. 检查是否有 pending 请求可合并
            pendingRequest?.let { pending ->
                val mergedHighAccuracy = pending.highAccuracy || highAccuracy
                val deferred = CompletableDeferred<LocationManager.CachedLocation?>()
                if (mergedHighAccuracy != pending.highAccuracy) {
                    // 更新 pending 请求的精度
                    pendingRequest = pending.copy(highAccuracy = mergedHighAccuracy)
                }
                pending.deferreds.add(deferred)
                locI("merged_into_pending: caller=$callerTag, " +
                    "mergedHighAccuracy=$mergedHighAccuracy, waiters=${pending.deferreds.size}")
                return@withLock Decision.Merged(deferred)
            }

            // 1d. 没有可合并的请求，创建新的 pending 请求
            val deferred = CompletableDeferred<LocationManager.CachedLocation?>()
            pendingRequest = PendingRequest(
                highAccuracy = highAccuracy,
                deferreds = mutableListOf(deferred),
                startTimeMs = android.os.SystemClock.elapsedRealtime()
            )
            locI("new_request: caller=$callerTag, highAccuracy=$highAccuracy")
            return@withLock Decision.StartNew(deferred)
        }

        // Phase 2: 根据决策结果执行
        return when (decision) {
            is Decision.Cached -> {
                decision.location
            }

            is Decision.RateLimited -> {
                null
            }

            is Decision.Merged -> {
                // 等待发起者的定位结果
                decision.deferred.await()
            }

            is Decision.StartNew -> {
                // Phase 3: 执行实际的 AMAP 调用（mutex 外，使用 NonCancellable 隔离取消）
                executeRequest(highAccuracy)
            }
        }
    }

    // ============ 内部方法 ============

    /**
     * 执行实际的定位请求，并在完成后通知所有等待者。
     *
     * 使用 NonCancellable 确保：
     * - AMAP 调用不会因发起者协程取消而中断
     * - 所有等待者的 deferred 在 AMAP 完成后一定会被 complete
     */
    private suspend fun executeRequest(
        highAccuracy: Boolean
    ): LocationManager.CachedLocation? {
        return withContext(NonCancellable + Dispatchers.IO) {
            val result = locationManager.requestBestLocation(highAccuracy = highAccuracy)

            // 更新状态并通知所有等待者
            mutex.withLock {
                pendingRequest?.let { p ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (result != null) {
                        cacheEntry = CacheEntry(result, now, p.highAccuracy)
                        lastFailureTimeMs = 0L
                        locI("request_success: elapsed=${now - p.startTimeMs}ms, " +
                            "accuracy=${result.accuracy}m, name=${result.name}")
                    } else {
                        lastFailureTimeMs = now
                        locW("request_failed: elapsed=${now - p.startTimeMs}ms, " +
                            "backoff_set=${FAILURE_BACKOFF_MS}ms")
                    }
                    // 通知所有等待者
                    p.deferreds.forEach { it.complete(result) }
                    pendingRequest = null
                }
            }

            result
        }
    }

    // ============ 日志 ============

    private fun locI(message: String) = FileLogger.locI(TAG, message)
    private fun locW(message: String) = FileLogger.locW(TAG, message)
}