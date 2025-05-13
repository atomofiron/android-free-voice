package app.atomofiron.freevoice

import android.Manifest.permission.RECORD_AUDIO
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.media.MediaRecorder
import android.media.MediaRecorder.AudioEncoder
import android.media.MediaRecorder.OutputFormat
import android.net.Uri.*
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.M
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
import app.atomofiron.freevoice.ui.theme.FreeVoiceTheme

const val FILE_NAME = "voicemessage"

class MainActivity : ComponentActivity() {

    private val recorder by lazy { if (SDK_INT >= S) MediaRecorder(this) else MediaRecorder() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var brokenAndroidFix: (() -> Unit)?
        brokenAndroidFix = {
            brokenAndroidFix = null
            enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(0x01808080, 0x01808080))
        }

        setContent {
            FreeVoiceTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    brokenAndroidFix?.invoke()
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
fun ColumnScope.Recorder(activity: ComponentActivity, recorder: MediaRecorder) {
    val interactionSource = remember { MutableInteractionSource() }
    var recording by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var encoder by remember { mutableStateOf(Encoder.AmrNB) }
    var format by remember { mutableStateOf(Format.Mpeg) }
    var allowed by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        var started = false
        interactionSource.interactions.collect { interaction ->
            if (!activity.checkPermission()) {
                return@collect
            }
            when (interaction) {
                is PressInteraction.Press -> recorder.record(activity.filePath(format.ext), encoder, format)
                    ?.also { activity.alert(it.toString()) }
                    .let { started = it == null }
                is PressInteraction.Cancel,
                is PressInteraction.Release -> recorder.takeIf { started }
                    ?.end()
                    ?.let { activity.alert(it.toString()) }
            }
            allowed = interaction !is PressInteraction.Press
            recording = started && interaction is PressInteraction.Press
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
        SomeIconButton(R.drawable.ic_send, enabled = allowed, onClick = { activity.send(format) })
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

private fun MediaRecorder.record(path: String, encoder: Encoder, format: Format): Exception? {
    reset()
    setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
    setOutputFormat(format.value)
    setAudioEncoder(encoder.value)
    setAudioSamplingRate(48000)
    setOutputFile(path)
    prepare()
    return tryRun { start() }
}

private fun MediaRecorder.end(): Exception? = tryRun { stop() }

inline fun tryRun(action: () -> Unit): Exception? {
    try {
        action()
    } catch (e: Exception) {
        return e
    }
    return null
}

private fun Context.alert(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

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

private fun ComponentActivity.checkPermission(): Boolean {
    if (SDK_INT < M || checkSelfPermission(RECORD_AUDIO) == PERMISSION_GRANTED) {
        return true
    }
    requestPermissions(arrayOf(RECORD_AUDIO), 1)
    return false
}
