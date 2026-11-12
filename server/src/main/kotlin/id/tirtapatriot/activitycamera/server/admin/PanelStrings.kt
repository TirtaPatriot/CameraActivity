package id.tirtapatriot.activitycamera.server.admin

/** Admin panel text in Indonesian (default) and English. */
class PanelStrings(val language: String) {
    private fun t(id: String, en: String) = if (language == "en") en else id

    // Navigation / common
    val panelTitle get() = t("Panel Admin", "Admin Panel")
    val navCaptures get() = t("Tangkapan", "Captures")
    val navUsers get() = t("Petugas", "Officers")
    val navBranding get() = t("Branding", "Branding")
    val navTasks get() = t("Tugas", "Tasks")
    val navPolicy get() = t("Aturan", "Rules")
    val navConnect get() = t("Hubungkan HP", "Connect phone")
    val logout get() = t("Keluar", "Sign out")
    val save get() = t("Simpan", "Save")
    val saved get() = t("Tersimpan.", "Saved.")

    // Login
    val loginTitle get() = t("Masuk sebagai admin", "Admin sign in")
    val username get() = t("Nama pengguna", "Username")
    val password get() = t("Kata sandi", "Password")
    val signIn get() = t("Masuk", "Sign in")
    val loginFailed get() = t("Nama pengguna atau kata sandi salah.", "Wrong username or password.")

    // Captures
    val capturesTitle get() = t("Tangkapan terbaru", "Latest captures")
    val from get() = t("Dari", "From")
    val to get() = t("Sampai", "To")
    val officer get() = t("Petugas", "Officer")
    val allOfficers get() = t("Semua petugas", "All officers")
    val filter get() = t("Tampilkan", "Show")
    val time get() = t("Waktu", "Time")
    val type get() = t("Jenis", "Type")
    val note get() = t("Keterangan", "Note")
    val location get() = t("Lokasi", "Location")
    val size get() = t("Ukuran", "Size")
    val file get() = t("File", "File")
    val open get() = t("Buka", "Open")
    val map get() = t("Peta", "Map")
    val photo get() = t("Foto", "Photo")
    val video get() = t("Video", "Video")
    val noCaptures get() = t("Belum ada tangkapan.", "No captures yet.")
    val task get() = t("Tugas", "Task")
    val anonymous get() = t("Tanpa akun", "Anonymous")
    val mockLocation get() = t("Lokasi palsu", "Mock location")
    val shared get() = t("Dibagikan", "Shared")

    // Officers
    val usersTitle get() = t("Akun petugas & admin", "Officer & admin accounts")
    val displayName get() = t("Nama", "Name")
    val userCode get() = t("Kode / NIP", "Code / employee ID")
    val role get() = t("Peran", "Role")
    val roleOfficer get() = t("Petugas", "Officer")
    val roleAdmin get() = t("Admin", "Admin")
    val status get() = t("Status", "Status")
    val active get() = t("Aktif", "Active")
    val inactive get() = t("Nonaktif", "Disabled")
    val activate get() = t("Aktifkan", "Enable")
    val deactivate get() = t("Nonaktifkan", "Disable")
    val newPassword get() = t("Kata sandi baru", "New password")
    val setPassword get() = t("Ganti sandi", "Set password")
    val addUser get() = t("Tambah akun", "Add account")
    val create get() = t("Buat", "Create")
    val errUsername get() = t(
        "Nama pengguna 3–50 karakter: huruf kecil, angka, titik, minus, garis bawah.",
        "Username must be 3–50 characters: lowercase letters, digits, dot, dash, underscore.",
    )
    val errPassword get() = t("Kata sandi minimal 8 karakter.", "Password must be at least 8 characters.")
    val errDisplayName get() = t("Nama wajib diisi.", "Name is required.")
    val errDuplicate get() = t("Nama pengguna sudah dipakai.", "Username already taken.")
    val errSelf get() = t("Tidak bisa menonaktifkan atau menurunkan akun Anda sendiri.", "You cannot disable or demote your own account.")
    val passwordChanged get() = t(
        "Kata sandi diganti; semua perangkat akun ini harus masuk ulang.",
        "Password changed; every device of this account must sign in again.",
    )

