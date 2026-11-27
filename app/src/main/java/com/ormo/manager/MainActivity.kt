package com.ormo.manager

import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ormo.manager.auth.FakeAuthGateway
import com.ormo.manager.domain.ORMO_BUSINESS_INFO
import com.ormo.manager.network.connectivityFlow
import com.ormo.manager.ui.AuthScreen
import com.ormo.manager.ui.AuthStep
import com.ormo.manager.ui.LoginViewModel
import com.ormo.manager.ui.ManagerScreen
import com.ormo.manager.ui.OrderViewModel
import com.ormo.manager.ui.PinViewModel
import com.ormo.manager.ui.SettingsViewModel
import com.ormo.manager.ui.theme.OrmoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 라이트 테마 전용: 기기가 다크 모드여도 상태바 아이콘을 어둡게 둔다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        hideSystemBars(window)
        // 새 주문을 놓치지 않도록 화면 자동 잠금을 막는다.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 태블릿의 음량 버튼이 알림음(미디어) 음량을 조절하게 한다.
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent {
            OrmoTheme {
                // 백엔드를 붙일 때 FakeAuthGateway를 실제 구현으로 바꾼다.
                val loginViewModel: LoginViewModel = viewModel { LoginViewModel(FakeAuthGateway()) }
                val auth by loginViewModel.uiState.collectAsStateWithLifecycle()
                if (auth.step != AuthStep.SIGNED_IN) {
                    AuthScreen(viewModel = loginViewModel, businessInfo = ORMO_BUSINESS_INFO)
                } else {
                    // 주문 데이터는 로그인한 뒤에 만든다(자동 취소 시간이 로그인 화면에서 흐르지 않게).
                    val viewModel: OrderViewModel = viewModel(factory = OrderViewModel.Factory)
                    val pinViewModel: PinViewModel = viewModel { PinViewModel() }
                    val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel() }
                    val connectivity = remember { connectivityFlow(applicationContext) }
                    LaunchedEffect(viewModel) { connectivity.collect(viewModel::onConnectionChange) }
                    ManagerScreen(
                        viewModel = viewModel,
                        pinViewModel = pinViewModel,
                        settingsViewModel = settingsViewModel,
                        businessInfo = ORMO_BUSINESS_INFO,
                        onLogout = loginViewModel::logout,
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars(window)
    }
}

/** Figma처럼 상단 바가 화면 맨 위에 오도록 시스템 바를 숨긴다. 화면 가장자리를 밀면 잠시 나타난다. */
fun hideSystemBars(window: Window) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}
