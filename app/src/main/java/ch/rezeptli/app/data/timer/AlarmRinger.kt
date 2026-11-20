package ch.rezeptli.app.data.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import ch.rezeptli.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Laesst den Kochtimer klingeln: mit dem Weckton des Telefons, dazu Vibration.
 *
 * Der Ton laeuft als Wecker, nicht als Musik. Damit gilt die Wecker-Lautstaerke, und er ist
 * auch zu hoeren, wenn das Telefon auf lautlos steht - so, wie es die Uhr-App haelt.
 * Laeuft nebenher ein Podcast, wird der fuer die Dauer leiser gestellt.
 */
@Singleton
class AlarmRinger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Welcher Ton laeuft. */
    enum class Sound {
        /** Der Weckton, der in den Einstellungen des Telefons gewaehlt ist. */
        SYSTEM,

        /** Der mitgelieferte Ton: Das Telefon hat keinen Weckton, oder er liess sich nicht abspielen. */
        BUILT_IN,

        /** Kein Ton - es bleibt die Vibration. */
        NONE,
    }

    private val audio = context.getSystemService(AudioManager::class.java)

    @Suppress("DEPRECATION") // Der Nachfolger VibratorManager kam erst mit Android 12.
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var sound: Sound? = null

    /** Welcher Ton gerade laeuft - `null`, solange es still ist. */
    val playing: Sound?
        @Synchronized get() = sound

    /** Ob wirklich etwas zu hoeren ist. */
    val isAudible: Boolean
        @Synchronized get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    /** Faengt an zu klingeln und hoert erst mit [stop] wieder auf. Mehrfach rufen schadet nicht. */
    @Synchronized
    fun start(): Sound {
        sound?.let { return it }

        focus = AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(ALARM)
            .build()
            .also { runCatching { audio?.requestAudioFocus(it) } }

        val started = sources().firstNotNullOfOrNull { (kind, uri) -> play(uri)?.let { kind to it } }
        player = started?.second
        vibrate()

        return (started?.first ?: Sound.NONE).also { sound = it }
    }

    @Synchronized
    fun stop() {
        player?.let { running ->
            runCatching { running.stop() }
            running.release()
        }
        player = null
        focus?.let { runCatching { audio?.abandonAudioFocusRequest(it) } }
        focus = null
        runCatching { vibrator?.cancel() }
        sound = null
    }

    /**
     * Woher der Ton kommt, in der Reihenfolge der Versuche.
     *
     * Zuerst die Einstellung des Telefons selbst: Hinter dieser Adresse haelt Android eine
     * Kopie des gewaehlten Wecktons bereit, die jede App abspielen darf - auch wenn die Person
     * eine eigene Musikdatei gewaehlt hat, die die App sonst nicht lesen duerfte. Ist kein
     * Weckton gewaehlt oder laesst er sich nicht abspielen, klingelt der mitgelieferte.
     */
    private fun sources(): List<Pair<Sound, Uri>> = listOf(
        Sound.SYSTEM to Settings.System.DEFAULT_ALARM_ALERT_URI,
        Sound.BUILT_IN to Uri.parse("android.resource://${context.packageName}/${R.raw.timer_klingeln}"),
    )

    private fun play(uri: Uri): MediaPlayer? {
        val candidate = MediaPlayer()
        return try {
            candidate.setAudioAttributes(ALARM)
            candidate.setDataSource(context, uri)
            candidate.isLooping = true
            candidate.prepare()
            candidate.start()
            candidate
        } catch (_: Exception) {
            // Die Datei fehlt, ist kein Ton oder darf nicht gelesen werden: der naechste Versuch.
            candidate.release()
            null
        }
    }

    private fun vibrate() {
        val motor = vibrator?.takeIf { it.hasVibrator() } ?: return
        runCatching {
            @Suppress("DEPRECATION") // VibrationAttributes gibt es erst ab Android 13.
            motor.vibrate(VibrationEffect.createWaveform(PATTERN, 0), ALARM)
        }
    }

    private companion object {
        val ALARM: AudioAttributes = AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        /** Pause, zweimal kurz, lange Pause - und von vorn. */
        val PATTERN = longArrayOf(0, 350, 200, 350, 1_100)
    }
}
