package com.besome.sketch.editor.makeblock;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import a.a.a.uq;

/**
 * Semantic bridge between the new creator's richer model and the legacy Moreblock representation.
 * <p>
 * The legacy builder ({@link MoreBlockBuilderView}) keeps one ordered {@code List<Pair<String,String>>}
 * and turns it into a spec string in {@code updateBlockPreview}. {@link #flatten} produces that exact
 * pair list and {@link #buildSpec} reproduces that exact string rules, so the final
 * {@code block_name} / {@code block_spec} stay byte-compatible. The legacy classes are not modified.
 * <p>
 * Order: all variables (in list order), then all custom parameters (in list order).
 */
public final class MoreBlockSpecAdapter {
    /** Same pattern the legacy custom parameter field uses. */
    public static final Pattern CUSTOM_PARAMETER_PATTERN = Pattern.compile("[mldb]\\.[a-zA-Z]+");

    private MoreBlockSpecAdapter() {
    }

    /** One legacy pair plus the UI entry it came from (needed for the preview's remove icons). */
    public static final class Token {
        public final Pair<String, String> pair;
        public final MoreBlockEntry owner;
        /** True if this token is the optional "t" label of a variable rather than the variable itself. */
        public final boolean isLabel;

        Token(Pair<String, String> pair, MoreBlockEntry owner, boolean isLabel) {
            this.pair = pair;
            this.owner = owner;
            this.isLabel = isLabel;
        }
    }

    @NonNull
    public static List<Token> flatten(@NonNull List<MoreBlockEntry> variables, @NonNull List<MoreBlockEntry> parameters) {
        List<Token> tokens = new ArrayList<>();
        for (MoreBlockEntry variable : variables) {
            // The pill already shows the variable name; only emit a separate label if it adds something.
            if (!variable.label.isEmpty() && !variable.label.equals(variable.name)) {
                tokens.add(new Token(new Pair<>("t", variable.label), variable, true));
            }
            tokens.add(new Token(new Pair<>(variable.typeCode, variable.name), variable, false));
        }
        for (MoreBlockEntry parameter : parameters) {
            tokens.add(new Token(new Pair<>(parameter.typeCode, parameter.name), parameter, false));
        }
        return tokens;
    }

    /** Verbatim port of the string rules in {@code MoreBlockBuilderView.updateBlockPreview}. This is the spec that gets saved. */
    @NonNull
    public static String buildSpec(@NonNull String blockName, @NonNull List<Token> tokens) {
        return buildSpec(blockName, tokens, false);
    }

    /**
     * Same as {@link #buildSpec(String, List)} but Float / Int variables are drawn as Number ({@code %d}) pills.
     * {@code BlockUtil.getVariableBlock} draws {@code m.varFloat} / {@code m.varIntNum} as plain view-shaped
     * pills, while the parameter is a number; this string is only ever used to render the preview.
     */
    @NonNull
    public static String buildPreviewSpec(@NonNull String blockName, @NonNull List<Token> tokens) {
        return buildSpec(blockName, tokens, true);
    }

    @NonNull
    private static String buildSpec(@NonNull String blockName, @NonNull List<Token> tokens, boolean preview) {
        StringBuilder fullSpec = new StringBuilder(blockName);
        for (Token token : tokens) {
            String parameterType = token.pair.first;
            String parameterName = token.pair.second;
            if (preview && (parameterType.equals("m.varFloat") || parameterType.equals("m.varIntNum"))) {
                parameterType = "d";
            }

            switch (parameterType) {
                case "b":
                    fullSpec.append(" %b.").append(parameterName);
                    break;

                case "d":
                    fullSpec.append(" %d.").append(parameterName);
                    break;

                case "s":
                    fullSpec.append(" %s.").append(parameterName);
                    break;

                default:
                    if (parameterType.length() > 2 && parameterType.contains(".")) {
                        fullSpec.append(" %").append(parameterType).append(".").append(parameterName);
                    } else {
                        fullSpec.append(" ").append(parameterName);
                    }
                    break;
            }
        }
        return fullSpec.toString();
    }

    /**
     * Names that a new variable name must not collide with: the same set the legacy creator feeds into
     * its variable-name validator (event names + every non-label name already used).
     */
    @NonNull
    public static String[] reservedNames(@NonNull List<MoreBlockEntry> variables, @NonNull List<MoreBlockEntry> parameters, @Nullable MoreBlockEntry ignore) {
        ArrayList<String> reserved = new ArrayList<>(Arrays.asList(uq.a()));
        for (MoreBlockEntry entry : variables) {
            if (entry != ignore) reserved.add(entry.name);
        }
        for (MoreBlockEntry entry : parameters) {
            if (entry != ignore) reserved.add(entry.name);
        }
        return reserved.toArray(new String[0]);
    }
}
