package id.tirtapatriot.activitycamera.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import id.tirtapatriot.activitycamera.camera.WatermarkStyle
import kotlinx.datetime.LocalDate
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

    // Common
    val back: String
    val close: String
    val cancel: String
    val select: String

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
    val connectFailed: String
    val invalidQr: String
    val laterToCamera: String

    // Sign in
    val username: String
    val password: String
    val signIn: String
    val signingIn: String
    val signInFailed: String
    val changeServer: String
    val sessionExpired: String
    val skipUseWithoutAccount: String
    val signInSso: String
    val orWithPassword: String
    val ssoRejected: String

    // Camera
    val modePhoto: String
    val modeVideo: String
    val account: String
    val settings: String
    val flash: String
    val grid: String
    val timer: String
    val flipCamera: String
    val watermark: String
    val share: String
    val gpsSearching: String
    val mockLocation: String
    val pickTask: String
    val pickTaskRequired: String
    val taskRequired: String
    val photoFailed: String
    val videoFailed: String
    val recordingStartFailed: String
    val locationRequired: String

    // Task sheet
    val tasksTitle: String
    val tasksOptionalHint: String
    val tasksRequiredHint: String
    val tasksEmpty: String
    val tasksNeedServer: String
    val noTask: String
    val addOwnTask: String
    val ownTaskPlaceholder: String
    val add: String
    val remove: String
    val noteButton: String
    val noteTitle: String
    val noteSheetHint: String
    val clearNote: String
    val done: String
    val noteForCapture: String
    val clearNoteAfterCapture: String
    val cameraSection: String
    val confirmAfterCapture: String
    val confirmAfterCaptureHint: String
    val keep: String
    val retakeDiscard: String
    val mirrorFrontPhotos: String
    val mirrorFrontPhotosHint: String
    val connectToServer: String

    // Watermark sheet & stamp
    val watermarkHint: String
    fun styleName(style: WatermarkStyle): String
    val noteLabel: String
    val notePlaceholder: String
    val extraTextLabel: String
    val extraTextPlaceholder: String
    val showOnPhoto: String
    val fieldOrganization: String
    val fieldTask: String
    val fieldCoordinates: String
    val fieldOfficer: String
    val fieldNote: String
    val resetPosition: String
    val weekdays: List<String> // Monday..Sunday
    val months: List<String> // January..December, short form
    val locationSearching: String
    fun officer(name: String): String
    /** Zone label from the UTC offset in hours, e.g. 7 -> "WIB". */
    fun zoneLabel(offsetHours: Int?, fallback: String): String

    // Review & share
    val shareCaption: String
    val retake: String
    val keepOnly: String
    val mockLocationRecorded: String
    val recordedOnServer: String
    val savedOnPhoneOnly: String
    val waitingForSignIn: String
    val uploadRetrying: String
    val uploadingInBackground: String
    fun captureCount(count: Int): String

    // History
    val historyTitle: String
    val historyEmpty: String
    fun selectedCount(count: Int): String
    fun shareCount(count: Int): String
    fun dayLabel(date: LocalDate): String
    val bannerNotConnected: String
    val bannerAnonymous: String
    fun bannerSignInRequired(pending: Int): String
    fun bannerPending(pending: Int): String
    val sendNow: String

    // Account
    val notSignedIn: String
    val notSignedInIntro: String
    val benefitName: String
    val benefitTasks: String
    val benefitRecorded: String
    val server: String
    val notConnectedIntro: String
    val allSent: String
    val disconnect: String
    val languageLabel: String
    val signOut: String
    fun deviceId(id: String): String
}

object IndonesianStrings : Strings {
    override val language = AppLanguage.INDONESIAN

    override val back = "Kembali"
    override val close = "Tutup"
    override val cancel = "Batal"
    override val select = "Pilih"

    override val permissionRationale = "Izinkan kamera dan lokasi supaya setiap foto langsung bercap waktu dan tempat."
    override val grantPermission = "Berikan izin"

    override val connectTitle = "Hubungkan ke server instansi"
    override val connectIntro = "Scan QR dari panel admin instansi Anda, atau ketik alamat servernya. Foto lalu tercatat otomatis."
    override val serverUrlLabel = "Alamat server"
    override val connect = "Hubungkan"
    override val connecting = "Menghubungkan…"
    override val scanQr = "Scan QR"
    override val connectFailed = "Server tidak bisa dihubungi. Periksa alamat dan koneksi internet."
    override val invalidQr = "QR ini bukan QR koneksi Kamera Kegiatan."
    override val laterToCamera = "Nanti saja, langsung ke kamera"

