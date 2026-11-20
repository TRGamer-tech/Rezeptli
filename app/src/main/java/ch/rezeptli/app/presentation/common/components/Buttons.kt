package ch.rezeptli.app.presentation.common.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.Tokens

/*
 * Die Knoepfe der App: Material-Knoepfe, die unter dem Finger nachgeben.
 *
 * Sie nehmen dieselben Parameter wie ihre Material-Vorbilder, damit ein Bildschirm
 * nur den Namen tauschen muss. Alle Knoepfe sind Pillen - an beiden Enden rund, wie
 * ein Etikett am Marktstand.
 */

@Composable
fun BouncyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = Tokens.Radius.PillShape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.pressScale(interaction),
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interaction,
        content = content,
    )
}

@Composable
fun BouncyFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = Tokens.Radius.PillShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.pressScale(interaction),
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interaction,
        content = content,
    )
}

@Composable
fun BouncyOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = Tokens.Radius.PillShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.pressScale(interaction),
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interaction,
        content = content,
    )
}

@Composable
fun BouncyTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = Tokens.Radius.PillShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = modifier.pressScale(interaction),
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interaction,
        content = content,
    )
}

/**
 * Ein Symbolknopf, dessen Symbol lebt: Beim Druecken duckt es sich und dreht sich ein
 * wenig weg, beim Loslassen springt es mit Schwung zurueck.
 */
@Composable
fun BouncyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interaction,
    ) {
        LivelyIcon(interaction) { content() }
    }
}

@Composable
fun BouncyFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.pressScale(interaction),
        enabled = enabled,
        shape = shape,
        colors = colors,
        interactionSource = interaction,
    ) {
        LivelyIcon(interaction) { content() }
    }
}

@Composable
fun BouncyFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.pressScale(interaction),
        enabled = enabled,
        shape = shape,
        colors = colors,
        interactionSource = interaction,
    ) {
        LivelyIcon(interaction) { content() }
    }
}

/**
 * Bewegt ein Symbol mit dem Finger: duckt sich beim Druck, dreht sich beim Loslassen
 * einmal federnd zurueck.
 */
@Composable
private fun LivelyIcon(
    interaction: MutableInteractionSource,
    content: @Composable () -> Unit,
) {
    val motion = RezeptliTheme.motion
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(interaction, motion.reduced) {
        if (motion.reduced) return@LaunchedEffect
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> {
                    scale.animateTo(ICON_PRESSED_SCALE, motion.snappy())
                }

                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    rotation.snapTo(ICON_TWIST)
                    scale.animateTo(1f, motion.bouncy())
                    rotation.animateTo(0f, motion.bouncy())
                }
            }
        }
    }

    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            rotationZ = rotation.value
        },
    ) {
        content()
    }
}

private const val ICON_PRESSED_SCALE = 0.78f
private const val ICON_TWIST = -14f
