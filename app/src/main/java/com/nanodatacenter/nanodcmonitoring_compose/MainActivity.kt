package com.nanodatacenter.nanodcmonitoring_compose

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.nanodatacenter.nanodcmonitoring_compose.data.DeviceType
import com.nanodatacenter.nanodcmonitoring_compose.data.DataCenterType
import com.nanodatacenter.nanodcmonitoring_compose.config.DeviceConfigurationManager
import com.nanodatacenter.nanodcmonitoring_compose.manager.ImageOrderManager
import com.nanodatacenter.nanodcmonitoring_compose.repository.NanoDcRepository
import com.nanodatacenter.nanodcmonitoring_compose.ui.component.DataCenterMonitoringScreen
import com.nanodatacenter.nanodcmonitoring_compose.ui.theme.DataCenterTheme
import com.nanodatacenter.nanodcmonitoring_compose.util.ImageConfigurationHelper
import com.nanodatacenter.nanodcmonitoring_compose.util.ImageScaleUtil
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private val repository = NanoDcRepository.getInstance()
    private lateinit var deviceConfigManager: DeviceConfigurationManager
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 기기 설정 매니저 초기화
        deviceConfigManager = DeviceConfigurationManager.getInstance(this)
        
        // 전체화면 모드 설정
        setupFullScreenMode()
        
        // 저장된 데이터센터 설정 로드 및 API 시작
        initializeDataCenter()
        
        enableEdgeToEdge()
        
        setContent {
            DataCenterTheme {
                MonitoringApp(
                    onDataCenterChanged = { dataCenter ->
                        handleDataCenterChange(dataCenter)
                    }
                )
            }
        }
    }
    
    /**
     * 저장된 데이터센터 설정을 로드하고 API 연결을 시작합니다
     */
    private fun initializeDataCenter() {
        val selectedDataCenter = deviceConfigManager.getSelectedDataCenter()
        Log.d(TAG, "🏢 Initializing with data center: ${selectedDataCenter.displayName} (${selectedDataCenter.nanoDcId})")
        
        // API 연결 테스트 및 자동 갱신 시작
        testApiConnection(selectedDataCenter.nanoDcId)
        startAutoDataRefresh(selectedDataCenter.nanoDcId)
    }
    
    /**
     * 데이터센터 변경 처리
     */
    private fun handleDataCenterChange(dataCenter: DataCenterType) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "🔄 Changing data center to: ${dataCenter.displayName}")
                
                // 기존 자동 갱신 중지
                repository.stopAutoRefresh()
                
                // 코루틴이 완전히 정리될 시간을 줌 (취소 처리 완료 대기)
                kotlinx.coroutines.delay(100)
                
                // 새 데이터센터 설정 저장
                deviceConfigManager.setSelectedDataCenter(dataCenter)
                
                // 새 데이터센터로 API 연결 테스트 및 자동 갱신 시작
                testApiConnection(dataCenter.nanoDcId)
                startAutoDataRefresh(dataCenter.nanoDcId)
                
                Log.d(TAG, "✅ Data center changed successfully to: ${dataCenter.displayName}")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to change data center: ${e.message}", e)
            }
        }
    }
    
    /**
     * 현재 선택된 데이터센터 반환
     */
    fun getCurrentDataCenter(): DataCenterType {
        return deviceConfigManager.getSelectedDataCenter()
    }
    
    /**
     * API 연결 테스트 실행
     */
    private fun testApiConnection(nanoDcId: String = getCurrentDataCenter().nanoDcId) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "🚀 Starting API connection test for: $nanoDcId")
                repository.testApiConnection(nanoDcId)
            } catch (e: Exception) {
                Log.e(TAG, "❌ API connection test failed with exception: ${e.message}", e)
            }
        }
    }
    
    /**
     * 자동 데이터 갱신 시작
     * 20초마다 백그라운드에서 데이터를 갱신합니다
     */
    private fun startAutoDataRefresh(nanoDcId: String = getCurrentDataCenter().nanoDcId) {
        Log.d(TAG, "🔄 Starting automatic data refresh for: $nanoDcId")
        repository.startAutoRefresh(nanoDcId)
    }
    
    /**
     * 전체화면 모드 설정 (상태바, 네비게이션바 숨기기)
     */
    private fun setupFullScreenMode() {
        // 시스템 UI와 앱 콘텐츠가 겹치도록 설정
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 화면을 항상 켜져있도록 설정 (모니터링 앱이므로)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // 시스템 UI 컨트롤러 가져오기
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        
        // 시스템 바들을 숨기고 몰입형 모드 설정
        windowInsetsController.apply {
            // 상태바와 네비게이션바 숨기기
            hide(WindowInsetsCompat.Type.systemBars())
            
            // 시스템 바가 자동으로 나타나지 않도록 설정
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        // 추가적인 몰입형 모드 설정 (API 30 이하 호환성)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        
        // 포커스를 다시 얻었을 때 전체화면 모드 재설정
        if (hasFocus) {
            setupFullScreenMode()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Repository 정리 (자동 갱신 중지 및 리소스 해제)
        repository.cleanup()
        Log.d(TAG, "MainActivity destroyed, resources cleaned up")
    }
}

@Composable
fun MonitoringApp(onDataCenterChanged: (DataCenterType) -> Unit) {
    // 앱 시작 시 설정 초기화
    LaunchedEffect(Unit) {
        ImageConfigurationHelper.applyAllConfigurations()
        ImageOrderManager.getInstance().setCurrentDeviceType(DeviceType.DEFAULT)
    }
    
    // 전체 화면을 어두운 배경으로 설정하고 이미지들을 간격 없이 연속적으로 표시
    DataCenterMonitoringScreen(
        deviceType = DeviceType.DEFAULT,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        scaleMode = ImageScaleUtil.ScaleMode.FIT_WIDTH,
        useOriginalSize = true, // 원본 크기로 표시하며 margin 적용
        onDataCenterChanged = onDataCenterChanged
    )
}

@Preview(showBackground = true, name = "데이터센터 모니터링")
@Composable
fun MonitoringAppPreview() {
    DataCenterTheme {
        MonitoringApp(onDataCenterChanged = { /* No-op for preview */ })
    }
}
