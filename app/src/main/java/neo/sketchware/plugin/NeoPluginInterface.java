package neo.sketchware.plugin;

import java.util.Collections;
import java.util.List;

public interface NeoPluginInterface {

    String getPluginId();

    int getPluginApiVersion();

    void onLoad(NeoPluginContext context) throws Exception;

    void onUnload();

    default List<NeoBlockContribution> getBlockContributions() {
        return Collections.emptyList();
    }

    default List<NeoDrawerEntry> getDrawerEntries() {
        return Collections.emptyList();
    }
}