    override val username = "Nama pengguna"
    override val password = "Kata sandi"
    override val signIn = "Masuk"
    override val signingIn = "Memeriksa…"
    override val signInFailed = "Nama pengguna atau kata sandi salah, atau akun dinonaktifkan."
    override val changeServer = "Ganti server"
    override val sessionExpired = "Sesi berakhir. Silakan masuk lagi."
    override val skipUseWithoutAccount = "Lewati, pakai tanpa akun"
    override val signInSso = "Masuk dengan SSO"
    override val orWithPassword = "atau dengan nama pengguna & kata sandi"
    override val ssoRejected = "Masuk lewat SSO gagal, atau akun Anda dinonaktifkan admin."

    override val modePhoto = "Foto"
    override val modeVideo = "Video"
    override val account = "Akun"
    override val settings = "Pengaturan"
    override val flash = "Lampu kilat"
    override val grid = "Garis bantu"
    override val timer = "Pewaktu"
    override val flipCamera = "Ganti kamera"
    override val watermark = "Watermark"
    override val share = "Bagikan"
    override val gpsSearching = "Mencari GPS…"
    override val mockLocation = "Lokasi palsu"
    override val pickTask = "Pilih tugas"
    override val pickTaskRequired = "Pilih tugas dulu"
    override val taskRequired = "Instansi meminta tugas dipilih dulu"
    override val photoFailed = "Gagal mengambil foto"
    override val videoFailed = "Gagal menyimpan video"
    override val recordingStartFailed = "Gagal memulai rekaman"
    override val locationRequired = "Tunggu lokasi GPS ditemukan"

    override val tasksTitle = "Tugas"
    override val tasksOptionalHint = "Membuat laporan lebih rapi"
    override val tasksRequiredHint = "Wajib dipilih sesuai aturan instansi"
    override val tasksEmpty = "Admin belum menyiapkan daftar tugas."
    override val tasksNeedServer = "Daftar tugas disiapkan admin instansi. Hubungkan ke server untuk melihatnya."
    override val noTask = "Tanpa tugas"
    override val addOwnTask = "Tambah tugas sendiri"
    override val ownTaskPlaceholder = "mis. Cek tekanan air"
    override val add = "Tambah"
    override val remove = "Hapus"
    override val noteButton = "Catatan"
    override val noteTitle = "Catatan"
    override val noteSheetHint = "Ikut tercetak di foto dan tercatat di laporan. Tetap dipakai untuk foto berikutnya sampai diubah."
    override val clearNote = "Hapus catatan"
    override val done = "Selesai"
    override val noteForCapture = "Catatan untuk foto ini"
    override val clearNoteAfterCapture = "Hapus catatan setelah foto diambil"
    override val cameraSection = "Kamera"
    override val confirmAfterCapture = "Konfirmasi setelah memotret"
    override val confirmAfterCaptureHint = "Tinjau dulu setiap foto; baru disimpan dan dikirim setelah Anda pilih Simpan."
    override val keep = "Simpan"
    override val retakeDiscard = "Ulangi (buang)"
    override val mirrorFrontPhotos = "Cerminkan foto kamera depan"
    override val mirrorFrontPhotosHint = "Hasil persis seperti di layar. Saat mati, tulisan di lokasi terbaca normal."
    override val connectToServer = "Hubungkan ke server"

    override val watermarkHint = "Geser watermark di layar untuk memindahkannya"
    override fun styleName(style: WatermarkStyle) = when (style) {
        WatermarkStyle.CARD -> "Kartu"
        WatermarkStyle.STRIP -> "Strip"
        WatermarkStyle.MINIMAL -> "Minimal"
    }
    override val noteLabel = "Catatan"
    override val notePlaceholder = "mis. Ganti meter rusak"
    override val extraTextLabel = "Teks tambahan"
    override val extraTextPlaceholder = "mis. No. SPK 123/2026"
    override val showOnPhoto = "Tampilkan di foto"
    override val fieldOrganization = "Nama instansi"
    override val fieldTask = "Tugas"
    override val fieldCoordinates = "Koordinat"
    override val fieldOfficer = "Nama petugas"
    override val fieldNote = "Catatan"
    override val resetPosition = "Kembalikan posisi"
    override val weekdays = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    override val months = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    override val locationSearching = "Mencari lokasi GPS…"
    override fun officer(name: String) = "Petugas: $name"
    override fun zoneLabel(offsetHours: Int?, fallback: String) = when (offsetHours) {
        7 -> "WIB"
        8 -> "WITA"
        9 -> "WIT"
        else -> fallback
    }

