package neo.sketchware.ai;

public interface AiResponseCallback {
    void onSuccess(String responseText);
    void onFailure(String errorMessage);

    default void onStart(String requestId) {}
    
    default void onChunk(String requestId, String chunk) {}
    
    default void onComplete(String requestId, String fullText) {
        onSuccess(fullText);
    }
    
    default void onError(String requestId, String errorMessage) {
        onFailure(errorMessage);
    }
    
    default void onCancelled(String requestId) {}
}
