package neo.sketchware.ai;

public interface ErrorFixCallback {
    void onResult(ErrorFixResult result);
    void onError(String message);
}
