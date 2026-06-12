package mod.sketchlibx.settings;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.beans.ViewBean;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import a.a.a.jC;
import a.a.a.yq;
import pro.sketchware.utility.FileUtil;

public class ProjectAnalyzerEngine {

    public static class UnusedResource {
        public int type; // 1=Image, 2=Sound, 3=Font, 4=CustomLayout
        public String name;
        public String detail;
        public Object reference;

        public UnusedResource(int type, String name, String detail, Object reference) {
            this.type = type;
            this.name = name;
            this.detail = detail;
            this.reference = reference;
        }
    }

    public static List<UnusedResource> scanForUnusedResources(String sc_id) {
        List<UnusedResource> unusedList = new ArrayList<>();
        
        ArrayList<ProjectFileBean> allFiles = new ArrayList<>();
        if (jC.b(sc_id).b() != null) allFiles.addAll(jC.b(sc_id).b());
        if (jC.b(sc_id).c() != null) allFiles.addAll(jC.b(sc_id).c());
        
        if (allFiles.isEmpty()) return unusedList;

        Set<String> usedResources = new HashSet<>();
        Set<String> usedCustomViews = new HashSet<>();

        yq javaCodeGenerator = new yq(pro.sketchware.SketchApplication.getContext(), sc_id);
        StringBuilder deepScanTextBuilder = new StringBuilder();

        // 1. Blocks and ViewBeans (Direct Sketchware Links)
        for (ProjectFileBean file : allFiles) {
            String targetFileName = file.getXmlName().isEmpty() ? file.getJavaName() : file.getXmlName();
            ArrayList<ViewBean> views = jC.a(sc_id).d(targetFileName);
            HashMap<String, ArrayList<BlockBean>> events = jC.a(sc_id).b(file.getJavaName());

            // Read Generated Java Code
            if (!file.getJavaName().isEmpty()) {
                 deepScanTextBuilder.append(javaCodeGenerator.getFileSrc(file.getJavaName(), jC.b(sc_id), jC.a(sc_id), jC.c(sc_id))).append("\n");
            }

            if (events != null) {
                for (Map.Entry<String, ArrayList<BlockBean>> entry : events.entrySet()) {
                    for (BlockBean block : entry.getValue()) {
                        if (block.parameters != null) {
                            usedResources.addAll(block.parameters);
                            usedCustomViews.addAll(block.parameters);
                        }
                    }
                }
            }

            if (views != null) {
                for (ViewBean view : views) {
                    if (view.image != null && view.image.resName != null && !view.image.resName.equals("NONE")) {
                        usedResources.add(view.image.resName);
                    }
                    if (view.layout != null && view.layout.backgroundResource != null && !view.layout.backgroundResource.equals("NONE")) {
                        usedResources.add(view.layout.backgroundResource);
                    }
                    if (view.text != null && view.text.textFont != null && !view.text.textFont.equals("default_font")) {
                        usedResources.add(view.text.textFont);
                    }
                    if (view.customView != null && !view.customView.isEmpty()) {
                        usedCustomViews.add(view.customView);
                    }
                }
            }
        }

        // 2. (Custom Java, Manifest, Resources, XMLs)
        String basePath = FileUtil.getExternalStorageDir() + "/.sketchware/";
        File dataDir = new File(basePath + "data/" + sc_id);
        File resDir = new File(basePath + "resources/" + sc_id);
        
        appendDeepScanFiles(dataDir, deepScanTextBuilder);
        appendDeepScanFiles(resDir, deepScanTextBuilder);
        
        String fullDeepText = deepScanTextBuilder.toString();

        // 3. Identify Unused Images
        ArrayList<ProjectResourceBean> images = jC.d(sc_id).b;
        if (images != null) {
            for (ProjectResourceBean img : images) {
                if (img.resName.equals("app_icon") || img.resName.equals("NONE")) continue;
                
                if (!usedResources.contains(img.resName) && 
                    !fullDeepText.contains("@drawable/" + img.resName) && 
                    !fullDeepText.contains("@mipmap/" + img.resName) && 
                    !fullDeepText.contains("R.drawable." + img.resName) && 
                    !fullDeepText.contains("R.mipmap." + img.resName)) {
                    
                    unusedList.add(new UnusedResource(1, img.resName, "Unused Image Drawable", img));
                }
            }
        }

        // 4. Identify Unused Sounds
        ArrayList<ProjectResourceBean> sounds = jC.d(sc_id).c;
        if (sounds != null) {
            for (ProjectResourceBean snd : sounds) {
                if (!usedResources.contains(snd.resName) && 
                    !fullDeepText.contains("@raw/" + snd.resName) && 
                    !fullDeepText.contains("R.raw." + snd.resName)) {
                    
                    unusedList.add(new UnusedResource(2, snd.resName, "Unused Audio File", snd));
                }
            }
        }

        // 5. Identify Unused Fonts
        ArrayList<ProjectResourceBean> fonts = jC.d(sc_id).d;
        if (fonts != null) {
            for (ProjectResourceBean fnt : fonts) {
                if (!usedResources.contains(fnt.resName) && 
                    !fullDeepText.contains("@font/" + fnt.resName) && 
                    !fullDeepText.contains("R.font." + fnt.resName) &&
                    !fullDeepText.contains(fnt.resName)) { 
                    
                    unusedList.add(new UnusedResource(3, fnt.resName, "Unused Font File", fnt));
                }
            }
        }

        // 6. Identify Unused Custom Views (Layouts)
        ArrayList<ProjectFileBean> customViews = jC.b(sc_id).c();
        if (customViews != null) {
            for (ProjectFileBean cv : customViews) {
                String rawName = cv.fileName.replace(".xml", "");
                
                if (!usedCustomViews.contains(cv.fileName) && 
                    !usedCustomViews.contains(cv.getXmlName()) &&
                    !fullDeepText.contains("@layout/" + rawName) && 
                    !fullDeepText.contains("R.layout." + rawName)) {
                    
                    unusedList.add(new UnusedResource(4, cv.getXmlName(), "Unused Custom Layout", cv));
                }
            }
        }

        return unusedList;
    }
    
    private static void appendDeepScanFiles(File dir, StringBuilder builder) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                appendDeepScanFiles(file, builder);
            } else {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".xml") || name.endsWith(".java") || name.endsWith(".kt") || 
                    name.endsWith(".json") || name.endsWith(".gradle") || name.endsWith(".pro")) {
                    builder.append(FileUtil.readFile(file.getAbsolutePath())).append("\n");
                }
            }
        }
    }
}
