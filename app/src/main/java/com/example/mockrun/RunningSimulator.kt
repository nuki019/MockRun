package com.example.mockrun

import kotlin.math.sqrt

/**
 * 模拟沿多个点位循环跑步的逻辑。
 *
 * 使用方式：
 * 1. 调用 [reset] 设置点位和起点
 * 2. 周期性调用 [step]，每次返回新的经纬度
 */
class RunningSimulator {

    private val points = mutableListOf<Pair<Double, Double>>()
    private var currentLat = 0.0
    private var currentLon = 0.0
    private var currentPointIndex = 0
    var speedMs = 0.0  // 速度，单位：米/秒

    /**
     * 重置模拟状态，以第一个点为起点。
     * @param waypoints 至少需要 2 个点才能开始跑步
     * @return true 表示重置成功（点位数量足够）
     */
    fun reset(waypoints: List<Pair<Double, Double>>): Boolean {
        if (waypoints.size < 2) return false
        points.clear()
        points.addAll(waypoints)
        currentPointIndex = 0
        currentLat = points[0].first
        currentLon = points[0].second
        return true
    }

    /**
     * 当前正在奔向的目标点序号（从 0 开始）。
     */
    val targetIndex: Int
        get() = (currentPointIndex + 1) % points.size

    /**
     * 执行一步模拟，返回更新后的位置。
     * @param intervalSeconds 距上次 step 的时间间隔（秒），用于计算位移
     * @return (纬度, 经度)
     */
    fun step(intervalSeconds: Double): Pair<Double, Double> {
        if (points.size < 2 || speedMs <= 0) return currentLat to currentLon

        val target = points[targetIndex]

        // 简化的平面距离计算（经/纬度 → 米，适用于校园级别精度）
        val latDiff = (target.first - currentLat) * 111_000
        val lonDiff = (target.second - currentLon) * 78_000
        val distance = sqrt(latDiff * latDiff + lonDiff * lonDiff)

        val stepDistance = speedMs * intervalSeconds

        if (distance <= stepDistance) {
            // 到达当前目标点，切换到下一段
            currentLat = target.first
            currentLon = target.second
            currentPointIndex = targetIndex
        } else {
            // 按比例向目标点移动
            val ratio = stepDistance / distance
            currentLat += (target.first - currentLat) * ratio
            currentLon += (target.second - currentLon) * ratio
        }

        return currentLat to currentLon
    }
}
