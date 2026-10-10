@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package dev.journeyscope.export.share

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/** Presents the system share sheet from the foreground window; the file lives in the temporary directory. */
public class IosShareSink : ShareSink {
    override suspend fun share(report: ExportedReport): Boolean = withContext(Dispatchers.Main) {
        val path = NSTemporaryDirectory() + report.fileName
        val written = NSString.create(string = report.json).writeToFile(path, true, NSUTF8StringEncoding, null)
        val presenter = if (written) topViewController() else null
        if (presenter == null) {
            false
        } else {
            val controller = UIActivityViewController(listOf(NSURL.fileURLWithPath(path)), null)
            presenter.presentViewController(controller, true, null)
            true
        }
    }

    private fun topViewController(): UIViewController? {
        val scene = UIApplication.sharedApplication.connectedScenes.firstNotNullOfOrNull { it as? UIWindowScene }
        val window = scene?.windows?.firstNotNullOfOrNull { (it as? UIWindow)?.takeIf(UIWindow::isKeyWindow) }
        var top = window?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        return top
    }
}
