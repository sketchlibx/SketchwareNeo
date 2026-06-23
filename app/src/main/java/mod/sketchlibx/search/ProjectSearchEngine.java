package mod.sketchlibx.search;

import android.text.TextUtils;
import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import a.a.a.jC;

public class ProjectSearchEngine {

    private final String sc_id;

    public ProjectSearchEngine(String sc_id) {
        this.sc_id = sc_id;
    }

    public List<SearchResult> search(String query, String filter) {
        List<SearchResult> results = new ArrayList<>();
        if (TextUtils.isEmpty(query)) return results;

        String q = query.toLowerCase();
        
        boolean searchViews = filter.equals("All") || filter.equals("Views");
        boolean searchLogic = filter.equals("All") || filter.equals("Logic");
        boolean searchComps = filter.equals("All") || filter.equals("Components");
        
        ArrayList<ProjectFileBean> allFiles = jC.b(sc_id).b();
        if (allFiles == null) return results;

        for (ProjectFileBean file : allFiles) {
            // FIXED BUG: Views are stored under xmlName, Logic/Components under javaName.
            String xmlName = file.getXmlName();
            String javaName = file.getJavaName();

            // 1. SCAN VIEWS (XML UI)
            if (searchViews && !TextUtils.isEmpty(xmlName)) {
                ArrayList<ViewBean> views = jC.a(sc_id).d(xmlName);
                if (views != null) {
                    for (ViewBean view : views) {
                        if (view.id.toLowerCase().contains(q)) {
                            results.add(new SearchResult(
                                    xmlName, "View", 
                                    view.id, 
                                    "Found in " + xmlName, 0, view.id, null));
                        }
                    }
                }
            }

            // 2. SCAN COMPONENTS & VARIABLES
            if (searchComps && !TextUtils.isEmpty(javaName)) {
                ArrayList<ComponentBean> components = jC.a(sc_id).e(javaName);
                if (components != null) {
                    for (ComponentBean comp : components) {
                        if (comp.componentId.toLowerCase().contains(q)) {
                            results.add(new SearchResult(
                                    javaName, "Component", 
                                    comp.componentId, 
                                    "Found in " + javaName, 2, comp.componentId, null));
                        }
                    }
                }

                ArrayList<Pair<Integer, String>> variables = jC.a(sc_id).k(javaName);
                if (variables != null) {
                    for (Pair<Integer, String> var : variables) {
                        if (var.second.toLowerCase().contains(q)) {
                            results.add(new SearchResult(
                                    javaName, "Variable", 
                                    var.second, "Found in " + javaName, 2, var.second, null));
                        }
                    }
                }

                ArrayList<Pair<Integer, String>> lists = jC.a(sc_id).j(javaName);
                if (lists != null) {
                    for (Pair<Integer, String> list : lists) {
                        if (list.second.toLowerCase().contains(q)) {
                            results.add(new SearchResult(
                                    javaName, "List", 
                                    list.second, "Found in " + javaName, 2, list.second, null));
                        }
                    }
                }
            }

            // 3. SCAN LOGIC BLOCKS & EVENTS
            if (searchLogic && !TextUtils.isEmpty(javaName)) {
                HashMap<String, ArrayList<BlockBean>> events = jC.a(sc_id).b(javaName);
                if (events != null) {
                    for (Map.Entry<String, ArrayList<BlockBean>> entry : events.entrySet()) {
                        String eventKey = entry.getKey(); 
                        String targetId = eventKey;
                        String eventName = "";
                        
                        int lastUnderscore = eventKey.lastIndexOf("_");
                        if (lastUnderscore != -1) {
                            targetId = eventKey.substring(0, lastUnderscore);
                            eventName = eventKey.substring(lastUnderscore + 1);
                        }

                        ArrayList<BlockBean> blocks = entry.getValue();
                        for (BlockBean block : blocks) {
                            boolean matched = block.opCode.toLowerCase().contains(q);
                            if (!matched && block.parameters != null) {
                                for (String param : block.parameters) {
                                    if (param != null && param.toLowerCase().contains(q)) {
                                        matched = true;
                                        break;
                                    }
                                }
                            }
                            
                            if (matched) {
                                results.add(new SearchResult(
                                        javaName, "Logic Block", 
                                        "Event: " + eventKey, 
                                        "Block: " + block.opCode, 1, targetId, eventName));
                                break;
                            }
                        }
                    }
                }
            }
        }
        return results;
    }
}
