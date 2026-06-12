package com.besome.sketch.editor.manage;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.BlockCollectionBean;
import com.besome.sketch.beans.MoreBlockCollectionBean;
import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.beans.SelectableBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.beans.WidgetCollectionBean;
import com.besome.sketch.editor.manage.font.AddFontActivity;
import com.besome.sketch.editor.manage.font.AddFontCollectionActivity;
import com.besome.sketch.editor.manage.image.AddImageCollectionActivity;
import com.besome.sketch.editor.manage.sound.AddSoundCollectionActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView; // Added Import
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigationrail.NavigationRailView;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;

import a.a.a.Mp;
import a.a.a.Np;
import a.a.a.Op;
import a.a.a.Pp;
import a.a.a.Qp;
import a.a.a.Rp;
import a.a.a.bB;
import a.a.a.mB;
import a.a.a.wq;
import mod.hey.studios.util.Helper;
import mod.jbk.util.AudioMetadata;
import mod.jbk.util.BlockUtil;
import mod.jbk.util.SoundPlayingAdapter;
import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;

public class ManageCollectionActivity extends BaseAppCompatActivity implements View.OnClickListener {
    private static final int REQUEST_CODE_ADD_IMAGE_DIALOG = 267;
    private static final int REQUEST_CODE_SHOW_IMAGE_DETAILS = 268;
    private static final int REQUEST_CODE_ADD_SOUND_DIALOG = 269;
    private static final int REQUEST_CODE_SHOW_SOUND_DETAILS = 270;
    private static final int REQUEST_CODE_ADD_FONT_DIALOG = 271;
    private static final int REQUEST_CODE_SHOW_FONT_DETAILS = 272;
    private static final int REQUEST_CODE_SHOW_WIDGET_DETAILS = 273;
    private static final int REQUEST_CODE_SHOW_BLOCK_DETAILS = 274;
    private static final int REQUEST_CODE_SHOW_MORE_BLOCK_DETAILS = 279;

    private LinearLayout actionButtonGroup;
    private boolean hasDeletedWidget;
    private boolean selectingToBeDeletedItems;
    private CollectionAdapter collectionAdapter;
    private RecyclerView collection;
    private NavigationRailView categories;
    private ArrayList<ProjectResourceBean> images;
    private ArrayList<ProjectResourceBean> sounds;
    private ArrayList<ProjectResourceBean> fonts;
    private ArrayList<WidgetCollectionBean> widgets;
    private ArrayList<BlockCollectionBean> blocks;
    private ArrayList<MoreBlockCollectionBean> moreBlocks;
    private int currentItemId = 1;
    private LinearLayout noItemsLayout;
    private FloatingActionButton fab;
    private String sc_id;
    private SearchView searchView;

    private void showAddImageDialog() {
        Intent intent = new Intent(getApplicationContext(), AddImageCollectionActivity.class);
        intent.putParcelableArrayListExtra("images", images);
        intent.putExtra("sc_id", sc_id);
        startActivityForResult(intent, REQUEST_CODE_ADD_IMAGE_DIALOG);
    }

    private void showAddSoundDialog() {
        collectionAdapter.stopPlayback();
        Intent intent = new Intent(getApplicationContext(), AddSoundCollectionActivity.class);
        intent.putParcelableArrayListExtra("sounds", sounds);
        intent.putExtra("sc_id", sc_id);
        startActivityForResult(intent, REQUEST_CODE_ADD_SOUND_DIALOG);
    }

    private void showAddFontDialog() {
        Intent intent = new Intent(getApplicationContext(), AddFontActivity.class);
        intent.putParcelableArrayListExtra("font_names", fonts);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("add_to_collection", true);
        startActivityForResult(intent, REQUEST_CODE_ADD_FONT_DIALOG);
    }

    private int getBlockIcon(BlockBean block) {
        return switch (block.type) {
            case "c" -> R.drawable.fav_block_c_96dp;
            case "b" -> R.drawable.fav_block_boolean_96dp;
            case "f" -> R.drawable.fav_block_final_96dp;
            case "e" -> R.drawable.fav_block_e_96dp;
            case "d" -> R.drawable.fav_block_number_96dp;
            case "s" -> R.drawable.fav_block_string_96dp;
            default -> R.drawable.fav_block_command_96dp;
        };
    }

