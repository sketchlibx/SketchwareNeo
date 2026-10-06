package com.besome.sketch.editor.makeblock;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * UI-side model of one row in the new Moreblock creator.
 * <p>
 * It is flattened back into the legacy {@code Pair<String, String>} representation by
 * {@link MoreBlockSpecAdapter}; fields that the legacy spec cannot express
 * ({@link #defaultValue}, {@link #parameterTypeIndex}) are UI-only metadata and are never written
 * into {@code block_spec}.
 */
public final class MoreBlockEntry {
    public static final int KIND_VARIABLE = 0;
    public static final int KIND_PARAMETER = 1;

    public final int kind;

    /**
     * Variable: legacy type code exactly as {@code MoreBlockBuilderView} built it
     * ({@code "b"}, {@code "d"}, {@code "s"}, {@code "f"}, {@code "i"}, {@code "m.varMap"}, ...).
     * Parameter: legacy custom parameter code matching {@code [mldb]\.[a-zA-Z]+}, e.g. {@code "m.view"}.
     */
    @NonNull
    public String typeCode = "";

    /** Second element of the legacy pair (becomes the Java variable name / pill text). */
    @NonNull
    public String name = "";

    /** Variables only: optional text label emitted as a legacy {@code "t"} token before the variable. */
    @NonNull
    public String label = "";

    /** UI-only metadata, not part of the spec. */
    @NonNull
    public String defaultValue = "";

    /** Parameters only, UI-only metadata: 0 none, 1 String, 2 Number, 3 Boolean, 4 Map. */
    public int parameterTypeIndex = 0;

    public MoreBlockEntry(int kind) {
        this.kind = kind;
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt("kind", kind);
        bundle.putString("typeCode", typeCode);
        bundle.putString("name", name);
        bundle.putString("label", label);
        bundle.putString("defaultValue", defaultValue);
        bundle.putInt("parameterTypeIndex", parameterTypeIndex);
        return bundle;
    }

    @Nullable
    public static MoreBlockEntry fromBundle(@Nullable Bundle bundle) {
        if (bundle == null) return null;
        MoreBlockEntry entry = new MoreBlockEntry(bundle.getInt("kind", KIND_VARIABLE));
        entry.typeCode = orEmpty(bundle.getString("typeCode"));
        entry.name = orEmpty(bundle.getString("name"));
        entry.label = orEmpty(bundle.getString("label"));
        entry.defaultValue = orEmpty(bundle.getString("defaultValue"));
        entry.parameterTypeIndex = bundle.getInt("parameterTypeIndex", 0);
        return entry;
    }

    @NonNull
    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
