package neo.sketchware.ai;

import android.content.Context;

public final class CustomBlockGenTask {

    private static final String SYSTEM_PROMPT =
            "You are generating a CUSTOM PALETTE BLOCK definition for Sketchware Neo, a visual Android app " +
            "builder. This is NOT a project's event logic - it's a new block TYPE added to the block palette " +
            "that users can drag in and use like any built-in block. " +
            "Reply with ONLY a single JSON object, no markdown fences, no extra text, in exactly this shape: " +
            "{\"name\":\"internal unique block name, short, no spaces, e.g. myCustomBlock\"," +
            "\"type\":\"one of: regular, c, e, s, b, d, v, a, f, l, p, h\"," +
            "\"typeName\":\"return type label shown on the block if it returns a value, empty string if none\"," +
            "\"spec\":\"the block's visible label with inline parameter placeholders\"," +
            "\"spec2\":\"only used when type is 'e' (if-else) - the second branch's spec, empty string otherwise\"," +
            "\"color\":\"a hex color like #4A90D9\"," +
            "\"imports\":\"newline-separated fully qualified Java imports this block's code needs, empty string if none\"," +
            "\"code\":\"the Java code template for this block\"}" +
            " Block type meanings: 'regular' = plain statement/action block. 'c' = if-style control block " +
            "(has an inner stack). 'e' = if-else style (has two inner stacks, needs spec2). 's' = returns a " +
            "String value. 'b' = returns a Boolean value. 'd' = returns a Number value. 'v' = variable-style " +
            "block. 'a' = returns a Map. 'f' = a stop/terminator block (like break/return, no inner stack " +
            "continuation). 'l' = returns a List. 'p' = component-style block. 'h' = header/label block. " +
            "Prefer 'regular', 's', 'b', or 'd' unless the request clearly needs control flow - those are safer " +
            "and more predictable. " +
            "Spec syntax for parameters (place these inline in the spec text where that parameter's input " +
            "socket should appear): %s = string input socket, %b = boolean input socket, %d = number input " +
            "socket, %s.inputOnly = plain inline text field with no plug (not a socket for another block). " +
            "For a special typed socket use %m.<kind> where <kind> is one of: varMap, view, textview, edittext, " +
            "imageview, listview, list, listMap, listStr, listInt, intent, color, activity, resource, " +
            "customViews, layout, anim, drawable, ResString. Every %m must be immediately followed by a dot and " +
            "one of those exact kinds - never a bare %m. " +
            "The code field is a Java code template using the SAME placeholder tokens (%s, %b, %d) in the same " +
            "left-to-right order as they appear in spec - each placeholder in code will be substituted with the " +
            "actual value/expression the user plugs into that socket at that position when the project builds. " +
            "Keep the code minimal, correct Java, and make sure the number and order of %s/%b/%d placeholders in " +
            "code matches the number and order of %s/%b/%d sockets in spec exactly.";

    private CustomBlockGenTask() {}

    public static void generate(Context context, String existingBlockNames, String userPrompt, AiResponseCallback callback) {
        StringBuilder fullPrompt = new StringBuilder(userPrompt);
        if (existingBlockNames != null && !existingBlockNames.isEmpty()) {
            fullPrompt.append("\n\nThese block names already exist in this palette, so \"name\" must NOT match any of them:\n")
                    .append(existingBlockNames);
        }

        AiManager.sendPrompt(context, SYSTEM_PROMPT, fullPrompt.toString(), callback);
    }
}
