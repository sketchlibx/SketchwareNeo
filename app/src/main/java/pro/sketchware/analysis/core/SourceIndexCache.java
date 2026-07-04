package pro.sketchware.analysis.core;

import java.util.concurrent.ConcurrentHashMap;

public final class SourceIndexCache {

    private static final class Entry {
        final long fingerprint;
        final String content;
        Entry(long fingerprint, String content) {
            this.fingerprint = fingerprint;
            this.content = content;
        }
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    
    public String getIfUnchanged(String path, long fingerprint) {
        Entry e = entries.get(path);
        if (e != null && e.fingerprint == fingerprint) {
            return e.content;
        }
        return null;
    }

    public void put(String path, long fingerprint, String content) {
        entries.put(path, new Entry(fingerprint, content));
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }
}
