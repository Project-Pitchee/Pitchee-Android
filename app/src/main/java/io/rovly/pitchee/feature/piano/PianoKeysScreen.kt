package io.rovly.pitchee.feature.piano

import io.rovly.pitchee.ui.theme.PitcheeColorTokens
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.R
import io.rovly.pitchee.ui.common.ScreenTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PianoKeysScreen(onBack: () -> Unit) {
    val engine = remember { PianoSoundEngine() }
    var activeNotes by remember { mutableStateOf(emptySet<Int>()) }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { engine.prepare() }
    }
    DisposableEffect(engine) {
        onDispose {
            engine.stopAll()
            engine.close()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 132.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ScreenTitle(
                    title = stringResource(R.string.piano_title),
                    subtitle = stringResource(R.string.piano_subtitle),
                    onBack = onBack,
                )
                Spacer(Modifier.height(8.dp))
            }
            items(
                items = PianoNote.allNotes,
                key = { it.midi },
            ) { note ->
                PianoNoteButton(
                    note = note,
                    active = note.midi in activeNotes,
                    onPressChanged = { pressed ->
                        if (pressed) {
                            engine.noteOn(note)
                            activeNotes = activeNotes + note.midi
                        } else {
                            engine.noteOff(note)
                            activeNotes = activeNotes - note.midi
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PianoNoteButton(
    note: PianoNote,
    active: Boolean,
    onPressChanged: (Boolean) -> Unit,
) {
    val tint = when (note.midi) {
        in 42..49 -> PitcheeColorTokens.Masculine
        in 54..61 -> PitcheeColorTokens.Feminine
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val scale by animateFloatAsState(
        targetValue = if (active) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 520f),
        label = "piano-note-scale",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .scale(scale)
            .semantics {
                role = Role.Button
                contentDescription = note.displayName
                onClick(label = note.displayName) {
                    onPressChanged(true)
                    onPressChanged(false)
                    true
                }
            }
            .pointerInput(note.midi) {
                detectTapGestures(
                    onPress = {
                        onPressChanged(true)
                        tryAwaitRelease()
                        onPressChanged(false)
                    },
                )
            },
        shape = MaterialTheme.shapes.large,
        color = tint.copy(alpha = if (active) 0.30f else 0.15f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = note.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "%.1f Hz".format(note.frequency),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
