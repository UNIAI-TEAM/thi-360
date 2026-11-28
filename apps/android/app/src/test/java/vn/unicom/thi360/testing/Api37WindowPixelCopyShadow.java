package vn.unicom.thi360.testing;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowPixelCopy;

/**
 * Forces a fresh HWUI draw traversal before {@link PixelCopy} captures a {@link Window} on
 * Robolectric API 37+.
 *
 * <p>Robolectric 4.18.1 unconditionally enables real draw traversals on API &gt;= 37
 * ({@code ShadowView.areRealDrawTraversalsEnabled()}), so {@link ShadowPixelCopy} reads from the
 * HWUI surface {@code ImageReader} instead of synchronously drawing the {@code DecorView}.
 * Jetpack Compose's {@code WindowCapture.forceRedraw} still short-circuits whenever
 * {@code Build.FINGERPRINT == "robolectric"}, leaving dark-theme windows (where
 * {@code systemBarsAppearance} remains at its default {@code 0}) vulnerable to capturing an
 * unrendered white surface frame.
 */
@Implements(value = PixelCopy.class, minSdk = 24)
public class Api37WindowPixelCopyShadow extends ShadowPixelCopy {

    @Implementation(minSdk = 26)
    protected static void request(
            Window source,
            Bitmap dest,
            PixelCopy.OnPixelCopyFinishedListener listener,
            Handler listenerThread) {
        request(source, null, dest, listener, listenerThread);
    }

    @Implementation(minSdk = 26)
    protected static void request(
            Window source,
            Rect srcRect,
            Bitmap dest,
            PixelCopy.OnPixelCopyFinishedListener listener,
            Handler listenerThread) {
        if (RuntimeEnvironment.getApiLevel() >= 37
                && Looper.getMainLooper().isCurrentThread()
                && source != null) {
            View decorView = source.getDecorView();
            if (decorView != null) {
                decorView.invalidate();
                Shadows.shadowOf(Looper.getMainLooper()).idle();
            }
        }
        ShadowPixelCopy.request(source, srcRect, dest, listener, listenerThread);
    }
}
