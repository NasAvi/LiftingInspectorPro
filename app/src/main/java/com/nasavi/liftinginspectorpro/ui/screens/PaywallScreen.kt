package com.nasavi.liftinginspectorpro.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PaywallScreen(
    requiresInternet: Boolean = false,
    onViewSavedOnly: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = if (requiresInternet) "אין חיבור לאינטרנט" else "תקופת הניסיון הסתיימה",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (requiresInternet) {
                    "לא ניתן לאמת את הרישיון ללא חיבור לאינטרנט.\nניתן לצפות בתסקירים שמורים בלבד."
                } else {
                    "4 חודשי הניסיון המחינם הסתיימו.\nלהמשך שימוש מלא יש לרכוש מנוי.\n\nמחיר: 200 ₪ לחודש"
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!requiresInternet) {
                Button(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://wa.me/972507202846")
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("צור קשר לתשלום בוואטסאפ")
                }
            }

            OutlinedButton(
                onClick = onViewSavedOnly,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("צפה בתסקירים שמורים בלבד")
            }
        }
    }
}

