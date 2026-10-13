package com.aakash.novafork.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aakash.novafork.ui.theme.LocalNovaPalette
import com.aakash.novafork.ui.theme.NovaColors
import com.aakash.novafork.ui.theme.NovaRadius
import com.aakash.novafork.ui.theme.NovaType

/*
 * iOS text input. There is no outlined Material field anywhere: forms are inset-grouped rows
 * (ListTextFieldRow), single fields in alerts and pages are filled rounded boxes (IosTextField),
 * and Search uses SearchField (Controls.kt).
 */

/** Keyboard options for a password field: no capitalisation, password keyboard, caller's IME action. */
private fun secureOptions(base: KeyboardOptions): KeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    keyboardType = KeyboardType.Password,
    imeAction = base.imeAction,
)

/** Requests focus once the field is attached, when [autoFocus] is set. */
@Composable
private fun AutoFocus(autoFocus: Boolean, focusRequester: FocusRequester) {
    if (!autoFocus) return
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            // Not attached yet (e.g. while a sheet animates in); the user can tap the field.
        }
    }
}

/**
 * The iOS rounded text field: a filled box with 10dp corners (44dp tall; pass height = 36.dp in an
 * IosAlert), Body text, a tertiary placeholder, a blue cursor and a clear button while it is
 * focused and has text. [secure] hides the text (passwords). Multi-line when [singleLine] is false
 * (then [height] is the minimum). Inside an InsetGroup use ListTextFieldRow instead.
 */
@Composable
fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    secure: Boolean = false,
    singleLine: Boolean = true,
    height: Dp = 44.dp,
    containerColor: Color = NovaColors.fill,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester = remember { FocusRequester() },
    autoFocus: Boolean = false,
) {
    val palette = LocalNovaPalette.current
    var focused by remember { mutableStateOf(false) }
    val clearGlyph = if (palette.dark) Color.Black else Color.White
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        textStyle = NovaType.body.copy(color = palette.label),
        singleLine = singleLine,
        cursorBrush = SolidColor(palette.tint),
        visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (secure) secureOptions(keyboardOptions) else keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (singleLine) Modifier.height(height) else Modifier.heightIn(min = height))
                    .clip(RoundedCornerShape(NovaRadius.Field))
                    .background(containerColor)
                    .padding(
                        start = 12.dp,
                        end = 4.dp,
                        top = if (singleLine) 0.dp else 11.dp,
                        bottom = if (singleLine) 0.dp else 11.dp,
                    ),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = NovaType.body,
                            color = palette.tertiaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (focused && enabled && value.isNotEmpty()) {
                    FieldClearButton(onClick = { onValueChange("") }, glyphColor = clearGlyph)
                } else {
                    Spacer(Modifier.width(8.dp))
                }
            }
        },
    )
    AutoFocus(autoFocus, focusRequester)
}

/**
 * A text field as an iOS form row inside an InsetGroup ("Server  nas.local", "Password  ••••"):
 * optional [label] (Body, [labelWidth] wide) and the field filling the rest, with a tertiary
 * placeholder and a clear button while editing. Tapping anywhere on the row focuses the field.
 * 44dp min height; the hairline under it is inset to the text, like ListRow.
 */
@Composable
fun ListTextFieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    enabled: Boolean = true,
    secure: Boolean = false,
    labelWidth: Dp = 110.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester = remember { FocusRequester() },
    autoFocus: Boolean = false,
    showDivider: Boolean = true,
) {
    val palette = LocalNovaPalette.current
    var focused by remember { mutableStateOf(false) }
    val padding = InsetGroupDefaults.RowPadding
    Box(
        modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) {
                try {
                    focusRequester.requestFocus()
                } catch (e: IllegalStateException) {
                    // The field is not attached; nothing to focus.
                }
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = InsetGroupDefaults.RowMinHeight)
                .alpha(if (enabled) 1f else 0.4f)
                .padding(start = padding, end = if (focused && value.isNotEmpty()) 6.dp else padding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = NovaType.body,
                    color = palette.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .width(labelWidth)
                        .padding(end = 8.dp),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 11.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused },
                enabled = enabled,
                textStyle = NovaType.body.copy(color = palette.label),
                singleLine = true,
                cursorBrush = SolidColor(palette.tint),
                visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = if (secure) secureOptions(keyboardOptions) else keyboardOptions,
                keyboardActions = keyboardActions,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = NovaType.body,
                                color = palette.tertiaryLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            if (focused && enabled && value.isNotEmpty()) {
                FieldClearButton(onClick = { onValueChange("") }, glyphColor = groupedCellColor())
            }
        }
        if (showDivider) {
            Hairline(
                Modifier.align(Alignment.BottomStart),
                startInset = padding,
            )
        }
    }
    AutoFocus(autoFocus, focusRequester)
}
