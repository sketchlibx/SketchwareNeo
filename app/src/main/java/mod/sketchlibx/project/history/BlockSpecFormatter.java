package mod.sketchlibx.project.history;

import com.besome.sketch.beans.BlockBean;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats a single block into a human-readable line, e.g. a block with
 * spec="When %m.view clicked" and parameters=["buttonLogin"] becomes
 * "When buttonLogin.view clicked" - the same substitution the block canvas
 * itself does, instead of showing raw JSON (spec/opCode/parameters as
 * separate fields).
 *
 * Placeholder letter (%s, %m, %d, %b, ...) isn't special-cased here -
 * whatever letter follows '%' is simply replaced with the next parameter
 * in order. This is deliberately generic so it doesn't need to know every
 * placeholder type Sketchware uses, only that they're positional.
 */
public class BlockSpecFormatter {

    private static final Pattern PLACEHOLDER = Pattern.compile("%[a-zA-Z]");

    public static String format(BlockBean block) {
        if (block == null) return "";

        String spec = block.spec;
        if (spec == null || spec.isEmpty()) {
            return block.opCode != null ? block.opCode : "(unknown block)";
        }

        List<String> params = block.parameters;
        Matcher matcher = PLACEHOLDER.matcher(spec);
        StringBuilder result = new StringBuilder();
        int paramIndex = 0;

        while (matcher.find()) {
            String replacement;
            if (params != null && paramIndex < params.size() && params.get(paramIndex) != null) {
                replacement = params.get(paramIndex);
            } else {
                replacement = matcher.group(); // no parameter available - leave the placeholder visible
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            paramIndex++;
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
