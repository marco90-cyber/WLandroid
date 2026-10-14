package com.marco.wlandroid

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val SheetBg = Color(0xFF15171C)
private val SheetIcon = Color(0xFF8AB4F8)
private val SheetText = Color(0xFFE3E3E8)

private data class AddEntry(val id: String, val label: String, val icon: ImageVector)

private val ADD_ENTRIES = listOf(
    AddEntry("app", "Applicazione", Icons.Outlined.Android),
    AddEntry("link", "Collegamento", Icons.Outlined.Shortcut),
    AddEntry("action", "Azione del launcher", Icons.Outlined.Dashboard),
    AddEntry("widget", "Widget", Icons.Outlined.Widgets),
    AddEntry("divider", "Divisore", Icons.Outlined.UnfoldMore),
    AddEntry("cube", "Cubo", Icons.Outlined.ViewInAr),
    AddEntry("group", "Gruppo di tile", Icons.Outlined.GridView),
    AddEntry("drawer", "Cassetto app", Icons.Outlined.Apps),
    AddEntry("contact", "Contatti", Icons.Outlined.Contacts)
)

/** Pannello scuro con angoli arrotondati ancorato in basso, con il titolo in una "pillola" grigia. */
@Composable
private fun Sheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .padding(horizontal = 18.dp, vertical = 24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = SheetBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF40434B))
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            title,
                            color = Color(0xFFA8AAB4),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = SheetIcon, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(24.dp))
        Text(label, color = SheetText, fontSize = 22.sp)
    }
}

@Composable
fun AddMenuDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Sheet("AGGIUNGI", onDismiss) {
        ADD_ENTRIES.forEach { e -> SheetRow(e.icon, e.label) { onPick(e.id) } }
    }
}

@Composable
fun ActionPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Sheet("AZIONE DEL LAUNCHER", onDismiss) {
        LAUNCHER_ACTIONS.forEach { a -> SheetRow(a.icon, a.label) { onPick(a.id) } }
    }
}

/** Contenuto di un "Gruppo di tile": griglia di app da aprire. */
@Composable
fun GroupDialog(name: String, apps: List<AppInfo>, onOpen: (AppInfo) -> Unit, onDismiss: () -> Unit) {
    Sheet(name.uppercase().ifBlank { "GRUPPO" }, onDismiss) {
        Spacer(Modifier.height(8.dp))
        apps.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpen(app) }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(app.icon, app.label, Modifier.size(48.dp))
                        Text(
                            app.label,
                            color = SheetText,
                            fontSize = 12.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Nome") }
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

/** Selezione di più app (con nome opzionale), usata da Cubo e Gruppo di tile. */
@Composable
fun MultiAppPicker(
    title: String,
    apps: List<AppInfo>,
    askName: Boolean,
    minSelected: Int,
    onConfirm: (String, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val sel = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (askName) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("Nome") }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(apps, key = { it.key }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (app.key in sel) sel.remove(app.key) else sel.add(app.key)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = app.key in sel, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Image(app.icon, app.label, Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = sel.size >= minSelected && (!askName || name.isNotBlank()),
                onClick = { onConfirm(name.trim(), sel.toList()) }
            ) { Text("Aggiungi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
