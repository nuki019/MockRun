package com.example.mockrun

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.ActivityCompat
import android.location.provider.ProviderProperties

/**
 * 封装 Android TestProvider 的创建、发送和销毁。
 * 使用前需确保已在"开发者选项"中选中本应用。
 */
class MockLocationProvider(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val providerName = LocationManager.GPS_PROVIDER
    private var isActive = false

    /**
     * 初始化 TestProvider；若已存在则先移除再添加。
     * @return true 表示初始化成功
     */
    fun setup(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "请先授予定位权限", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            // 若 provider 已存在，先移除（不存在时会抛异常，直接忽略）
            try {
                locationManager.removeTestProvider(providerName)
            } catch (_: Exception) { /* 忽略移除失败 */ }

            locationManager.addTestProvider(
                providerName,
                false, false, false, false, true, true, true,
                ProviderProperties.POWER_USAGE_LOW,
                ProviderProperties.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(providerName, true)
            isActive = true
            true
        } catch (e: SecurityException) {
            Toast.makeText(context, "⚠️ 必须在开发者选项中选中本应用！", Toast.LENGTH_LONG).show()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * 发送一条模拟位置；若权限校验失败会自动停止。
     */
    fun setLocation(lat: Double, lon: Double) {
        if (!isActive) return
        try {
            val mockLocation = Location(providerName).apply {
                latitude = lat
                longitude = lon
                altitude = 0.0
                time = System.currentTimeMillis()
                accuracy = 1.0f
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            locationManager.setTestProviderLocation(providerName, mockLocation)
        } catch (e: SecurityException) {
            isActive = false
            Toast.makeText(context, "权限校验失败！请在开发者选项中重新勾选本应用", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 移除 TestProvider 并重置状态。
     */
    fun stop() {
        if (!isActive) return
        try {
            locationManager.removeTestProvider(providerName)
        } catch (_: Exception) { /* 忽略 */ }
        isActive = false
    }
}
