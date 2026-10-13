package org.jellyfin.mobile.player.cast;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Compatibility adapter to make CastCallback work like the old JavascriptCallback
 * This allows existing Chromecast code to work without major refactoring
 */
public class JavascriptCallbackCompat implements CastCallback {
    private final CastCallback delegate;
    
    public JavascriptCallbackCompat(CastCallback delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public void onSuccess(boolean keep, String result) {
        delegate.onSuccess(keep, result);
    }
    
    @Override
    public void onError(boolean keep, String message) {
        delegate.onError(keep, message);
    }
    
    // Old JavascriptCallback interface methods for compatibility
    public void success(boolean keep, String result) {
        onSuccess(keep, result);
    }
    
    public void success(boolean keep, JSONObject result) {
        onSuccess(keep, result != null ? result.toString() : null);
    }
    
    public void success(boolean keep, JSONArray result) {
        onSuccess(keep, result != null ? result.toString() : null);
    }
    
    public void success(String result) {
        onSuccess(false, result);
    }
    
    public void success(JSONObject result) {
        onSuccess(false, result != null ? result.toString() : null);
    }
    
    public void success(JSONArray result) {
        onSuccess(false, result != null ? result.toString() : null);
    }
    
    public void success() {
        onSuccess(false, null);
    }
    
    public void error(boolean keep, String message) {
        onError(keep, message);
    }
    
    public void error(boolean keep, JSONObject error) {
        onError(keep, error != null ? error.toString() : "Unknown error");
    }
    
    public void error(String message) {
        onError(false, message);
    }
    
    public void error(JSONObject error) {
        onError(false, error != null ? error.toString() : "Unknown error");
    }
}
