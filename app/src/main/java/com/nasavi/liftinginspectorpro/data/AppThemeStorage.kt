package com.nasavi.liftinginspectorpro.data

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.themedButtonBorder(): Modifier {
    val theme = AppThemeState.current
    return if (theme.buttonBorderWidth > 0)
        this.border(theme.buttonBorderWidth.dp, theme.buttonBorderColor, RoundedCornerShape(50))
    else this
}

@Composable
fun Modifier.themedTitleBorder(): Modifier {
    val theme = AppThemeState.current
    return if (theme.titleBorderWidth > 0)
        this.border(theme.titleBorderWidth.dp, theme.titleBorderColor)
    else this
}

data class AppTheme(
    val backgroundColor: Color = Color(0xFFE8F5E9),
    val borderColor: Color = Color(0xFF1B5E20),
    val titleColor: Color = Color(0xFF1B5E20),
    val buttonColor: Color = Color(0xFFA34840),
    val buttonBorderColor: Color = Color(0xFF1B5E20),
    val buttonBorderWidth: Int = 0,
    val titleBorderColor: Color = Color(0xFF1B5E20),
    val titleBorderWidth: Int = 0
)

object AppThemeState {
    var current by mutableStateOf(AppTheme())

    fun load(context: Context) {
        val p = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
        current = AppTheme(
            backgroundColor    = Color(p.getInt("bg",                android.graphics.Color.parseColor("#E8F5E9"))),
            borderColor        = Color(p.getInt("border",            android.graphics.Color.parseColor("#1B5E20"))),
            titleColor         = Color(p.getInt("title",             android.graphics.Color.parseColor("#1B5E20"))),
            buttonColor        = Color(p.getInt("button",            android.graphics.Color.parseColor("#A34840"))),
            buttonBorderColor  = Color(p.getInt("buttonBorderColor", android.graphics.Color.parseColor("#1B5E20"))),
            buttonBorderWidth  = p.getInt("buttonBorderWidth", 0),
            titleBorderColor   = Color(p.getInt("titleBorderColor",  android.graphics.Color.parseColor("#1B5E20"))),
            titleBorderWidth   = p.getInt("titleBorderWidth", 0)
        )
    }

    fun save(context: Context, theme: AppTheme) {
        current = theme
        context.getSharedPreferences("app_theme", Context.MODE_PRIVATE).edit()
            .putInt("bg",               theme.backgroundColor.toArgb())
            .putInt("border",           theme.borderColor.toArgb())
            .putInt("title",            theme.titleColor.toArgb())
            .putInt("button",           theme.buttonColor.toArgb())
            .putInt("buttonBorderColor",theme.buttonBorderColor.toArgb())
            .putInt("buttonBorderWidth",theme.buttonBorderWidth)
            .putInt("titleBorderColor", theme.titleBorderColor.toArgb())
            .putInt("titleBorderWidth", theme.titleBorderWidth)
            .apply()
    }
}

val themeBackgroundColors = listOf(
    Color(0xFFE8F5E9), Color(0xFFE3F2FD), Color(0xFFFFF8E1),
    Color(0xFFF3E5F5), Color(0xFFFBE9E7), Color(0xFFF1F8E9),
    Color(0xFFE0F7FA), Color(0xFFFCE4EC), Color(0xFFEFEBE9),
    Color(0xFFF5F5F5), Color(0xFFE8EAF6), Color(0xFFE0F2F1),
    Color(0xFFFFF3E0), Color(0xFFECEFF1), Color(0xFFFFFFFF),
    Color(0xFFEEEEEE), Color(0xFFF9FBE7), Color(0xFFE1F5FE),
    Color(0xFFFFF9C4), Color(0xFFDCEDC8)
)

val themeBorderColors = listOf(
    Color(0xFF1B5E20), Color(0xFF0D47A1), Color(0xFFBF360C),
    Color(0xFF4A148C), Color(0xFF827717), Color(0xFF006064),
    Color(0xFF880E4F), Color(0xFF3E2723), Color(0xFF212121),
    Color(0xFF37474F), Color(0xFF1A237E), Color(0xFF004D40),
    Color(0xFF33691E), Color(0xFF7B1FA2), Color(0xFFC62828),
    Color(0xFF283593), Color(0xFF4527A0), Color(0xFF00695C),
    Color(0xFF558B2F), Color(0xFFAD1457)
)

val themeButtonColors = listOf(
    Color(0xFFA34840), Color(0xFF1565C0), Color(0xFF2E7D32),
    Color(0xFF6A1B9A), Color(0xFFE65100), Color(0xFF558B2F),
    Color(0xFF00695C), Color(0xFFAD1457), Color(0xFF4527A0),
    Color(0xFF283593), Color(0xFF424242), Color(0xFF5D4037),
    Color(0xFF00838F), Color(0xFF546E7A), Color(0xFF7B1FA2),
    Color(0xFF0277BD), Color(0xFF37474F), Color(0xFF2E7D32),
    Color(0xFF795548), Color(0xFFF57F17)
)

