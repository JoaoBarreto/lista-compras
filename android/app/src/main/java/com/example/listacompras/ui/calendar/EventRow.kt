package com.example.listacompras.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.listacompras.R
import com.example.listacompras.domain.model.CalendarEvent
import com.example.listacompras.domain.model.EventType
import java.time.ZoneId

/** Colour used for a type; neutral when the type is not known locally yet or its colour does not parse. */
@Composable
fun typeColor(type: EventType?): Color {
    val neutral = MaterialTheme.colorScheme.outline
    return parseTypeColor(type?.color)?.let { adaptToTheme(it, isSystemInDarkTheme()) } ?: neutral
}

/** A colour swatch. It is decorative: the type name always appears next to it as text. */
@Composable
fun ColorSwatch(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(16.dp).width(16.dp).clip(RoundedCornerShape(4.dp)).background(color))
}

@Composable
fun EventRow(
    event: CalendarEvent,
    type: EventType?,
    zone: ZoneId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typeName = type?.name ?: stringResource(R.string.calendar_unknown_type)
    val when_ = if (event.allDay) {
        stringResource(R.string.calendar_all_day)
    } else {
        CalendarFormats.time(CalendarDates.timeOf(event, zone))
    }
    val description = stringResource(
        R.string.calendar_event_description,
        event.title,
        CalendarFormats.fullDate(CalendarDates.dateOf(event, zone)),
        when_,
        typeName,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .width(6.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(typeColor(type)),
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(event.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "$when_ · $typeName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
