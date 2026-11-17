package space.ghstmail.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import space.ghstmail.android.R
import space.ghstmail.android.ui.theme.GhstColors

enum class GhstButtonStyle { Primary, Secondary, Burn }

@Composable
fun GhostMark(modifier: Modifier = Modifier, size: Dp = 26.dp, tint: Color = GhstColors.Amber) {
    Icon(
        painter = painterResource(R.drawable.ic_ghost),
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint,
    )
}

@Composable
fun GhstTopBar(modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Column(modifier = modifier.background(GhstColors.Background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .height(56.dp)
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostMark()
            Spacer(Modifier.width(10.dp))
            Icon(
                painter = painterResource(R.drawable.ic_wordmark),
                contentDescription = stringResource(R.string.app_name),
                tint = GhstColors.Text,
            )
            Spacer(Modifier.weight(1f))
            actions()
        }
        HorizontalDivider(color = GhstColors.Border)
    }
}

/** The bar of a screen opened from the home screen: back, then where you are. */
@Composable
fun GhstBackBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(GhstColors.Background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .height(56.dp)
                // The arrow's ink starts 16dp inside its button: this puts it on the 20dp gutter.
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.nav_back),
                    tint = GhstColors.Text,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = GhstColors.Text,
            )
        }
        HorizontalDivider(color = GhstColors.Border)
    }
}

@Composable
fun GhstCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = GhstColors.Card,
        contentColor = GhstColors.Text,
        border = BorderStroke(1.dp, GhstColors.Border),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun GhstButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GhstButtonStyle = GhstButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    icon: Painter? = null,
    tintIcon: Boolean = true,
) {
    val container = when (style) {
        GhstButtonStyle.Primary -> GhstColors.Amber
        GhstButtonStyle.Secondary -> GhstColors.Card
        GhstButtonStyle.Burn -> GhstColors.Flame
    }
    val content = when (style) {
        GhstButtonStyle.Primary -> GhstColors.OnAmber
        GhstButtonStyle.Secondary -> GhstColors.Text
        GhstButtonStyle.Burn -> GhstColors.OnFlame
    }
    val bordered = style == GhstButtonStyle.Secondary
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.defaultMinSize(minHeight = if (compact) 40.dp else 48.dp),
        shape = RoundedCornerShape(if (compact) 10.dp else 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = if (loading) container else container.copy(alpha = 0.45f),
            disabledContentColor = if (loading) content else content.copy(alpha = 0.6f),
        ),
        border = if (bordered) BorderStroke(1.dp, GhstColors.Border) else null,
        contentPadding = PaddingValues(horizontal = if (compact) 16.dp else 18.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = content,
            )
        } else {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (tintIcon) LocalContentColor.current else Color.Unspecified,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (bordered) FontWeight.Medium else FontWeight.SemiBold
                ),
                maxLines = 1,
            )
        }
    }
}

/** A row of mutually exclusive choices in one bordered box. */
@Composable
fun <T> GhstSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = GhstColors.Card,
        border = BorderStroke(1.dp, GhstColors.Border),
    ) {
        Row(modifier = Modifier.padding(3.dp).selectableGroup()) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .then(if (fill) Modifier.weight(1f) else Modifier)
                        .height(40.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSelected) GhstColors.Raised else Color.Transparent)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(value) },
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) GhstColors.Text else GhstColors.TextMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Something went wrong, said where the thing that failed is. */
@Composable
fun GhstErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = GhstColors.Burn.copy(alpha = 0.1f).compositeOver(GhstColors.Background),
        contentColor = GhstColors.Burn,
        border = BorderStroke(1.dp, GhstColors.Burn.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = if (action != null) 8.dp else 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            )
            action?.invoke()
        }
    }
}

@Composable
fun GhstTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = GhstColors.Text,
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            isError = isError,
            singleLine = true,
            textStyle = textStyle,
            placeholder = {
                Text(text = placeholder, style = textStyle, color = GhstColors.TextDim)
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = GhstColors.Text,
                unfocusedTextColor = GhstColors.Text,
                disabledTextColor = GhstColors.TextMuted,
                focusedContainerColor = GhstColors.Inset,
                unfocusedContainerColor = GhstColors.Inset,
                disabledContainerColor = GhstColors.Inset,
                errorContainerColor = GhstColors.Inset,
                focusedBorderColor = GhstColors.Ring,
                unfocusedBorderColor = GhstColors.Border,
                disabledBorderColor = GhstColors.Border,
                errorBorderColor = GhstColors.Burn,
                cursorColor = GhstColors.Amber,
                errorCursorColor = GhstColors.Burn,
                focusedLeadingIconColor = GhstColors.TextMuted,
                unfocusedLeadingIconColor = GhstColors.TextMuted,
                focusedTrailingIconColor = GhstColors.TextMuted,
                unfocusedTrailingIconColor = GhstColors.TextMuted,
            ),
        )
    }
}

/** A question with Cancel on the left and the answer on the right. */
@Composable
fun GhstDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.headlineSmall) },
        text = { Column { body() } },
        confirmButton = confirm,
        dismissButton = {
            GhstButton(
                text = stringResource(R.string.home_cancel),
                onClick = onDismiss,
                style = GhstButtonStyle.Secondary,
                compact = true,
            )
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = GhstColors.Card,
        titleContentColor = GhstColors.Text,
        textContentColor = GhstColors.TextMuted,
    )
}

@Composable
fun DashedDivider(modifier: Modifier = Modifier, color: Color = GhstColors.Border) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }
    )
}
