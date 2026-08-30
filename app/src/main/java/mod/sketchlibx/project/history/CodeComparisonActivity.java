package mod.sketchlibx.project.history;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.besome.sketch.beans.BlockBean;

import java.util.List;
import java.util.Map;

import pro.sketchware.R;
import pro.sketchware.utility.ThemeUtils;

public class CodeComparisonActivity extends BaseAppCompatActivity {

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

        if ("BLOCKS".equals(type)) {
            renderBlocks(containerOld, oldCode);
            renderBlocks(containerNew, newCode);
        } else {
            renderTextDiff(containerOld, containerNew, oldCode, newCode, type);
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

        Map<String, List<BlockBean>> blocksMap;
        try {
            blocksMap = BlocksJsonParser.parse(json);
        } catch (Exception e) {
            e.printStackTrace();
            container.addView(createInfoLabel("(could not parse blocks: " + e.getMessage() + ")"));
            return;
        }

        if (blocksMap == null || blocksMap.isEmpty()) {
            container.addView(createInfoLabel("(no blocks)"));
            return;
        }

        for (Map.Entry<String, List<BlockBean>> entry : blocksMap.entrySet()) {
            TextView header = new TextView(this);
            header.setText(entry.getKey());
            header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            header.setTextSize(12f);
            header.setTextColor(ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
            header.setPadding(0, 16, 0, 4);
            container.addView(header);

            List<BlockBean> blocks = entry.getValue();
            if (blocks == null) continue;

            for (BlockBean block : blocks) {
                if (block == null) continue;

                TextView tv = new TextView(this);
                tv.setText(BlockSpecFormatter.format(block));
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(12f);
                tv.setPadding(24, 16, 24, 16);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, 0, 4);
                tv.setLayoutParams(params);

                String opCode = block.opCode != null ? block.opCode : "";
                GradientDrawable gd = new GradientDrawable();
                gd.setCornerRadius(8f);
                gd.setColor(opCode.equals("getArg") ? Color.parseColor("#4CAF50") : Color.parseColor("#2196F3"));
                tv.setBackground(gd);

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