    override val shareCaption = "Teks yang ikut dibagikan"
    override val retake = "Ulangi"
    override val keepOnly = "Simpan saja"
    override val mockLocationRecorded = "Lokasi palsu terdeteksi · tetap dicatat"
    override val recordedOnServer = "Sudah tercatat di server instansi"
    override val savedOnPhoneOnly = "Tersimpan di HP · belum terhubung ke server"
    override val waitingForSignIn = "Menunggu Anda masuk untuk dikirim ke server"
    override val uploadRetrying = "Belum terkirim · dicoba lagi otomatis"
    override val uploadingInBackground = "Otomatis tercatat juga di server instansi"
    override fun captureCount(count: Int) = "$count dokumentasi"

    override val historyTitle = "Riwayat"
    override val historyEmpty = "Belum ada foto. Yuk ambil yang pertama."
    override fun selectedCount(count: Int) = "$count dipilih"
    override fun shareCount(count: Int) = if (count == 1) "Bagikan 1 foto" else "Bagikan $count foto"
    override fun dayLabel(date: LocalDate) =
        "${weekdays[date.dayOfWeek.ordinal]}, ${date.dayOfMonth} ${months[date.monthNumber - 1]} ${date.year}"
    override val bannerNotConnected = "Foto tersimpan di HP. Hubungkan ke server instansi agar ikut tercatat."
    override val bannerAnonymous = "Terkirim atas nama perangkat ini. Masuk agar tercatat atas nama Anda."
    override fun bannerSignInRequired(pending: Int) = "$pending foto menunggu. Instansi meminta Anda masuk dulu."
    override fun bannerPending(pending: Int) = "$pending foto menunggu dikirim"
    override val sendNow = "Kirim sekarang"

    override val notSignedIn = "Belum masuk"
    override val notSignedInIntro = "Kamera tetap bisa dipakai. Dengan masuk, laporan Anda lebih lengkap:"
    override val benefitName = "Foto tercatat atas nama Anda"
    override val benefitTasks = "Daftar tugas dari instansi"
    override val benefitRecorded = "Riwayat kerja tersimpan rapi di server"
    override val server = "Server instansi"
    override val notConnectedIntro = "Belum terhubung. Semua foto tetap tersimpan di HP dan bisa dikirim kapan saja."
    override val allSent = "Semua foto sudah terkirim"
    override val disconnect = "Putuskan"
    override val languageLabel = "Bahasa"
    override val signOut = "Keluar"
    override fun deviceId(id: String) = "ID perangkat: $id"
}

object EnglishStrings : Strings {
    override val language = AppLanguage.ENGLISH

    override val back = "Back"
    override val close = "Close"
    override val cancel = "Cancel"
    override val select = "Select"

    override val permissionRationale = "Allow the camera and location so every photo is stamped with time and place."
    override val grantPermission = "Grant permission"

    override val connectTitle = "Connect to your organization's server"
    override val connectIntro = "Scan the QR from your organization's admin panel, or type the server address. Photos are then recorded automatically."
    override val serverUrlLabel = "Server address"
    override val connect = "Connect"
    override val connecting = "Connecting…"
    override val scanQr = "Scan QR"
    override val connectFailed = "Cannot reach the server. Check the address and your internet connection."
    override val invalidQr = "This is not an Activity Camera connect QR."
    override val laterToCamera = "Not now, go to the camera"

    override val username = "Username"
    override val password = "Password"
    override val signIn = "Sign in"
    override val signingIn = "Checking…"
    override val signInFailed = "Wrong username or password, or the account is disabled."
    override val changeServer = "Change server"
    override val sessionExpired = "Your session ended. Please sign in again."
    override val skipUseWithoutAccount = "Skip, use without an account"
    override val signInSso = "Sign in with SSO"
    override val orWithPassword = "or with username & password"
    override val ssoRejected = "SSO sign-in failed, or your account was disabled by the admin."

    override val modePhoto = "Photo"
    override val modeVideo = "Video"
    override val account = "Account"
    override val settings = "Settings"
    override val flash = "Flash"
    override val grid = "Grid"
    override val timer = "Timer"
    override val flipCamera = "Switch camera"
    override val watermark = "Watermark"
    override val share = "Share"
    override val gpsSearching = "Finding GPS…"
    override val mockLocation = "Mock location"
    override val pickTask = "Pick a task"
    override val pickTaskRequired = "Pick a task first"
    override val taskRequired = "Your organization asks you to pick a task first"
    override val photoFailed = "Failed to take photo"
    override val videoFailed = "Failed to save video"
    override val recordingStartFailed = "Failed to start recording"
    override val locationRequired = "Wait for a GPS location"

