package com.beacon2.home;

import android.content.Context;
import android.widget.ImageView;

/**
 * An ImageView that tells the renderer its content never overlaps itself, so fading it
 * applies alpha directly instead of first drawing into a full-screen offscreen buffer
 * (about 8 MB of GPU memory at 1920x1080 on every photo or banner crossfade).
 */
final class FlatImageView extends ImageView {
    FlatImageView(Context c) {
        super(c);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
