package id.tirtapatriot.activitycamera.media

import platform.Foundation.NSURL
// Star import: popoverPresentationController is a UIKit category property (Kotlin extension).
import platform.UIKit.*

actual object ShareSheet {
    actual fun share(files: List<ShareFile>, text: String, onShared: () -> Unit) {
        val items: List<Any?> = listOf<Any?>(text) + files.map { NSURL.fileURLWithPath(it.path) }
        val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
        controller.completionWithItemsHandler = { _, completed, _, _ -> if (completed) onShared() }
        val top = topViewController() ?: return
        // iPad presents the sheet as a popover anchored to a view.
        controller.popoverPresentationController?.sourceView = top.view
        top.presentViewController(controller, animated = true, completion = null)
    }

    @Suppress("DEPRECATION")
    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
        while (true) top = top.presentedViewController ?: return top
    }
}
