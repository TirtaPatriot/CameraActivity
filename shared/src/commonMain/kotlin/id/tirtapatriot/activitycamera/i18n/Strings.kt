package id.tirtapatriot.activitycamera.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.Serializable

@Serializable
enum class AppLanguage(val code: String, val displayName: String) {
    INDONESIAN("id", "Bahasa Indonesia"),
    ENGLISH("en", "English"),
    ;

    companion object {
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: INDONESIAN
    }
}

/** All user-facing text. Add a new language by implementing this interface. */
interface Strings {
    val language: AppLanguage

    // Permission
    val permissionRationale: String
    val grantPermission: String

    // Connect to a server
    val connectTitle: String
    val connectIntro: String
    val serverUrlLabel: String
    val connect: String
    val connecting: String
    val scanQr: String
    val cancel: String
    val connectFailed: String
    val invalidQr: String

    // Sign in
    val signInTitle: String
    val username: String
    val password: String
    val signIn: String
    val signingIn: String
    val signInFailed: String
    val changeServer: String
    val sessionExpired: String

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
    val locationRequired: String
    val noteRequired: String

    // Stamp (also burned into photos)
    val weekdays: List<String> // Monday..Sunday
    val months: List<String> // January..December, short form
    val locationSearching: String
    val noLocation: String
    fun officer(name: String): String
    /** Zone label from the UTC offset in hours, e.g. 7 -> "WIB". */
    fun zoneLabel(offsetHours: Int?, fallback: String): String

    // Gallery
    val backToCamera: String
    fun gallerySummary(total: Int, pending: Int): String
    fun syncButton(pending: Int): String
    val syncing: String
    val syncAlreadyRunning: String
    fun syncResult(uploaded: Int, failed: Int): String
    val statusPending: String
    val statusSyncing: String
    val statusSynced: String
    val statusFailed: String

    // Settings
    val settingsTitle: String
    val server: String
    val account: String
    val languageLabel: String
    val signOut: String
    fun pendingWarning(count: Int): String
}

object IndonesianStrings : Strings {
    override val language = AppLanguage.INDONESIAN

    override val permissionRationale = "Aplikasi butuh izin kamera, mikrofon, dan lokasi untuk mencatat kegiatan."
    override val grantPermission = "Berikan izin"

    override val connectTitle = "Hubungkan ke server"
    override val connectIntro = "Scan QR dari panel admin instansi Anda, atau ketik alamat servernya."
    override val serverUrlLabel = "Alamat server"
    override val connect = "Hubungkan"
    override val connecting = "Menghubungkan…"
    override val scanQr = "Scan QR"
    override val cancel = "Batal"
    override val connectFailed = "Server tidak bisa dihubungi. Periksa alamat dan koneksi internet."
    override val invalidQr = "QR ini bukan QR koneksi Kamera Kegiatan."

    override val signInTitle = "Masuk"
    override val username = "Nama pengguna"
    override val password = "Kata sandi"
    override val signIn = "Masuk"
    override val signingIn = "Memeriksa…"
    override val signInFailed = "Nama pengguna atau kata sandi salah, atau akun dinonaktifkan."
    override val changeServer = "Ganti server"
    override val sessionExpired = "Sesi berakhir. Silakan masuk lagi."

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
    override val locationRequired = "Tunggu lokasi GPS ditemukan"
    override val noteRequired = "Isi keterangan kegiatan dulu"

    override val weekdays = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    override val months = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    override val locationSearching = "Lokasi: mencari GPS…"
    override val noLocation = "Tanpa lokasi"
    override fun officer(name: String) = "Petugas: $name"
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
    override val syncAlreadyRunning = "Sinkronisasi sedang berjalan"
    override fun syncResult(uploaded: Int, failed: Int) = "Terkirim $uploaded, gagal $failed"
    override val statusPending = "Belum sinkron"
    override val statusSyncing = "Mengirim…"
    override val statusSynced = "Tersinkron"
    override val statusFailed = "Gagal"

    override val settingsTitle = "Pengaturan"
    override val server = "Server"
    override val account = "Akun"
    override val languageLabel = "Bahasa"
    override val signOut = "Keluar"
    override fun pendingWarning(count: Int) =
        "$count tangkapan belum sinkron. Tangkapan tetap tersimpan di HP dan bisa dikirim setelah masuk lagi."
}

object EnglishStrings : Strings {
    override val language = AppLanguage.ENGLISH

    override val permissionRationale = "The app needs camera, microphone, and location access to record activities."
    override val grantPermission = "Grant permission"

    override val connectTitle = "Connect to a server"
    override val connectIntro = "Scan the QR from your organization's admin panel, or type the server address."
    override val serverUrlLabel = "Server address"
    override val connect = "Connect"
    override val connecting = "Connecting…"
    override val scanQr = "Scan QR"
    override val cancel = "Cancel"
    override val connectFailed = "Cannot reach the server. Check the address and your internet connection."
    override val invalidQr = "This is not an Activity Camera connect QR."

    override val signInTitle = "Sign in"
    override val username = "Username"
    override val password = "Password"
    override val signIn = "Sign in"
    override val signingIn = "Checking…"
    override val signInFailed = "Wrong username or password, or the account is disabled."
    override val changeServer = "Change server"
    override val sessionExpired = "Your session ended. Please sign in again."

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
    override val locationRequired = "Wait for a GPS location"
    override val noteRequired = "Enter an activity description first"

    override val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    override val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    override val locationSearching = "Location: searching GPS…"
    override val noLocation = "No location"
    override fun officer(name: String) = "Officer: $name"
    override fun zoneLabel(offsetHours: Int?, fallback: String) = fallback

    override val backToCamera = "‹ Camera"
    override fun gallerySummary(total: Int, pending: Int) = "$total captures · $pending not synced"
    override fun syncButton(pending: Int) = "Sync to server ($pending)"
    override val syncing = "Syncing…"
    override val syncAlreadyRunning = "Sync already in progress"
    override fun syncResult(uploaded: Int, failed: Int) = "Uploaded $uploaded, failed $failed"
    override val statusPending = "Not synced"
    override val statusSyncing = "Uploading…"
    override val statusSynced = "Synced"
    override val statusFailed = "Failed"

    override val settingsTitle = "Settings"
    override val server = "Server"
    override val account = "Account"
    override val languageLabel = "Language"
    override val signOut = "Sign out"
    override fun pendingWarning(count: Int) =
        "$count captures are not synced. They stay on the phone and can be uploaded after signing in again."
}

fun stringsFor(language: AppLanguage): Strings = when (language) {
    AppLanguage.INDONESIAN -> IndonesianStrings
    AppLanguage.ENGLISH -> EnglishStrings
}

/** Current UI strings, provided by App() from the language setting. */
val LocalStrings = staticCompositionLocalOf<Strings> { IndonesianStrings }
