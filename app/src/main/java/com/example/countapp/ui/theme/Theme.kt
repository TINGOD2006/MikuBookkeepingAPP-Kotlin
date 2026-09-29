package com.example.countapp.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 全 App 的色彩。
 *
 * 與 Flutter 版 `lib/constants/app_colors.dart` 完全一致，
 * 維持原本「黑底、白卡、藍色主色」的視覺。
 */
object MikuColors {
    val Primary = Color(0xFF007AF4)
    val Secondary = Color(0xFF05A9EF)
    val Background = Color(0xFF000000)
    val Text = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB4B4B4)

    /** 卡片是白底（內容文字用深色）。 */
    val Card = Color(0xFFFFFFFF)
    val OnCard = Color(0xFF1A1A1A)
    val OnCardSecondary = Color(0xFF6B6B6B)

    val Expense = Color(0xFFE53935)
    val Income = Color(0xFF43A047)

    /** 深色容器（輸入區、設定卡片）。 */
    val Surface = Color(0xFF1C1C1E)
    val SurfaceVariant = Color(0xFF2C2C2E)
}

/** 把 0xAARRGGBB 轉成 Compose 的 [Color]。 */
fun Long.toComposeColor(): Color = Color(this.toInt())
