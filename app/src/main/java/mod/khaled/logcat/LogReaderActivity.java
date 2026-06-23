package mod.khaled.logcat;

import static pro.sketchware.utility.FileUtil.createNewFileIfNotPresent;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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
    
    // Highly robust regex supporting: "MM-dd HH:mm:ss.SSS LEVEL TAG: MSG" OR "MM-dd HH:mm:ss.SSS LEVEL/TAG(PID): MSG"
    private final Pattern logPattern = Pattern.compile("^.*?(\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+([VDIWEA])[\\s/]+([^:]+?)(?:\\(\\s*\\d+\\))?:\\s+(.*)$");
    
    private final ArrayList<HashMap<String, Object>> mainList = new ArrayList<>();
    private final ArrayList<HashMap<String, Object>> displayList = new ArrayList<>();
    
    // Batching to prevent UI Freezes
    private final ArrayList<HashMap<String, Object>> pendingLogs = new ArrayList<>();
    private final Handler batchHandler = new Handler(Looper.getMainLooper());
    private boolean isBatchScheduled = false;
    
    private String pkgFilter = "";
    private String packageName = "pro.sketchware";
    private boolean autoScroll = true;
    private ArrayList<String> pkgFilterList = new ArrayList<>();

    private ActivityLogcatreaderBinding binding;
    private Adapter adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityLogcatreaderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initialize();
    }

    private void initialize() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(false);
        binding.logsRecyclerView.setLayoutManager(layoutManager);
        
        adapter = new Adapter(displayList);
        binding.logsRecyclerView.setAdapter(adapter);

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
                pendingLogs.clear();
                adapter.notifyDataSetChanged();
                adapter.checkEmptyState();
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

        adapter.notifyDataSetChanged();
        adapter.checkEmptyState();
    }

    void showFilterDialog() {
        var dialogBinding = EasyDeleteEdittextBinding.inflate(getLayoutInflater());
        View view = dialogBinding.getRoot();
        dialogBinding.imgDelete.setVisibility(View.GONE);
        dialogBinding.easyEdInput.setText(pkgFilter);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Filter by Package Name")
                .setMessage("Separate multiple package names with a comma (,).")
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
                    applyFilters();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
            createNewFileIfNotPresent(filePath);
            StringBuilder contentBuilder = new StringBuilder();
            String formattedDate = new SimpleDateFormat("yyyy/MM/dd 'at' HH:mm:ss", Locale.ENGLISH).format(new Date());

            contentBuilder.append(stars).append("\n");
            contentBuilder.append("** Exported logcat for ").append(packageName).append(" on ").append(formattedDate).append(" **\n");
            contentBuilder.append(stars).append("\n\n");

            for (HashMap<String, Object> log : logs) {
                String date = safeGet(log, "date");
                String type = safeGet(log, "type");
                String tag = safeGet(log, "header");
                String body = safeGet(log, "body");

                if (!type.isEmpty()) {
                    contentBuilder.append(String.format(Locale.ENGLISH, "[%s] %s/%s: %s\n", date, type, tag, body));
                } else {
                    contentBuilder.append(safeGet(log, "logRaw")).append("\n");
                }
            }
            FileUtil.writeFile(filePath, contentBuilder.toString());
            SketchwareUtil.toast("Logcat exported successfully: " + filePath);
        } catch (Exception ex) {
            SketchwareUtil.toastError("Export failed!");
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
        batchHandler.removeCallbacksAndMessages(null);
    }

    // High performance batch processor
    private final Runnable processBatchRunnable = () -> {
        isBatchScheduled = false;
        if (pendingLogs.isEmpty()) return;

        int startPos = displayList.size();
        int addedCount = 0;
        
        String _charSeq = Helper.getText(binding.searchInput).trim();
        boolean hasSearchFilter = !_charSeq.isEmpty();
        boolean hasPkgFilter = !pkgFilterList.isEmpty();

        for (HashMap<String, Object> map : pendingLogs) {
            boolean matchesSearch = !hasSearchFilter || safeGet(map, "logRaw").toLowerCase().contains(_charSeq.toLowerCase());
            boolean matchesPkg = !hasPkgFilter || (map.containsKey("pkgName") && pkgFilterList.contains(safeGet(map, "pkgName")));

            if (matchesSearch && matchesPkg) {
                displayList.add(map);
                addedCount++;
            }
        }

        pendingLogs.clear();

        if (addedCount > 0) {
            adapter.notifyItemRangeInserted(startPos, addedCount);
            adapter.checkEmptyState();
        }
    };

    private class Logger extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("log") && intent.getStringExtra("log") != null) {
                HashMap<String, Object> map = new HashMap<>();
                
                if (intent.hasExtra("packageName")) {
                    String pkg = intent.getStringExtra("packageName");
                    map.put("pkgName", pkg);
                    packageName = pkg;
                }
                
                String logRaw = intent.getStringExtra("log");
                map.put("logRaw", logRaw);

                Matcher matcher = logPattern.matcher(logRaw);
                if (matcher.matches()) {
                    map.put("date", matcher.group(1).trim());
                    map.put("type", matcher.group(2).trim());
                    map.put("header", matcher.group(3).trim());
                    map.put("body", matcher.group(4).trim());
                    map.put("culturedLog", "true");
                }

                mainList.add(map);
                pendingLogs.add(map);

                // Throttle UI updates to 200ms
                if (!isBatchScheduled) {
                    isBatchScheduled = true;
                    batchHandler.postDelayed(processBatchRunnable, 200);
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
            return new ViewHolder(listBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            var lb = holder.listBinding;
            HashMap<String, Object> itemData = data.get(position);

            if (itemData.containsKey("culturedLog")) {
                String typeStr = safeGet(itemData, "type");
                String tagStr = safeGet(itemData, "header");
                
                lb.type.setText(typeStr);
                lb.dateHeader.setText(String.format("%s | %s", safeGet(itemData, "date"), tagStr));
                lb.log.setText(safeGet(itemData, "body"));

                // Material 3 Color coding
                int indicatorColor;
                int badgeBgColor;
                int badgeTextColor;

                switch (typeStr) {
                    case "E": // Error - Red
                        indicatorColor = Color.parseColor("#F44336");
                        badgeBgColor = Color.parseColor("#FFEBEE");
                        badgeTextColor = Color.parseColor("#D32F2F");
                        break;
                    case "W": // Warn - Orange
                        indicatorColor = Color.parseColor("#FF9800");
                        badgeBgColor = Color.parseColor("#FFF8E1");
                        badgeTextColor = Color.parseColor("#E65100");
                        break;
                    case "I": // Info - Green
                        indicatorColor = Color.parseColor("#4CAF50");
                        badgeBgColor = Color.parseColor("#E8F5E9");
                        badgeTextColor = Color.parseColor("#2E7D32");
                        break;
                    case "A": // Assert - Purple
                        indicatorColor = Color.parseColor("#9C27B0");
                        badgeBgColor = Color.parseColor("#F3E5F5");
                        badgeTextColor = Color.parseColor("#7B1FA2");
                        break;
                    case "D": // Debug - Blue
                        indicatorColor = Color.parseColor("#2196F3");
                        badgeBgColor = Color.parseColor("#E3F2FD");
                        badgeTextColor = Color.parseColor("#1976D2");
                        break;
                    default: // Verbose/Unknown - Grey
                        indicatorColor = Color.parseColor("#9E9E9E");
                        badgeBgColor = Color.parseColor("#F5F5F5");
                        badgeTextColor = Color.parseColor("#616161");
                        break;
                }

                lb.severityIndicator.setBackgroundColor(indicatorColor);
                lb.badgeType.setCardBackgroundColor(badgeBgColor);
                lb.type.setTextColor(badgeTextColor);
            } else {
                lb.log.setText(safeGet(itemData, "logRaw"));
                lb.type.setText("U");
                lb.severityIndicator.setBackgroundColor(Color.parseColor("#9E9E9E"));
                lb.badgeType.setCardBackgroundColor(Color.parseColor("#F5F5F5"));
                lb.type.setTextColor(Color.parseColor("#616161"));
                lb.dateHeader.setText("Unknown Format");
            }

            if (itemData.containsKey("pkgName")) {
                lb.pkgName.setText(safeGet(itemData, "pkgName"));
                lb.pkgName.setVisibility(View.VISIBLE);
            } else {
                lb.pkgName.setVisibility(View.GONE);
            }
            
            lb.cardRoot.setOnLongClickListener(v -> {
                SketchwareUtil.toast("Copied to clipboard");
                ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                        .setPrimaryClip(ClipData.newPlainText("log", safeGet(itemData, "logRaw")));
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
