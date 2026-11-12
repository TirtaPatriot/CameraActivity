package id.tirtapatriot.activitycamera.server.admin

/** Admin panel text in Indonesian (default) and English. */
class PanelStrings(val language: String) {
    private fun t(id: String, en: String) = if (language == "en") en else id

    // Navigation / common
    val panelTitle get() = t("Panel Admin", "Admin Panel")
    val navCaptures get() = t("Tangkapan", "Captures")
    val navUsers get() = t("Petugas", "Officers")
    val navBranding get() = t("Branding", "Branding")
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

    // Capture rules
    val policyTitle get() = t("Aturan pengambilan", "Capture rules")
    val allowPhoto get() = t("Izinkan foto", "Allow photos")
    val allowVideo get() = t("Izinkan video", "Allow videos")
    val requireLocation get() = t("Wajib ada lokasi GPS", "Require GPS location")
    val requireNote get() = t("Wajib isi keterangan", "Require a note")
    val recordAudio get() = t("Rekam suara pada video", "Record audio in videos")
    val maxVideoSeconds get() = t("Durasi video maksimal (detik)", "Maximum video length (seconds)")
    val stampTitle get() = t("Isi cap foto", "Photo stamp contents")
    val stampOrganization get() = t("Nama instansi", "Organization name")
    val stampCoordinates get() = t("Koordinat", "Coordinates")
    val stampOfficer get() = t("Kode petugas", "Officer code")
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
