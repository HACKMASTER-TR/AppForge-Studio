package com.appforge.studio.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun AppForgeAgentVisualDesignerScreen(
    initialBlueprint: AppForgeAgentBlueprint,
    onBlueprintChange: (AppForgeAgentBlueprint) -> Unit,
    modifier: Modifier = Modifier
) {
    var history by remember(initialBlueprint) {
        mutableStateOf(AppForgeAgentVisualDesigner.create(initialBlueprint))
    }
    val state = history.current
    val blueprint = state.blueprint
    val selected = blueprint.screens.first { it.id == state.selectedScreenId }
    val preview = AppForgeAgentVisualDesigner.preview(history)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Visual Designer", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Blueprint tabanlı güvenli tasarım düzenleyici · rev ${state.revision}",
            style = MaterialTheme.typography.bodySmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    history = AppForgeAgentVisualDesigner.undo(history)
                    onBlueprintChange(history.current.blueprint)
                },
                enabled = history.undo.isNotEmpty()
            ) { Text("Geri al") }
            OutlinedButton(
                onClick = {
                    history = AppForgeAgentVisualDesigner.redo(history)
                    onBlueprintChange(history.current.blueprint)
                },
                enabled = history.redo.isNotEmpty()
            ) { Text("Yinele") }
        }

        Text("Ekranlar", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            blueprint.screens.forEach { screen ->
                Card(
                    modifier = Modifier.clickable {
                        history = AppForgeAgentVisualDesigner.selectScreen(history, screen.id)
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (screen.id == selected.id) {
                            safeColor(blueprint.tokens.primary)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(
                        screen.title.take(22),
                        modifier = Modifier.padding(10.dp),
                        color = if (screen.id == selected.id) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DesignerTokenEditor(
            tokens = blueprint.tokens,
            onUpdate = { tokens ->
                history = AppForgeAgentVisualDesigner.updateTokens(history, tokens)
                onBlueprintChange(history.current.blueprint)
            }
        )

        Text("Seçili ekran", style = MaterialTheme.typography.titleMedium)
        var titleDraft by remember(selected.id, selected.title) { mutableStateOf(selected.title) }
        var routeDraft by remember(selected.id, selected.route) { mutableStateOf(selected.route) }

        OutlinedTextField(
            value = titleDraft,
            onValueChange = { titleDraft = it.take(120) },
            label = { Text("Başlık") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = routeDraft,
            onValueChange = { routeDraft = it.take(80) },
            label = { Text("Route") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                history = AppForgeAgentVisualDesigner.updateScreenText(
                    history,
                    screenId = selected.id,
                    title = titleDraft
                )
                history = AppForgeAgentVisualDesigner.renameRoute(
                    history,
                    screenId = selected.id,
                    newRoute = routeDraft
                )
                onBlueprintChange(history.current.blueprint)
            }
        ) { Text("Ekranı uygula") }

        Text("Önizleme", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppForgeAgentPreviewDevice.entries.forEach { device ->
                OutlinedButton(
                    onClick = { history = AppForgeAgentVisualDesigner.setDevice(history, device) }
                ) { Text(device.title) }
            }
        }

        DesignerPreviewCard(
            preview = preview,
            tokens = blueprint.tokens
        )
    }
}

@Composable
private fun DesignerTokenEditor(
    tokens: AppForgeAgentDesignTokens,
    onUpdate: (AppForgeAgentDesignTokens) -> Unit
) {
    var primary by remember(tokens.primary) { mutableStateOf(tokens.primary) }
    var secondary by remember(tokens.secondary) { mutableStateOf(tokens.secondary) }
    var background by remember(tokens.background) { mutableStateOf(tokens.background) }

    Text("Design Tokens", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = primary,
        onValueChange = { primary = it.take(9) },
        label = { Text("Primary #RRGGBB") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = secondary,
        onValueChange = { secondary = it.take(9) },
        label = { Text("Secondary #RRGGBB") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = background,
        onValueChange = { background = it.take(9) },
        label = { Text("Background #RRGGBB") },
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = {
            onUpdate(
                tokens.copy(
                    primary = primary.trim(),
                    secondary = secondary.trim(),
                    background = background.trim()
                )
            )
        }
    ) { Text("Tokenları uygula") }
}

@Composable
private fun DesignerPreviewCard(
    preview: AppForgeAgentScreenPreview,
    tokens: AppForgeAgentDesignTokens
) {
    val scale = when (preview.device) {
        AppForgeAgentPreviewDevice.PHONE -> 0.82f
        AppForgeAgentPreviewDevice.TABLET -> 0.58f
        AppForgeAgentPreviewDevice.DESKTOP -> 0.32f
    }
    val width = (preview.device.widthDp * scale).dp
    val minHeight = (preview.device.heightDp * scale).dp.coerceAtMost(620.dp)

    Box(
        modifier = Modifier
            .width(width)
            .height(minHeight)
            .background(
                safeColor(tokens.background),
                RoundedCornerShape(tokens.cornerRadiusDp.dp)
            )
            .border(
                1.dp,
                safeColor(tokens.primary),
                RoundedCornerShape(tokens.cornerRadiusDp.dp)
            )
            .padding((tokens.spacingUnitDp * 2).dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(tokens.spacingUnitDp.dp)
        ) {
            Text(preview.title, style = MaterialTheme.typography.titleLarge)
            Text(preview.route, style = MaterialTheme.typography.labelSmall)
            preview.nodes.drop(1).forEach { node ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(tokens.spacingUnitDp.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(node.kind, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(8.dp))
                        Text(node.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun safeColor(raw: String): Color {
    val value = raw.removePrefix("#")
    val argb = when (value.length) {
        6 -> ("FF$value").toLongOrNull(16)
        8 -> value.toLongOrNull(16)
        else -> null
    } ?: return Color.Gray
    return Color(argb)
}
