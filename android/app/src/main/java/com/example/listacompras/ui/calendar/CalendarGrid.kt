package com.example.listacompras.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.listacompras.R
import com.example.listacompras.domain.model.CalendarEvent
import com.example.listacompras.domain.model.EventType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

private const val MAX_DOTS = 3

/** Weekday names are cut to three letters (Seg, Ter, Qua…) so the seven columns never crowd. */
private const val WEEKDAY_LETTERS = 3

/**
 * The month as a grid of days, Monday first. A day shows its number and up to three dots in the
 * colours of its events' types; the dots only hint, the exact list is the agenda below, and
 * TalkBack reads the date and the number of events of each day.
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    typesById: Map<String, EventType>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read from the configuration so the header follows a language change.
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            CalendarDates.weekdays().forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.FULL, locale).take(WEEKDAY_LETTERS).replaceFirstChar { it.titlecase(locale) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
            }
        }
        CalendarDates.monthCells(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    if (date == null) {
                        Box(Modifier.weight(1f).height(CELL_HEIGHT))
                    } else {
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date == selected,
                            events = eventsByDay[date].orEmpty(),
                            typesById = typesById,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private val CELL_HEIGHT = 48.dp

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<CalendarEvent>,
    typesById: Map<String, EventType>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fullDate = CalendarFormats.fullDate(date)
    val withCount = if (events.isEmpty()) fullDate else pluralStringResource(R.plurals.calendar_day_events, events.size, fullDate, events.size)
    val description = if (isToday) stringResource(R.string.calendar_day_today, withCount) else withCount
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(CELL_HEIGHT)
            .padding(1.dp)
            .clip(shape)
            .background(if (isSelected) colors.primaryContainer else colors.surface)
            .let { if (isToday) it.border(2.dp, colors.primary, shape) else it }
            .selectable(selected = isSelected, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(8.dp).padding(top = 2.dp)) {
            events.take(MAX_DOTS).forEach { event ->
                Box(Modifier.size(6.dp).clip(CircleShape).background(typeColor(typesById[event.typeId])))
            }
        }
    }
}