    // Branding
    val brandingTitle get() = t("Branding", "Branding")
    val brandingIntro get() = t(
        "Tampil di dalam aplikasi (layar masuk, judul, warna, cap foto). Nama & ikon di layar HP ditentukan saat build aplikasi.",
        "Shown inside the app (sign-in screen, title, colors, photo stamp). The home-screen name & icon are set when the app is built.",
    )
    val organizationName get() = t("Nama instansi", "Organization name")
    val appTitleId get() = t("Judul aplikasi (Bahasa Indonesia)", "App title (Indonesian)")
    val appTitleEn get() = t("Judul aplikasi (English)", "App title (English)")
    val primaryColor get() = t("Warna utama", "Primary color")
    val logo get() = t("Logo", "Logo")
    val logoHint get() = t("PNG atau JPEG, maksimal 1 MB.", "PNG or JPEG, max 1 MB.")
    val uploadLogo get() = t("Unggah logo", "Upload logo")
    val removeLogo get() = t("Hapus logo", "Remove logo")
    val noLogo get() = t("Belum ada logo.", "No logo yet.")
    val errColor get() = t("Warna harus format #RRGGBB.", "Color must be #RRGGBB.")
    val errLogo get() = t("Logo harus PNG/JPEG dan maksimal 1 MB.", "Logo must be PNG/JPEG and at most 1 MB.")

    // Tasks
    val tasksTitle get() = t("Jenis tugas", "Task types")
    val tasksIntro get() = t(
        "Pilihan tugas yang muncul di kamera petugas. Petugas boleh tidak memilih tugas, kecuali aturan \"wajib pilih tugas\" dinyalakan.",
        "Tasks officers can pick in the camera. Picking one is optional unless the \"require a task\" rule is on.",
    )
    val taskName get() = t("Nama tugas", "Task name")
    val icon get() = t("Ikon", "Icon")
    val order get() = t("Urutan", "Order")
    val addTask get() = t("Tambah tugas", "Add task")
    val noTasks get() = t("Belum ada jenis tugas.", "No task types yet.")
    val errTaskName get() = t("Nama tugas wajib diisi (maks. 100 karakter).", "Task name is required (max 100 characters).")
    fun iconLabel(key: String) = when (key) {
        "meter" -> t("Meter", "Meter")
        "inspect" -> t("Periksa", "Inspect")
        "replace" -> t("Ganti", "Replace")
        "leak" -> t("Bocor", "Leak")
        "seal" -> t("Segel", "Seal")
        else -> t("Lainnya", "Other")
    }

    // Capture rules
    val policyTitle get() = t("Aturan pengambilan", "Capture rules")
    val policyIntro get() = t(
        "Semua aturan longgar secara bawaan supaya petugas terbiasa dulu. Ketatkan pelan-pelan setelah kebiasaannya terbentuk.",
        "All rules start permissive so officers get used to the app first. Tighten them gradually once the habit has formed.",
    )
    val accessTitle get() = t("Akun & tugas", "Accounts & tasks")
    val allowAnonymousUpload get() = t("Izinkan unggah tanpa akun (dicatat per perangkat)", "Allow uploads without an account (recorded per device)")
    val requireSignIn get() = t("Wajib masuk sebelum foto dikirim ke server", "Require sign-in before captures are uploaded")
    val requireTask get() = t("Wajib pilih tugas sebelum memotret", "Require a task before taking a photo")
    val allowPhoto get() = t("Izinkan foto", "Allow photos")
    val allowVideo get() = t("Izinkan video", "Allow videos")
    val requireLocation get() = t("Wajib ada lokasi GPS", "Require GPS location")
    val recordAudio get() = t("Rekam suara pada video", "Record audio in videos")
    val maxVideoSeconds get() = t("Durasi video maksimal (detik)", "Maximum video length (seconds)")
    val defaultLanguage get() = t("Bahasa awal aplikasi", "Default app language")
    val errPolicy get() = t(
        "Minimal salah satu foto atau video harus diizinkan; durasi 5–3600 detik.",
        "Allow at least photos or videos; length must be 5–3600 seconds.",
    )

    // Connect
    val connectTitle get() = t("Hubungkan aplikasi", "Connect the app")
    val connectIntro get() = t(
        "Di HP petugas, buka aplikasi lalu scan QR ini, atau ketik alamat server di bawah. Setelah itu petugas masuk dengan akunnya.",
        "On the officer's phone, open the app and scan this QR, or type the server address below. Then the officer signs in with their account.",
    )
    val serverAddress get() = t("Alamat server", "Server address")
}
