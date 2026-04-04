package com.example.mockrun // 注意：这行必须是你自己的包名！

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlin.math.sqrt
import android.content.res.ColorStateList
import android.location.provider.ProviderProperties

enum class MockState { IDLE, TELEPORTING, RUNNING }
class MainActivity : AppCompatActivity() {

    private lateinit var locationManager: LocationManager
    private val providerName = LocationManager.GPS_PROVIDER

    private lateinit var etPoint1: EditText
    private lateinit var etPoint2: EditText
    private lateinit var etPoint3: EditText
    private lateinit var etPoint4: EditText
    private lateinit var btnGetLocation1: Button
    private lateinit var btnGetLocation2: Button
    private lateinit var btnGetLocation3: Button
    private lateinit var btnGetLocation4: Button
    private lateinit var btnTeleport: Button
    private lateinit var btnStartRun: Button
    private lateinit var speedSeekBar: SeekBar
    private lateinit var tvCurrentSpeed: TextView

    private var currentSpeedMs = 0.0 // 速度：米/秒
    private var isRunning = false
    private var currentPointIndex = 0

    // 使用 Handler 配合 Runnable 来实现循环定时任务
    private val handler = Handler(Looper.getMainLooper())
    private val runTask = object : Runnable {
        override fun run() {
            if (isRunning && currentSpeedMs > 0) {
                simulateRunningStep()
            }
            handler.postDelayed(this, 1000) // 每1秒更新一次位置
        }
    }
    private val locationTask = object : Runnable {
        override fun run() {
            when (currentState) {
                MockState.TELEPORTING -> {
                    if (points.isNotEmpty()) {
                        setMockLocation(points[0].first, points[0].second)
                        tvCurrentSpeed.text = "状态: 定点传送中"
                    }
                }
                MockState.RUNNING -> {
                    if (currentSpeedMs <= 0) {
                        tvCurrentSpeed.text = "状态: 待机 (请调节速度)"
                    } else {
                        simulateRunningStep()
                        // 实时显示当前跑向第几个点，让你知道它在动
                        tvCurrentSpeed.text = "状态: 奔向点 ${currentPointIndex + 1}"
                    }
                }
                MockState.IDLE -> return
            }
            // 每 500ms 刷新一次，确保位置极其稳定
            handler.postDelayed(this, 500)
        }
    }

    // 存储解析后的坐标
    private val points = mutableListOf<Pair<Double, Double>>()
    private var currentLat = 0.0
    private var currentLon = 0.0
    private var currentState = MockState.IDLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. 初始化 (保持不变)
        initViews()

        // 2. 速度条监听 (增加初始速度提醒)
        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // 将最高速度限制在 10m/s (约 36km/h)，步道乐跑通常要求在 2-5m/s 之间
                currentSpeedMs = (progress / 100.0) * 8.0
                val pace = if (currentSpeedMs > 0) (16.67 / currentSpeedMs).toInt() else 0
                findViewById<TextView>(R.id.tvSpeedLabel).text =
                    String.format("配速调节: %.1f m/s (约 %d min/km)", currentSpeedMs, pace)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 3. 传送按钮 (优化状态切换)
        btnTeleport.setOnClickListener {
            if (currentState != MockState.TELEPORTING) {
                parseCoordinates()
                if (points.isEmpty()) return@setOnClickListener

                setupMockProvider()
                currentState = MockState.TELEPORTING

                // UI 更新：传送按钮变红表示“正在生效”，跑步按钮保持可用
                updateButtonUI()

                // 确保循环任务在运行
                handler.removeCallbacks(locationTask)
                handler.post(locationTask)
            } else {
                stopAllMocking()
            }
        }

