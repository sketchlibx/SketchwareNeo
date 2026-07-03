package mod.sketchlibx.sync;

import com.besome.sketch.beans.BlockBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.eC;
import a.a.a.jC;
import pro.sketchware.utility.SketchwareUtil;

public class BlockSyncEngine {

    public static void syncJavaToBlocks(String sc_id, String activityName, String newJavaContent) throws Exception {
        if (sc_id == null || activityName == null || newJavaContent == null) return;

        // Get the logic data manager (eC)
        eC dataManager = jC.a(sc_id);
        
        // 1. Get the current File's Logic Map (Event Name -> Block List)
        String javaFileName = activityName + ".java"; 
        HashMap<String, ArrayList<BlockBean>> allLogicBlocks = dataManager.b(javaFileName);
        
        if (allLogicBlocks == null) return;

        boolean isModified = false;

        // 2. Iterate through all events we have recorded in the Source Map
        Map<String, Map<String, List<SourceMapRegistry.BlockRecord>>> fileMap = SourceMapRegistry.registry.get(sc_id);
        if (fileMap == null || !fileMap.containsKey(activityName)) return;

        Map<String, List<SourceMapRegistry.BlockRecord>> eventsMap = fileMap.get(activityName);

        for (String eventName : eventsMap.keySet()) {
            List<SourceMapRegistry.BlockRecord> mappedBlocks = eventsMap.get(eventName);
            ArrayList<BlockBean> originalBlocks = allLogicBlocks.get(eventName);
            
            if (originalBlocks == null || mappedBlocks == null || mappedBlocks.isEmpty()) continue;

            // 3. Simple Structural Diffing
            for (SourceMapRegistry.BlockRecord record : mappedBlocks) {
                // If original block code contains strings, user might have changed them.
                // Example original: textview1.setText("Hello");
                // We create a regex to find: textview1.setText("(.*?)");
                
                String regexPattern = escapeRegexAndCreateCaptureGroups(record.generatedCode);
                
                if (regexPattern != null && !regexPattern.equals(record.generatedCode)) {
                    Pattern pattern = Pattern.compile(regexPattern);
                    Matcher matcher = pattern.matcher(newJavaContent);
                    
                    if (matcher.find()) {
                        // Match found with modified arguments! Update the Block.
                        BlockBean targetBlock = findBlockById(originalBlocks, record.blockId);
                        if (targetBlock != null) {
                            updateBlockParameters(targetBlock, matcher);
                            isModified = true;
                        }
                    } else if (!newJavaContent.contains(record.generatedCode)) {
                        // The entire block structure was deleted or irreparably modified by user.
                        // Convert to Add Source Directly to preserve project integrity.
                        BlockBean targetBlock = findBlockById(originalBlocks, record.blockId);
                        if (targetBlock != null && !targetBlock.opCode.equals("addSourceDirectly")) {
                            targetBlock.opCode = "addSourceDirectly";
                            targetBlock.parameters.clear();
                            // Attempt to extract the closest line from the new content based on context,
                            // or fallback to preserving a warning comment.
                            targetBlock.parameters.add("/* Code structurally modified by user outside of Blocks */");
                            isModified = true;
                        }
                    }
                }
            }
        }

        // 4. Commit changes to Project Data Manager
        if (isModified) {
            dataManager.j(); // Saves block data to memory/disk
            SketchwareUtil.toast("Blocks Synchronized with Java edits!");
        }
    }

    private static BlockBean findBlockById(ArrayList<BlockBean> blocks, String id) {
        for (BlockBean block : blocks) {
            if (block.id.equals(id)) return block;
        }
        return null;
    }

    private static String escapeRegexAndCreateCaptureGroups(String generatedCode) {
        // Escapes the Java string to be used as a regex, replacing quoted strings with capture groups.
        // Example: textview1.setText("Hello"); -> textview1\.setText\("(.*?)"\);
        
        if (!generatedCode.contains("\"")) return generatedCode; // Nothing to extract

        String escaped = generatedCode
                .replace("\\", "\\\\")
                .replace(".", "\\.")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("+", "\\+")
                .replace("*", "\\*")
                .replace("?", "\\?");

        // Replace literal "..." with capture groups "(.*?)"
        escaped = escaped.replaceAll("\"(.*?)\"", "\"([^\\\\\"]*)\"");

        return escaped;
    }

    private static void updateBlockParameters(BlockBean block, Matcher matcher) {
        int groupCount = matcher.groupCount();
        int paramIndex = 0;
        
        for (int i = 1; i <= groupCount; i++) {
            String extractedValue = matcher.group(i);
            
            // Update the string parameters in the block
            while (paramIndex < block.parameters.size()) {
                // Find the next string parameter to update
                if (block.parameters.get(paramIndex) != null) {
                    block.parameters.set(paramIndex, extractedValue);
                    paramIndex++;
                    break;
                }
                paramIndex++;
            }
        }
    }
}
