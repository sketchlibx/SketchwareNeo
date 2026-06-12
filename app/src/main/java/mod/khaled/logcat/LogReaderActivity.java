package mod.khaled.logcat;

import static pro.sketchware.utility.FileUtil.createNewFileIfNotPresent;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityLogcatreaderBinding;
import pro.sketchware.databinding.EasyDeleteEdittextBinding;
import pro.sketchware.databinding.ViewLogcatItemBinding;
import pro.sketchware.lib.base.BaseTextWatcher;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class LogReaderActivity extends BaseAppCompatActivity {

    private final BroadcastReceiver logger = new Logger();
    private final Pattern logPattern = Pattern.compile("^(.*\\d) ([VADEIW]) (.*): (.*)");
    
    private final ArrayList<HashMap<String, Object>> mainList = new ArrayList<>();
    private final ArrayList<HashMap<String, Object>> displayList = new ArrayList<>();
    
    private String pkgFilter = "";
    private String packageName = "pro.sketchware";
    private boolean autoScroll = true;
    private ArrayList<String> pkgFilterList = new ArrayList<>();

    private ActivityLogcatreaderBinding binding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityLogcatreaderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initialize();
    }

    private void initialize() {
        // PRO FIX: Initialized LayoutManager correctly so logs actually render on screen
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(false);
        binding.logsRecyclerView.setLayoutManager(layoutManager);
        binding.logsRecyclerView.setAdapter(new Adapter(displayList));

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("pro.sketchware.ACTION_NEW_DEBUG_LOG");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logger, intentFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(logger, intentFilter);
        }

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.topAppBar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_clear) {
                mainList.clear();
                displayList.clear();
                if (binding.logsRecyclerView.getAdapter() != null) {
                    binding.logsRecyclerView.getAdapter().notifyDataSetChanged();
                    ((Adapter) binding.logsRecyclerView.getAdapter()).checkEmptyState();
                }
            } else if (id == R.id.action_auto_scroll) {
                autoScroll = !item.isChecked();
                item.setChecked(autoScroll);
                if (autoScroll && !displayList.isEmpty()) {
                    binding.logsRecyclerView.scrollToPosition(displayList.size() - 1);
                }
            } else if (id == R.id.action_filter) {
                showFilterDialog();
            } else if (id == R.id.action_export) {
                exportLogcat(mainList);
            }
            return true;
        });

        binding.searchInput.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }
        });
    }

    private void applyFilters() {
        displayList.clear();
        String _charSeq = Helper.getText(binding.searchInput).trim();
        boolean hasSearchFilter = !_charSeq.isEmpty();
        boolean hasPkgFilter = !pkgFilterList.isEmpty();

        for (HashMap<String, Object> m : mainList) {
            boolean matchesSearch = !hasSearchFilter || safeGet(m, "logRaw").toLowerCase().contains(_charSeq.toLowerCase());
            boolean matchesPkg = !hasPkgFilter || (m.containsKey("pkgName") && pkgFilterList.contains(safeGet(m, "pkgName")));

            if (matchesSearch && matchesPkg) {
                displayList.add(m);
            }
        }

        if (binding.logsRecyclerView.getAdapter() != null) {
            binding.logsRecyclerView.getAdapter().notifyDataSetChanged();
            ((Adapter) binding.logsRecyclerView.getAdapter()).checkEmptyState();
        }
    }

    void showFilterDialog() {
        var dialogBinding = EasyDeleteEdittextBinding.inflate(getLayoutInflater());
        View view = dialogBinding.getRoot();

        dialogBinding.imgDelete.setVisibility(View.GONE);

        var builder = new MaterialAlertDialogBuilder(this)
                .setTitle("Filter by package name")
                .setMessage("For multiple package names, separate them with a comma (,).")
                .setIcon(R.drawable.ic_mtrl_filter)
                .setView(view)
                .setPositiveButton("Apply", (dialog, which) -> {
                    pkgFilter = Helper.getText(dialogBinding.easyEdInput).trim();
                    if (pkgFilter.isEmpty()) {
                        pkgFilterList.clear();
                    } else {
                        pkgFilterList = new ArrayList<>(Arrays.asList(pkgFilter.split("\\s*,\\s*")));
                    }
                    applyFilters();
                })
                .setNeutralButton("Reset", (dialog, which) -> {
                    pkgFilter = "";
                    pkgFilterList.clear();
                    dialogBinding.easyEdInput.setText("");
                    applyFilters();
                })
                .setNegativeButton("Cancel", null)
                .create();

        builder.show();
    }

    private String safeGet(HashMap<String, Object> log, String key) {
        Object value = log.get(key);
        return value != null ? value.toString() : "";
    }

    private void exportLogcat(ArrayList<HashMap<String, Object>> logs) {
        if (logs.isEmpty()) {
            SketchwareUtil.toastError("Nothing to Export");
            return;
        }
        try {
            String fileName = Calendar.getInstance(Locale.ENGLISH).getTimeInMillis() + ".txt";
            String filePath = Environment.getExternalStorageDirectory() + "/.sketchware/logcat/" + packageName + "/" + fileName;
            String stars = "*".repeat(95);
            String blank = " ".repeat(87);
            createNewFileIfNotPresent(filePath);
            StringBuilder contentBuilder = new StringBuilder();
            String formattedDate = new SimpleDateFormat("yyyy/MM/dd 'at' HH:mm:ss", Locale.ENGLISH).format(new Date());

            contentBuilder.append(stars).append("\n");
            contentBuilder.append(stars).append("\n");
            contentBuilder.append("**").append(blank).append("**");
            contentBuilder.append("\n** Exported logcat reader for ").append(packageName).append(" on ").append(formattedDate).append("  **\n");
            contentBuilder.append("**").append(blank).append("**\n");
            contentBuilder.append(stars).append("\n");
            contentBuilder.append(stars).append("\n");

            for (HashMap<String, Object> log : logs) {
                String date = safeGet(log, "date");
                String type = safeGet(log, "type");
                String tag = safeGet(log, "header");
                String body = safeGet(log, "body");

                if (!type.isEmpty()) {
                    contentBuilder.append("\n\n|-- Log Type: ").append(type).append("\n");
                    contentBuilder.append("    |-- Date: ").append(date).append("\n");
                    contentBuilder.append("    |-- Tag: ").append(tag).append("\n");
                    contentBuilder.append("    |-- Message: ").append(body).append("\n");
                    contentBuilder.append("------------------------------------------------");
                }

            }
            FileUtil.writeFile(filePath, contentBuilder.toString());
            SketchwareUtil.toast("Logcat exported successfully: " + filePath);
        } catch (Exception ex) {
            SketchwareUtil.toastError("Something went wrong!");
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        binding.searchInput.clearFocus();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(logger);
    }

    private class Logger extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            HashMap<String, Object> map = new HashMap<>();
            if (intent.hasExtra("log") && intent.getStringExtra("log") != null) {
                if (intent.hasExtra("packageName")) {
                    map.put("pkgName", intent.getStringExtra("packageName"));
                    packageName = intent.getStringExtra("packageName");
                }
                String logRaw = intent.getStringExtra("log");
                map.put("logRaw", logRaw);
                if (logRaw == null) return;

                Matcher matcher = logPattern.matcher(logRaw);
                if (matcher.matches()) {
                    map.put("date", matcher.group(1).trim());
                    map.put("type", matcher.group(2).trim());
                    map.put("header", matcher.group(3));
                    map.put("body", matcher.group(4));
                    map.put("culturedLog", "true");
                }

                // Add to master list always
                mainList.add(map);

                // Check if current log matches active filters
                boolean hasSearchFilter = !Helper.getText(binding.searchInput).isEmpty();
                boolean hasPkgFilter = !pkgFilterList.isEmpty();

                boolean matchesSearch = !hasSearchFilter || logRaw.toLowerCase().contains(Helper.getText(binding.searchInput).toLowerCase());
                boolean matchesPkg = !hasPkgFilter || (map.containsKey("pkgName") && pkgFilterList.contains(safeGet(map, "pkgName")));

                if (matchesSearch && matchesPkg) {
                    displayList.add(map);
                    if (binding.logsRecyclerView.getAdapter() != null) {
                        Adapter adapter = (Adapter) binding.logsRecyclerView.getAdapter();
                        adapter.notifyItemInsertedSafely();
                    }
                }
            }
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {
        private final ArrayList<HashMap<String, Object>> data;

        public Adapter(ArrayList<HashMap<String, Object>> data) {
            this.data = data;
            checkEmptyState();
        }

        public void notifyItemInsertedSafely() {
            notifyItemInserted(data.size() - 1);
            checkEmptyState();
        }

        public void checkEmptyState() {
            binding.noContentLayout.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
            if (autoScroll && !data.isEmpty()) {
                binding.logsRecyclerView.scrollToPosition(data.size() - 1);
            }
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            var listBinding = ViewLogcatItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            var layoutParams = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            listBinding.getRoot().setLayoutParams(layoutParams);
            return new ViewHolder(listBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            var listBinding = holder.listBinding;
            HashMap<String, Object> itemData = data.get(position);

            if (itemData.containsKey("pkgName")) {
                listBinding.pkgName.setText(safeGet(itemData, "pkgName"));
                listBinding.pkgName.setVisibility(View.VISIBLE);
            } else {
                listBinding.pkgName.setVisibility(View.GONE);
            }
            
            if (itemData.containsKey("culturedLog")) {
                listBinding.dateHeader.setVisibility(View.VISIBLE);
                String typeStr = safeGet(itemData, "type");
                listBinding.type.setText(typeStr);
                listBinding.dateHeader.setText(safeGet(itemData, "date") + " | " + safeGet(itemData, "header"));
                
                switch (typeStr) {
                    case "A" -> listBinding.type.setBackgroundColor(0xFF9C27B0);
                    case "D" -> listBinding.type.setBackgroundColor(0xFF2196F3);
                    case "E" -> listBinding.type.setBackgroundColor(0xFFF44336);
                    case "I" -> listBinding.type.setBackgroundColor(0xFF4CAF50);
                    case "V" -> listBinding.type.setBackgroundColor(0xFF000000);
                    case "W" -> listBinding.type.setBackgroundColor(0xFFFFC107);
                    default -> {
                        listBinding.type.setBackgroundColor(0xFF000000);
                        listBinding.type.setText("U");
                    }
                }
                listBinding.log.setText(safeGet(itemData, "body"));
                
                try {
                    if (position < data.size() - 1 && safeGet(itemData, "date").equals(safeGet(data.get(position + 1), "date"))) {
                        if (safeGet(itemData, "pkgName").equals(safeGet(data.get(position + 1), "pkgName"))) {
                            listBinding.pkgName.setVisibility(View.GONE);
                        }
                        if (safeGet(itemData, "header").equals(safeGet(data.get(position + 1), "header"))) {
                            listBinding.dateHeader.setVisibility(View.GONE);
                        }
                    }
                } catch (Exception ignored) { }
                
            } else {
                listBinding.log.setText(safeGet(itemData, "logRaw"));
                listBinding.type.setBackgroundColor(0xFF000000);
                listBinding.type.setText("U");
                listBinding.dateHeader.setVisibility(View.GONE);
            }
            
            listBinding.getRoot().setOnLongClickListener(v -> {
                SketchwareUtil.toast("Copied to clipboard");
                ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("clipboard", safeGet(itemData, "logRaw")));
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final ViewLogcatItemBinding listBinding;

            public ViewHolder(@NonNull ViewLogcatItemBinding listBinding) {
                super(listBinding.getRoot());
                this.listBinding = listBinding;
            }
        }
    }
}
