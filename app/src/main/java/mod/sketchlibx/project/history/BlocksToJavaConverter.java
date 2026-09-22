package mod.sketchlibx.project.history;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.hilal.saif.blocks.BlocksHandler;

/**
 * Converts a chain of BlockBean into actual Java-like source text, by
 * substituting each block's real parameters (and any nested substack code)
 * into the REAL code template for that block type - the same templates
 * BlocksHandler.builtInBlocks() registers for the block palette/editor.
 *
 * This is deliberately NOT a reimplementation of those templates - it calls
 * BlocksHandler.builtInBlocks() directly to get them, so if block
 * definitions are ever added/changed, this stays correct automatically
 * without needing to be updated here too.
 *
 * Placeholder convention (confirmed against BlockBean/BlocksHandler): a
 * template's placeholders (%s, %d, %b, %m, ...) map POSITIONALLY - the first
 * N placeholders (N = block.parameters.size()) are filled from parameters,
 * in order; any placeholders BEYOND that map to the block's substacks, in
 * order (subStack1's generated code first, then subStack2's, if present).
 * E.g. viewOnClick's code has 2 placeholders but only 1 parameter (the view
 * name) - the 2nd is the click handler body (subStack1).
 */
public class BlocksToJavaConverter {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-zA-Z]");
    private static Map<String, String> templateCache;

    private static synchronized Map<String, String> getTemplates() {
        if (templateCache == null) {
            templateCache = new HashMap<>();
            ArrayList<HashMap<String, Object>> allBlocks = new ArrayList<>();
            try {
                BlocksHandler.builtInBlocks(allBlocks);
            } catch (Throwable ignored) {
                // If this ever fails (e.g. a dependency inside builtInBlocks isn't
                // available in this context), fall back to an empty registry -
                // convertChain()/convertSingleBlock() already handle unknown
                // opCodes gracefully.
            }
            for (HashMap<String, Object> def : allBlocks) {
                Object name = def.get("name");
                Object code = def.get("code");
                if (name instanceof String && code instanceof String) {
                    templateCache.put((String) name, (String) code);
                }
            }
        }
        return templateCache;
    }

    /**
     * Convenience overload for a single event: finds the chain's root block
     * automatically (the one block, if any, that no other block in this
     * event points to via nextBlock/subStack1/subStack2), rather than
     * assuming any particular id numbering convention (e.g. that the root is
     * always id "1" - not verified, so not relied on).
     */
    public static String convertEvent(List<BlockBean> blocksInEvent) {
        if (blocksInEvent == null || blocksInEvent.isEmpty()) return "";

        java.util.Set<String> referenced = new java.util.HashSet<>();
        for (BlockBean b : blocksInEvent) {
            if (b == null) continue;
            if (b.nextBlock != -1) referenced.add(String.valueOf(b.nextBlock));
            if (b.subStack1 != -1) referenced.add(String.valueOf(b.subStack1));
            if (b.subStack2 != -1) referenced.add(String.valueOf(b.subStack2));
        }

        StringBuilder sb = new StringBuilder();
        for (BlockBean b : blocksInEvent) {
            if (b != null && b.id != null && !referenced.contains(b.id)) {
                sb.append(convertChain(blocksInEvent, b.id));
            }
        }
        return sb.toString();
    }

    /**
     * Converts one event's full block chain into Java-like text.
     *
     * @param blocksInEvent all blocks belonging to this single event (as
     *                      stored - one flat list, chain order determined by
     *                      nextBlock/subStack1/subStack2 pointers, not list order)
     * @param startBlockId  the id of the first block in the chain (commonly "1",
     *                      but not assumed - pass whatever the event's root is)
     */
    public static String convertChain(List<BlockBean> blocksInEvent, String startBlockId) {
        Map<String, BlockBean> byId = new HashMap<>();
        if (blocksInEvent != null) {
            for (BlockBean b : blocksInEvent) {
                if (b != null && b.id != null) byId.put(b.id, b);
            }
        }
        return convertChain(byId, startBlockId, 0);
    }

    private static String convertChain(Map<String, BlockBean> byId, String startBlockId, int depth) {
        StringBuilder sb = new StringBuilder();
        String currentId = startBlockId;
        int guard = 0; // defends against a corrupt/circular nextBlock chain
        while (currentId != null && !"-1".equals(currentId) && guard++ < 5000) {
            BlockBean block = byId.get(currentId);
            if (block == null) break;
            sb.append(indent(depth)).append(convertSingleBlock(block, byId, depth)).append('\n');
            currentId = block.nextBlock == -1 ? null : String.valueOf(block.nextBlock);
        }
        return sb.toString();
    }

    private static final Pattern BLOCK_REFERENCE = Pattern.compile("^@(\\d+)$");

    private static String convertSingleBlock(BlockBean block, Map<String, BlockBean> byId, int depth) {
        if (block == null) return "";

        String template = getTemplates().get(block.opCode);
        if (template == null) {
            return "/* unknown block: " + (block.opCode != null ? block.opCode : "?") + " */";
        }

        List<String> rawParams = block.parameters != null ? block.parameters : java.util.Collections.emptyList();

        // A parameter like "@12" is not a literal value - it's a reference to
        // another block (an inline expression, e.g. the boolean condition of
        // an ifElse, or a nested string/number computation) whose OWN
        // generated code should be substituted here instead of the literal
        // text "@12". Confirmed against real block data, not assumed.
        List<String> params = new ArrayList<>(rawParams.size());
        for (String p : rawParams) {
            if (p != null) {
                Matcher refMatch = BLOCK_REFERENCE.matcher(p);
                if (refMatch.matches()) {
                    BlockBean referenced = byId.get(refMatch.group(1));
                    params.add(referenced != null ? convertSingleBlock(referenced, byId, depth) : p);
                    continue;
                }
            }
            params.add(p != null ? p : "");
        }

        List<String> substacks = new ArrayList<>();
        if (block.subStack1 != -1) {
            substacks.add(convertChain(byId, String.valueOf(block.subStack1), depth + 1).stripTrailing());
        }
        if (block.subStack2 != -1) {
            substacks.add(convertChain(byId, String.valueOf(block.subStack2), depth + 1).stripTrailing());
        }

        return substitute(template, params, substacks);
    }

    private static String substitute(String template, List<String> params, List<String> substacks) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        int index = 0;
        int paramCount = params.size();

        while (matcher.find()) {
            String replacement;
            if (index < paramCount) {
                String p = params.get(index);
                replacement = p != null ? p : "";
            } else {
                int substackIndex = index - paramCount;
                replacement = substackIndex < substacks.size() ? substacks.get(substackIndex) : "";
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            index++;
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String indent(int depth) {
        return "    ".repeat(Math.max(0, depth));
    }
}
