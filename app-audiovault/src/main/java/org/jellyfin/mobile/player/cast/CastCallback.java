package org.jellyfin.mobile.player.cast;

/**
 * Simple callback interface for Chromecast operations
 * Replaces JavascriptCallback which was tied to WebView
 * Java-compatible version
 */
public interface CastCallback {
    void onSuccess(boolean keep, String result);
    void onError(boolean keep, String message);
    
    default void onSuccess(String result) {
        onSuccess(false, result);
    }
    
    default void onError(String message) {
        onError(false, message);
    }
}
