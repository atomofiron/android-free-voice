package app.atomofiron.freevoice

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.media.MediaRecorder.AudioEncoder
import android.media.MediaRecorder.OutputFormat
import android.net.Uri.*
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.atomofiron.freevoice.ui.theme.FreeVoiceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val FILE_NAME = "voicemessage"

class MainActivity : ComponentActivity() {

    private val recorder by lazy { if (SDK_INT >= S) MediaRecorder(this) else MediaRecorder() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.Main) {
            enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(0x01808080, 0x01808080))
        }

        setContent {
            FreeVoiceTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Recorder(this@MainActivity, recorder)
                        }
                    }
                }
            }
        }
    }
}

enum class Format(val value: Int, val ext: String) {
    ThreeGP(OutputFormat.THREE_GPP, "3gp"),
    Mpeg(OutputFormat.MPEG_4, "m4a"),
    ;
    val title = ext
}

enum class Encoder(val value: Int, val title: String) {
    Aac(AudioEncoder.AAC, "AAC"),
    HeAac(AudioEncoder.HE_AAC, "HE-AAC"),
    AacEld(AudioEncoder.AAC_ELD, "AAC-ELD"),
    AmrNB(AudioEncoder.AMR_NB, "AMR-NB"),
    AmrWB(AudioEncoder.AMR_WB, "AMR-WB"),
    Vorbis(AudioEncoder.VORBIS, "Vorbis"),
}

fun Context.filePath(ext: String) = "${filesDir.absolutePath}/$FILE_NAME.$ext"

@Composable
fun ColumnScope.Recorder(context: Context, recorder: MediaRecorder) {
    val interactionSource = remember { MutableInteractionSource() }
    var recording by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var encoder by remember { mutableStateOf(Encoder.AmrNB) }
    var format by remember { mutableStateOf(Format.Mpeg) }
    var allowed by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            recording = interaction is PressInteraction.Press
            allowed = interaction !is PressInteraction.Press
            when (interaction) {
                is PressInteraction.Press -> recorder.record(context.filePath(format.ext), encoder, format)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> recorder.stop()
            }
        }
    }
    if (settings) LazyVerticalStaggeredGrid(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        columns = StaggeredGridCells.Adaptive(100.dp),
    ) {
        items(Encoder.entries) {
            Cell(it.title, it == encoder) { encoder = it }
        }
        items(Format.entries) {
            Cell(it.title, it == format) { format = it }
        }
    }
    SomeIcon(icon = R.drawable.ic_lock)
    Row(verticalAlignment = Alignment.CenterVertically) {
        SomeIcon(icon = R.drawable.ic_trash)
        FilledIconButton(
            modifier = Modifier.padding(16.dp).size(56.dp),
            colors = if (recording) IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer) else IconButtonDefaults.filledIconButtonColors(),
            onClick = { },
        ) {
            Icon(
                painterResource(R.drawable.ic_mic),
                modifier = Modifier.clickable(interactionSource, indication = null, onClick = { }),
                contentDescription = null,
            )
        }
        SomeIconButton(R.drawable.ic_send, enabled = allowed, onClick = { context.send(format) })
    }
    SomeIconButton(R.drawable.ic_settings, onClick = { settings = !settings })
}

@Composable
fun SomeIcon(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
) {
    Icon(
        painterResource(icon),
        modifier = modifier
            .size(56.dp)
            .padding(16.dp)
            .alpha(0.3f),
        contentDescription = null,
    )
}

@Composable
fun SomeIconButton(@DrawableRes icon: Int, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(modifier = Modifier.size(56.dp), enabled = enabled, onClick = onClick) {
        Icon(painterResource(icon), contentDescription = null)
    }
}

@Composable
fun Cell(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        modifier = Modifier
            .padding(8.dp)
            .clip(ButtonDefaults.filledTonalShape)
            .background(
                when {
                    !selected -> Color.Transparent
                    isSystemInDarkTheme() -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.inversePrimary
                },
            )
            .clickable(onClick = onClick, role = Role.Tab, onClickLabel = text)
            .padding(16.dp),
        text = text,
        textAlign = TextAlign.Center,
    )
}

private fun MediaRecorder.record(path: String, encoder: Encoder, format: Format) {
    setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
    setOutputFormat(format.value)
    setAudioEncoder(encoder.value)
    setAudioSamplingRate(48000)
    setOutputFile(path)
    prepare()
    start()
}

private fun Context.send(format: Format) {
    val intent = Intent(Intent.ACTION_SEND)
        .putExtra(Intent.EXTRA_STREAM, parse("content://${BuildConfig.AUTHORITY}/$FILE_NAME.${format.ext}"))
        .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(MIME_TYPE))
        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        .setType(MIME_TYPE)
    when (intent.resolveActivity(packageManager)) {
        null -> Toast.makeText(this, "Nope", Toast.LENGTH_SHORT).show()
        else -> startActivity(Intent.createChooser(intent, "Send the voice message"))
    }
}