    override val tasksTitle = "Task"
    override val tasksOptionalHint = "Makes reports tidier"
    override val tasksRequiredHint = "Required by your organization"
    override val tasksEmpty = "The admin hasn't set up tasks yet."
    override val tasksNeedServer = "Tasks are prepared by your organization's admin. Connect to the server to see them."
    override val noTask = "No task"
    override val addOwnTask = "Add your own task"
    override val ownTaskPlaceholder = "e.g. Check water pressure"
    override val add = "Add"
    override val remove = "Remove"
    override val noteButton = "Note"
    override val noteTitle = "Note"
    override val noteSheetHint = "Printed on the photo and recorded in the report. Kept for the next photos until you change it."
    override val clearNote = "Clear note"
    override val done = "Done"
    override val noteForCapture = "Note for this photo"
    override val clearNoteAfterCapture = "Clear the note after taking a photo"
    override val cameraSection = "Camera"
    override val confirmAfterCapture = "Confirm after taking a photo"
    override val confirmAfterCaptureHint = "Review each photo first; it is kept and uploaded only after you tap Keep."
    override val keep = "Keep"
    override val retakeDiscard = "Retake (discard)"
    override val mirrorFrontPhotos = "Mirror front-camera photos"
    override val mirrorFrontPhotosHint = "Saved exactly as on screen. When off, text in the scene reads normally."
    override val connectToServer = "Connect to server"

    override val watermarkHint = "Drag the watermark on screen to move it"
    override fun styleName(style: WatermarkStyle) = when (style) {
        WatermarkStyle.CARD -> "Card"
        WatermarkStyle.STRIP -> "Strip"
        WatermarkStyle.MINIMAL -> "Minimal"
    }
    override val noteLabel = "Note"
    override val notePlaceholder = "e.g. Replaced broken meter"
    override val extraTextLabel = "Extra text"
    override val extraTextPlaceholder = "e.g. Work order 123/2026"
    override val showOnPhoto = "Show on photo"
    override val fieldOrganization = "Organization"
    override val fieldTask = "Task"
    override val fieldCoordinates = "Coordinates"
    override val fieldOfficer = "Officer name"
    override val fieldNote = "Note"
    override val resetPosition = "Reset position"
    override val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    override val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    override val locationSearching = "Finding GPS location…"
    override fun officer(name: String) = "Officer: $name"
    override fun zoneLabel(offsetHours: Int?, fallback: String) = fallback

    override val shareCaption = "Text shared along"
    override val retake = "Retake"
    override val keepOnly = "Just keep it"
    override val mockLocationRecorded = "Mock location detected · still recorded"
    override val recordedOnServer = "Recorded on your organization's server"
    override val savedOnPhoneOnly = "Saved on the phone · not connected to a server"
    override val waitingForSignIn = "Waiting for you to sign in before uploading"
    override val uploadRetrying = "Not sent yet · retrying automatically"
    override val uploadingInBackground = "Also recorded automatically on your organization's server"
    override fun captureCount(count: Int) = "$count captures"

    override val historyTitle = "History"
    override val historyEmpty = "No photos yet. Take the first one."
    override fun selectedCount(count: Int) = "$count selected"
    override fun shareCount(count: Int) = if (count == 1) "Share 1 photo" else "Share $count photos"
    override fun dayLabel(date: LocalDate) =
        "${weekdays[date.dayOfWeek.ordinal]}, ${months[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"
    override val bannerNotConnected = "Photos are saved on the phone. Connect to your organization's server so they are recorded too."
    override val bannerAnonymous = "Uploaded as this device. Sign in so they are recorded under your name."
    override fun bannerSignInRequired(pending: Int) = "$pending photos waiting. Your organization asks you to sign in first."
    override fun bannerPending(pending: Int) = "$pending photos waiting to upload"
    override val sendNow = "Send now"

    override val notSignedIn = "Not signed in"
    override val notSignedInIntro = "The camera works anyway. Signing in makes your reports more complete:"
    override val benefitName = "Photos recorded under your name"
    override val benefitTasks = "Task list from your organization"
    override val benefitRecorded = "Your work history kept neatly on the server"
    override val server = "Organization server"
    override val notConnectedIntro = "Not connected. Every photo stays on the phone and can be uploaded any time."
    override val allSent = "All photos uploaded"
    override val disconnect = "Disconnect"
    override val languageLabel = "Language"
    override val signOut = "Sign out"
    override fun deviceId(id: String) = "Device ID: $id"
}

fun stringsFor(language: AppLanguage): Strings = when (language) {
    AppLanguage.INDONESIAN -> IndonesianStrings
    AppLanguage.ENGLISH -> EnglishStrings
}

/** Current UI strings, provided by App() from the language setting. */
val LocalStrings = staticCompositionLocalOf<Strings> { IndonesianStrings }
