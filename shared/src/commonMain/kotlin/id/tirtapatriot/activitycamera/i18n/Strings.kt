package id.tirtapatriot.activitycamera.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.Serializable

@Serializable
enum class AppLanguage(val code: String, val displayName: String) {
    INDONESIAN("id", "Bahasa Indonesia"),
    ENGLISH("en", "English"),
}

/** All user-facing text. Add a new language by implementing this interface. */
interface Strings {
    val appName: String

    // Permission
    val permissionRationale: String
    val grantPermission: String

    // Camera
    val modePhoto: String
    val modeVideo: String
    val settings: String
    val gallery: String
    val notePlaceholder: String
    val photoSaved: String
    val photoFailed: String
    val videoSaved: String
    val videoFailed: String
    val recordingStartFailed: String

    // Stamp (also burned into photos)
    val weekdays: List<String> // Monday..Sunday
    val months: List<String> // January..December, short form
    val locationSearching: String
    val noLocation: String
    fun officer(code: String): String
    /** Zone label from the UTC offset in hours, e.g. 7 -> "WIB". */
    fun zoneLabel(offsetHours: Int?, fallback: String): String

    // Gallery
    val backToCamera: String
    fun gallerySummary(total: Int, pending: Int): String
    fun syncButton(pending: Int): String
    val syncing: String
    val serverNotConfigured: String
    val syncAlreadyRunning: String
    fun syncResult(uploaded: Int, failed: Int): String
    val statusPending: String
    val statusSyncing: String
    val statusSynced: String
    val statusFailed: String

    // Settings
    val settingsTitle: String
    val serverUrlLabel: String
    val apiKeyLabel: String
    val officerCodeLabel: String
    val recordAudioLabel: String
    val languageLabel: String
    val save: String
}

object IndonesianStrings : Strings {
    override val appName = "Kamera Kegiatan"

    override val permissionRationale = "Aplikasi butuh izin kamera, mikrofon, dan lokasi untuk mencatat kegiatan."
    override val grantPermission = "Berikan izin"

    override val modePhoto = "Foto"
    override val modeVideo = "Video"
    override val settings = "Pengaturan"
    override val gallery = "Galeri"
    override val notePlaceholder = "Keterangan kegiatan…"
    override val photoSaved = "Foto tersimpan"
    override val photoFailed = "Gagal mengambil foto"
    override val videoSaved = "Video tersimpan"
    override val videoFailed = "Gagal menyimpan video"
    override val recordingStartFailed = "Gagal memulai rekaman"

    override val weekdays = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    override val months = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    override val locationSearching = "Lokasi: mencari GPS…"
    override val noLocation = "Tanpa lokasi"
    override fun officer(code: String) = "Petugas: $code"
    override fun zoneLabel(offsetHours: Int?, fallback: String) = when (offsetHours) {
        7 -> "WIB"
        8 -> "WITA"
        9 -> "WIT"
        else -> fallback
    }

    override val backToCamera = "‹ Kamera"
    override fun gallerySummary(total: Int, pending: Int) = "$total tangkapan · $pending belum sinkron"
    override fun syncButton(pending: Int) = "Sinkronkan ke server ($pending)"
    override val syncing = "Menyinkronkan…"
    override val serverNotConfigured = "Alamat server & API key belum diatur — buka Pengaturan."
    override val syncAlreadyRunning = "Sinkronisasi sedang berjalan"
    override fun syncResult(uploaded: Int, failed: Int) = "Terkirim $uploaded, gagal $failed"
    override val statusPending = "Belum sinkron"
    override val statusSyncing = "Mengirim…"
    override val statusSynced = "Tersinkron"
    override val statusFailed = "Gagal"

    override val settingsTitle = "Pengaturan"
    override val serverUrlLabel = "Alamat server sinkronisasi"
    override val apiKeyLabel = "API key"
    override val officerCodeLabel = "Kode / NIP petugas"
    override val recordAudioLabel = "Rekam suara pada video"
    override val languageLabel = "Bahasa"
    override val save = "Simpan"
}

object EnglishStrings : Strings {
    override val appName = "Activity Camera"

    override val permissionRationale = "The app needs camera, microphone, and location access to record activities."
    override val grantPermission = "Grant permission"

    override val modePhoto = "Photo"
    override val modeVideo = "Video"
    override val settings = "Settings"
    override val gallery = "Gallery"
    override val notePlaceholder = "Activity description…"
    override val photoSaved = "Photo saved"
    override val photoFailed = "Failed to take photo"
    override val videoSaved = "Video saved"
    override val videoFailed = "Failed to save video"
    override val recordingStartFailed = "Failed to start recording"

    override val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    override val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    override val locationSearching = "Location: searching GPS…"
    override val noLocation = "No location"
    override fun officer(code: String) = "Officer: $code"
    override fun zoneLabel(offsetHours: Int?, fallback: String) = fallback

    override val backToCamera = "‹ Camera"
    override fun gallerySummary(total: Int, pending: Int) = "$total captures · $pending not synced"
    override fun syncButton(pending: Int) = "Sync to server ($pending)"
    override val syncing = "Syncing…"
    override val serverNotConfigured = "Server address & API key not set — open Settings."
    override val syncAlreadyRunning = "Sync already in progress"
    override fun syncResult(uploaded: Int, failed: Int) = "Uploaded $uploaded, failed $failed"
    override val statusPending = "Not synced"
    override val statusSyncing = "Uploading…"
    override val statusSynced = "Synced"
    override val statusFailed = "Failed"

    override val settingsTitle = "Settings"
    override val serverUrlLabel = "Sync server address"
    override val apiKeyLabel = "API key"
    override val officerCodeLabel = "Officer code / employee ID"
    override val recordAudioLabel = "Record audio in videos"
    override val languageLabel = "Language"
    override val save = "Save"
}

fun stringsFor(language: AppLanguage): Strings = when (language) {
    AppLanguage.INDONESIAN -> IndonesianStrings
    AppLanguage.ENGLISH -> EnglishStrings
}

/** Current UI strings, provided by App() from the saved language setting. */
val LocalStrings = staticCompositionLocalOf<Strings> { IndonesianStrings }
