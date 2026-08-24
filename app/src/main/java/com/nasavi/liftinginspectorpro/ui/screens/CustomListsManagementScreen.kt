package com.nasavi.liftinginspectorpro.ui.screens

import android.content.Context
import com.nasavi.liftinginspectorpro.data.themedButtonBorder
import com.nasavi.liftinginspectorpro.data.themedTitleBorder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * מסך ניהול רשימות בחירה אישיות.
 *
 * המטרה:
 * כאשר המשתמש בוחר "אחר" בטופס ומזין ערך חדש, הערך נשמר לרשימה.
 * במסך הזה אפשר לתקן ערכים שהוזנו בטעות, למחוק ערכים מיותרים ולסדר את הרשימות.
 *
 * חשוב:
 * עריכה/מחיקה כאן משנה רק את הרשימות לבחירות עתידיות.
 * תסקירים שכבר נשמרו או PDF שכבר הופקו לא משתנים.
 */
@Composable
fun CustomListsManagementScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("lifting_inspection_prefs", Context.MODE_PRIVATE)
    }

    val lists = remember {
        listOf(
            CustomListDefinition(
                title = "סוגי אביזרים נוספים",
                key = "custom_accessory_types",
                description = "סוגי אביזרים שהוספת דרך בחירת אחר במסך סוג אביזר."
            ),
            CustomListDefinition(
                title = "יצרני מענבי שרשרת",
                key = "custom_chain_manufacturers",
                description = "יצרנים שנוספו למענבי שרשרת דרך בחירת אחר."
            ),
            CustomListDefinition(
                title = "אביזרי קצה למענבי שרשרת",
                key = "custom_chain_end_options",
                description = "אביזרי קצה שנוספו לרשימת מענבי שרשרת."
            ),
            CustomListDefinition(
                title = "יצרני מענבי כבל פלדה",
                key = "custom_wire_manufacturers",
                description = "יצרנים שנוספו למענבי כבל פלדה דרך בחירת אחר."
            ),
            CustomListDefinition(
                title = "אביזרי קצה למענבי כבל פלדה",
                key = "custom_wire_extra_ends",
                description = "אביזרי קצה נוספים שנוספו לרשימת מענבי כבל פלדה."
            ),
            CustomListDefinition(
                title = "יצרני רצועות הרמה",
                key = "custom_textile_manufacturers",
                description = "יצרנים שנוספו לרצועות הרמה דרך בחירת אחר."
            ),
            CustomListDefinition(
                title = "יצרני אביזרי קצה",
                key = "custom_omega_manufacturers",
                description = "יצרנים שנוספו לאביזרי קצה דרך בחירת אחר."
            ),
            CustomListDefinition(
                title = "אביזרי קצה",
                key = "custom_end_accessory_options",
                description = "אביזרי קצה שנוספו דרך בחירת אחר ברשימת אביזרי קצה."
            )
        )
    }

    val theme = com.nasavi.liftinginspectorpro.data.AppThemeState.current
    val bgColor = theme.backgroundColor
    val borderColor = theme.borderColor
    val titleColor = theme.titleColor
    val buttonColor = theme.buttonColor

    var selectedList by remember { mutableStateOf<CustomListDefinition?>(null) }
    var editDialogOpen by remember { mutableStateOf(false) }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var selectedValue by remember { mutableStateOf("") }
    var editedValue by remember { mutableStateOf("") }
    var refreshCounter by remember { mutableStateOf(0) }

    fun loadValues(key: String): List<String> {
        // refreshCounter נמצא כאן כדי ש-Compose ירענן את המסך אחרי עריכה/מחיקה.
        @Suppress("UNUSED_VARIABLE")
        val refresh = refreshCounter

        return prefs.getStringSet(key, emptySet())
            ?.toList()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.sorted()
            ?: emptyList()
    }

    fun saveValues(key: String, values: List<String>) {
        val cleanValues = values
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .toSet()

        prefs.edit()
            .putStringSet(key, cleanValues)
            .apply()

        refreshCounter++
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .border(10.dp, borderColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp)
                .background(titleColor)
                .themedTitleBorder()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (selectedList == null) "ניהול רשימות בחירה" else selectedList!!.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        if (selectedList == null) {
            Text(
                text = "כאן אפשר לערוך או למחוק ערכים שהוספת דרך בחירת אחר. שינוי כאן משפיע רק על רשימות עתידיות ולא משנה תסקירים קיימים.",
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(lists.size) { index ->
                    val list = lists[index]
                    val count = loadValues(list.key).size

                    Button(
                        onClick = { selectedList = list },
                        modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                        colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
                    ) {
                        Text("${list.title} ($count)")
                    }
                }
            }

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text("חזרה לתפריט ראשי")
            }
        } else {
            val currentList = selectedList ?: return@Column
            val values = loadValues(currentList.key)

            Text(
                text = currentList.description,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            if (values.isEmpty()) {
                Text(
                    text = "עדיין לא נוספו ערכים אישיים לרשימה זו.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(values.size) { index ->
                    val value = values[index]
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.LightGray)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = value,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        selectedValue = value
                                        editedValue = value
                                        editDialogOpen = true
                                    },
                                    modifier = Modifier.weight(1f).themedButtonBorder(),
                                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
                                ) {
                                    Text("ערוך")
                                }

                                Button(
                                    onClick = {
                                        selectedValue = value
                                        deleteDialogOpen = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))
                                ) {
                                    Text("מחק")
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { selectedList = null },
                modifier = Modifier.fillMaxWidth().themedButtonBorder(),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text("חזרה לרשימות")
            }
        }
    }

    if (editDialogOpen) {
        val currentList = selectedList
        AlertDialog(
            onDismissRequest = { editDialogOpen = false },
            title = { Text("עריכת ערך") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("תקן את הערך כפי שתרצה שיופיע ברשימה הנפתחת:")
                    OutlinedTextField(
                        value = editedValue,
                        onValueChange = { editedValue = it },
                        label = { Text("ערך מתוקן") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (currentList != null) {
                            val newValue = editedValue.trim()
                            val oldValue = selectedValue.trim()
                            if (newValue.isNotBlank()) {
                                val updatedValues = loadValues(currentList.key)
                                    .map { if (it == oldValue) newValue else it }
                                saveValues(currentList.key, updatedValues)
                            }
                        }
                        editDialogOpen = false
                        selectedValue = ""
                        editedValue = ""
                    }
                ) {
                    Text("שמור")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editDialogOpen = false
                        selectedValue = ""
                        editedValue = ""
                    }
                ) {
                    Text("ביטול")
                }
            }
        )
    }

    if (deleteDialogOpen) {
        val currentList = selectedList
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text("מחיקת ערך") },
            text = {
                Text(
                    text = "האם למחוק את הערך \"$selectedValue\" מהרשימה?\n\nהמחיקה תשפיע רק על בחירות עתידיות ולא תשנה תסקירים שכבר נשמרו.",
                    textAlign = TextAlign.Right
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (currentList != null) {
                            val updatedValues = loadValues(currentList.key)
                                .filter { it != selectedValue }
                            saveValues(currentList.key, updatedValues)
                        }
                        deleteDialogOpen = false
                        selectedValue = ""
                    }
                ) {
                    Text("מחק")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteDialogOpen = false
                        selectedValue = ""
                    }
                ) {
                    Text("ביטול")
                }
            }
        )
    }
}

private data class CustomListDefinition(
    val title: String,
    val key: String,
    val description: String
)



