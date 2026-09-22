package mod.sketchlibx.project.history;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.besome.sketch.beans.BlockBean;

import java.util.List;
import java.util.Map;

import pro.sketchware.R;
import pro.sketchware.utility.ThemeUtils;

public class CodeComparisonActivity extends BaseAppCompatActivity {

    private static final int MENU_COPY_RAW = 1;
    private String rawOldCode, rawNewCode, rawType;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_code_comparison);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getIntent().getStringExtra("fileName"));
        toolbar.setNavigationOnClickListener(v -> finish());

        LinearLayout containerOld = findViewById(R.id.container_old);
        LinearLayout containerNew = findViewById(R.id.container_new);

        String type = getIntent().getStringExtra("type");
        String oldCode = getIntent().getStringExtra("oldCode");
        String newCode = getIntent().getStringExtra("newCode");
        rawType = type;
        rawOldCode = oldCode;
        rawNewCode = newCode;

        if ("BLOCKS".equals(type)) {
            renderBlocks(containerOld, oldCode);
            renderBlocks(containerNew, newCode);
        } else if ("XML".equals(type)) {
            renderTextDiff(containerOld, containerNew, renderViewSections(oldCode), renderViewSections(newCode), "XML");
        } else if ("JAVA".equals(type)) {
            // "file" data is the activity/custom-view registry, not Java source -
            // rendered as plain readable text, not Java-syntax-highlighted, since
            // treating it as Java was the actual bug being reported.
            renderTextDiff(containerOld, containerNew, ProjectFileRenderer.render(oldCode), ProjectFileRenderer.render(newCode), "TEXT");
        } else {
            renderTextDiff(containerOld, containerNew, oldCode, newCode, type);
        }
    }

    /**
     * Renders every section of a "view" file (each layout's widget tree, and
     * each layout's separate "_fab" section) as real XML text, labeled by
     * which file/section it came from.
     */
    private String renderViewSections(String raw) {
        if (raw == null || raw.isBlank()) return "";
        SketchwareDataFile file = SketchwareDataFile.parse(raw);
        if (file.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> section : file.sections.entrySet()) {
            String key = section.getKey();
            sb.append("<!-- ").append(key).append(" -->\n");
            if (key.endsWith("_fab")) {
                sb.append(section.getValue().isEmpty() ? "" : ViewXmlRenderer.renderSingle(section.getValue().get(0)));
            } else {
                sb.append(ViewXmlRenderer.render(section.getValue()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Plain menu.add() - no XML menu resource needed, so nothing here needs
        // guessing at unfamiliar resource conventions.
        menu.add(Menu.NONE, MENU_COPY_RAW, Menu.NONE, "Copy raw content")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_COPY_RAW) {
            copyRawToClipboard(null);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Copies the exact raw content this screen is working with to the
     * clipboard - always available from the toolbar, and also called
     * automatically when block parsing fails, so the exact failing content
     * can be shared for debugging instead of a screenshot.
     */
    private void copyRawToClipboard(String reasonPrefix) {
        String label = "Local History raw content (" + rawType + ")";
        String text = (reasonPrefix != null ? reasonPrefix + "\n\n" : "")
                + "=== OLD ===\n" + (rawOldCode != null ? rawOldCode : "")
                + "\n\n=== NEW ===\n" + (rawNewCode != null ? rawNewCode : "");

        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderTextDiff(LinearLayout cOld, LinearLayout cNew, String oldCode, String newCode, String language) {
        List<DiffUtils.DiffLine> diffs = DiffUtils.getDiff(oldCode, newCode);

        for (DiffUtils.DiffLine line : diffs) {
            if (line.type == DiffUtils.DiffType.REMOVED || line.type == DiffUtils.DiffType.UNCHANGED) {
                cOld.addView(createCodeLine(line.text, line.type == DiffUtils.DiffType.REMOVED ? "#FCE4E4" : null, language));
            } else {
                cOld.addView(createCodeLine("", null, language)); // Empty space sync
            }

            if (line.type == DiffUtils.DiffType.ADDED || line.type == DiffUtils.DiffType.UNCHANGED) {
                cNew.addView(createCodeLine(line.text, line.type == DiffUtils.DiffType.ADDED ? "#E8F5E9" : null, language));
            } else {
                cNew.addView(createCodeLine("", null, language)); // Empty space sync
            }
        }
    }

    private TextView createCodeLine(String text, String bgColor, String language) {
        TextView tv = new TextView(this);
        tv.setText(SimpleSyntaxHighlighter.highlight(text, language));
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(12f);
        // Only override the color when we're NOT syntax-highlighting - highlight()
        // already colors keywords/strings/comments; forcing colorOnSurface on top
        // afterwards would have overwritten those spans, which was the root cause
        // of "highlighting doesn't show" (Bug 3).
        if (!"JAVA".equals(language) && !"XML".equals(language)) {
            tv.setTextColor(ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOnSurface));
        }
        if (bgColor != null) tv.setBackgroundColor(Color.parseColor(bgColor));
        return tv;
    }

    private void renderBlocks(LinearLayout container, String json) {
        if (json == null || json.trim().isEmpty()) {
            container.addView(createInfoLabel("(no blocks)"));
            return;
        }

        Map<String, Map<String, List<BlockBean>>> blocksMap;
        try {
            blocksMap = BlocksJsonParser.parse(json);
        } catch (Exception e) {
            e.printStackTrace();
            copyRawToClipboard("Block parsing failed: " + e.getMessage());
            container.addView(createInfoLabel("(could not parse blocks - raw content copied to clipboard, error: " + e.getMessage() + ")"));
            return;
        }

        if (blocksMap == null || blocksMap.isEmpty()) {
            container.addView(createInfoLabel("(no blocks)"));
            return;
        }

        for (Map.Entry<String, Map<String, List<BlockBean>>> activityEntry : blocksMap.entrySet()) {
            TextView activityHeader = new TextView(this);
            activityHeader.setText(activityEntry.getKey());
            activityHeader.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            activityHeader.setTextSize(13f);
            activityHeader.setTextColor(ThemeUtils.getColor(this, android.R.attr.colorPrimary));
            activityHeader.setPadding(0, 20, 0, 6);
            container.addView(activityHeader);

            Map<String, List<BlockBean>> eventsMap = activityEntry.getValue();
            if (eventsMap == null) continue;

            for (Map.Entry<String, List<BlockBean>> eventEntry : eventsMap.entrySet()) {
                TextView eventHeader = new TextView(this);
                eventHeader.setText("  // " + eventEntry.getKey());
                eventHeader.setTypeface(Typeface.MONOSPACE);
                eventHeader.setTextSize(12f);
                eventHeader.setTextColor(ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
                eventHeader.setPadding(0, 12, 0, 2);
                container.addView(eventHeader);

                List<BlockBean> blocks = eventEntry.getValue();
                String code;
                try {
                    code = BlocksToJavaConverter.convertEvent(blocks);
                } catch (Exception e) {
                    code = "/* failed to render: " + e.getMessage() + " */";
                }

                TextView tv = new TextView(this);
                tv.setText(SimpleSyntaxHighlighter.highlight(code, "JAVA"));
                tv.setTypeface(Typeface.MONOSPACE);
                tv.setTextSize(12f);
                tv.setPadding(24, 4, 24, 12);
                container.addView(tv);
            }
        }
    }

    private TextView createInfoLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
        tv.setPadding(0, 16, 0, 16);
        return tv;
    }
}
