package mod.hilal.saif.activities.tools;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.view.MenuItemCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.gson.JsonParseException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.PropertiesUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class BlocksManagerDetailsActivity extends BaseAppCompatActivity {

    private static final String BLOCK_EXPORT_PATH = new File(FileUtil.getExternalStorageDir(), ".sketchware/resources/block/export/").getAbsolutePath();

    private final ArrayList<HashMap<String, Object>> filtered_list = new ArrayList<>();
    private final ArrayList<Integer> reference_list = new ArrayList<>();
    private ArrayList<HashMap<String, Object>> all_blocks_list = new ArrayList<>();
    private String blocks_path = "";
    private String mode = "normal";
    private ArrayList<HashMap<String, Object>> pallet_list = new ArrayList<>();
    private String pallet_path = "";
    private int palette = 0;
    private Parcelable listViewSavedState;
    private String searchQuery = "";

    private Toolbar toolbar;
    private ListView block_list;
    private LinearLayout background;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fab_button;
    private CircularProgressIndicator loadingIndicator;
    private TextView emptySearchState;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private androidx.activity.result.ActivityResultLauncher<Intent> generationLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocks_manager_details);

        background = findViewById(R.id.background);
        block_list = findViewById(R.id.block_list);
        fab_button = findViewById(R.id.fab_button);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        emptySearchState = findViewById(R.id.emptySearchState);

        generationLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleGenerationResult(result.getData());
                    }
                });

        initialize();
        _receive_intents();
    }

    private void initialize() {

        toolbar = (Toolbar) getLayoutInflater().inflate(R.layout.toolbar_improved, background, false);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(view -> onBackPressed());
        background.addView(toolbar, 0);

        fab_button.setOnClickListener(v -> {
            Object paletteColor = pallet_list.get(palette - 9).get("color");
            if (paletteColor instanceof String) {
                Intent intent = new Intent(getApplicationContext(), BlocksManagerCreatorActivity.class);
                intent.putExtra("mode", "add");
                intent.putExtra("color", (String) paletteColor);
                intent.putExtra("path", blocks_path);
                intent.putExtra("pallet", String.valueOf(palette));
                startActivity(intent);
            } else {
                SketchwareUtil.toastError("Invalid color of palette #" + (palette - 9));
            }
        });
    }

    public void openFileExplorerImport() {
        FilePickerOptions options = new FilePickerOptions();
        options.setExtensions(new String[]{"json"});
        options.setTitle("Select a JSON file");

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFileSelected(File file) {
                if (FileUtil.readFile(file.getAbsolutePath()).isEmpty()) {
                    SketchwareUtil.toastError("The selected file is empty!");
                } else if (FileUtil.readFile(file.getAbsolutePath()).equals("[]")) {
                    SketchwareUtil.toastError("The selected file is empty!");
                } else {
                    try {
                        ArrayList<HashMap<String, Object>> readMap = getGson().fromJson(FileUtil.readFile(file.getAbsolutePath()), Helper.TYPE_MAP_LIST);
                        _importBlocks(readMap);
                    } catch (JsonParseException e) {
                        SketchwareUtil.toastError("Invalid JSON file");
                    }
                }
            }
        };

        FilePickerDialogFragment dialog = new FilePickerDialogFragment(options, callback);

        dialog.show(getSupportFragmentManager(), "filePickerDialog");
    }

    private void showAiGenerationPromptDialog() {
        int dp24 = (int) (24 * getResources().getDisplayMetrics().density);
        int dp16 = (int) (16 * getResources().getDisplayMetrics().density);
        int dp8 = (int) (8 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp24, dp24, dp24, dp8);

        TextView title = new TextView(this);
        title.setText("Generate Blocks with AI");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOnSurface));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Describe an SDK/API/library or a group of related operations. AI will plan, then generate and validate the blocks on the next screen before you import anything.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.setMargins(0, dp8, 0, dp16);
        subtitle.setLayoutParams(subParams);
        root.addView(subtitle);

        EditText topicInput = new EditText(this);
        topicInput.setHint("Library/SDK/API name (optional), e.g. Unity Ads");
        topicInput.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams topicParams = new LinearLayout.LayoutParams(-1, -2);
        topicParams.setMargins(0, 0, 0, dp8);
        topicInput.setLayoutParams(topicParams);
        root.addView(topicInput);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardElevation(0);
        card.setRadius(dp8);
        card.setStrokeWidth((int) (1 * getResources().getDisplayMetrics().density));
        card.setStrokeColor(ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(ThemeUtils.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant));

        EditText promptInput = new EditText(this);
        promptInput.setHint("e.g. initialize, load, and show a Unity interstitial ad, with load/show callbacks");
        promptInput.setBackground(null);
        promptInput.setPadding(dp16, dp16, dp16, dp16);
        promptInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        promptInput.setMinLines(4);
        promptInput.setMaxLines(10);
        promptInput.setGravity(Gravity.TOP | Gravity.START);
        promptInput.setTextColor(ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOnSurface));
        promptInput.setHintTextColor(ThemeUtils.getColor(this, com.google.android.material.R.attr.colorOutline));
        card.addView(promptInput, new ViewGroup.LayoutParams(-1, -2));
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));

        new MaterialAlertDialogBuilder(this)
                .setView(root)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Next", (dialog, which) -> {
                    String prompt = promptInput.getText() == null ? "" : promptInput.getText().toString().trim();
                    if (prompt.isEmpty()) {
                        SketchwareUtil.toastError("Describe what you need first.");
                        return;
                    }
                    String topic = topicInput.getText() == null ? "" : topicInput.getText().toString().trim();
                    launchGenerationScreen(topic, prompt);
                })
                .show();
    }

    private void launchGenerationScreen(String topic, String prompt) {
        StringBuilder existingNames = new StringBuilder();
        if (all_blocks_list != null) {
            for (HashMap<String, Object> block : all_blocks_list) {
                Object name = block.get("name");
                if (name instanceof String) {
                    if (existingNames.length() > 0) existingNames.append(", ");
                    existingNames.append((String) name);
                }
            }
        }

        Intent intent = new Intent(this, neo.sketchware.ai.BlockGenerationActivity.class);
        intent.putExtra(neo.sketchware.ai.BlockGenerationActivity.EXTRA_TOPIC, topic);
        intent.putExtra(neo.sketchware.ai.BlockGenerationActivity.EXTRA_PROMPT, prompt);
        intent.putExtra(neo.sketchware.ai.BlockGenerationActivity.EXTRA_EXISTING_NAMES, existingNames.toString());
        generationLauncher.launch(intent);
    }

    /**
     * Converts the approved blocks returned by BlockGenerationActivity into the exact on-disk
     * shape addBlock()/the file-import path already use (same "regular"->" " conversion, same
     * spec2-only-for-"e", same imports-only-if-non-empty), then hands them to the EXISTING
     * _importBlocks() accept/reject checklist - reused rather than rebuilt, same as before.
     */
    private void handleGenerationResult(Intent data) {
        String json = data.getStringExtra(neo.sketchware.ai.BlockGenerationActivity.EXTRA_RESULT_BLOCKS_JSON);
        if (json == null) return;

        JSONArray blocksArray;
        try {
            blocksArray = new JSONObject(json).getJSONArray("blocks");
        } catch (JSONException e) {
            SketchwareUtil.toastError("Couldn't read the generated blocks.");
            return;
        }

        ArrayList<HashMap<String, Object>> converted = new ArrayList<>();
        for (int i = 0; i < blocksArray.length(); i++) {
            JSONObject b;
            try {
                b = blocksArray.getJSONObject(i);
            } catch (JSONException e) {
                continue;
            }
            HashMap<String, Object> map = new HashMap<>();
            map.put("name", b.optString("name", ""));
            String type = b.optString("type", "regular");
            map.put("type", type.isEmpty() || type.equals("regular") ? " " : type);
            map.put("typeName", b.optString("typeName", ""));
            map.put("spec", b.optString("spec", ""));
            if ("e".equals(type)) {
                map.put("spec2", b.optString("spec2", ""));
            }
            String color = b.optString("color", "");
            map.put("color", !color.isEmpty() ? color : "#F0F0F0");
            String imports = b.optString("imports", "");
            if (!TextUtils.isEmpty(imports)) {
                map.put("imports", imports);
            }
            map.put("code", b.optString("code", ""));
            // palette intentionally not set - _importBlocks() always assigns the currently
            // open palette, matching "inherit the currently selected palette".
            converted.add(map);
        }

        if (converted.isEmpty()) {
            SketchwareUtil.toastError("No blocks to import.");
            return;
        }
        _importBlocks(converted);
    }

    @Override
    public void onStop() {
        super.onStop();
        listViewSavedState = block_list.onSaveInstanceState();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (listViewSavedState != null) {
            block_list.onRestoreInstanceState(listViewSavedState);
            _refreshLists();
        }
    }

    @Override
    public void onBackPressed() {
        if (mode.equals("editor")) {
            mode = "normal";
            Parcelable savedState = block_list.onSaveInstanceState();
            block_list.setAdapter(new Adapter(filtered_list));
            ((BaseAdapter) block_list.getAdapter()).notifyDataSetChanged();
            block_list.onRestoreInstanceState(savedState);
            fabButtonVisibility(true);
            onCreateOptionsMenu(toolbar.getMenu());
        } else {
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        if (Integer.parseInt(getIntent().getStringExtra("position")) != -1) {
            if (mode.equals("normal")) {
                MenuItem searchItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Search");
                searchItem.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_search));
                searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
                SearchView searchView = new SearchView(this);
                searchView.setQueryHint("Search blocks");
                searchView.setQuery(searchQuery, false);
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        searchQuery = newText == null ? "" : newText;
                        applySearchableFilter();
                        return true;
                    }
                });
                MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        searchQuery = "";
                        applySearchableFilter();
                        return true;
                    }
                });
                MenuItemCompat.setActionView(searchItem, searchView);

                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Swap").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_swap_vertical)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Import");
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Export");
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Generate Blocks with AI");
            } else {
                menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Swap").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_save)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {
        String title = menuItem.getTitle().toString();
        switch (title) {
            case "Swap":
                if (mode.equals("normal")) {
                    mode = "editor";
                    fabButtonVisibility(false);
                    if (!searchQuery.isEmpty()) {
                        // Up/down reordering indexes filtered_list/reference_list
                        // positionally, so it must operate on the full, unfiltered palette.
                        searchQuery = "";
                        applySearchableFilter();
                    }
                } else {
                    mode = "normal";
                    fabButtonVisibility(true);
                }
                Parcelable savedInstanceState = block_list.onSaveInstanceState();
                block_list.setAdapter(new Adapter(filtered_list));
                ((BaseAdapter) block_list.getAdapter()).notifyDataSetChanged();
                block_list.onRestoreInstanceState(savedInstanceState);
                onCreateOptionsMenu(toolbar.getMenu());
                break;

            case "Import":
                openFileExplorerImport();
                break;

            case "Generate Blocks with AI":
                showAiGenerationPromptDialog();
                break;

            case "Export":
                Object paletteName = pallet_list.get(palette - 9).get("name");
                if (paletteName instanceof String) {
                    // Export always covers the whole palette, regardless of an active
                    // search filter — search is a display-only concern.
                    ArrayList<HashMap<String, Object>> exportList = new ArrayList<>();
                    for (HashMap<String, Object> block : all_blocks_list) {
                        Object blockPalette = block.get("palette");
                        if (blockPalette instanceof String) {
                            try {
                                if (Integer.parseInt((String) blockPalette) == palette) {
                                    exportList.add(block);
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    String exportTo = new File(BLOCK_EXPORT_PATH, paletteName + ".json").getAbsolutePath();
                    FileUtil.writeFile(exportTo, getGson().toJson(exportList));
                    SketchwareUtil.toast("Successfully exported blocks to:\n" + exportTo, Toast.LENGTH_LONG);
                } else {
                    SketchwareUtil.toastError("Invalid name of palette #" + (palette - 9));
                }
                break;

            default:
                return false;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void _receive_intents() {
        palette = Integer.parseInt(getIntent().getStringExtra("position"));
        pallet_path = getIntent().getStringExtra("dirP");
        blocks_path = getIntent().getStringExtra("dirB");
        if (palette == -1) {
            getSupportActionBar().setTitle("Recycle Bin");
            fab_button.setVisibility(View.GONE);
        }
        loadDataAsync();
    }

    /**
     * Async replacement for the initial _refreshLists() call — the one that ran on
     * onCreate/_receive_intents and froze the UI while reading potentially large
     * palette/block JSON files. _refreshLists() itself is left untouched for its many
     * other (post-edit/delete/import/swap) call sites, which are small in-memory refreshes
     * already triggered from the main thread by user actions.
     */
    private void loadDataAsync() {
        loadingIndicator.setVisibility(View.VISIBLE);
        block_list.setVisibility(View.GONE);
        emptySearchState.setVisibility(View.GONE);

        ioExecutor.execute(() -> {
            String paletteFileContent = FileUtil.readFile(pallet_path);
            String blocksFileContent = FileUtil.readFile(blocks_path);
            mainHandler.post(() -> applyLoadedData(paletteFileContent, blocksFileContent));
        });
    }

    private void applyLoadedData(String paletteFileContent, String blocksFileContent) {
        if (isFinishing() || isDestroyed()) return;

        if (paletteFileContent.isEmpty()) {
            FileUtil.writeFile(pallet_path, "[]");
            paletteFileContent = "[]";
        }
        if (blocksFileContent.isEmpty()) {
            FileUtil.writeFile(blocks_path, "[]");
            blocksFileContent = "[]";
        }

        try {
            ArrayList<HashMap<String, Object>> parsed = getGson().fromJson(paletteFileContent, Helper.TYPE_MAP_LIST);
            pallet_list = parsed != null ? parsed : new ArrayList<>();
        } catch (JsonParseException e) {
            SketchwareUtil.showFailedToParseJsonDialog(this, new File(pallet_path), "Custom Block Palettes", v -> loadDataAsync());
            pallet_list = new ArrayList<>();
        }

        try {
            ArrayList<HashMap<String, Object>> parsed = getGson().fromJson(blocksFileContent, Helper.TYPE_MAP_LIST);
            all_blocks_list = parsed != null ? parsed : new ArrayList<>();
        } catch (JsonParseException e) {
            SketchwareUtil.showFailedToParseJsonDialog(this, new File(blocks_path), "Custom Blocks", v -> loadDataAsync());
            all_blocks_list = new ArrayList<>();
        }

        if (palette != -1) {
            Object paletteName = palette - 9 < pallet_list.size() ? pallet_list.get(palette - 9).get("name") : null;
            if (paletteName instanceof String) {
                getSupportActionBar().setTitle("Manage Block");
                getSupportActionBar().setSubtitle((String) paletteName);
            }
        }

        applySearchableFilter();

        loadingIndicator.setVisibility(View.GONE);
        block_list.setVisibility(View.VISIBLE);
    }

    /** Rebuilds filtered_list/reference_list from all_blocks_list: palette membership AND
     *  (if searchQuery is set) a name/spec match — the same invariant _refreshLists() keeps,
     *  just with the extra text condition, so every existing position-based operation
     *  (edit/delete/swap/export via reference_list) keeps resolving correctly. */
    private void applySearchableFilter() {
        filtered_list.clear();
        reference_list.clear();
        String q = searchQuery.trim().toLowerCase(Locale.getDefault());

        for (int i = 0; i < all_blocks_list.size(); i++) {
            HashMap<String, Object> block = all_blocks_list.get(i);
            Object blockPalette = block.get("palette");
            if (!(blockPalette instanceof String)) continue;
            try {
                if (Integer.parseInt((String) blockPalette) != palette) continue;
            } catch (NumberFormatException e) {
                SketchwareUtil.toastError("Invalid palette entry in block #" + (i + 1));
                continue;
            }
            if (q.isEmpty() || matchesBlockSearch(block, q)) {
                reference_list.add(i);
                filtered_list.add(block);
            }
        }

        Parcelable savedState = block_list.onSaveInstanceState();
        block_list.setAdapter(new Adapter(filtered_list));
        ((BaseAdapter) block_list.getAdapter()).notifyDataSetChanged();
        block_list.onRestoreInstanceState(savedState);

        boolean noResults = !q.isEmpty() && filtered_list.isEmpty();
        emptySearchState.setVisibility(noResults ? View.VISIBLE : View.GONE);
        block_list.setVisibility(noResults ? View.GONE : View.VISIBLE);
    }

    private boolean matchesBlockSearch(HashMap<String, Object> block, String lowerCaseQuery) {
        Object name = block.get("name");
        if (name instanceof String && ((String) name).toLowerCase(Locale.getDefault()).contains(lowerCaseQuery)) return true;
        Object spec = block.get("spec");
        if (spec instanceof String && ((String) spec).toLowerCase(Locale.getDefault()).contains(lowerCaseQuery)) return true;
        Object typeName = block.get("typeName");
        return typeName instanceof String && ((String) typeName).toLowerCase(Locale.getDefault()).contains(lowerCaseQuery);
    }

    private void _refreshLists() {
        filtered_list.clear();
        reference_list.clear();
        String paletteFileContent = FileUtil.readFile(pallet_path);
        String blocksFileContent = FileUtil.readFile(blocks_path);
        if (paletteFileContent.isEmpty()) {
            FileUtil.writeFile(pallet_path, "[]");
            paletteFileContent = "[]";
        }
        if (blocksFileContent.isEmpty()) {
            FileUtil.writeFile(blocks_path, "[]");
            blocksFileContent = "[]";
        }

        parseLists:
        {
            try {
                pallet_list = getGson().fromJson(paletteFileContent, Helper.TYPE_MAP_LIST);

                if (pallet_list != null) {
                    break parseLists;
                }
                // fall-through to shared error handling
            } catch (JsonParseException e) {
                // fall-through to shared error handling
            }

            SketchwareUtil.showFailedToParseJsonDialog(this, new File(pallet_path), "Custom Block Palettes", v -> _refreshLists());
            pallet_list = new ArrayList<>();
        }

        parseBlocks:
        {
            try {
                all_blocks_list = getGson().fromJson(blocksFileContent, Helper.TYPE_MAP_LIST);

                if (all_blocks_list != null) {
                    break parseBlocks;
                }
                // fall-through to shared error handling
            } catch (JsonParseException e) {
                // fall-through to shared error handling
            }

            SketchwareUtil.showFailedToParseJsonDialog(this, new File(blocks_path), "Custom Blocks", v -> _refreshLists());
            all_blocks_list = new ArrayList<>();
        }

        for (int i = 0; i < all_blocks_list.size(); i++) {
            HashMap<String, Object> block = all_blocks_list.get(i);

            Object blockPalette = block.get("palette");
            if (blockPalette instanceof String) {
                try {
                    if (Integer.parseInt((String) blockPalette) == palette) {
                        reference_list.add(i);
                        filtered_list.add(block);
                    }
                } catch (NumberFormatException e) {
                    SketchwareUtil.toastError("Invalid palette entry in block #" + (i + 1));
                }
            }
        }
        Parcelable onSaveInstanceState = block_list.onSaveInstanceState();
        block_list.setAdapter(new Adapter(filtered_list));
        ((BaseAdapter) block_list.getAdapter()).notifyDataSetChanged();
        block_list.onRestoreInstanceState(onSaveInstanceState);
    }

    private void _swapitems(int sourcePosition, int targetPosition) {
        Collections.swap(all_blocks_list, sourcePosition, targetPosition);
        FileUtil.writeFile(blocks_path, getGson().toJson(all_blocks_list));
        _refreshLists();
    }

    private void _showItemPopup(View view, int position) {
        if (palette == -1) {
            PopupMenu popupMenu = new PopupMenu(this, view);
            Menu menu = popupMenu.getMenu();
            menu.add("Delete permanently");
            menu.add("Restore");
            popupMenu.setOnMenuItemClickListener(item -> {
                switch (item.getTitle().toString()) {
                    case "Delete permanently":
                        _deleteBlock(position);
                        break;

                    case "Restore":
                        _changePallette(position);
                        break;

                    default:
                        return false;
                }
                return true;
            });
            popupMenu.show();
            return;
        }
        PopupMenu popupMenu = new PopupMenu(this, view);
        Menu menu = popupMenu.getMenu();
        menu.add("Insert above");
        menu.add("Delete");
        menu.add("Duplicate");
        menu.add("Move to palette");
        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getTitle().toString()) {
                case "Duplicate":
                    _duplicateBlock(position);
                    break;

                case "Insert above":
                    Object paletteColor = pallet_list.get(palette - 9).get("color");
                    if (paletteColor instanceof String) {
                        Intent intent = new Intent(getApplicationContext(), BlocksManagerCreatorActivity.class);
                        intent.putExtra("mode", "insert");
                        intent.putExtra("path", blocks_path);
                        intent.putExtra("color", (String) paletteColor);
                        intent.putExtra("pos", String.valueOf(position));
                        startActivity(intent);
                    } else {
                        SketchwareUtil.toastError("Invalid color of palette #" + (palette - 9));
                    }
                    break;

                case "Move to palette":
                    _changePallette(position);
                    break;

                case "Delete":
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Delete block?")
                            .setMessage("Are you sure you want to delete this block?")
                            .setPositiveButton("Recycle bin", (dialog, which) -> _moveToRecycleBin(position))
                            .setNegativeButton(R.string.common_word_cancel, null)
                            .setNeutralButton("Delete permanently", (dialog, which) -> _deleteBlock(position))
                            .show();
                    break;

                default:
                    return false;
            }
            return true;
        });
        popupMenu.show();
    }

    private void _duplicateBlock(int position) {
        HashMap<String, Object> block = new HashMap<>(all_blocks_list.get(position));
        Object blockName = block.get("name");

        if (blockName instanceof String) {
            if (((String) blockName).matches("(?s).*_copy[0-9][0-9]")) {
                block.put("name", ((String) blockName).replaceAll("_copy[0-9][0-9]", "_copy" + SketchwareUtil.getRandom(11, 99)));
            } else {
                block.put("name", blockName + "_copy" + SketchwareUtil.getRandom(11, 99));
            }
        }
        all_blocks_list.add(position + 1, block);
        FileUtil.writeFile(blocks_path, getGson().toJson(all_blocks_list));
        _refreshLists();
    }

    private void _deleteBlock(int position) {
        all_blocks_list.remove(position);
        FileUtil.writeFile(blocks_path, getGson().toJson(all_blocks_list));
        _refreshLists();
    }

    private void _moveToRecycleBin(int position) {
        all_blocks_list.get(position).put("palette", "-1");
        FileUtil.writeFile(blocks_path, getGson().toJson(all_blocks_list));
        _refreshLists();
    }

    private void _changePallette(int position) {
        ArrayList<String> paletteNames = new ArrayList<>();
        for (int j = 0, pallet_listSize = pallet_list.size(); j < pallet_listSize; j++) {
            HashMap<String, Object> palette = pallet_list.get(j);
            Object name = palette.get("name");

            if (name instanceof String) {
                paletteNames.add((String) name);
            } else {
                SketchwareUtil.toastError("Invalid name of Custom Block palette #" + (j + 1));
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setNegativeButton(R.string.common_word_cancel, null);
        if (palette == -1) {
            AtomicInteger restoreToChoice = new AtomicInteger(-1);
            builder.setTitle("Restore to")
                    .setSingleChoiceItems(paletteNames.toArray(new String[0]), -1, (dialog, which) -> restoreToChoice.set(which))
                    .setPositiveButton("Restore", (dialog, which) -> {
                        if (restoreToChoice.get() != -1) {
                            all_blocks_list.get(position).put("palette", String.valueOf(restoreToChoice.get() + 9));
                            Collections.swap(all_blocks_list, position, all_blocks_list.size() - 1);
                            FileUtil.writeFile(blocks_path, getGson().toJson(all_blocks_list));
                            _refreshLists();
                        }
                    });
        } else {
            AtomicInteger moveToChoice = new AtomicInteger(palette - 9);
            builder.setTitle("Move to")
                    .setSingleChoiceItems(paletteNames.toArray(new String[0]), palette - 9, (dialog, which) -> moveToChoice.set(which))
                    .setPositiveButton("Move", (dialog, which) -> {
                        all_blocks_list.get(position).put("palette", String.valueOf(moveToChoice.get() + 9));
                        Collections.swap(all_blocks_list, position, all_blocks_list.size() - 1);
                        FileUtil.writeFile(blocks_path, getGson().toJson(all_blocks_list));
                        _refreshLists();
                    });
        }
        builder.show();
    }

    private void _importBlocks(ArrayList<HashMap<String, Object>> blocks) {
        try {
            ArrayList<String> names = new ArrayList<>();
            ArrayList<Integer> toAdd = new ArrayList<>();
            for (int i = 0; i < blocks.size(); i++) {
                Object blockName = blocks.get(i).get("name");

                if (blockName instanceof String) {
                    names.add((String) blockName);
                } else {
                    SketchwareUtil.toastError("Invalid name entry of Custom Block #" + (i + 1) + " in Blocks to import");
                }
            }
            MaterialAlertDialogBuilder import_dialog = new MaterialAlertDialogBuilder(this);
            import_dialog.setTitle("Import blocks")
                    .setMultiChoiceItems(names.toArray(new CharSequence[0]), null, (dialog, which, isChecked) -> {
                        if (isChecked) {
                            toAdd.add(which);
                        } else {
                            toAdd.remove((Integer) which);
                        }
                    })
                    .setPositiveButton("Import", (dialog, which) -> {
                        for (int i = 0; i < blocks.size(); i++) {
                            if (toAdd.contains(i)) {
                                HashMap<String, Object> map = blocks.get(i);
                                map.put("palette", String.valueOf(palette));
                                all_blocks_list.add(map);
                            }
                        }
                        FileUtil.writeFile(blocks_path, getGson().toJson(all_blocks_list));
                        _refreshLists();
                        SketchwareUtil.toast("Imported successfully");
                    })
                    .setNegativeButton("Reverse", (dialog, which) -> {
                        for (int i = 0; i < blocks.size(); i++) {
                            if (!toAdd.contains(i)) {
                                HashMap<String, Object> map = blocks.get(i);
                                map.put("palette", String.valueOf(palette));
                                all_blocks_list.add(map);
                            }
                        }
                        FileUtil.writeFile(blocks_path, getGson().toJson(all_blocks_list));
                        _refreshLists();
                        SketchwareUtil.toast("Imported successfully");
                    })
                    .setNeutralButton("All", (dialog, which) -> {
                        for (int i = 0; i < blocks.size(); i++) {
                            HashMap<String, Object> map = blocks.get(i);
                            map.put("palette", String.valueOf(palette));
                            all_blocks_list.add(map);
                        }
                        FileUtil.writeFile(blocks_path, getGson().toJson(all_blocks_list));
                        _refreshLists();
                        SketchwareUtil.toast("Imported successfully");
                    })
                    .show();
        } catch (Exception e) {
            SketchwareUtil.toastError("An error occurred! [" + e.getMessage() + "]");
        }
    }

    private void fabButtonVisibility(boolean visible) {
        if (visible) {
            ObjectAnimator.ofFloat(fab_button, "translationX", fab_button.getTranslationX(), -50.0f, 0.0f).setDuration(400L).start();
        } else {
            ObjectAnimator.ofFloat(fab_button, "translationX", fab_button.getTranslationX(), -50.0f, 250.0f).setDuration(400L).start();
        }
    }

    private class Adapter extends BaseAdapter {

        private final ArrayList<HashMap<String, Object>> blocks;

        public Adapter(ArrayList<HashMap<String, Object>> data) {
            blocks = data;
        }

        @Override
        public int getCount() {
            return blocks.size();
        }

        @Override
        public HashMap<String, Object> getItem(int position) {
            return blocks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.block_customview, parent, false);
            }

            HashMap<String, Object> block = blocks.get(position);

            LinearLayout background = convertView.findViewById(R.id.background);
            TextView name = convertView.findViewById(R.id.name);
            TextView spec = convertView.findViewById(R.id.spec);
            CardView upLayout = convertView.findViewById(R.id.up_layout);
            CardView downLayout = convertView.findViewById(R.id.down_layout);
            LinearLayout down = convertView.findViewById(R.id.down);
            LinearLayout up = convertView.findViewById(R.id.up);

            if (mode.equals("normal")) {
                downLayout.setVisibility(View.GONE);
                upLayout.setVisibility(View.GONE);
            } else {
                downLayout.setVisibility(position != blocks.size() - 1 ? View.VISIBLE : View.GONE);
                upLayout.setVisibility(position != 0 ? View.VISIBLE : View.GONE);
            }

            Object blockName = block.get("name");
            if (blockName instanceof String) {
                name.setText((String) blockName);
                spec.setHint("");
            } else {
                name.setText("");
                name.setHint("(Invalid block name entry)");
            }

            Object blockSpec = block.get("spec");
            if (blockSpec instanceof String) {
                spec.setText((String) blockSpec);
                spec.setHint("");
            } else {
                spec.setText("");
                spec.setHint("(Invalid block spec entry)");
            }

            Object blockType = block.get("type");
            if (blockType instanceof String) {
                switch ((String) blockType) {
                    case " ":
                    case "regular":
                        spec.setBackgroundResource(R.drawable.block_ori);
                        break;

                    case "b":
                        spec.setBackgroundResource(R.drawable.block_boolean);
                        break;

                    case "c":
                    case "e":
                        spec.setBackgroundResource(R.drawable.if_else);
                        break;

                    case "d":
                        spec.setBackgroundResource(R.drawable.block_num);
                        break;

                    case "f":
                        spec.setBackgroundResource(R.drawable.block_stop);
                        break;

                    default:
                        spec.setBackgroundResource(R.drawable.block_string);
                        break;
                }
            } else {
                spec.setBackgroundResource(R.drawable.block_string);
            }

            if (palette == -1) {
                spec.getBackground().setColorFilter(new PorterDuffColorFilter(0xff9e9e9e, PorterDuff.Mode.MULTIPLY));
            } else {
                if (block.containsKey("color")) {
                    Object blockColor = block.get("color");

                    if (blockColor instanceof String) {
                        int color = -1;
                        try {
                            color = Color.parseColor((String) blockColor);
                        } catch (IllegalArgumentException e) {
                            SketchwareUtil.toastError("Invalid color entry in block #" + (position + 1));
                        }

                        if (color != -1) {
                            spec.getBackground().setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                        }
                    } else {
                        SketchwareUtil.toastError("Invalid color entry in block #" + (position + 1));
                    }
                } else {
                    HashMap<String, Object> paletteObject = pallet_list.get(palette - 9);
                    Object paletteColor = paletteObject.get("color");

                    if (paletteColor instanceof String) {
                        try {
                            spec.getBackground().setColorFilter(new PorterDuffColorFilter(
                                    Color.parseColor((String) paletteColor),
                                    PorterDuff.Mode.MULTIPLY
                            ));
                        } catch (IllegalArgumentException e) {
                            SketchwareUtil.toastError("Invalid color in Custom Block palette #" + (palette - 8));
                        }
                    }
                }
            }
            up.setOnClickListener(v -> {
                if (position > 0) {
                    _swapitems(reference_list.get(position), reference_list.get(position - 1));
                }
            });
            down.setOnClickListener(v -> {
                if (position < filtered_list.size() - 1) {
                    _swapitems(reference_list.get(position), reference_list.get(position + 1));
                }
            });
            if (mode.equals("normal")) {
                background.setOnClickListener(v -> {
                    if (palette == -1) {
                        _showItemPopup(background, reference_list.get(position));
                    } else {
                        Object paletteColor = pallet_list.get(palette - 9).get("color");

                        if (paletteColor instanceof String) {
                            Intent intent = new Intent(getApplicationContext(), BlocksManagerCreatorActivity.class);
                            intent.putExtra("mode", "edit");
                            intent.putExtra("color", (String) paletteColor);
                            intent.putExtra("path", blocks_path);
                            intent.putExtra("pos", String.valueOf(reference_list.get(position)));
                            startActivity(intent);
                        }
                    }
                });
                background.setOnLongClickListener(v -> {
                    _showItemPopup(background, reference_list.get(position));
                    return true;
                });
            }
            return convertView;
        }
    }
}