    private void changeDeletingItemsState(boolean deletingItems) {
        selectingToBeDeletedItems = deletingItems;
        invalidateOptionsMenu();
        unselectToBeDeletedItems();
        if (selectingToBeDeletedItems) {
            collectionAdapter.stopPlayback();
            actionButtonGroup.setVisibility(View.VISIBLE);
            if (searchView != null && !searchView.isIconified()) {
                searchView.onActionViewCollapsed();
            }
        } else {
            actionButtonGroup.setVisibility(View.GONE);
            if (currentItemId == 3 || currentItemId == 4 || currentItemId == 5) {
                fab.setVisibility(View.GONE);
            } else {
                fab.setVisibility(View.VISIBLE);
            }
        }
        collectionAdapter.notifyDataSetChanged();
    }

    private void handleFabOnClick(int categoryId) {
        if (categoryId == 0) showAddImageDialog();
        else if (categoryId == 1) showAddSoundDialog();
        else showAddFontDialog();
    }

    private void openImageDetails(int position) {
        ProjectResourceBean editTarget = (ProjectResourceBean) collectionAdapter.getItem(position);
        Intent intent = new Intent(getApplicationContext(), AddImageCollectionActivity.class);
        intent.putParcelableArrayListExtra("images", images);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("edit_target", editTarget);
        startActivityForResult(intent, REQUEST_CODE_SHOW_IMAGE_DETAILS);
    }

    private void openSoundDetails(int position) {
        ProjectResourceBean editTarget = (ProjectResourceBean) collectionAdapter.getItem(position);
        collectionAdapter.stopPlayback();
        Intent intent = new Intent(getApplicationContext(), AddSoundCollectionActivity.class);
        intent.putParcelableArrayListExtra("sounds", sounds);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("edit_target", editTarget);
        startActivityForResult(intent, REQUEST_CODE_SHOW_SOUND_DETAILS);
    }

    private void openFontDetails(int position) {
        ProjectResourceBean editTarget = (ProjectResourceBean) collectionAdapter.getItem(position);
        Intent intent = new Intent(getApplicationContext(), AddFontCollectionActivity.class);
        intent.putParcelableArrayListExtra("fonts", fonts);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("edit_target", editTarget);
        startActivityForResult(intent, REQUEST_CODE_SHOW_FONT_DETAILS);
    }

    private void openWidgetDetails(int position) {
        WidgetCollectionBean bean = (WidgetCollectionBean) collectionAdapter.getItem(position);
        Intent intent = new Intent(getApplicationContext(), ShowWidgetCollectionActivity.class);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("widget_name", bean.name);
        startActivityForResult(intent, REQUEST_CODE_SHOW_WIDGET_DETAILS);
    }

    private void openBlockDetails(int position) {
        BlockCollectionBean bean = (BlockCollectionBean) collectionAdapter.getItem(position);
        Intent intent = new Intent(getApplicationContext(), ShowBlockCollectionActivity.class);
        intent.putExtra("block_name", bean.name);
        startActivityForResult(intent, REQUEST_CODE_SHOW_BLOCK_DETAILS);
    }

    private void openMoreBlockDetails(int position) {
        MoreBlockCollectionBean bean = (MoreBlockCollectionBean) collectionAdapter.getItem(position);
        Intent intent = new Intent(getApplicationContext(), ShowMoreBlockCollectionActivity.class);
        intent.putExtra("block_name", bean.name);
        startActivityForResult(intent, REQUEST_CODE_SHOW_MORE_BLOCK_DETAILS);
    }

    private int getSelectedIndex(int id) {
        if (id == R.id.image) return 0;
        else if (id == R.id.audio) return 1;
        else if (id == R.id.font) return 2;
        else if (id == R.id.widget) return 3;
        else if (id == R.id.block) return 4;
        else if (id == R.id.moreblock) return 5;
        return -1;
    }

