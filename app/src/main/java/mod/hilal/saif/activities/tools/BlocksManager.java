package mod.hilal.saif.activities.tools;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.ColorPickerDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.editor.manage.block.v2.BlockLoader;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityBlocksManagerBinding;
import pro.sketchware.databinding.DialogBlockConfigurationBinding;
import pro.sketchware.databinding.DialogPaletteBinding;
import pro.sketchware.databinding.PalletCustomviewBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.PropertiesUtil;
import pro.sketchware.utility.SketchwareUtil;

public class BlocksManager extends BaseAppCompatActivity {

    boolean isDialogShowing;
    View draggedView;
    private ArrayList<HashMap<String, Object>> all_blocks_list = new ArrayList<>();
    private String blocks_dir;
    private String pallet_dir;
    private int oldPos;
    private int newPos;
    private Activity activity;
    private ArrayList<HashMap<String, Object>> pallet_listmap = new ArrayList<>();
    // Maps a position in the currently-displayed (possibly search-filtered) adapter data
    // back to its real index in pallet_listmap. When no search is active this is simply
    // [0, 1, 2, ...]. All position-dependent operations (open details, edit, delete,
    // insert) must resolve through this instead of assuming display position == real index.
    private final ArrayList<Integer> pallet_reference_list = new ArrayList<>();
    private String searchQuery = "";
    private ItemTouchHelper itemTouchHelper;
    private ActivityBlocksManagerBinding binding;
    private DialogPaletteBinding dialogBinding;
    private Vibrator vibrator;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle _savedInstanceState) {
        super.onCreate(_savedInstanceState);
        binding = ActivityBlocksManagerBinding.inflate(getLayoutInflater());
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.background, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        initialize();
    }

    @Override
    public void onStop() {
        super.onStop();
        BlockLoader.refresh();
    }

    private void initialize() {
        activity = this;

        setSupportActionBar(binding.toolbar);

        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        binding.toolbar.setNavigationOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
        binding.paletteRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.paletteRecycler.setAdapter(new PaletteAdapter());
        binding.fab.setOnClickListener(v -> showPaletteDialog(false, null, null, "#ffffff", null));

        recycleBin(binding.recycleBinCard);
        loadDataAsync();

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (!searchQuery.isEmpty()) return false;

                oldPos = viewHolder.getBindingAdapterPosition();
                newPos = target.getBindingAdapterPosition();

                Collections.swap(pallet_listmap, oldPos, newPos);

                Objects.requireNonNull(binding.paletteRecycler.getAdapter()).notifyItemMoved(oldPos, newPos);
                swapRelatedBlocks(oldPos + 9, newPos + 9);

                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int action) {
                if (action == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder.itemView.setAlpha(0.7f);
                    draggedView = viewHolder.itemView;
                }
                super.onSelectedChanged(viewHolder, action);
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                viewHolder.itemView.setAlpha(1f);
                FileUtil.writeFile(blocks_dir, getGson().toJson(all_blocks_list));
                FileUtil.writeFile(pallet_dir, getGson().toJson(pallet_listmap));
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    binding.background.setClipChildren(!isItNearTrash(draggedView, binding.recycleBin));
                    if (isItInTrash(draggedView, binding.recycleBin)) {
                        int pos = viewHolder.getBindingAdapterPosition();
                        binding.recycleBinCard.setAlpha(0.5f);
                        if (!isCurrentlyActive && pos != RecyclerView.NO_POSITION && pos < pallet_listmap.size() && !isDialogShowing) {
                            vibrator.vibrate(40L);
                            showMoveToBinDialog(pos);
                            isDialogShowing = true;
                        }
                        return;
                    }
                }
                binding.recycleBinCard.setAlpha(1f);
                isDialogShowing = false;
            }

        });

        itemTouchHelper.attachToRecyclerView(binding.paletteRecycler);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem searchItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Search");
        searchItem.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_search));
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        SearchView searchView = new SearchView(this);
        searchView.setQueryHint("Search palettes");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchQuery = newText == null ? "" : newText;
                applyFilter();
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
                applyFilter();
                return true;
            }
        });
        MenuItemCompat.setActionView(searchItem, searchView);

        menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Settings").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_settings)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {
        String title = Objects.requireNonNull(menuItem.getTitle()).toString();
        if (title.equals("Settings")) {
            showBlockConfigurationDialog();
        } else {
            return false;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDataAsync();
    }

    private void showBlockConfigurationDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.ic_folder_48dp);
        dialog.setTitle("Block configuration");

        DialogBlockConfigurationBinding dialogBinding = DialogBlockConfigurationBinding.inflate(getLayoutInflater());

        dialogBinding.palettesPath.setText(pallet_dir.replace(FileUtil.getExternalStorageDir(), ""));
        dialogBinding.blocksPath.setText(blocks_dir.replace(FileUtil.getExternalStorageDir(), ""));

        dialog.setView(dialogBinding.getRoot());

        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (view, which) -> {
            ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH, Objects.requireNonNull(dialogBinding.palettesPath.getText()).toString());
            ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH, Objects.requireNonNull(dialogBinding.blocksPath.getText()).toString());

            readSettings();
            refreshList();
            view.dismiss();
        });

        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);

        dialog.setNeutralButton("Defaults", (view, which) -> {
            ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH, ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH));
            ConfigActivity.setSetting(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH, ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH));

            readSettings();
            refreshList();
            view.dismiss();
        });

        dialog.show();
    }

    private void showMoveToBinDialog(int position) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity);
        dialog.setIcon(R.drawable.ic_mtrl_delete);
        dialog.setTitle(R.string.block_move_to_bin);
        dialog.setMessage(R.string.common_message_confirm);
        dialog.setPositiveButton(R.string.common_word_yes, (v, which) -> {
            pallet_listmap.remove(position);
            applyFilter();
            draggedView = null;
            moveRelatedBlocksToRecycleBin(position + 9);
            removeRelatedBlocks(position + 9);
            FileUtil.writeFile(blocks_dir, getGson().toJson(all_blocks_list));
            FileUtil.writeFile(pallet_dir, getGson().toJson(pallet_listmap));
            refreshCount();
            v.dismiss();
        });
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    /**
     * Async replacement for the initial readSettings()+refreshList() sequence, used only
     * for onCreate/onResume — the moment that previously froze the UI on large block/palette
     * files. File reading happens off the main thread; parsing (fast) and any error dialog
     * stay on the main thread since Gson parsing of these files is not the slow part and
     * SketchwareUtil.showFailedToParseJsonDialog must not be called off the main thread.
     * readSettings()/refreshList() themselves are left exactly as-is for their many other
     * (post-edit/delete/swap) call sites, which are small, already-in-memory refreshes.
     */
    private void loadDataAsync() {
        binding.loadingIndicator.setVisibility(View.VISIBLE);
        binding.paletteRecycler.setVisibility(View.GONE);
        binding.emptySearchState.setVisibility(View.GONE);

        String resolvedPalletDir = FileUtil.getExternalStorageDir() + ConfigActivity.getStringSettingValueOrSetAndGet(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH,
                (String) ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH));
        String resolvedBlocksDir = FileUtil.getExternalStorageDir() + ConfigActivity.getStringSettingValueOrSetAndGet(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH,
                (String) ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH));
        pallet_dir = resolvedPalletDir;
        blocks_dir = resolvedBlocksDir;

        ioExecutor.execute(() -> {
            String blocksContent = FileUtil.isExistFile(resolvedBlocksDir) ? FileUtil.readFile(resolvedBlocksDir) : "";
            String paletteContent = FileUtil.isExistFile(resolvedPalletDir) ? FileUtil.readFile(resolvedPalletDir) : "";
            mainHandler.post(() -> applyLoadedData(blocksContent, paletteContent));
        });
    }

    private void applyLoadedData(String blocksContent, String paletteContent) {
        if (isFinishing() || isDestroyed()) return;

        if (!blocksContent.isEmpty() && isValidJson(blocksContent)) {
            try {
                ArrayList<HashMap<String, Object>> parsed = getGson().fromJson(blocksContent, Helper.TYPE_MAP_LIST);
                if (parsed != null) {
                    all_blocks_list = parsed;
                } else {
                    SketchwareUtil.showFailedToParseJsonDialog(this, new File(blocks_dir), "Custom Blocks", v -> loadDataAsync());
                }
            } catch (JsonParseException e) {
                SketchwareUtil.showFailedToParseJsonDialog(this, new File(blocks_dir), "Custom Blocks", v -> loadDataAsync());
            }
        }

        if (!paletteContent.isEmpty()) {
            try {
                ArrayList<HashMap<String, Object>> parsed = getGson().fromJson(paletteContent, Helper.TYPE_MAP_LIST);
                if (parsed != null) {
                    pallet_listmap = parsed;
                } else {
                    SketchwareUtil.showFailedToParseJsonDialog(this, new File(pallet_dir), "Custom Block Palettes", v -> loadDataAsync());
                    pallet_listmap = new ArrayList<>();
                }
            } catch (JsonParseException e) {
                SketchwareUtil.showFailedToParseJsonDialog(this, new File(pallet_dir), "Custom Block Palettes", v -> loadDataAsync());
                pallet_listmap = new ArrayList<>();
            }
        } else {
            pallet_listmap = new ArrayList<>();
        }

        applyFilter();
        binding.recycleSub.setText("Blocks: " + (long) getN(-1));
        refreshCount();

        binding.loadingIndicator.setVisibility(View.GONE);
        binding.paletteRecycler.setVisibility(View.VISIBLE);
    }

    /**
     * Rebuilds pallet_reference_list from pallet_listmap, applying searchQuery (palette name,
     * case-insensitive) if one is active. This is the single source of truth the adapter reads
     * through — never index pallet_listmap by adapter position directly.
     */
    private void applyFilter() {
        pallet_reference_list.clear();
        String q = searchQuery.trim().toLowerCase(Locale.getDefault());
        for (int i = 0; i < pallet_listmap.size(); i++) {
            if (q.isEmpty() || matchesPaletteSearch(pallet_listmap.get(i), q)) {
                pallet_reference_list.add(i);
            }
        }
        Objects.requireNonNull(binding.paletteRecycler.getAdapter()).notifyDataSetChanged();

        boolean noResults = !q.isEmpty() && pallet_reference_list.isEmpty();
        binding.emptySearchState.setVisibility(noResults ? View.VISIBLE : View.GONE);
        binding.paletteRecycler.setVisibility(noResults ? View.GONE : View.VISIBLE);
    }

    private boolean matchesPaletteSearch(HashMap<String, Object> palette, String lowerCaseQuery) {
        Object name = palette.get("name");
        if (name instanceof String && ((String) name).toLowerCase(Locale.getDefault()).contains(lowerCaseQuery)) {
            return true;
        }
        // Also match if any block belonging to this palette has a matching name/spec, so
        // searching "json" finds the right palette even if the palette itself isn't named that.
        int paletteId = pallet_listmap.indexOf(palette) + 9;
        if (all_blocks_list == null) return false;
        for (HashMap<String, Object> block : all_blocks_list) {
            Object p = block.get("palette");
            if (p != null && String.valueOf(paletteId).equals(p.toString())) {
                Object bName = block.get("name");
                Object bSpec = block.get("spec");
                if (bName instanceof String && ((String) bName).toLowerCase(Locale.getDefault()).contains(lowerCaseQuery)) return true;
                if (bSpec instanceof String && ((String) bSpec).toLowerCase(Locale.getDefault()).contains(lowerCaseQuery)) return true;
            }
        }
        return false;
    }

    private void readSettings() {
        pallet_dir = FileUtil.getExternalStorageDir() + ConfigActivity.getStringSettingValueOrSetAndGet(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH,
                (String) ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_PALETTE_FILE_PATH));
        blocks_dir = FileUtil.getExternalStorageDir() + ConfigActivity.getStringSettingValueOrSetAndGet(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH,
                (String) ConfigActivity.getDefaultValue(ConfigActivity.SETTING_BLOCKMANAGER_DIRECTORY_BLOCK_FILE_PATH));

        if (FileUtil.isExistFile(blocks_dir) && isValidJson(FileUtil.readFile(blocks_dir))) {
            try {
                all_blocks_list = getGson().fromJson(FileUtil.readFile(blocks_dir), Helper.TYPE_MAP_LIST);

                if (all_blocks_list != null) {
                    return;
                }
                // fall-through to shared handler
            } catch (JsonParseException e) {
                // fall-through to shared handler
            }

            SketchwareUtil.showFailedToParseJsonDialog(this, new File(blocks_dir), "Custom Blocks", v -> readSettings());
        }
    }

    private Boolean isValidJson(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonObject() || element.isJsonArray();
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    private void refreshList() {
        parsePaletteJson:
        {
            String paletteJsonContent;
            if (FileUtil.isExistFile(pallet_dir) && !(paletteJsonContent = FileUtil.readFile(pallet_dir)).isEmpty()) {
                try {
                    pallet_listmap = getGson().fromJson(paletteJsonContent, Helper.TYPE_MAP_LIST);

                    if (pallet_listmap != null) {
                        break parsePaletteJson;
                    }
                    // fall-through to shared handler
                } catch (JsonParseException e) {
                    // fall-through to shared handler
                }

                SketchwareUtil.showFailedToParseJsonDialog(this, new File(pallet_dir), "Custom Block Palettes", v -> refreshList());
            }
            pallet_listmap = new ArrayList<>();
        }

        applyFilter();
        binding.recycleSub.setText("Blocks: " + (long) getN(-1));
        refreshCount();
    }

    private double getN(double _p) {
        int n = 0;
        if (all_blocks_list == null) return 0;

        for (int i = 0; i < all_blocks_list.size(); i++) {
            if (Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString().equals(String.valueOf((long) _p))) {
                n++;
            }
        }
        return n;
    }

    private void refreshCount() {
        if (pallet_listmap.isEmpty()) {
            binding.paletteCount.setText("No palettes");
        } else {
            binding.paletteCount.setText(pallet_listmap.size() + " Palettes");
        }
    }

    private void recycleBin(View view) {
        view.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), BlocksManagerDetailsActivity.class);
            intent.putExtra("position", "-1");
            intent.putExtra("dirB", blocks_dir);
            intent.putExtra("dirP", pallet_dir);
            startActivity(intent);
        });
        view.setOnLongClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Recycle bin")
                    .setMessage("Are you sure you want to empty the recycle bin? " +
                            "Blocks inside will be deleted PERMANENTLY, you CANNOT recover them!")
                    .setPositiveButton("Empty", (dialog, which) -> emptyRecyclebin())
                    .setNegativeButton(R.string.common_word_cancel, null)
                    .show();
            return true;
        });
    }

    private void removeRelatedBlocks(double _p) {
        List<Map<String, Object>> newBlocks = new LinkedList<>();
        for (int i = 0; i < all_blocks_list.size(); i++) {
            if (!(Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) == _p)) {
                if (Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) > _p) {
                    HashMap<String, Object> m = all_blocks_list.get(i);
                    m.put("palette", String.valueOf((long) (Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) - 1)));
                    newBlocks.add(m);
                } else {
                    newBlocks.add(all_blocks_list.get(i));
                }
            }
        }
        FileUtil.writeFile(blocks_dir, getGson().toJson(newBlocks));
        readSettings();
    }

    private void swapRelatedBlocks(double f, double s) {
        final String TEMP_PALETTE = "TEMP_SWAP";
        for (Map<String, Object> block : all_blocks_list) {
            Object paletteObj = block.get("palette");

            if (paletteObj == null) continue;
            double paletteValue;
            try {
                paletteValue = Double.parseDouble(paletteObj.toString());
            } catch (NumberFormatException e) {
                continue;
            }

            if (paletteValue == f) {
                block.put("palette", TEMP_PALETTE);
            } else if (paletteValue == s) {
                block.put("palette", String.valueOf((long) f));
            }
        }
        for (Map<String, Object> block : all_blocks_list) {
            if (TEMP_PALETTE.equals(block.get("palette"))) {
                block.put("palette", String.valueOf((long) s));
            }
        }
    }

    private void insertBlocksAt(double _p) {
        for (int i = 0; i < all_blocks_list.size(); i++) {
            if (Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) > _p || Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) == _p) {
                all_blocks_list.get(i).put("palette", String.valueOf((long) (Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) + 1)));
            }
        }
        FileUtil.writeFile(blocks_dir, getGson().toJson(all_blocks_list));
        readSettings();
        refreshList();
    }

    private void moveRelatedBlocksToRecycleBin(double _p) {
        for (int i = 0; i < all_blocks_list.size(); i++) {
            if (Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) == _p) {
                all_blocks_list.get(i).put("palette", "-1");
            }
        }
        FileUtil.writeFile(blocks_dir, getGson().toJson(all_blocks_list));
        readSettings();
    }

    private void emptyRecyclebin() {
        List<Map<String, Object>> newBlocks = new LinkedList<>();
        for (int i = 0; i < all_blocks_list.size(); i++) {
            if (!(Double.parseDouble(Objects.requireNonNull(all_blocks_list.get(i).get("palette")).toString()) == -1)) {
                newBlocks.add(all_blocks_list.get(i));
            }
        }
        FileUtil.writeFile(blocks_dir, getGson().toJson(newBlocks));
        readSettings();
        refreshList();
    }

    private void showPaletteDialog(boolean isEditing, Integer oldPosition, String oldName, String oldColor, Integer insertAtPosition) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.icon_style_white_96);
        dialog.setTitle(!isEditing ? "Create a new palette" : "Edit palette");

        dialogBinding = DialogPaletteBinding.inflate(getLayoutInflater());

        if (isEditing) {
            dialogBinding.nameEditText.setText(oldName);
            dialogBinding.colorEditText.setText(oldColor.replace("#", ""));
        }

        dialogBinding.openColorPalette.setOnClickListener(v1 -> {
            ColorPickerDialog colorPickerDialog = new ColorPickerDialog(this, 0xFFFFFFFF, false, false);
            colorPickerDialog.a(new ColorPickerDialog.b() {
                @Override
                public void a(int colorInt) {
                    dialogBinding.colorEditText.setText(String.format("%06X", colorInt & 0x00FFFFFF));
                }

                @Override
                public void a(String var1, int var2) {

                }
            });
            colorPickerDialog.showAtLocation(dialogBinding.openColorPalette, Gravity.CENTER, 0, 0);
        });

        dialog.setView(dialogBinding.getRoot());

        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (v, which) -> {
            String nameInput = Objects.requireNonNull(dialogBinding.nameEditText.getText()).toString();
            String colorInput = Objects.requireNonNull(dialogBinding.colorEditText.getText()).toString();

            if (nameInput.isEmpty()) {
                SketchwareUtil.toast("Name cannot be empty", Toast.LENGTH_SHORT);
                return;
            }
            // add hash for the color 
            colorInput = "#" + colorInput;

            if (!PropertiesUtil.isHexColor(colorInput)) {
                SketchwareUtil.toast("Please enter a valid HEX color", Toast.LENGTH_SHORT);
                return;
            }

            if (PropertiesUtil.isHexColor(colorInput)) {
                Color.parseColor(colorInput);
                if (!isEditing) {
                    HashMap<String, Object> map = new HashMap<>();
                    map.put("name", nameInput);
                    map.put("color", colorInput);

                    if (insertAtPosition == null) {
                        pallet_listmap.add(map);
                        FileUtil.writeFile(pallet_dir, getGson().toJson(pallet_listmap));
                        readSettings();
                        applyFilter();
                    } else {
                        pallet_listmap.add(insertAtPosition, map);
                        FileUtil.writeFile(pallet_dir, getGson().toJson(pallet_listmap));
                        readSettings();
                        insertBlocksAt(insertAtPosition + 9);
                        applyFilter();
                    }
                } else {
                    pallet_listmap.get(oldPosition).put("name", nameInput);
                    pallet_listmap.get(oldPosition).put("color", colorInput);
                    FileUtil.writeFile(pallet_dir, getGson().toJson(pallet_listmap));
                    readSettings();
                    refreshList();
                }
                refreshCount();
                v.dismiss();
            }
        });

        dialog.setNegativeButton(Helper.getResString(R.string.cancel), null);
        dialog.show();
    }


    private boolean isItInTrash(View draggedView, View trash) {
        if (draggedView == null) return false;

        int[] trashLocation = new int[2];
        trash.getLocationOnScreen(trashLocation);

        int[] draggedLocation = new int[2];
        draggedView.getLocationOnScreen(draggedLocation);

        int draggedY = draggedLocation[1];

        return draggedY <= trashLocation[1] + draggedView.getMeasuredHeight() / 2 && draggedY >= trashLocation[1] - draggedView.getMeasuredHeight() / 2;
    }

    private boolean isItNearTrash(View draggedView, View trash) {
        if (draggedView == null) return false;

        int[] trashLocation = new int[2];
        trash.getLocationOnScreen(trashLocation);

        int[] draggedLocation = new int[2];
        draggedView.getLocationOnScreen(draggedLocation);

        int draggedY = draggedLocation[1];

        return draggedY <= trashLocation[1] + draggedView.getMeasuredHeight() * 2 / 2 && draggedY >= trashLocation[1] - draggedView.getMeasuredHeight() * 2 / 2;
    }


    public class PaletteAdapter extends RecyclerView.Adapter<PaletteAdapter.ViewHolder> {

        @NonNull
        @Override
        public PaletteAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PalletCustomviewBinding itemBinding = PalletCustomviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new PaletteAdapter.ViewHolder(itemBinding);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull PaletteAdapter.ViewHolder holder, int position) {
            // Always resolve the real pallet_listmap index through pallet_reference_list —
            // "position" is only meaningful within the currently displayed (possibly
            // search-filtered) subset.
            int realIndex = pallet_reference_list.get(position);
            HashMap<String, Object> paletteEntry = pallet_listmap.get(realIndex);

            String paletteColorValue = (String) paletteEntry.get("color");
            assert paletteColorValue != null;
            int backgroundColor = PropertiesUtil.parseColor(paletteColorValue);

            holder.itemView.setVisibility(View.VISIBLE);
            holder.itemBinding.title.setText(Objects.requireNonNull(paletteEntry.get("name")).toString());
            holder.itemBinding.sub.setText("Blocks: " + (long) getN(realIndex + 9));
            holder.itemBinding.color.setBackgroundColor(backgroundColor);
            // Reordering by drag only makes sense against the unfiltered list, so it's
            // disabled while a search is active (existing drag/reorder behavior is fully
            // preserved when not searching).
            holder.itemBinding.dragHandler.setVisibility(searchQuery.isEmpty() ? View.VISIBLE : View.GONE);
            binding.recycleSub.setText("Blocks: " + (long) getN(-1));

            holder.itemBinding.backgroundCard.setOnLongClickListener(v -> {
                final String edit = "Edit";
                final String delete = "Delete";
                final String insert = "Insert";

                PopupMenu popup = new PopupMenu(BlocksManager.this, holder.itemBinding.color);
                Menu menu = popup.getMenu();
                menu.add(edit);
                menu.add(delete);
                if (searchQuery.isEmpty()) menu.add(insert); // insert-at-position is ambiguous while filtered
                popup.setOnMenuItemClickListener(item -> {
                    int pos = pallet_reference_list.get(holder.getAbsoluteAdapterPosition());
                    switch (Objects.requireNonNull(item.getTitle()).toString()) {
                        case edit:
                            showPaletteDialog(true, pos,
                                    Objects.requireNonNull(pallet_listmap.get(pos).get("name")).toString(),
                                    Objects.requireNonNull(pallet_listmap.get(pos).get("color")).toString(), null);
                            break;

                        case delete:
                            new MaterialAlertDialogBuilder(BlocksManager.this)
                                    .setTitle(Objects.requireNonNull(pallet_listmap.get(pos).get("name")).toString())
                                    .setMessage("Remove all blocks related to this palette?")
                                    .setPositiveButton("Remove permanently", (dialog, which) -> {
                                        pallet_listmap.remove(pos);
                                        FileUtil.writeFile(pallet_dir, getGson().toJson(pallet_listmap));
                                        removeRelatedBlocks(pos + 9);
                                        readSettings();
                                        applyFilter();
                                        refreshCount();
                                    })
                                    .setNegativeButton(R.string.common_word_cancel, null)
                                    .setNeutralButton(R.string.block_move_to_bin, (dialog, which) -> {
                                        moveRelatedBlocksToRecycleBin(pos + 9);
                                        pallet_listmap.remove(pos);
                                        FileUtil.writeFile(pallet_dir, getGson().toJson(pallet_listmap));
                                        removeRelatedBlocks(pos + 9);
                                        readSettings();
                                        applyFilter();
                                        refreshCount();
                                    }).show();
                            break;

                        case insert:
                            showPaletteDialog(false, null, null, null, pos);
                            break;

                        default:
                    }
                    return true;
                });
                popup.show();

                return true;
            });

            holder.itemBinding.dragHandler.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(holder);
                }

                return false;
            });

            holder.itemBinding.backgroundCard.setOnClickListener(v -> {
                int clickedRealIndex = pallet_reference_list.get(holder.getAbsoluteAdapterPosition());
                Intent intent = new Intent(getApplicationContext(), BlocksManagerDetailsActivity.class);
                intent.putExtra("position", String.valueOf((long) (clickedRealIndex + 9)));
                intent.putExtra("dirB", blocks_dir);
                intent.putExtra("dirP", pallet_dir);
                startActivity(intent);
            });

        }

        @Override
        public int getItemCount() {
            return pallet_reference_list.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            public PalletCustomviewBinding itemBinding;

            public ViewHolder(PalletCustomviewBinding itemBinding) {
                super(itemBinding.getRoot());
                this.itemBinding = itemBinding;
            }
        }
    }
}

