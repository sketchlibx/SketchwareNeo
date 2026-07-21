package neo.sketchware.plugin;

import java.util.List;
import java.util.Map;

public record NeoBlockContribution(String categoryName, String categoryColorHex, List<Map<String, Object>> blocks) {
}
