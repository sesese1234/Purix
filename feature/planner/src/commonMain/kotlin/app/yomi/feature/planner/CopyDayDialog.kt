package app.yomi.feature.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.yomi.designsystem.components.SettingRow
import app.yomi.designsystem.components.SoftDivider
import app.yomi.designsystem.components.YomiChip
import app.yomi.designsystem.components.YomiDialog
import app.yomi.designsystem.format.Fmt
import app.yomi.designsystem.i18n.LocalStrings
import app.yomi.designsystem.theme.YomiTheme
import app.yomi.domain.plan.CopyMode
import app.yomi.domain.plan.CopyOptions
import app.yomi.model.weekdaysStartingFrom
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** How the user chose to describe the target dates. */
sealed interface CopyTarget {
    data class NextDays(val count: Int) : CopyTarget
    data class Weekdays(val days: Set<DayOfWeek>, val weeksAhead: Int) : CopyTarget
    data class Tomorrow(val date: LocalDate) : CopyTarget
}

/**
 * The copy-a-day sheet.
 *
 * Copying a routine to "every Sunday and Tuesday for the next four weeks" is
 * the single most-used bulk action in a planner, so it gets a first-class
 * dialog rather than being buried behind repeated manual edits.
 */
@Composable
fun CopyDayDialog(
    sourceDate: LocalDate,
    firstDayOfWeek: DayOfWeek,
    onConfirm: (CopyTarget, CopyOptions) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val spacing = YomiTheme.spacing

    var mode by remember { mutableStateOf(CopyMode.Merge) }
    var selectedWeekdays by remember { mutableStateOf(emptySet<DayOfWeek>()) }
    var nextDays by remember { mutableStateOf(0) }
    var weeksAhead by remember { mutableStateOf(DEFAULT_WEEKS) }
    var includeAdHoc by remember { mutableStateOf(true) }
    var includeRecurring by remember { mutableStateOf(true) }
    var includeUnfinished by remember { mutableStateOf(true) }
    var resetProgress by remember { mutableStateOf(true) }
    var includeCheckIn by remember { mutableStateOf(false) }
    var includeNote by remember { mutableStateOf(false) }

    val target: CopyTarget? = when {
        selectedWeekdays.isNotEmpty() -> CopyTarget.Weekdays(selectedWeekdays, weeksAhead)
        nextDays > 0 -> CopyTarget.NextDays(nextDays)
        else -> null
    }

    YomiDialog(
        title = "${strings.copyDay} · ${Fmt.dateWithWeekday(sourceDate, strings)}",
        confirmLabel = strings.copy,
        confirmEnabled = target != null,
        onConfirm = {
            target?.let { onConfirm(it, CopyOptions(
                mode = mode,
                includeAdHoc = includeAdHoc,
                includeRecurring = includeRecurring,
                includeUnfinished = includeUnfinished,
                resetProgress = resetProgress,
                includeCheckInTargets = includeCheckIn,
                includeNote = includeNote
            )) }
        },
        onDismiss = onDismiss
    ) {
        Text(strings.copyTo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            QUICK_DAY_COUNTS.forEach { count ->
                YomiChip(
                    label = strings.copyNextDays.replace("%1\$s", count.toString()),
                    selected = nextDays == count && selectedWeekdays.isEmpty(),
                    onClick = {
                        nextDays = if (nextDays == count) 0 else count
                        selectedWeekdays = emptySet()
                    }
                )
            }
        }

        Spacer(Modifier.height(spacing.tiny))
        Text(strings.copyWeekdays, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.tiny)) {
            weekdaysStartingFrom(firstDayOfWeek).forEach { day ->
                YomiChip(
                    label = Fmt.weekdayShort(day, strings),
                    selected = day in selectedWeekdays,
                    onClick = {
                        selectedWeekdays = if (day in selectedWeekdays) {
                            selectedWeekdays - day
                        } else {
                            nextDays = 0
                            selectedWeekdays + day
                        }
                    }
                )
            }
        }
        if (selectedWeekdays.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                WEEK_SPANS.forEach { weeks ->
                    YomiChip(
                        label = "$weeks × ${strings.week}",
                        selected = weeksAhead == weeks,
                        onClick = { weeksAhead = weeks }
                    )
                }
            }
        }

        SoftDivider()

        Text(strings.copyMode, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(spacing.tiny)) {
            listOf(
                CopyMode.Merge to strings.copyModeMerge,
                CopyMode.Replace to strings.copyModeReplace,
                CopyMode.Append to strings.copyModeAppend
            ).forEach { (value, label) ->
                YomiChip(
                    label = label,
                    selected = mode == value,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { mode = value }
                )
            }
        }

        SoftDivider()

        SwitchRow(strings.copyIncludeRecurring, includeRecurring) { includeRecurring = it }
        SwitchRow(strings.copyIncludeAdHoc, includeAdHoc) { includeAdHoc = it }
        SwitchRow(strings.copyIncludeUnfinished, includeUnfinished) { includeUnfinished = it }
        SwitchRow(strings.copyResetProgress, resetProgress) { resetProgress = it }
        SwitchRow(strings.copyIncludeCheckIn, includeCheckIn) { includeCheckIn = it }
        SwitchRow(strings.copyIncludeNote, includeNote) { includeNote = it }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(
        title = label,
        trailing = { Switch(checked = checked, onCheckedChange = onChange) },
        onClick = { onChange(!checked) }
    )
}

private val QUICK_DAY_COUNTS = listOf(1, 3, 7, 14)
private val WEEK_SPANS = listOf(1, 2, 4, 8)
private const val DEFAULT_WEEKS = 4