    private void setSelectedIndex(int item) {
        if (!mB.a()) {
            if (item != -1 && item != currentItemId) {
                if (currentItemId == 1) collectionAdapter.stopPlayback();
                if (searchView != null && !searchView.isIconified()) {
                    searchView.setQuery("", false);
                    searchView.onActionViewCollapsed();
                }
                if (selectingToBeDeletedItems) {
                    changeDeletingItemsState(false);
                }
                currentItemId = item;
                collection.removeAllViews();
                collectionAdapter.currentViewType = currentItemId;
                collectionAdapter.setData(switch (currentItemId) {
                    case 0 -> images;
                    case 1 -> sounds;
                    case 2 -> fonts;
                    case 3 -> widgets;
                    case 4 -> blocks;
                    default -> moreBlocks;
                });
                if (collectionAdapter.currentViewType == 0) {
                    collection.setLayoutManager(new GridLayoutManager(getApplicationContext(), getGridLayoutColumnCount()));
                    fab.show();
                } else {
                    collection.setLayoutManager(new LinearLayoutManager(getApplicationContext(), RecyclerView.VERTICAL, false));
                    if (collectionAdapter.currentViewType > 2) fab.hide();
                    else fab.show();
                }
            }
        }
    }

    private void confirmDeletion() {
        int count = 0;
        for (SelectableBean bean : collectionAdapter.currentCollectionTypeItems) {
            if (bean.isSelected) count++;
        }
        if (count == 0) {
            SketchwareUtil.toast("Please select items to delete");
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Confirm Deletion")
                .setMessage("Are you sure you want to delete " + count + " selected item(s)?")
                .setPositiveButton(R.string.common_word_delete, (dialog, which) -> deleteSelectedToBeDeletedItems())
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void deleteSelectedToBeDeletedItems() {
        int id = getCurrentCategoryItemId();
        switch (id) {
            case 0 -> { for (ProjectResourceBean bean : images) if (bean.isSelected) Op.g().a(bean.resName, false); Op.g().e(); loadImages(); }
            case 1 -> { for (ProjectResourceBean bean : sounds) if (bean.isSelected) Qp.g().a(bean.resName, false); Qp.g().e(); loadSounds(); }
            case 2 -> { for (ProjectResourceBean bean : fonts) if (bean.isSelected) Np.g().a(bean.resName, false); Np.g().e(); loadFonts(); }
            case 3 -> { for (WidgetCollectionBean bean : widgets) if (bean.isSelected) { hasDeletedWidget = true; Rp.h().a(bean.name, false); } Rp.h().e(); loadWidgets(); }
            case 4 -> { for (BlockCollectionBean bean : blocks) if (bean.isSelected) Mp.h().a(bean.name, false); Mp.h().e(); loadBlocks(); }
            case 5 -> { for (MoreBlockCollectionBean bean : moreBlocks) if (bean.isSelected) Pp.h().a(bean.name, false); Pp.h().e(); loadMoreBlocks(); }
        }
        unselectToBeDeletedItems();
        changeDeletingItemsState(false);
        bB.a(getApplicationContext(), Helper.getResString(R.string.common_message_complete_delete), 1).show();
    }

    private int getCurrentCategoryItemId() { return currentItemId; }

    private int getGridLayoutColumnCount() {
        int var1 = (int) ((float) getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density) / 100;
        return (var1 > 2) ? var1 - 1 : var1;
    }

    private void initialize() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        findViewById(R.id.layout_main_logo).setVisibility(View.GONE);
        Objects.requireNonNull(getSupportActionBar()).setTitle(Helper.getResString(R.string.design_actionbar_title_manager_collection));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        toolbar.setNavigationOnClickListener(v -> { if (!mB.a()) onBackPressed(); });

        noItemsLayout = findViewById(R.id.layout_no_collections);
        categories = findViewById(R.id.category_list);
        collection = findViewById(R.id.collection_list);
        collectionAdapter = new CollectionAdapter(collection);
        collection.setAdapter(collectionAdapter);
        fab = findViewById(R.id.fab);
        fab.setOnClickListener(this);
        actionButtonGroup = findViewById(R.id.layout_btn_group);

        MaterialButton delete = findViewById(R.id.btn_delete);
        MaterialButton cancel = findViewById(R.id.btn_cancel);
        delete.setText(Helper.getResString(R.string.common_word_delete));
        cancel.setText(Helper.getResString(R.string.common_word_cancel));
        delete.setOnClickListener(v -> confirmDeletion());
        cancel.setOnClickListener(this);

        categories.setOnItemSelectedListener(item -> {
            setSelectedIndex(getSelectedIndex(item.getItemId()));
            return true;
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_ADD_IMAGE_DIALOG, REQUEST_CODE_SHOW_IMAGE_DETAILS -> loadImages();
            case REQUEST_CODE_ADD_SOUND_DIALOG, REQUEST_CODE_SHOW_SOUND_DETAILS -> loadSounds();
            case REQUEST_CODE_ADD_FONT_DIALOG, REQUEST_CODE_SHOW_FONT_DETAILS -> loadFonts();
            case REQUEST_CODE_SHOW_WIDGET_DETAILS -> loadWidgets();
            case REQUEST_CODE_SHOW_BLOCK_DETAILS -> loadBlocks();
            case REQUEST_CODE_SHOW_MORE_BLOCK_DETAILS -> loadMoreBlocks();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (selectingToBeDeletedItems) { changeDeletingItemsState(false); }
        else if (searchView != null && !searchView.isIconified()) { searchView.onActionViewCollapsed(); }
        else {
            if (hasDeletedWidget) { setResult(RESULT_OK); finish(); }
            super.onBackPressed();
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_cancel && selectingToBeDeletedItems) changeDeletingItemsState(false);
        else if (id == R.id.fab) { changeDeletingItemsState(false); handleFabOnClick(currentItemId); }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (collectionAdapter != null && collectionAdapter.currentViewType == 0) {
            ((GridLayoutManager) Objects.requireNonNull(collection.getLayoutManager())).setSpanCount(getGridLayoutColumnCount());
            collection.requestLayout();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isStoragePermissionGranted()) finish();
        setContentView(R.layout.manage_collection);
        initialize();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.manage_collection_menu, menu);
        MenuItem deleteItem = menu.findItem(R.id.menu_collection_delete);
        if (selectingToBeDeletedItems) {
            deleteItem.setVisible(false);
            MenuItem selectAllItem = menu.add(Menu.NONE, 999, Menu.NONE, "Select All");
            selectAllItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        } else {
            deleteItem.setVisible(true);
            MenuItem searchMenuItem = menu.add(Menu.NONE, 998, Menu.NONE, "Search");
            searchMenuItem.setIcon(R.drawable.ic_mtrl_search);
            searchMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW | MenuItem.SHOW_AS_ACTION_ALWAYS);
            searchView = new SearchView(this);
            searchView.setQueryHint("Search...");
            searchMenuItem.setActionView(searchView);
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String query) { return false; }
                @Override public boolean onQueryTextChange(String newText) {
                    if (collectionAdapter != null) collectionAdapter.filter(newText);
                    return true;
                }
            });
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.menu_collection_delete) changeDeletingItemsState(!selectingToBeDeletedItems);
        else if (menuItem.getItemId() == 999) collectionAdapter.toggleSelectAll();
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        sc_id = savedInstanceState == null ? getIntent().getStringExtra("sc_id") : savedInstanceState.getString("sc_id");
        loadAllCollectionItems();
        categories.setSelectedItemId(R.id.image);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isStoragePermissionGranted()) finish();
        if (collectionAdapter != null) collectionAdapter.notifyDataSetChanged();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }

    private void loadAllCollectionItems() {
        images = Op.g().f(); sounds = Qp.g().f(); fonts = Np.g().f();
        widgets = Rp.h().f(); blocks = Mp.h().f(); moreBlocks = Pp.h().f();
        if (currentItemId == -1) {
            collectionAdapter.currentViewType = 0; collectionAdapter.setData(images);
            collection.setLayoutManager(new GridLayoutManager(getApplicationContext(), getGridLayoutColumnCount()));
            currentItemId = 0;
        }
    }

    private void loadImages() { images = Op.g().f(); if (currentItemId == 0) collectionAdapter.setData(images); }
    private void loadSounds() { sounds = Qp.g().f(); if (currentItemId == 1) collectionAdapter.setData(sounds); }
    private void loadFonts() { fonts = Np.g().f(); if (currentItemId == 2) collectionAdapter.setData(fonts); }
    private void loadWidgets() { widgets = Rp.h().f(); if (currentItemId == 3) collectionAdapter.setData(widgets); }
    private void loadBlocks() { blocks = Mp.h().f(); if (currentItemId == 4) collectionAdapter.setData(blocks); }
    private void loadMoreBlocks() { moreBlocks = Pp.h().f(); if (currentItemId == 5) collectionAdapter.setData(moreBlocks); }

    private void unselectToBeDeletedItems() {
        if (collectionAdapter != null && collectionAdapter.currentCollectionTypeItems != null) {
            for (SelectableBean bean : collectionAdapter.currentCollectionTypeItems) bean.isSelected = false;
        }
    }

    private class CollectionAdapter extends SoundPlayingAdapter<SoundPlayingAdapter.ViewHolder> {
        private int lastSelectedItemPosition = -1;
        private int currentViewType = -1;
        private final ArrayList<SelectableBean> originalItems = new ArrayList<>();
        private final ArrayList<SelectableBean> currentCollectionTypeItems = new ArrayList<>();

        public CollectionAdapter(RecyclerView target) {
            super(ManageCollectionActivity.this);
            target.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (currentViewType > 2) return;
                    if (dy > 2 && fab.isEnabled()) fab.hide();
                    else if (dy < -2 && fab.isEnabled()) fab.show();
                }
            });
        }

        @Override public int getItemCount() { return currentCollectionTypeItems.size(); }
        public SelectableBean getItem(int pos) { return currentCollectionTypeItems.get(pos); }

        public void toggleSelectAll() {
            boolean allSelected = true;
            for (SelectableBean bean : currentCollectionTypeItems) if (!bean.isSelected) { allSelected = false; break; }
            for (SelectableBean bean : currentCollectionTypeItems) bean.isSelected = !allSelected;
            notifyDataSetChanged();
        }

        public void filter(String query) {
            query = query.toLowerCase().trim();
            currentCollectionTypeItems.clear();
            if (query.isEmpty()) currentCollectionTypeItems.addAll(originalItems);
            else {
                for (SelectableBean bean : originalItems) {
                    String name = (bean instanceof ProjectResourceBean) ? ((ProjectResourceBean) bean).resName :
                            (bean instanceof WidgetCollectionBean) ? ((WidgetCollectionBean) bean).name :
                            (bean instanceof BlockCollectionBean) ? ((BlockCollectionBean) bean).name :
                            ((MoreBlockCollectionBean) bean).name;
                    if (name.toLowerCase().contains(query)) currentCollectionTypeItems.add(bean);
                }
            }
            notifyDataSetChanged();
            checkEmptyState();
        }

        private void setData(ArrayList<? extends SelectableBean> beans) {
            originalItems.clear(); originalItems.addAll(beans);
            currentCollectionTypeItems.clear(); currentCollectionTypeItems.addAll(beans);
            checkEmptyState(); notifyDataSetChanged();
        }

        private void checkEmptyState() {
            noItemsLayout.setVisibility(currentCollectionTypeItems.isEmpty() ? View.VISIBLE : View.GONE);
            collection.setVisibility(currentCollectionTypeItems.isEmpty() ? View.GONE : View.VISIBLE);
        }

        @Override public int getItemViewType(int position) { return currentViewType; }

        @NonNull @Override public SoundPlayingAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater in = LayoutInflater.from(parent.getContext());
            return switch (viewType) {
                case 0 -> new ImageCollectionViewHolder(in.inflate(R.layout.manage_image_list_item, parent, false));
                case 1 -> new SoundCollectionViewHolder(in.inflate(R.layout.manage_sound_list_item, parent, false));
                case 2 -> new FontCollectionViewHolder(in.inflate(R.layout.manage_font_list_item, parent, false));
                case 3 -> new WidgetCollectionViewHolder(in.inflate(R.layout.manage_collection_widget_list_item, parent, false));
                case 4 -> new BlockCollectionViewHolder(in.inflate(R.layout.manage_collection_block_list_item, parent, false));
                default -> new MoreBlockCollectionViewHolder(in.inflate(R.layout.manage_collection_more_block_list_item, parent, false));
            };
        }

        @Override public void onBindViewHolder(@NonNull SoundPlayingAdapter.ViewHolder h, int p) {
            switch (h.getItemViewType()) {
                case 0 -> bindImage((ImageCollectionViewHolder) h, p);
                case 1 -> bindSound((SoundCollectionViewHolder) h, p);
                case 2 -> bindFont((FontCollectionViewHolder) h, p);
                case 3 -> bindWidget((WidgetCollectionViewHolder) h, p);
                case 4 -> bindBlock((BlockCollectionViewHolder) h, p);
                case 5 -> bindMoreBlock((MoreBlockCollectionViewHolder) h, p);
            }
        }

        private void bindImage(ImageCollectionViewHolder h, int p) {
            ProjectResourceBean b = (ProjectResourceBean) currentCollectionTypeItems.get(p);
            h.deleteContainer.setVisibility(selectingToBeDeletedItems ? View.VISIBLE : View.GONE);
            h.ninePatchIcon.setVisibility(b.isNinePatch() ? View.VISIBLE : View.GONE);
            h.delete.setImageResource(b.isSelected ? R.drawable.ic_checkmark_green_48dp : R.drawable.ic_trashcan_white_48dp);
            Glide.with(getApplicationContext()).asBitmap().load(wq.a() + "/image/data/" + b.resFullName).centerCrop().into(h.image);
            h.name.setText(b.resName); h.checkBox.setChecked(b.isSelected);
        }

        private void bindSound(SoundCollectionViewHolder h, int p) {
            ProjectResourceBean b = (ProjectResourceBean) currentCollectionTypeItems.get(p);
            h.deleteContainer.setVisibility(selectingToBeDeletedItems ? View.VISIBLE : View.GONE);
            h.album.setVisibility(selectingToBeDeletedItems ? View.GONE : View.VISIBLE);
            if (!selectingToBeDeletedItems) {
                h.audioMetadata = AudioMetadata.fromPath(getAudio(p));
                b.totalSoundDuration = h.audioMetadata.getDurationInMs();
                h.audioMetadata.setEmbeddedPictureAsAlbumCover(ManageCollectionActivity.this, h.album);
            }
            h.delete.setImageResource(b.isSelected ? R.drawable.ic_checkmark_green_48dp : R.drawable.ic_trashcan_white_48dp);
            int sPos = b.curSoundPosition / 1000, tDur = b.totalSoundDuration / 1000;
            h.currentPosition.setText(String.format("%d:%02d", sPos / 60, sPos % 60));
            h.totalDuration.setText(String.format("%d:%02d", tDur / 60, tDur % 60));
            h.name.setText(b.resName); h.checkBox.setChecked(b.isSelected);
            boolean playing = p == soundPlayer.getNowPlayingPosition() && soundPlayer.isPlaying();
            h.play.setImageResource(playing ? R.drawable.ic_pause_blue_circle_48dp : R.drawable.circled_play_96_blue);
            h.playbackProgress.setMax(b.totalSoundDuration / 100); h.playbackProgress.setProgress(b.curSoundPosition / 100);
        }

        private void bindFont(FontCollectionViewHolder h, int p) {
            ProjectResourceBean b = (ProjectResourceBean) currentCollectionTypeItems.get(p);
            h.deleteContainer.setVisibility(selectingToBeDeletedItems ? View.VISIBLE : View.GONE);
            h.delete.setImageResource(b.isSelected ? R.drawable.ic_checkmark_green_48dp : R.drawable.ic_trashcan_white_48dp);
            h.name.setText(b.resName + ".ttf"); h.checkBox.setChecked(b.isSelected);
            try { h.preview.setTypeface(Typeface.createFromFile(wq.a() + "/font/data/" + b.resFullName)); } catch (Exception ignored) {}
        }

        private void bindWidget(WidgetCollectionViewHolder h, int p) {
            WidgetCollectionBean b = (WidgetCollectionBean) currentCollectionTypeItems.get(p);
            h.deleteContainer.setVisibility(selectingToBeDeletedItems ? View.VISIBLE : View.GONE);
            h.widgetIcon.setVisibility(selectingToBeDeletedItems ? View.GONE : View.VISIBLE);
            h.delete.setImageResource(b.isSelected ? R.drawable.ic_checkmark_green_48dp : R.drawable.ic_trashcan_white_48dp);
            h.widgetIcon.setImageResource(ViewBean.getViewTypeResId(b.widgets.get(0).type));
            h.name.setText(b.name); h.checkBox.setChecked(b.isSelected);
        }

        private void bindBlock(BlockCollectionViewHolder h, int p) {
            BlockCollectionBean b = (BlockCollectionBean) currentCollectionTypeItems.get(p);
            h.deleteContainer.setVisibility(selectingToBeDeletedItems ? View.VISIBLE : View.GONE);
            h.blockIcon.setVisibility(selectingToBeDeletedItems ? View.GONE : View.VISIBLE);
            h.delete.setImageResource(b.isSelected ? R.drawable.ic_checkmark_green_48dp : R.drawable.ic_trashcan_white_48dp);
            h.blockIcon.setImageResource(getBlockIcon(b.blocks.get(0)));
            h.name.setText(b.name); h.checkBox.setChecked(b.isSelected);
        }

        private void bindMoreBlock(MoreBlockCollectionViewHolder h, int p) {
            MoreBlockCollectionBean b = (MoreBlockCollectionBean) currentCollectionTypeItems.get(p);
            h.deleteContainer.setVisibility(selectingToBeDeletedItems ? View.VISIBLE : View.GONE);
            h.delete.setImageResource(b.isSelected ? R.drawable.ic_checkmark_green_48dp : R.drawable.ic_trashcan_white_48dp);
            h.name.setText(b.name); h.checkBox.setChecked(b.isSelected);
            h.blockArea.removeAllViews(); BlockUtil.loadMoreblockPreview(h.blockArea, b.spec);
        }

        @Override public ProjectResourceBean getData(int p) { return (ProjectResourceBean) currentCollectionTypeItems.get(p); }
        @Override public Path getAudio(int p) { return Paths.get(wq.a(), "sound", "data", getData(p).resFullName); }

        private class BlockCollectionViewHolder extends SoundlessViewHolder {
            public final View cardView; public final CheckBox checkBox;
            public final ImageView blockIcon, delete; public final TextView name;
            public final LinearLayout deleteContainer;
            public BlockCollectionViewHolder(View v) {
                super(v); cardView = v.findViewById(R.id.layout_item); checkBox = v.findViewById(R.id.chk_select);
                blockIcon = v.findViewById(R.id.img_block); delete = v.findViewById(R.id.img_delete);
                name = v.findViewById(R.id.tv_block_name); deleteContainer = v.findViewById(R.id.delete_img_container);
                checkBox.setVisibility(View.GONE);
                cardView.setOnClickListener(view -> { if (selectingToBeDeletedItems) { bSelected(getLayoutPosition()); } else openBlockDetails(getLayoutPosition()); });
                cardView.setOnLongClickListener(view -> { changeDeletingItemsState(true); bSelected(getLayoutPosition()); return true; });
            }
            private void bSelected(int p) { currentCollectionTypeItems.get(p).isSelected = !currentCollectionTypeItems.get(p).isSelected; notifyItemChanged(p); }
        }

        private class FontCollectionViewHolder extends SoundlessViewHolder {
            public final View cardView; public final CheckBox checkBox;
            public final ImageView delete; public final TextView name, preview;
            public final LinearLayout deleteContainer;
            public FontCollectionViewHolder(View v) {
                super(v); cardView = v.findViewById(R.id.layout_item); checkBox = v.findViewById(R.id.chk_select);
                delete = v.findViewById(R.id.img_delete); name = v.findViewById(R.id.tv_font_name);
                deleteContainer = v.findViewById(R.id.delete_img_container); preview = v.findViewById(R.id.tv_font_preview);
                checkBox.setVisibility(View.GONE);
                cardView.setOnClickListener(view -> { if (selectingToBeDeletedItems) { bSelected(getLayoutPosition()); } else openFontDetails(getLayoutPosition()); });
                cardView.setOnLongClickListener(view -> { changeDeletingItemsState(true); bSelected(getLayoutPosition()); return true; });
            }
            private void bSelected(int p) { currentCollectionTypeItems.get(p).isSelected = !currentCollectionTypeItems.get(p).isSelected; notifyItemChanged(p); }
        }

        private class ImageCollectionViewHolder extends SoundlessViewHolder {
            public final View root; public final CheckBox checkBox; public final TextView name;
            public final ImageView image, delete, ninePatchIcon; public final LinearLayout deleteContainer;
            public ImageCollectionViewHolder(View v) {
                super(v); root = v; checkBox = v.findViewById(R.id.chk_select); name = v.findViewById(R.id.tv_image_name);
                image = v.findViewById(R.id.img); delete = v.findViewById(R.id.img_delete);
                deleteContainer = v.findViewById(R.id.delete_img_container); ninePatchIcon = v.findViewById(R.id.img_nine_patch);
                checkBox.setVisibility(View.GONE);
                image.setOnClickListener(view -> { if (selectingToBeDeletedItems) { bSelected(getLayoutPosition()); } else openImageDetails(getLayoutPosition()); });
                image.setOnLongClickListener(view -> { changeDeletingItemsState(true); bSelected(getLayoutPosition()); return true; });
            }
            private void bSelected(int p) { currentCollectionTypeItems.get(p).isSelected = !currentCollectionTypeItems.get(p).isSelected; notifyItemChanged(p); }
        }

        private class MoreBlockCollectionViewHolder extends SoundlessViewHolder {
            public final View cardView; public final CheckBox checkBox;
            public final ImageView delete; public final LinearLayout deleteContainer;
            public final TextView name; public final RelativeLayout blockArea;
            public MoreBlockCollectionViewHolder(View v) {
                super(v); cardView = v.findViewById(R.id.layout_item); checkBox = v.findViewById(R.id.chk_select);
                delete = v.findViewById(R.id.img_delete); deleteContainer = v.findViewById(R.id.delete_img_container);
                name = v.findViewById(R.id.tv_block_name); blockArea = v.findViewById(R.id.block_area);
                checkBox.setVisibility(View.GONE);
                cardView.setOnClickListener(view -> { if (selectingToBeDeletedItems) { bSelected(getLayoutPosition()); } else openMoreBlockDetails(getLayoutPosition()); });
                cardView.setOnLongClickListener(view -> { changeDeletingItemsState(true); bSelected(getLayoutPosition()); return true; });
            }
            private void bSelected(int p) { currentCollectionTypeItems.get(p).isSelected = !currentCollectionTypeItems.get(p).isSelected; notifyItemChanged(p); }
        }

        private class SoundCollectionViewHolder extends SoundPlayingAdapter.ViewHolder {
            public final ProgressBar playbackProgress; public final TextView totalDuration, currentPosition, name;
            public final LinearLayout deleteContainer; public final View cardView;
            public final CheckBox checkBox; public final ImageView album, delete, play;
            public AudioMetadata audioMetadata;
            public SoundCollectionViewHolder(View v) {
                super(v); cardView = v.findViewById(R.id.layout_item); checkBox = v.findViewById(R.id.chk_select);
                album = v.findViewById(R.id.img_album); name = v.findViewById(R.id.tv_sound_name);
                play = v.findViewById(R.id.img_play); delete = v.findViewById(R.id.img_delete);
                currentPosition = v.findViewById(R.id.tv_currenttime); playbackProgress = v.findViewById(R.id.prog_playtime);
                totalDuration = v.findViewById(R.id.tv_endtime); deleteContainer = v.findViewById(R.id.delete_img_container);
                checkBox.setVisibility(View.GONE);
                play.setOnClickListener(view -> soundPlayer.onPlayPressed(getLayoutPosition()));
                cardView.setOnClickListener(view -> { if (selectingToBeDeletedItems) { bSelected(getLayoutPosition()); } else openSoundDetails(getLayoutPosition()); });
                cardView.setOnLongClickListener(view -> { changeDeletingItemsState(true); bSelected(getLayoutPosition()); return true; });
            }
            private void bSelected(int p) { currentCollectionTypeItems.get(p).isSelected = !currentCollectionTypeItems.get(p).isSelected; notifyItemChanged(p); }
            @Override protected TextView getCurrentPosition() { return currentPosition; }
            @Override protected ProgressBar getPlaybackProgress() { return playbackProgress; }
        }

        private class WidgetCollectionViewHolder extends SoundlessViewHolder {
            public final View cardView; public final CheckBox checkBox;
            public final ImageView widgetIcon, delete; public final TextView name;
            public final LinearLayout deleteContainer;
            public WidgetCollectionViewHolder(View v) {
                super(v); cardView = v.findViewById(R.id.layout_item); checkBox = v.findViewById(R.id.chk_select);
                widgetIcon = v.findViewById(R.id.img_widget); delete = v.findViewById(R.id.img_delete);
                name = v.findViewById(R.id.tv_widget_name); deleteContainer = v.findViewById(R.id.delete_img_container);
                checkBox.setVisibility(View.GONE);
                cardView.setOnClickListener(view -> { if (selectingToBeDeletedItems) { bSelected(getLayoutPosition()); } else openWidgetDetails(getLayoutPosition()); });
                cardView.setOnLongClickListener(view -> { changeDeletingItemsState(true); bSelected(getLayoutPosition()); return true; });
            }
            private void bSelected(int p) { currentCollectionTypeItems.get(p).isSelected = !currentCollectionTypeItems.get(p).isSelected; notifyItemChanged(p); }
        }
    }
}
