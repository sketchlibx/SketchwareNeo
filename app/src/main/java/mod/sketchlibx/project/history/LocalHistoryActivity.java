package mod.sketchlibx.project.history;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;

import pro.sketchware.R;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class LocalHistoryActivity extends BaseAppCompatActivity {

    private String sc_id;
    private RecyclerView recyclerView;
    private CircularProgressIndicator progressBar;
    private final List<SnapshotGroup> groups = new ArrayList<>();

    private Cipher mCipher;

    /** One row = one snapshot zip - directly restorable as a whole, with the
     *  specific files it changed (relative to the previous snapshot) nested
     *  underneath as expandable sub-items. */
    private static class SnapshotGroup {
        String timestamp;
        File zipFile;
        final List<ChangedFile> changedFiles = new ArrayList<>();
        boolean expanded = false;
    }

    private static class ChangedFile {
        String fileName, type, oldContent, newContent, zipEntryName;
        File snapshotZip;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_history);

        sc_id = getIntent().getStringExtra("sc_id");

        try {
            mCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] bytes = "sketchwaresecure".getBytes();
            mCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(bytes, "AES"), new IvParameterSpec(bytes));
        } catch (Exception e) {
            e.printStackTrace();
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        progressBar = findViewById(R.id.progress_bar);

        loadHistoryAsync();
    }

    private void loadHistoryAsync() {
        new Thread(() -> {
            File historyFolder = new File(Environment.getExternalStorageDirectory(), ".sketchware/backups/history/" + sc_id);
            if (historyFolder.exists() && historyFolder.listFiles() != null) {
                File[] files = historyFolder.listFiles((dir, name) -> name.endsWith(".zip"));
                if (files != null && files.length > 0) {
                    Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

                    for (int i = 0; i < files.length; i++) {
                        File olderZip = (i + 1 < files.length) ? files[i + 1] : null;
                        SnapshotGroup group = buildGroup(files[i], olderZip);
                        if (group != null) groups.add(group);
                    }
                }
            }

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (groups.isEmpty()) {
                    SketchwareUtil.toast("No history changes found yet.");
                } else {
                    recyclerView.setAdapter(new HistoryAdapter());
                }
            });
        }).start();
    }

    /** Builds one snapshot's group: what changed to reach currentZip's state
     *  from olderZip's state (or from nothing, if currentZip is the oldest). */
    private SnapshotGroup buildGroup(File currentZip, File olderZip) {
        try (ZipFile zipNew = new ZipFile(currentZip)) {
            SnapshotGroup group = new SnapshotGroup();
            group.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(new Date(currentZip.lastModified()));
            group.zipFile = currentZip;

            String logicEntry = resolveZipEntryName(zipNew, "logic");
            String viewEntry = resolveZipEntryName(zipNew, "view");
            String fileEntry = resolveZipEntryName(zipNew, "file");

            String oldLogic = "", oldView = "", oldFile = "";
            if (olderZip != null) {
                try (ZipFile zipOld = new ZipFile(olderZip)) {
                    oldLogic = getZipEntryContent(zipOld, resolveZipEntryName(zipOld, "logic"));
                    oldView = getZipEntryContent(zipOld, resolveZipEntryName(zipOld, "view"));
                    oldFile = getZipEntryContent(zipOld, resolveZipEntryName(zipOld, "file"));
                }
            }

            addIfChanged(group, oldLogic, getZipEntryContent(zipNew, logicEntry), "logic", "BLOCKS", logicEntry, currentZip);
            addIfChanged(group, oldView, getZipEntryContent(zipNew, viewEntry), "view", "XML", viewEntry, currentZip);
            addIfChanged(group, oldFile, getZipEntryContent(zipNew, fileEntry), "file", "JAVA", fileEntry, currentZip);

            return group;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void addIfChanged(SnapshotGroup group, String oldContent, String newContent, String fileName, String type, String zipEntry, File zip) {
        if (!oldContent.equals(newContent)) {
            ChangedFile cf = new ChangedFile();
            cf.fileName = fileName;
            cf.type = type;
            cf.oldContent = oldContent;
            cf.newContent = newContent;
            cf.zipEntryName = zipEntry;
            cf.snapshotZip = zip;
            group.changedFiles.add(cf);
        }
    }

    private String readAndDecryptFile(String filePath) {
        try {
            if (!FileUtil.isExistFile(filePath)) return "";

            RandomAccessFile randomAccessFile = new RandomAccessFile(filePath, "r");
            byte[] bArr = new byte[(int) randomAccessFile.length()];
            randomAccessFile.readFully(bArr);
            randomAccessFile.close();

            if (mCipher != null) {
                try {
                    return new String(mCipher.doFinal(bArr));
                } catch (Exception e) {
                    return new String(bArr, "UTF-8");
                }
            }
            return new String(bArr, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private String getZipEntryContent(ZipFile zip, String entryName) {
        try {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) return "";

            InputStream is = zip.getInputStream(entry);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = is.read(buffer)) != -1) baos.write(buffer, 0, count);
            is.close();

            byte[] zipBytes = baos.toByteArray();
            if (mCipher != null) {
                try {
                    return new String(mCipher.doFinal(zipBytes));
                } catch (Exception e) {
                    return new String(zipBytes, "UTF-8");
                }
            }
            return baos.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private String resolveZipEntryName(ZipFile zip, String fileName) {
        if (zip.getEntry("data/" + fileName) != null) return "data/" + fileName;
        if (zip.getEntry(fileName) != null) return fileName;
        if (zip.getEntry("data/" + sc_id + "/" + fileName) != null) return "data/" + sc_id + "/" + fileName;
        return "data/" + fileName;
    }

    /**
     * Converts the raw "logic" JSON (a Map<eventName, List<BlockBean>>) into
     * one readable line per block (spec with parameters substituted in),
     * instead of diffing the raw JSON text - fixes Bug 1 (Difference Summary
     * showing undecoded block JSON instead of something resembling actual
     * block/code content).
     */
    private List<String> logicJsonToReadableLines(String json) {
        List<String> lines = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return lines;
        try {
            Map<String, List<BlockBean>> blocksMap = BlocksJsonParser.parse(json);
            if (blocksMap == null) return lines;
            for (Map.Entry<String, List<BlockBean>> entry : blocksMap.entrySet()) {
                lines.add("// " + entry.getKey());
                List<BlockBean> blocks = entry.getValue();
                if (blocks != null) {
                    for (BlockBean b : blocks) {
                        if (b != null) lines.add(BlockSpecFormatter.format(b));
                    }
                }
            }
        } catch (Exception ignored) {
            // Malformed/unparseable - fall through with whatever lines were gathered so far.
        }
        return lines;
    }

    /**
     * Routes the Diff button: for BLOCKS, a flat +/- text diff of raw block
     * JSON isn't meaningful the way it is for actual text files (ids,
     * nextBlock pointers etc. change even when nothing user-visible did), so
     * it goes straight to the side-by-side CodeComparisonActivity, which
     * renders each block's readable spec instead. XML/JAVA keep the
     * Difference Summary dialog first, with "Details" available from there.
     */
    private void openDiff(ChangedFile item) {
        if ("BLOCKS".equals(item.type)) {
            Intent intent = new Intent(LocalHistoryActivity.this, CodeComparisonActivity.class);
            intent.putExtra("type", item.type);
            intent.putExtra("oldCode", item.oldContent);
            intent.putExtra("newCode", item.newContent);
            intent.putExtra("fileName", item.fileName);
            startActivity(intent);
        } else {
            showSummaryDialog(item);
        }
    }

    private void showSummaryDialog(ChangedFile item) {
        k(); // Show loading dialog

        new Thread(() -> {
            String oldForDiff = item.oldContent;
            String newForDiff = item.newContent;
            if ("BLOCKS".equals(item.type)) {
                oldForDiff = String.join("\n", logicJsonToReadableLines(item.oldContent));
                newForDiff = String.join("\n", logicJsonToReadableLines(item.newContent));
            }

            List<DiffUtils.DiffLine> diffs = DiffUtils.getDiff(oldForDiff, newForDiff);

            runOnUiThread(() -> {
                View view = getLayoutInflater().inflate(R.layout.dialog_diff_summary, null);
                LinearLayout container = view.findViewById(R.id.container_diff);

                for (DiffUtils.DiffLine line : diffs) {
                    if (line.type == DiffUtils.DiffType.UNCHANGED) continue;

                    TextView tv = new TextView(LocalHistoryActivity.this);
                    tv.setTextSize(12f);
                    tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                    if (line.type == DiffUtils.DiffType.ADDED) {
                        tv.setText("+   " + line.text);
                        tv.setTextColor(Color.parseColor("#4CAF50")); // Green
                    } else {
                        tv.setText("-   " + line.text);
                        tv.setTextColor(Color.parseColor("#F44336")); // Red
                    }
                    container.addView(tv);
                }

                h(); // Hide loading dialog

                new MaterialAlertDialogBuilder(LocalHistoryActivity.this)
                        .setTitle("Difference Summary")
                        .setView(view)
                        .setPositiveButton("OK", null)
                        .setNeutralButton("Details", (d, w) -> {
                            Intent intent = new Intent(LocalHistoryActivity.this, CodeComparisonActivity.class);
                            intent.putExtra("type", item.type);
                            intent.putExtra("oldCode", item.oldContent);
                            intent.putExtra("newCode", item.newContent);
                            intent.putExtra("fileName", item.fileName);
                            startActivity(intent);
                        })
                        .show();
            });
        }).start();
    }

    private void restoreSingleFile(ChangedFile item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Restore file?")
                .setMessage("Revert '" + item.type + "' (" + item.fileName + ") to the state from\n" + item.snapshotZip.getName() + "?\n\nThe project will close and reopen automatically once done.")
                .setPositiveButton("Restore", (d, w) -> {
                    k();
                    new Thread(() -> {
                        try (ZipFile zip = new ZipFile(item.snapshotZip)) {
                            ZipEntry entry = zip.getEntry(item.zipEntryName);
                            if (entry != null) {
                                String dataDir = Environment.getExternalStorageDirectory() + "/.sketchware/data/" + sc_id;
                                File targetFile = new File(dataDir, item.fileName);
                                File tmpFile = new File(dataDir, item.fileName + ".tmp");

                                try (InputStream is = zip.getInputStream(entry);
                                     FileOutputStream fos = new FileOutputStream(tmpFile)) {
                                    byte[] buffer = new byte[4096];
                                    int count;
                                    while ((count = is.read(buffer)) != -1) {
                                        fos.write(buffer, 0, count);
                                    }
                                }

                                runOnUiThread(() -> {
                                    h();
                                    if (tmpFile.exists() && tmpFile.length() > 0) {
                                        if (targetFile.exists()) targetFile.delete();
                                        tmpFile.renameTo(targetFile);
                                        SketchwareUtil.toast(item.type + " restored! Reloading project...");
                                        setResult(RESULT_OK);
                                        finish();
                                    } else {
                                        SketchwareUtil.toastError("Safety Abort: Temp file could not be verified.");
                                    }
                                });
                            } else {
                                runOnUiThread(() -> {
                                    h();
                                    SketchwareUtil.toastError("Failed to read snapshot file.");
                                });
                            }
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                h();
                                SketchwareUtil.toastError("Failed to restore safely: " + e.getMessage());
                            });
                        }
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restoreWholeSnapshot(SnapshotGroup group) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Restore this version?")
                .setMessage("This restores the ENTIRE project (blocks, layouts, code) to the state from\n" + group.timestamp + ".\n\nThe project will close and reopen automatically once done.")
                .setPositiveButton("Restore", (d, w) -> {
                    k();
                    new Thread(() -> {
                        boolean ok = TimeMachineManager.restoreSnapshot(sc_id, group.zipFile);
                        runOnUiThread(() -> {
                            h();
                            if (ok) {
                                SketchwareUtil.toast("Project restored! Reloading...");
                                setResult(RESULT_OK);
                                finish();
                            } else {
                                SketchwareUtil.toastError("Restore failed.");
                            }
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void cleanCache() {
        File cacheDir = new File(Environment.getExternalStorageDirectory(), ".sketchware/cache/history_temp_" + sc_id);
        if (cacheDir.exists()) {
            FileUtil.deleteFile(cacheDir.getAbsolutePath());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanCache();
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_local_history_group, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            SnapshotGroup group = groups.get(position);

            holder.tvDate.setText(group.timestamp);
            holder.tvDesc.setText(group.changedFiles.size() + " file(s) changed");
            holder.tvExpandArrow.setText(group.expanded ? "\u25BE" : "\u25B8");
            holder.containerChildren.setVisibility(group.expanded ? View.VISIBLE : View.GONE);

            holder.containerChildren.removeAllViews();
            for (ChangedFile cf : group.changedFiles) {
                View childView = LayoutInflater.from(LocalHistoryActivity.this)
                        .inflate(R.layout.item_local_history_child, holder.containerChildren, false);

                ImageView imgType = childView.findViewById(R.id.img_type);
                View viewAccent = childView.findViewById(R.id.view_accent);
                TextView tvTypeLabel = childView.findViewById(R.id.tv_type_label);
                TextView tvDesc = childView.findViewById(R.id.tv_desc);
                View btnDiff = childView.findViewById(R.id.btn_diff);
                View btnRestore = childView.findViewById(R.id.btn_restore);

                tvTypeLabel.setText(cf.type);
                tvDesc.setText(cf.fileName + " \u00b7 " + cf.newContent.length() + " chars");

                int iconRes = R.drawable.ic_mtrl_code;
                int accentColor = Color.parseColor("#2196F3"); // JAVA - blue
                if ("BLOCKS".equals(cf.type)) {
                    iconRes = R.drawable.ic_mtrl_block;
                    accentColor = Color.parseColor("#9C27B0"); // purple
                } else if ("XML".equals(cf.type)) {
                    iconRes = R.drawable.ic_mtrl_devices;
                    accentColor = Color.parseColor("#4CAF50"); // green
                } else if ("JAVA".equals(cf.type)) {
                    iconRes = R.drawable.ic_mtrl_java;
                }
                imgType.setImageResource(iconRes);
                imgType.setImageTintList(android.content.res.ColorStateList.valueOf(accentColor));
                viewAccent.setBackgroundColor(accentColor);
                tvTypeLabel.setTextColor(accentColor);

                btnDiff.setOnClickListener(v -> openDiff(cf));
                btnRestore.setOnClickListener(v -> restoreSingleFile(cf));

                holder.containerChildren.addView(childView);
            }

            holder.rowHeader.setOnClickListener(v -> {
                group.expanded = !group.expanded;
                notifyItemChanged(position);
            });

            holder.btnRestoreAll.setOnClickListener(v -> restoreWholeSnapshot(group));
        }

        @Override
        public int getItemCount() {
            return groups.size();
        }

        class VH extends RecyclerView.ViewHolder {
            View rowHeader;
            TextView tvExpandArrow, tvDate, tvDesc;
            View btnRestoreAll;
            LinearLayout containerChildren;

            VH(View v) {
                super(v);
                rowHeader = v.findViewById(R.id.row_header);
                tvExpandArrow = v.findViewById(R.id.tv_expand_arrow);
                tvDate = v.findViewById(R.id.tv_date);
                tvDesc = v.findViewById(R.id.tv_desc);
                btnRestoreAll = v.findViewById(R.id.btn_restore_all);
                containerChildren = v.findViewById(R.id.container_children);
            }
        }
    }
}
