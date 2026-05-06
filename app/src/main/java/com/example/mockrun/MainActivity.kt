package com.example.mockrun

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

enum class MockState { IDLE, TELEPORTING, RUNNING }

class MainActivity : AppCompatActivity() {

    // ── UI 控件 ──────────────────────────────────────────────────
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

    // ── 业务组件 ─────────────────────────────────────────────────
    private val parser = LocationParser()
    private lateinit var mockProvider: MockLocationProvider
    private val simulator = RunningSimulator()

    // ── 状态 ─────────────────────────────────────────────────────
    private var currentState = MockState.IDLE
    private var parsedPoints = listOf<Pair<Double, Double>>()

    // ── 定时任务（500ms 刷新一次位置） ────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private val locationTask = object : Runnable {
        override fun run() {
            when (currentState) {
                MockState.TELEPORTING -> {
                    if (parsedPoints.isNotEmpty()) {
                        mockProvider.setLocation(parsedPoints[0].first, parsedPoints[0].second)
                        tvCurrentSpeed.text = "状态: 定点传送中"
                    }
                }
                MockState.RUNNING -> {
                    if (simulator.speedMs <= 0) {
                        tvCurrentSpeed.text = "状态: 待机 (请调节速度)"
                    } else {
                        val (lat, lon) = simulator.step(0.5)
                        mockProvider.setLocation(lat, lon)
                        tvCurrentSpeed.text = "状态: 奔向点 ${simulator.targetIndex + 1}"
                    }
                }
                MockState.IDLE -> return
            }
            handler.postDelayed(this, 500)
        }
    }

    // ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mockProvider = MockLocationProvider(this)
        initViews()
        requestLocationPermissionIfNeeded()
        setupSpeedSeekBar()
        setupTeleportButton()
        setupStartRunButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(locationTask)
        mockProvider.stop()
    }

    // ── 初始化 & 权限 ────────────────────────────────────────────

    private fun initViews() {
        etPoint1 = findViewById(R.id.etPoint1)
        etPoint2 = findViewById(R.id.etPoint2)
        etPoint3 = findViewById(R.id.etPoint3)
        etPoint4 = findViewById(R.id.etPoint4)
        btnGetLocation1 = findViewById(R.id.btnGetLocation1)
        btnGetLocation2 = findViewById(R.id.btnGetLocation2)
        btnGetLocation3 = findViewById(R.id.btnGetLocation3)
        btnGetLocation4 = findViewById(R.id.btnGetLocation4)
        btnTeleport = findViewById(R.id.btnTeleport)
        btnStartRun = findViewById(R.id.btnStartRun)
        speedSeekBar = findViewById(R.id.speedSeekBar)
        tvCurrentSpeed = findViewById(R.id.tvCurrentSpeed)

        btnGetLocation1.setOnClickListener { fetchRealLocationForEditText(etPoint1) }
        btnGetLocation2.setOnClickListener { fetchRealLocationForEditText(etPoint2) }
        btnGetLocation3.setOnClickListener { fetchRealLocationForEditText(etPoint3) }
        btnGetLocation4.setOnClickListener { fetchRealLocationForEditText(etPoint4) }
    }

    private fun requestLocationPermissionIfNeeded() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                100
            )
        }
    }

    // ── 速度条 ───────────────────────────────────────────────────

    private fun setupSpeedSeekBar() {
        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                simulator.speedMs = (progress / 100.0) * 8.0 // 最大 8 m/s
                val pace = if (simulator.speedMs > 0) (16.67 / simulator.speedMs).toInt() else 0
                findViewById<TextView>(R.id.tvSpeedLabel).text =
                    String.format("配速调节: %.1f m/s (约 %d min/km)", simulator.speedMs, pace)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // ── 传送按钮 ─────────────────────────────────────────────────

    private fun setupTeleportButton() {
        btnTeleport.setOnClickListener {
            if (currentState != MockState.TELEPORTING) {
                parsedPoints = parser.parse(
                    etPoint1.text.toString(),
                    etPoint2.text.toString(),
                    etPoint3.text.toString(),
                    etPoint4.text.toString()
                )
                if (parsedPoints.isEmpty()) {
                    Toast.makeText(this, "请输入至少一个有效坐标", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!mockProvider.setup()) return@setOnClickListener

                currentState = MockState.TELEPORTING
                updateButtonUI()
                startLocationLoop()
            } else {
                stopAllMocking()
            }
        }
    }

    // ── 跑步按钮 ─────────────────────────────────────────────────

    private fun setupStartRunButton() {
        btnStartRun.setOnClickListener {
            if (currentState != MockState.RUNNING) {
                parsedPoints = parser.parse(
                    etPoint1.text.toString(),
                    etPoint2.text.toString(),
                    etPoint3.text.toString(),
                    etPoint4.text.toString()
                )
                if (!simulator.reset(parsedPoints)) {
                    Toast.makeText(this, "跑步至少需要 2 个有效坐标", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (simulator.speedMs <= 0) {
                    Toast.makeText(this, "请先调节速度！", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!mockProvider.setup()) return@setOnClickListener

                currentState = MockState.RUNNING
                updateButtonUI()
                startLocationLoop()
            } else {
                stopAllMocking()
            }
        }
    }

    // ── 状态管理 ─────────────────────────────────────────────────

    private fun startLocationLoop() {
        handler.removeCallbacks(locationTask)
        handler.post(locationTask)
    }

    private fun stopAllMocking() {
        currentState = MockState.IDLE
        handler.removeCallbacks(locationTask)
        mockProvider.stop()
        updateButtonUI()
    }

    private fun updateButtonUI() {
        when (currentState) {
            MockState.TELEPORTING -> {
                btnTeleport.text = "停止传送"
                btnTeleport.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
                btnStartRun.text = "开始跑步"
                btnStartRun.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#388E3C"))
            }
            MockState.RUNNING -> {
                btnStartRun.text = "停止跑步"
                btnStartRun.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
                btnTeleport.text = "回到点1"
                btnTeleport.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1976D2"))
            }
            MockState.IDLE -> {
                btnTeleport.text = "传送至点1"
                btnTeleport.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1976D2"))
                btnStartRun.text = "开始跑步"
                btnStartRun.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#388E3C"))
                tvCurrentSpeed.text = "状态: 停止"
            }
        }
    }

    // ── 获取真实定位 ─────────────────────────────────────────────

    private fun fetchRealLocationForEditText(targetEditText: EditText) {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "请先授予手机定位权限", Toast.LENGTH_SHORT).show()
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var provider = LocationManager.GPS_PROVIDER
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            provider = LocationManager.NETWORK_PROVIDER
        }

        // 若未在模拟中，先尝试缓存位置（快速）
        val lastLocation = locationManager.getLastKnownLocation(provider)
        if (lastLocation != null && currentState == MockState.IDLE) {
            targetEditText.setText(
                String.format("%.6f, %.6f", lastLocation.latitude, lastLocation.longitude)
            )
            Toast.makeText(this, "已获取当前位置", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "正在请求GPS卫星定位，请在室外或窗边等待...", Toast.LENGTH_SHORT).show()

        val locationListener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                targetEditText.setText(
                    String.format("%.6f, %.6f", location.latitude, location.longitude)
                )
                Toast.makeText(this@MainActivity, "定位成功！", Toast.LENGTH_SHORT).show()
                locationManager.removeUpdates(this)
            }

            @Deprecated("兼容低版本 Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        locationManager.requestLocationUpdates(provider, 0L, 0f, locationListener)
    }
}
