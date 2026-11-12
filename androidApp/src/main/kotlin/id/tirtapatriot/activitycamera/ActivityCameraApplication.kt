package id.tirtapatriot.activitycamera

import android.app.Application
import id.tirtapatriot.activitycamera.platform.AndroidPlatform

class ActivityCameraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Before anything touches AppGraph (UI or the background sync worker).
        AndroidPlatform.init(this)
    }
}
