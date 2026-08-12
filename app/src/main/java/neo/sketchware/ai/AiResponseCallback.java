package neo.sketchware.ai;

public interface AiResponseCallback {
    void onSuccess(String responseText);
    void onFailure(String errorMessage);
}