        // 4. 跑步按钮 (关键：重置索引和更新 UI)
        btnStartRun.setOnClickListener {
            if (currentState != MockState.RUNNING) {
                parseCoordinates()
                if (points.isEmpty()) return@setOnClickListener
                if (currentSpeedMs <= 0) {
                    Toast.makeText(this, "请先调节速度！", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                setupMockProvider()
                currentState = MockState.RUNNING

                // 重置跑步起点
                currentPointIndex = 0
                currentLat = points[0].first
                currentLon = points[0].second

                // UI 更新
                updateButtonUI()

                // 确保循环任务在运行
                handler.removeCallbacks(locationTask)
                handler.post(locationTask)
            } else {
                stopAllMocking()
            }
        }
    }
    private fun initViews() {
        etPoint1 = findViewById(R.id.etPoint1); etPoint2 = findViewById(R.id.etPoint2)
        etPoint3 = findViewById(R.id.etPoint3); etPoint4 = findViewById(R.id.etPoint4)
        btnGetLocation1 = findViewById(R.id.btnGetLocation1); btnGetLocation2 = findViewById(R.id.btnGetLocation2)
        btnGetLocation3 = findViewById(R.id.btnGetLocation3); btnGetLocation4 = findViewById(R.id.btnGetLocation4)
        btnTeleport = findViewById(R.id.btnTeleport); btnStartRun = findViewById(R.id.btnStartRun)
        speedSeekBar = findViewById(R.id.speedSeekBar); tvCurrentSpeed = findViewById(R.id.tvCurrentSpeed)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private fun stopAllMocking() {
        currentState = MockState.IDLE
        handler.removeCallbacks(locationTask)
        updateButtonUI()
    }
    private fun updateButtonUI() {
        when (currentState) {
            MockState.TELEPORTING -> {
                // 传送中：传送按钮变红（显示停止），跑步按钮恢复正常（显示开始）
                btnTeleport.text = "停止传送"
                btnTeleport.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))

                btnStartRun.text = "开始跑步"
                btnStartRun.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#388E3C"))
            }
            MockState.RUNNING -> {
                // 跑步中：跑步按钮变红（显示停止），传送按钮恢复正常
                btnStartRun.text = "停止跑步"
                btnStartRun.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))

                btnTeleport.text = "回到点1" // 也可以保持“传送至点1”
                btnTeleport.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1976D2"))
            }
            MockState.IDLE -> {
                // 闲置：全部恢复初始颜色
                btnTeleport.text = "传送至点1"
                btnTeleport.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1976D2"))

                btnStartRun.text = "开始跑步"
                btnStartRun.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#388E3C"))

                tvCurrentSpeed.text = "状态: 停止"
            }
        }
    }
    // 解析输入框的坐标
    private fun parseCoordinates() {
        points.clear()
        val inputs = listOf(etPoint1.text.toString(), etPoint2.text.toString(), etPoint3.text.toString(), etPoint4.text.toString())
        for (input in inputs) {
            val parts = input.split(",")
            if (parts.size == 2) {
                points.add(Pair(parts[0].trim().toDouble(), parts[1].trim().toDouble()))
            }
        }
    }

    // 配置模拟位置服务
    private fun setupMockProvider() {
        try {
            locationManager.addTestProvider(
                providerName,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                ProviderProperties.POWER_USAGE_LOW, // 修改这里：功耗低
                ProviderProperties.ACCURACY_FINE    // 修改这里：精度高
            )
            locationManager.setTestProviderEnabled(providerName, true)
        } catch (e: IllegalArgumentException) {
            // Provider 已经存在，忽略
        }
    }

    // 发送模拟定位
    private fun setMockLocation(lat: Double, lon: Double) {
        val mockLocation = Location(providerName)
        mockLocation.latitude = lat
        mockLocation.longitude = lon
        mockLocation.altitude = 0.0
        mockLocation.time = System.currentTimeMillis()
        mockLocation.accuracy = 1.0f
        mockLocation.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

        locationManager.setTestProviderLocation(providerName, mockLocation)
    }

    // 简易的跑步逻辑：计算朝向下一个目标点的向量并移动
    private fun simulateRunningStep() {
        if (points.size < 2) return

        val targetPoint = points[(currentPointIndex + 1) % points.size]

        // 计算当前位置到目标点的距离
        val latDiff = (targetPoint.first - currentLat) * 111000
        val lonDiff = (targetPoint.second - currentLon) * 78000
        val distance = sqrt(latDiff * latDiff + lonDiff * lonDiff)

        // 因为 locationTask 是每 500ms (0.5秒) 执行一次
        // 所以实际移动距离应该是速度的一半
        val stepDistance = currentSpeedMs * 0.5

        if (distance <= stepDistance) {
            // 到达目标点，切到下一段
            currentLat = targetPoint.first
            currentLon = targetPoint.second
            currentPointIndex = (currentPointIndex + 1) % points.size
        } else {
            // 按比例移动
            val ratio = stepDistance / distance
            currentLat += (targetPoint.first - currentLat) * ratio
            currentLon += (targetPoint.second - currentLon) * ratio
        }

        setMockLocation(currentLat, currentLon)
    }
    // 获取真实的物理定位，并填入对应的 EditText
    private fun fetchRealLocationForEditText(targetEditText: EditText) {
        // 检查是否开启了定位权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "请先授予手机定位权限", Toast.LENGTH_SHORT).show()
            return
        }

        // 优先尝试获取网络定位（速度较快），其次尝试GPS定位
        var provider = LocationManager.GPS_PROVIDER
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            provider = LocationManager.NETWORK_PROVIDER
        }

        // 1. 先尝试获取手机上一次的缓存位置（瞬间完成）
        val lastLocation = locationManager.getLastKnownLocation(provider)
        if (lastLocation != null && !isRunning) { // 注意：如果正在模拟跑步，这里的缓存位置就是假位置了！
            val lat = String.format("%.6f", lastLocation.latitude)
            val lon = String.format("%.6f", lastLocation.longitude)
            targetEditText.setText("$lat, $lon")
            Toast.makeText(this, "已获取当前位置", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "正在请求GPS卫星定位，请在室外或窗边等待...", Toast.LENGTH_SHORT).show()

        // 2. 如果没有缓存，或者要求最精确，则向硬件请求一次实时更新
        val locationListener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                val lat = String.format("%.6f", location.latitude)
                val lon = String.format("%.6f", location.longitude)
                targetEditText.setText("$lat, $lon")
                Toast.makeText(this@MainActivity, "定位成功！", Toast.LENGTH_SHORT).show()
                // 获取到一次就立刻注销监听器，省电
                locationManager.removeUpdates(this)
            }
            // 兼容低版本Android所需的空实现
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // 发起单次定位请求
        locationManager.requestLocationUpdates(provider, 0L, 0f, locationListener)
    }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runTask)
        try {
            locationManager.removeTestProvider(providerName)
        } catch (e: Exception) {
            // 忽略
        }
    }
}