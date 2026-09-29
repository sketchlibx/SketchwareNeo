package neo.sketchware.ai;

import android.content.Context;

/**
 * Generates MULTIPLE related custom palette blocks in one AI request (e.g. all the blocks
 * needed to wrap a JSON parser, or a whole ad-SDK's init/load/show/callbacks).
 *
 * Deliberately mirrors {@link CustomBlockGenTask}'s exact prompt conventions (plain %s/%b/%d
 * placeholders, same "type" vocabulary, same AiManager/AiProviderRegistry call) so the two
 * generators produce blocks that follow one consistent style - this is not a parallel AI
 * system, just a batch-shaped sibling of the existing single-block task.
 */
public final class MultiBlockGenTask {

    private static final String SYSTEM_PROMPT =
            "You are generating MULTIPLE related CUSTOM PALETTE BLOCK definitions for Sketchware Neo, a " +
            "visual Android app builder. These are NOT a project's event logic - they are new block TYPES " +
            "added to the block palette that users can drag in and use like built-in blocks. " +
            "The user will describe a library/SDK/API or a group of related operations (e.g. \"JSON Parser\" " +
            "or \"Unity Ads\"). Generate ALL the blocks logically required to use it from a Sketchware Neo " +
            "project: for an SDK, that normally means an init/setup block, action blocks (load/show/get/...), " +
            "and event/callback blocks for its important callbacks (success, failure, loaded, closed, etc.) - " +
            "use your judgement for what's actually needed, do not pad with unnecessary blocks and do not " +
            "generate just one block if the request clearly implies several. " +
            "Reply with ONLY a single JSON object, no markdown fences, no extra text, in exactly this shape: " +
            "{\"blocks\":[{\"name\":\"...\",\"type\":\"...\",\"typeName\":\"...\",\"spec\":\"...\"," +
            "\"spec2\":\"...\",\"color\":\"...\",\"imports\":\"...\",\"code\":\"...\"}, ...]} " +
            "with one object per block, using the exact same per-block field meanings as a single-block " +
            "generator would: " +
            "\"name\" = internal unique block name, short, no spaces, e.g. jsonGetElement. " +
            "\"type\" = one of: regular, c, e, s, b, d, v, a, f, l, p, h. " +
            "\"typeName\" = return type label shown on the block if it returns a value, empty string if none. " +
            "\"spec\" = the block's visible label with inline parameter placeholders. " +
            "\"spec2\" = only used when type is 'e' (if-else) - the second branch's spec, empty string otherwise. " +
            "\"color\" = a hex color like #4A90D9 - reuse the SAME color across all blocks in this batch unless " +
            "there's a good reason to distinguish a sub-group (e.g. event blocks a shade different), since they " +
            "belong to the same palette. " +
            "\"imports\" = newline-separated fully qualified Java imports this block's code needs, empty string " +
            "if none. " +
            "\"code\" = the Java code template for this block. " +
            "Block type meanings: 'regular' = plain statement/action block. 'c' = if-style control block (has " +
            "an inner stack). 'e' = if-else style (has two inner stacks, needs spec2). 's' = returns a String " +
            "value. 'b' = returns a Boolean value. 'd' = returns a Number value. 'v' = variable-style block. " +
            "'a' = returns a Map. 'f' = a stop/terminator block (like break/return, no inner stack " +
            "continuation). 'l' = returns a List. 'p' = component-style block. 'h' = header/label block. An " +
            "event/callback block (e.g. \"Ad Loaded\") is normally type 'h' paired with a matching listener " +
            "registered in the init/load block's code - prefer this pattern for callbacks over inventing a new " +
            "shape. Prefer 'regular', 's', 'b', or 'd' for non-event blocks unless control flow is clearly " +
            "needed - those are safer and more predictable. " +
            "Spec syntax for parameters (place inline in the spec text where that parameter's input socket " +
            "should appear): %s = string input socket, %b = boolean input socket, %d = number input socket, " +
            "%s.inputOnly = plain inline text field with no plug. For a special typed socket use %m.<kind> " +
            "where <kind> is one of: varMap, view, textview, edittext, imageview, listview, list, listMap, " +
            "listStr, listInt, intent, color, activity, resource, customViews, layout, anim, drawable, " +
            "ResString. Every %m must be immediately followed by a dot and one of those exact kinds - never a " +
            "bare %m. " +
            "The code field is a Java code template using the SAME placeholder tokens (%s, %b, %d) in the same " +
            "left-to-right order as they appear in spec - each will be substituted with the actual " +
            "value/expression plugged into that socket when the project builds. Keep code minimal, correct " +
            "Java, and make sure the number and order of %s/%b/%d in code matches spec exactly. " +
            "Every \"name\" across ALL blocks in this response, and against the existing names listed below, " +
            "must be unique - never repeat a name within your own response.";

    private MultiBlockGenTask() {}

    public static void generate(Context context, String existingBlockNames, String libraryOrTopic, String userPrompt, AiResponseCallback callback) {
        StringBuilder fullPrompt = new StringBuilder();
        if (libraryOrTopic != null && !libraryOrTopic.trim().isEmpty()) {
            fullPrompt.append("Library/SDK/API or topic: ").append(libraryOrTopic.trim()).append("\n\n");
        }
        fullPrompt.append(userPrompt);
        if (existingBlockNames != null && !existingBlockNames.isEmpty()) {
            fullPrompt.append("\n\nThese block names already exist, so no \"name\" in your response may match any of them:\n")
                    .append(existingBlockNames);
        }

        AiManager.sendPrompt(context, SYSTEM_PROMPT, fullPrompt.toString(), callback);
    }
}
