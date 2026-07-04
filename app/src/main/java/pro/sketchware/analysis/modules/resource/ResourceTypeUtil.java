package pro.sketchware.analysis.modules.resource;

import java.io.File;

import pro.sketchware.utility.FilePathUtil;

public final class ResourceTypeUtil {

    private ResourceTypeUtil() {}

    public enum Type { IMAGE, SOUND, FONT, UNKNOWN }

    public static Type typeOf(String resFullName) {
        if (resFullName == null) return Type.UNKNOWN;
        String lower = resFullName.toLowerCase();
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".webp") || lower.endsWith(".gif")) {
            return Type.IMAGE;
        }
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".m4a")) {
            return Type.SOUND;
        }
        if (lower.endsWith(".ttf") || lower.endsWith(".otf")) {
            return Type.FONT;
        }
        return Type.UNKNOWN;
    }

    public static String absolutePathOf(String scId, String resFullName) {
        FilePathUtil paths = new FilePathUtil();
        String resourceRoot = paths.getPathResource(scId);
        Type type = typeOf(resFullName);
        if (type == Type.IMAGE) {
            return new File(resourceRoot, "drawable/" + resFullName).getAbsolutePath();
        }
        return new File(resourceRoot, resFullName).getAbsolutePath();
    }
}
