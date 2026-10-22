package com.fittrace.core.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 所有表单、搜索与备注共用；键盘、校验及只读行为由业务提供。 */
@Composable
fun FitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f),
                focusedContainerColor =
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f),
                unfocusedContainerColor =
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f),
                disabledContainerColor =
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = .15f),
            ),
    )
}

/** 数字步进是输入框的紧凑变体，共用圆角、主题、焦点和无障碍约定。 */
@Composable
fun FitStepperField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDecrease,
                enabled = enabled,
                modifier =
                    Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
                        contentDescription = decreaseLabel
                    },
            ) {
                Text("−", fontSize = 24.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier =
                    Modifier.weight(1f).padding(vertical = 8.dp).semantics {
                        contentDescription = label
                    },
                enabled = enabled,
                singleLine = true,
                textStyle =
                    LocalTextStyle.current.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            TextButton(
                onClick = onIncrease,
                enabled = enabled,
                modifier =
                    Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
                        contentDescription = increaseLabel
                    },
            ) {
                Text("+", fontSize = 24.sp)
            }
        }
    }
}
