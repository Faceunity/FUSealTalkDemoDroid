package cn.rongcloud.im.utils.qrcode.barcodescanner.camera;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.view.SurfaceHolder;
import java.io.IOException;

/**
 * A surface on which a camera preview is displayed.
 *
 * <p>This wraps either a SurfaceHolder or a SurfaceTexture.
 */
public class CameraSurface {
    private SurfaceHolder surfaceHolder;
    private SurfaceTexture surfaceTexture;

    public CameraSurface(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalArgumentException("surfaceHolder may not be null");
        }
        this.surfaceHolder = surfaceHolder;
    }

    public CameraSurface(SurfaceTexture surfaceTexture) {
        if (surfaceTexture == null) {
            throw new IllegalArgumentException("surfaceTexture may not be null");
        }
        this.surfaceTexture = surfaceTexture;
    }

    public SurfaceHolder getSurfaceHolder() {
        return surfaceHolder;
    }

    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }

    public void setPreview(Camera camera) throws IOException {
        if (surfaceHolder != null) {
            camera.setPreviewDisplay(surfaceHolder);
        } else {
            if (Build.VERSION.SDK_INT >= 11) {
                camera.setPreviewTexture(surfaceTexture);
            } else {
                // This should not happen, but we check for it anyway.
                throw new IllegalStateException("SurfaceTexture not supported.");
            }
        }
    }
}
