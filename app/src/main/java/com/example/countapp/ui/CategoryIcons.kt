package com.example.countapp.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmokeFree
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 把 [com.example.countapp.domain.CategoryItem.iconKey] 轉成實際圖示。
 *
 * 用字串而不是直接存 ImageVector，是為了讓 domain 層保持與 Android 無關
 * （可純 JVM 單元測試），同時也讓資料移轉可以把 Flutter 的 iconName 直接沿用。
 * 未知的 key 一律退回 [Icons.Filled.Label]，不會崩潰。
 */
object CategoryIcons {

    private val icons: Map<String, ImageVector> = mapOf(
        // 支出分類
        "school" to Icons.Filled.School,
        "shopping_bag" to Icons.Filled.ShoppingBag,
        "restaurant" to Icons.Filled.Restaurant,
        "phone_android" to Icons.Filled.PhoneAndroid,
        "movie" to Icons.Filled.Movie,
        "menu_book" to Icons.Filled.MenuBook,
        "spa" to Icons.Filled.Spa,
        "fitness_center" to Icons.Filled.FitnessCenter,
        "people" to Icons.Filled.People,
        "directions_car" to Icons.Filled.DirectionsCar,
        "checkroom" to Icons.Filled.Checkroom,
        "car_repair" to Icons.Filled.CarRepair,
        "local_bar" to Icons.Filled.LocalBar,
        "smoke_free" to Icons.Filled.SmokeFree,
        "computer" to Icons.Filled.Computer,
        "flight_takeoff" to Icons.Filled.FlightTakeoff,
        "local_hospital" to Icons.Filled.LocalHospital,
        "pets" to Icons.Filled.Pets,
        "build" to Icons.Filled.Build,
        "home" to Icons.Filled.Home,
        "chair" to Icons.Filled.Chair,
        "card_giftcard" to Icons.Filled.CardGiftcard,
        "volunteer_activism" to Icons.Filled.Favorite,
        "confirmation_number" to Icons.Filled.ConfirmationNumber,
        "icecream" to Icons.Filled.Icecream,
        "child_care" to Icons.Filled.ChildCare,
        "agriculture" to Icons.Filled.Agriculture,
        "swap_horiz" to Icons.Filled.SwapHoriz,

        // 收入分類
        "payments" to Icons.Filled.Payments,
        "emoji_events" to Icons.Filled.EmojiEvents,
        "health_and_safety" to Icons.Filled.HealthAndSafety,
        "trending_up" to Icons.Filled.TrendingUp,
        "home_work" to Icons.Filled.HomeWork,
        "work_outline" to Icons.Filled.WorkOutline,
        "edit_note" to Icons.Filled.EditNote,
        "account_balance" to Icons.Filled.AccountBalance,
        "savings" to Icons.Filled.Savings,
        "receipt_long" to Icons.Filled.ReceiptLong,
        "assignment" to Icons.Filled.Assignment,
        "assignment_icon" to Icons.Filled.Assignment,
        "sell" to Icons.Filled.Sell,
        "favorite" to Icons.Filled.Favorite,
        "more_horiz" to Icons.Filled.MoreHoriz,

        // 新增自訂分類時可選
        "label" to Icons.Filled.Label,
        "star" to Icons.Filled.Star,
        "bolt" to Icons.Filled.Bolt,
        "water_drop" to Icons.Filled.WaterDrop,
        "cloud" to Icons.Filled.Cloud,
        "wb_sunny" to Icons.Filled.WbSunny,
        "nightlight" to Icons.Filled.Nightlight,
        "music_note" to Icons.Filled.MusicNote,
        "book" to Icons.Filled.Book,
        "work" to Icons.Filled.Work,
        "shopping_cart" to Icons.Filled.ShoppingCart,
        "flight" to Icons.Filled.Flight,
        "sports_esports" to Icons.Filled.SportsEsports,
        "local_cafe" to Icons.Filled.LocalCafe,
        "category" to Icons.Filled.Category,
    )

    fun forKey(key: String): ImageVector = icons[key] ?: Icons.Filled.Label
}
