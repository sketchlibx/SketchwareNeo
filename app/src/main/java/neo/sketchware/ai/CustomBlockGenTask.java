package neo.sketchware.ai;

import android.content.Context;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

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
            "socket, %f = decimal input socket, %i = integer input socket, %s.inputOnly = plain inline text field with no plug (not a socket for another block). " +
            "For a special typed socket use %m.<kind> where <kind> is one of: varMap, view, textview, edittext, " +
            "imageview, listview, list, listMap, listStr, listInt, intent, color, activity, resource, " +
            "customViews, layout, anim, drawable, ResString. Every %m must be immediately followed by a dot and " +
            "one of those exact kinds - never a bare %m. " +
            "The code field is a Java code template processed by String.format with Java EXPRESSION TEXT as its " +
            "arguments. Therefore use %s in code for EVERY input socket in the same left-to-right order, regardless " +
            "of whether the spec socket is %s, %b, %d, %f, %i, %s.inputOnly, or typed %m.<kind>. Never use %b or %d in " +
            "the code template: those format the expression text itself and break generated Java. For type 'c', " +
            "append exactly one additional %s placeholder for its inner stack; for type 'e', append two %s stack " +
            "placeholders after all spec inputs. Use no stack placeholders for other types. Do not put literal " +
            "formatter tokens inside Java string literals. Keep the code minimal, correct Java, and match the exact " +
            "placeholder count/order required by the spec and block type.";

    private CustomBlockGenTask() {}

    public static void generate(Context context, String existingBlockNames, String userPrompt, AiResponseCallback callback) {
        StringBuilder fullPrompt = new StringBuilder(userPrompt);
        if (existingBlockNames != null && !existingBlockNames.isEmpty()) {
            fullPrompt.append("\n\nThese block names already exist in this palette, so \"name\" must NOT match any of them:\n")
                    .append(existingBlockNames);
        }

        AiManager.sendPrompt(context, SYSTEM_PROMPT, fullPrompt.toString(), AiManager.Feature.CUSTOM_BLOCKS, new AiResponseCallback() {
            @Override public void onSuccess(String responseText) {
                try {
                    String jsonText = responseText == null ? "" : responseText.trim();
                    int start = jsonText.indexOf('{');
                    int end = jsonText.lastIndexOf('}');
                    if (start < 0 || end < start) throw new org.json.JSONException("No JSON object was returned.");
                    JSONObject block = new JSONObject(jsonText.substring(start, end + 1));
                    Set<String> existing = new HashSet<>();
                    if (existingBlockNames != null) {
                        for (String name : existingBlockNames.split("[,\\s]+")) if (!name.trim().isEmpty()) existing.add(name.trim());
                    }
                    GeneratedBlockValidator.Result validation = GeneratedBlockValidator.validate(block, null, existing, new HashSet<String>());
                    if (!validation.ok) {
                        callback.onFailure("Generated block failed validation: " + validation.error + ". Try a simpler block request.");
                        return;
                    }
                    callback.onSuccess(block.toString());
                } catch (Exception e) {
                    callback.onFailure("AI returned an invalid block definition: " + (e.getMessage() == null ? "invalid JSON" : e.getMessage()));
                }
            }
            @Override public void onFailure(String errorMessage) { callback.onFailure(errorMessage); }
        });
    }
}
