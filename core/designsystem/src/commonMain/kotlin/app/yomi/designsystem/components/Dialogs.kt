package app.yomi.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import app.yomi.designsystem.i18n.LocalStrings
import app.yomi.designsystem.theme.YomiTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.number

/** Yes/no confirmation with an optional destructive tone. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String? = null,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(
                    text = confirmLabel ?: strings.confirm,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        },
        shape = MaterialTheme.shapes.large
    )
}

/** A single free-text question. */
@Composable
fun TextPromptDialog(
    title: String,
    label: String,
    initial: String = "",
    supporting: String? = null,
    confirmLabel: String? = null,
    allowEmpty: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = YomiTheme.spacing.small)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = allowEmpty || text.isNotBlank(),
                onClick = { onConfirm(text.trim()); onDismiss() }
            ) { Text(confirmLabel ?: strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
        shape = MaterialTheme.shapes.large
    )
}

/** Material's clock picker, wired to `kotlinx.datetime`. */
@Composable
fun TimePickerDialog(
    title: String,
    initial: LocalTime?,
    use24h: Boolean,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val state = rememberTimePickerState(
        initialHour = initial?.hour ?: DEFAULT_HOUR,
        initialMinute = initial?.minute ?: 0,
        is24Hour = use24h
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime(state.hour, state.minute))
                onDismiss()
            }) { Text(strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
        shape = MaterialTheme.shapes.large,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(YomiTheme.spacing.xlarge)
    )
}

/** Material's calendar, wired to `kotlinx.datetime`. */
@Composable
fun DatePickerDialog(
    title: String,
    initial: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let { it.toEpochMillis() }
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(state = state, title = null, headline = null, showModeToggle = false)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(it.toLocalDate()) }
                onDismiss()
            }) { Text(strings.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
        shape = MaterialTheme.shapes.large,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(YomiTheme.spacing.large)
    )
}

/** A dialog that hosts arbitrary content plus the standard button row. */
@Composable
fun YomiDialog(
    title: String,
    confirmLabel: String? = null,
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScopeAlias.() -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(YomiTheme.spacing.medium),
                content = content
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(YomiTheme.spacing.small)) {
                TextButton(onClick = onDismiss) { Text(strings.cancel) }
                Button(enabled = confirmEnabled, onClick = { onConfirm(); onDismiss() }) {
                    Text(confirmLabel ?: strings.save)
                }
            }
        },
        shape = MaterialTheme.shapes.large,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(YomiTheme.spacing.xlarge)
    )
}

// Material's date picker speaks epoch milliseconds; these two keep that
// conversion in exactly one place.
private const val MILLIS_PER_DAY = 86_400_000L
private const val DEFAULT_HOUR = 8

private fun LocalDate.toEpochMillis(): Long = toEpochDays() * MILLIS_PER_DAY

private fun Long.toLocalDate(): LocalDate =
    LocalDate.fromEpochDays(floorDiv(MILLIS_PER_DAY))

/** Human-readable month label for calendar headers. */
fun LocalDate.monthIndex(): Int = month.number - 1
