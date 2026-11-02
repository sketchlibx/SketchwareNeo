package com.besome.sketch.editor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PathEffect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.BlockCollectionBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.HistoryBlockBean;
import com.besome.sketch.beans.MoreBlockCollectionBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.design.DesignActivity;
import com.besome.sketch.editor.component.AddComponentBottomSheet;
import com.besome.sketch.editor.logic.BlockPane;
import com.besome.sketch.editor.logic.LogicTopMenu;
import com.besome.sketch.editor.logic.PaletteBlock;
import com.besome.sketch.editor.logic.PaletteSelector;
import com.besome.sketch.editor.makeblock.MakeBlockActivity;
import com.besome.sketch.editor.makeblock.NewMoreBlockCreatorActivity;
import com.besome.sketch.editor.manage.ShowBlockCollectionActivity;
import com.besome.sketch.editor.view.ViewDummy;
import com.besome.sketch.editor.view.ViewLogicEditor;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.ColorPickerDialog;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import dev.aldi.sayuti.block.ExtraPaletteBlock;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import mod.bobur.VectorDrawableLoader;
import mod.hey.studios.editor.view.IdGenerator;
import mod.hey.studios.moreblock.MoreBlockDefaultsStore;
import mod.hey.studios.moreblock.ReturnMoreblockManager;
import mod.hey.studios.moreblock.importer.MoreblockImporterDialog;
import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.activities.tools.ConfigActivity;
import mod.hilal.saif.asd.AsdDialog;
import mod.jbk.editor.manage.MoreblockImporter;
import mod.jbk.util.BlockUtil;
import mod.jbk.util.LogUtil;
import mod.pranav.viewbinding.ViewBindingBuilder;
import mod.sketchlibx.project.editor.BlocksConverter;

import neo.sketchware.ai.AiResponseCallback;
import neo.sketchware.ai.LogicGenTask;
import neo.sketchware.plugin.NeoBlockConverter;
import neo.sketchware.plugin.NeoEvent;
import neo.sketchware.plugin.PluginManager;

import pro.sketchware.R;
import pro.sketchware.activities.editor.view.CodeViewerActivity;
import pro.sketchware.activities.resourceseditor.ResourcesEditorActivity;
import pro.sketchware.databinding.ImagePickerItemBinding;
import pro.sketchware.databinding.SearchWithRecyclerViewBinding;
import pro.sketchware.menu.ExtraMenuBean;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.SvgUtils;
import pro.sketchware.utility.ThemeUtils;

import a.a.a.DB;
import a.a.a.FB;
import a.a.a.Fx;
import a.a.a.GB;
import a.a.a.Gx;
import a.a.a.MA;
import a.a.a.Mp;
import a.a.a.NB;
import a.a.a.Ox;
import a.a.a.Pp;
import a.a.a.Rs;
import a.a.a.Ss;
import a.a.a.Ts;
import a.a.a.Us;
import a.a.a.Vs;
import a.a.a.ZB;
import a.a.a.bC;
import a.a.a.eC;
import a.a.a.jC;
import a.a.a.jq;
import a.a.a.kC;
import a.a.a.mB;
import a.a.a.sq;
import a.a.a.uq;
import a.a.a.wB;
import a.a.a.xB;
import a.a.a.yq;

@SuppressLint({"ClickableViewAccessibility", "RtlHardcoded", "SetTextI18n", "DefaultLocale"})
public class LogicEditorActivity extends BaseAppCompatActivity implements View.OnClickListener, Vs, View.OnTouchListener, MoreblockImporterDialog.CallBack {

    private final Handler handler = new Handler();
    private final int[] v = new int[2];
    private final FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
    public ProjectFileBean M;
    public PaletteBlock m;
    public BlockPane o;
    public String scId = "";
    public String id = "";
    public String eventName = "";
    private Vibrator vibrator;
    private LinearLayout J, K;
    private FloatingActionButton openBlocksMenuButton;
    private LogicTopMenu logicTopMenu;
    private LogicEditorDrawer O;
    private ObjectAnimator U, V, ba, ca, fa, ga;
    private ExtraPaletteBlock extraPaletteBlock;
    private ViewLogicEditor viewLogicEditor;
    private ViewDummy dummy;
    private PaletteSelector paletteSelector;
    private final ActivityResultLauncher<Intent> openResourcesEditor = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            paletteSelector.performClickPalette(-1);
        }
    });
    private Rs w;
    private float posInitY, posInitX, s, t;
    private int minDist, S, x, y;
    private int T = -30;
    private View currentTouchedView;
    private Ss touchedBlockInput;
    private boolean touchStartedOnInput;
    private boolean G, isDragged, W, X, da, ea, ha, ia;
    private ArrayList<BlockBean> savedBlockBean = new ArrayList<>();
    private final Runnable longPressed = this::r;
    private Boolean isViewBindingEnabled;

    private SvgUtils svgUtils;
    private TextView eventToolbarTitle;
    private TextView eventToolbarSubtitle;
    private TextView editorStatusText;
    private TextView editorStatusChangesText;
    private TextView editorStatusProblemText;
    private View editorStatusCard;
    private View editorStatusProblemChip;
    private FrameLayout editorNotesOverlay;
    private boolean editorBlocksReady;
    private int loadedEventBlockCount;
    private ArrayList<BlockBean> loadedEventBlockBeans = new ArrayList<>();
    private final HashMap<String, NoteBadgeView> blockNoteBadges = new HashMap<>();
    private final WeakHashMap<View, Drawable> inputHighlightOriginalBackgrounds = new WeakHashMap<>();
    private final WeakHashMap<View, Boolean> inputHighlightHadOriginalBackgrounds = new WeakHashMap<>();
    private final WeakHashMap<View, ObjectAnimator> inputHighlightAnimations = new WeakHashMap<>();
    private TextView zoomPercentText;
    private float editorZoom = 1.0f;
    private boolean editorChanged;
    private Rs selectedBlock;
    private PopupWindow blockActionPopup;
    private PopupWindow blockMorePopup;
    private final FilePathUtil fpu = new FilePathUtil();

    public static ArrayList<String> getAllJavaFileNames(String projectScId) {
        ArrayList<String> javaFileNames = new ArrayList<>();
        for (ProjectFileBean projectFile : jC.b(projectScId).b()) {
            javaFileNames.add(projectFile.getJavaName());
        }
        return javaFileNames;
    }

    public static ArrayList<String> getAllXmlFileNames(String projectScId) {
        ArrayList<String> xmlFileNames = new ArrayList<>();
        for (ProjectFileBean projectFile : jC.b(projectScId).b()) {
            String xmlName = projectFile.getXmlName();
            if (xmlName != null && !xmlName.isEmpty()) {
                xmlFileNames.add(xmlName);
            }
        }
        return xmlFileNames;
    }

    private void attachEventBlocks(ArrayList<BlockBean> eventBlocks) {
        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
            h();
            return;
        }
        if (eventBlocks == null) {
            editorBlocksReady = true;
            loadedEventBlockCount = 0;
            h();
            updateEditorStatus();
            return;
        }
        loadedEventBlockCount = 0;
        loadedEventBlockBeans = new ArrayList<>(eventBlocks.size());
        for (BlockBean bean : eventBlocks) {
            if (bean != null) loadedEventBlockBeans.add(bean.clone());
        }
        if (eventBlocks.isEmpty()) {
            e(X);
            o.getRoot().k();
            o.b();
            editorBlocksReady = true;
            h();
            updateEditorStatus();
            updateBlockNotePositions();
            return;
        }

        HashMap<Integer, Rs> blockIdsAndBlocks = new HashMap<>(eventBlocks.size());
        handler.post(new Runnable() {
            private int index;
            private boolean rootAttached;

            @Override
            public void run() {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                    h();
                    return;
                }
                int end = Math.min(index + 8, eventBlocks.size());
                while (index < end) {
                    BlockBean bean = eventBlocks.get(index++);
                    if (eventName.equals("onTextChanged") && "getArg".equals(bean.opCode)
                            && "text".equals(bean.spec)) {
                        bean.spec = "charSeq";
                    }
                    Rs block = b(bean);
                    int blockId = (Integer) block.getTag();
                    blockIdsAndBlocks.put(blockId, block);
                    o.g = Math.max(o.g, blockId + 1);
                    o.a(block, 0, 0);
                    block.setOnTouchListener(LogicEditorActivity.this);
                    if (!rootAttached) {
                        o.getRoot().b(block);
                        rootAttached = true;
                    }
                }

                if (index < eventBlocks.size()) {
                    viewLogicEditor.postOnAnimation(this);
                    return;
                }
                // Count only actual logic blocks. The editor's generated event/argument
                // anchors are not user blocks and must not show as "1 block".
                loadedEventBlockCount = 0;
                for (Rs loadedBlock : blockIdsAndBlocks.values()) {
                    if (loadedBlock != null && loadedBlock.getBlockType() == 0) {
                        loadedEventBlockCount++;
                    }
                }
                attachEventBlockConnections(eventBlocks, blockIdsAndBlocks);
                updateEditorStatus();
            }
        });
    }

    private void attachEventBlockConnections(ArrayList<BlockBean> eventBlocks,
                                             HashMap<Integer, Rs> blockIdsAndBlocks) {
        handler.post(new Runnable() {
            private int index;

            @Override
            public void run() {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                    h();
                    return;
                }
                int end = Math.min(index + 8, eventBlocks.size());
                while (index < end) {
                    BlockBean bean = eventBlocks.get(index++);
                    final int blockId;
                    try {
                        blockId = Integer.parseInt(bean.id);
                    } catch (RuntimeException invalidId) {
                        Log.w("LogicEditorActivity", "Skipping block with invalid ID: " + bean.id);
                        continue;
                    }
                    Rs block = blockIdsAndBlocks.get(blockId);
                    if (block == null) {
                        Log.w("LogicEditorActivity", "Skipping connections for missing block ID: " + bean.id);
                        continue;
                    }

                    if (bean.subStack1 >= 0) {
                        Rs child = blockIdsAndBlocks.get(bean.subStack1);
                        if (child != null) block.e(child);
                    }
                    if (bean.subStack2 >= 0) {
                        Rs child = blockIdsAndBlocks.get(bean.subStack2);
                        if (child != null) block.f(child);
                    }
                    if (bean.nextBlock >= 0) {
                        Rs next = blockIdsAndBlocks.get(bean.nextBlock);
                        if (next != null) block.b(next);
                    }

                    if (bean.parameters == null || block.V == null) continue;
                    boolean hasLiteralParameter = false;
                    for (int parameterIndex = 0; parameterIndex < bean.parameters.size(); parameterIndex++) {
                        String parameter = bean.parameters.get(parameterIndex);
                        if (parameter == null || parameter.isEmpty() || parameterIndex >= block.V.size()) continue;
                        if (parameter.charAt(0) == '@') {
                            try {
                                Rs parameterBlock = blockIdsAndBlocks.get(Integer.parseInt(parameter.substring(1)));
                                if (parameterBlock != null) block.a((Ts) block.V.get(parameterIndex), parameterBlock);
                            } catch (NumberFormatException ignored) {
                            }
                        } else {
                            ((Ss) block.V.get(parameterIndex)).setArgValue(parameter);
                            hasLiteralParameter = true;
                        }
                    }
                    if (hasLiteralParameter) block.m();
                }

                if (index < eventBlocks.size()) {
                    viewLogicEditor.postOnAnimation(this);
                    return;
                }
                o.getRoot().k();
                o.b();
                editorBlocksReady = true;
                h();
                updateEditorStatus();
                updateBlockNotePositions();
            }
        });
    }

    private void redo() {
        if (!isDragged) {
            HistoryBlockBean historyBlockBean = bC.d(scId).i(s());
            if (historyBlockBean != null) {
                int actionType = historyBlockBean.getActionType();
                if (actionType == HistoryBlockBean.ACTION_TYPE_ADD) {
                    int[] locationOnScreen = new int[2];
                    o.getLocationOnScreen(locationOnScreen);
                    a(historyBlockBean.getAddedData(), historyBlockBean.getCurrentX() + locationOnScreen[0], historyBlockBean.getCurrentY() + locationOnScreen[1], true);
                    if (historyBlockBean.getCurrentParentData() != null) {
                        a(historyBlockBean.getCurrentParentData(), true);
                    }
                } else if (actionType == HistoryBlockBean.ACTION_TYPE_UPDATE) {
                    a(historyBlockBean.getCurrentUpdateData(), true);
                } else if (actionType == HistoryBlockBean.ACTION_TYPE_REMOVE) {
                    ArrayList<BlockBean> removedData = historyBlockBean.getRemovedData();

                    for (int i = removedData.size() - 1; i >= 0; i--) {
                        o.a(removedData.get(i), false);
                    }
                    if (historyBlockBean.getCurrentParentData() != null) {
                        a(historyBlockBean.getCurrentParentData(), true);
                    }
                } else if (actionType == HistoryBlockBean.ACTION_TYPE_MOVE) {
                    for (BlockBean afterMoveData : historyBlockBean.getAfterMoveData()) {
                        o.a(afterMoveData, true);
                    }

                    int[] locationOnScreen = new int[2];
                    o.getLocationOnScreen(locationOnScreen);
                    a(historyBlockBean.getAfterMoveData(), historyBlockBean.getCurrentX() + locationOnScreen[0], historyBlockBean.getCurrentY() + locationOnScreen[1], true);
                    if (historyBlockBean.getCurrentParentData() != null) {
                        a(historyBlockBean.getCurrentParentData(), true);
                    }

                    if (historyBlockBean.getCurrentOriginalParent() != null) {
                        a(historyBlockBean.getCurrentOriginalParent(), true);
                    }
                }
            }
            editorChanged = true;
            invalidateOptionsMenu();
            updateEditorStatus();
            updateBlockNotePositions();
        }
    }

    public void C() {
        editorChanged = true;
        invalidateOptionsMenu();
        updateEditorStatus();
        updateBlockNotePositions();
    }

    public void E() {
        eC a2 = jC.a(scId);
        String javaName = M.getJavaName();
        ArrayList<BlockBean> blocks = o.getBlocks();
        a2.a(javaName, id + "_" + eventName, blocks);
        loadedEventBlockBeans = cloneBlockBeans(collectCurrentBlockBeans());
        loadedEventBlockCount = countBlockBeans(loadedEventBlockBeans);
        editorChanged = false;
        updateEditorStatus();
    }

    public void G() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(R.string.logic_editor_title_add_new_list);
        View a2 = wB.a(this, R.layout.logic_popup_add_list);
        RadioGroup radioGroup = a2.findViewById(R.id.rg_type);
        TextInputEditText editText = a2.findViewById(R.id.ed_input);
        ZB zb = new ZB(getContext(), a2.findViewById(R.id.ti_input), uq.b, uq.a(), jC.a(scId).a(M));
        dialog.setView(a2);
        dialog.setPositiveButton(R.string.common_word_add, (v, which) -> {
            if (zb.b()) {
                int i = 1;
                int checkedRadioButtonId = radioGroup.getCheckedRadioButtonId();
                if (checkedRadioButtonId != R.id.rb_int) {
                    if (checkedRadioButtonId == R.id.rb_string) {
                        i = 2;
                    } else if (checkedRadioButtonId == R.id.rb_map) {
                        i = 3;
                    }
                    // NOTE: List<Float> and List<Int> are explicitly out of scope.
                    // logic_popup_add_list.xml has no rb_float/rb_int_num radio buttons, so
                    // there is intentionally no branch for them here. Do not re-add an
                    // "i = 4" (Float) or "i = 7" (Int) branch for lists without first
                    // implementing that list type end-to-end (layout, ExtraMenuBean
                    // list-type constant, codegen, BlocksConverter, etc).
                }

                a(i, Helper.getText(editText));
                v.dismiss();
            }
        });
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    private void showAddNewVariableDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(R.string.logic_editor_title_add_new_variable);

        View customView = wB.a(this, R.layout.logic_popup_add_variable);
        RadioGroup radioGroup = customView.findViewById(R.id.rg_type);
        TextInputEditText editText = customView.findViewById(R.id.ed_input);
        ZB nameValidator = new ZB(getContext(), customView.findViewById(R.id.ti_input), uq.b, uq.a(), jC.a(scId).a(M));
        dialog.setView(customView);
        dialog.setPositiveButton(R.string.common_word_add, (v, which) -> {
            int variableType = 1;
            if (radioGroup.getCheckedRadioButtonId() == R.id.rb_boolean) {
                variableType = 0;
            } else if (radioGroup.getCheckedRadioButtonId() != R.id.rb_int) {
                if (radioGroup.getCheckedRadioButtonId() == R.id.rb_string) {
                    variableType = 2;
                } else if (radioGroup.getCheckedRadioButtonId() == R.id.rb_map) {
                    variableType = 3;
                } else if (radioGroup.getCheckedRadioButtonId() == R.id.rb_float) {
                    variableType = 4;
                } else if (radioGroup.getCheckedRadioButtonId() == R.id.rb_int_num) {
                    variableType = 7;
                }
            }

            if (nameValidator.b()) {
                b(variableType, Helper.getText(editText));
                v.dismiss();
            }
        });
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    public void openResourcesEditor() {
        Intent intent = new Intent();
        intent.setClass(getApplicationContext(), ResourcesEditorActivity.class);
        intent.putExtra("sc_id", scId);
        openResourcesEditor.launch(intent);
    }

    public void I() {
        ArrayList<MoreBlockCollectionBean> moreBlocks = Pp.h().f();
        new MoreblockImporterDialog(this, moreBlocks, this).show();
    }

    public void J() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(R.string.logic_editor_title_remove_list);
        View a2 = wB.a(this, R.layout.property_popup_selector_single);
        ViewGroup viewGroup = a2.findViewById(R.id.rg_content);
        for (Pair<Integer, String> list : jC.a(scId).j(M.getJavaName())) {
            viewGroup.addView(e(list.second));
        }
        dialog.setView(a2);
        dialog.setPositiveButton(R.string.common_word_remove, (v, which) -> {
            int childCount = viewGroup.getChildCount();
            int i = 0;
            while (i < childCount) {
                RadioButton radioButton = (RadioButton) viewGroup.getChildAt(i);
                if (radioButton.isChecked()) {
                    if (!o.b(Helper.getText(radioButton))) {
                        if (!jC.a(scId).b(M.getJavaName(), Helper.getText(radioButton), id + "_" + eventName)) {
                            l(Helper.getText(radioButton));
                        }
                    }
                    Toast.makeText(getContext(), R.string.logic_editor_message_currently_used_list, Toast.LENGTH_SHORT).show();
                    return;
                }
                i++;
            }
            v.dismiss();
        });
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    public void K() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(R.string.logic_editor_title_remove_variable);
        View a2 = wB.a(this, R.layout.property_popup_selector_single);
        ViewGroup viewGroup = a2.findViewById(R.id.rg_content);
        ArrayList<Pair<Integer, String>> variables = new ArrayList<>(jC.a(scId).k(M.getJavaName()));
        int[] numericVariableTypes = {
                ExtraMenuBean.VARIABLE_TYPE_FLOAT,
                ExtraMenuBean.VARIABLE_TYPE_INT_NUM
        };
        for (int variableType : numericVariableTypes) {
            for (String variableName : jC.a(scId).e(M.getJavaName(), variableType)) {
                boolean alreadyListed = false;
                for (Pair<Integer, String> variable : variables) {
                    if (variable != null && variable.first != null
                            && variable.first == variableType
                            && variableName.equals(variable.second)) {
                        alreadyListed = true;
                        break;
                    }
                }
                if (!alreadyListed) {
                    variables.add(new Pair<>(variableType, variableName));
                }
            }
        }
        for (Pair<Integer, String> next : variables) {
            RadioButton e = e(next.second);
            e.setTag(next.first);
            viewGroup.addView(e);
        }
        dialog.setView(a2);
        dialog.setPositiveButton(R.string.common_word_remove, (v, which) -> {
            int childCount = viewGroup.getChildCount();
            int i = 0;
            while (i < childCount) {
                RadioButton radioButton = (RadioButton) viewGroup.getChildAt(i);
                if (radioButton.isChecked()) {
                    if (!o.c(Helper.getText(radioButton))) {
                        if (!jC.a(scId).c(M.getJavaName(), Helper.getText(radioButton), id + "_" + eventName)) {
                            m(Helper.getText(radioButton));
                        }
                    }
                    Toast.makeText(getContext(), R.string.logic_editor_message_currently_used_variable, Toast.LENGTH_SHORT).show();
                    return;
                }
                i++;
            }
            v.dismiss();
        });
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    public void L() {
        try {
            new Handler().postDelayed(() -> new ProjectSaver(this).execute(), 500L);
        } catch (Exception e) {
            crashlytics.recordException(e);
        }
    }

    private void undo() {
        if (!isDragged) {
            HistoryBlockBean history = bC.d(scId).j(s());
            if (history != null) {
                int actionType = history.getActionType();
                if (actionType == HistoryBlockBean.ACTION_TYPE_ADD) {
                    ArrayList<BlockBean> addedData = history.getAddedData();
                    for (int i = addedData.size() - 1; i >= 0; i--) {
                        o.a(addedData.get(i), false);
                    }

                    if (history.getPrevParentData() != null) {
                        history.getPrevParentData().print();
                        a(history.getPrevParentData(), true);
                    }
                } else if (actionType == HistoryBlockBean.ACTION_TYPE_UPDATE) {
                    a(history.getPrevUpdateData(), true);
                } else if (actionType == HistoryBlockBean.ACTION_TYPE_REMOVE) {
                    int[] oLocationOnScreen = new int[2];
                    o.getLocationOnScreen(oLocationOnScreen);
                    a(history.getRemovedData(), history.getCurrentX() + oLocationOnScreen[0], history.getCurrentY() + oLocationOnScreen[1], true);

                    if (history.getPrevParentData() != null) {
                        a(history.getPrevParentData(), true);
                    }
                } else if (actionType == HistoryBlockBean.ACTION_TYPE_MOVE) {
                    for (BlockBean beforeMoveBlock : history.getBeforeMoveData()) {
                        o.a(beforeMoveBlock, true);
                    }

                    int[] oLocationOnScreen = new int[2];
                    o.getLocationOnScreen(oLocationOnScreen);
                    a(history.getBeforeMoveData(), history.getPrevX() + oLocationOnScreen[0], history.getPrevY() + oLocationOnScreen[1], true);

                    if (history.getPrevParentData() != null) {
                        a(history.getPrevParentData(), true);
                    }
                    if (history.getPrevOriginalParent() != null) {
                        a(history.getPrevOriginalParent(), true);
                    }
                }
            }

            editorChanged = true;
            invalidateOptionsMenu();
            updateEditorStatus();
            updateBlockNotePositions();
        }
    }

    public Rs a(Rs rs, int i, int i2, boolean z) {
        int addTargetId = o.getAddTargetId();
        Rs a2 = o.a(rs, i, i2, z);
        if (!z) {
            a2.setOnTouchListener(this);
            PluginManager.publishEvent(new NeoEvent.BlockAdded(scId, a2.getBean().id));
        } else {
            PluginManager.publishEvent(new NeoEvent.BlockMoved(scId, a2.getBean().id));
        }
        if (addTargetId >= 0) {
            PluginManager.publishEvent(new NeoEvent.BlockConnected(scId, a2.getBean().id, String.valueOf(addTargetId)));
        }
        return a2;
    }

    public void addDeprecatedBlock(String message, String type, String opCode) {
        m.addDeprecatedBlock(message, type, opCode);
    }

    public View a(String str, String str2) {
        Ts a2 = m.a("", str, str2);
        a2.setTag(str2);
        a2.setClickable(true);
        a2.setOnTouchListener(this);
        return a2;
    }

    public final View a(String str, String str2, String str3) {
        Ts a2 = m.a(str, str2, str3);
        a2.setTag(str3);
        a2.setClickable(true);
        a2.setOnTouchListener(this);
        return a2;
    }

    public final View a(String str, String str2, String str3, String str4) {
        Ts a2 = m.a(str, str2, str3, str4);
        a2.setTag(str4);
        a2.setClickable(true);
        a2.setOnTouchListener(this);
        return a2;
    }

    private ImageView setImageViewContent(String name) {
        float dp = wB.a(this, 1.0f);
        int size = (int) (dp * 48);
        ImageView imageView = new ImageView(this);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackgroundResource(R.drawable.bg_outline);

        if ("NONE".equals(name)) {
            return imageView;
        }

        if ("default_image".equals(name)) {
            int resId = getResources().getIdentifier(name, "drawable", getPackageName());
            if (resId != 0) imageView.setImageResource(resId);
            return imageView;
        }

        File imageFile = new File(jC.d(scId).f(name));
        if (imageFile.exists()) {
            String path = imageFile.getAbsolutePath();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", imageFile);

            if (path.endsWith(".xml")) {
                svgUtils.loadImage(imageView, fpu.getSvgFullPath(scId, name));
            } else {
                Glide.with(this)
                        .load(uri)
                        .signature(kC.n())
                        .error(R.drawable.ic_remove_grey600_24dp)
                        .into(imageView);
            }
        } else {
            try {
                new VectorDrawableLoader().setImageVectorFromFile(imageView, new VectorDrawableLoader().getVectorFullPath(DesignActivity.sc_id, name));
            } catch (Exception e) {
                imageView.setImageResource(R.drawable.ic_remove_grey600_24dp);
            }
        }

        return imageView;
    }

    public final ArrayList<BlockBean> a(ArrayList<BlockBean> arrayList, int i, int i2, boolean z) {
        HashMap<Integer, Integer> hashMap = new HashMap<>();
        ArrayList<BlockBean> arrayList2 = new ArrayList<>();
        for (BlockBean next : arrayList) {
            if (next.id != null && !next.id.isEmpty()) {
                arrayList2.add(next.clone());
            }
        }
        for (BlockBean next2 : arrayList2) {
            if (Integer.parseInt(next2.id) >= 99000000) {
                hashMap.put(Integer.valueOf(next2.id), o.g);
                o.g = o.g + 1;
            } else {
                hashMap.put(Integer.valueOf(next2.id), Integer.valueOf(next2.id));
            }
        }
        int size = arrayList2.size();
        while (true) {
            size--;
            if (size < 0) {
                break;
            }
            BlockBean blockBean = arrayList2.get(size);
            if (!a(blockBean)) {
                arrayList2.remove(size);
                hashMap.remove(Integer.valueOf(blockBean.id));
            }
        }
        for (BlockBean block : arrayList2) {
            if (hashMap.containsKey(Integer.valueOf(block.id))) {
                block.id = String.valueOf(hashMap.get(Integer.valueOf(block.id)));
            } else {
                block.id = "";
            }
            for (int j = 0; j < block.parameters.size(); j++) {
                String parameter = block.parameters.get(j);
                if (parameter != null && !parameter.isEmpty() && parameter.charAt(0) == '@') {
                    int parameterId = Integer.parseInt(parameter.substring(1));
                    int parameterAsBlockId = hashMap.containsKey(parameterId) ? hashMap.get(parameterId) : 0;
                    if (parameterAsBlockId >= 0) {
                        block.parameters.set(j, '@' + String.valueOf(parameterAsBlockId));
                    } else {
                        block.parameters.set(j, "");
                    }
                }
            }
            if (block.subStack1 >= 0 && hashMap.containsKey(block.subStack1)) {
                block.subStack1 = hashMap.get(block.subStack1);
            }
            if (block.subStack2 >= 0 && hashMap.containsKey(block.subStack2)) {
                block.subStack2 = hashMap.get(block.subStack2);
            }
            if (block.nextBlock >= 0 && hashMap.containsKey(block.nextBlock)) {
                block.nextBlock = hashMap.get(block.nextBlock);
            }
        }
        Rs firstBlock = null;
        for (int j = 0; j < arrayList2.size(); j++) {
            BlockBean blockBean = arrayList2.get(j);
            if (blockBean.id != null && !blockBean.id.isEmpty()) {
                Rs block = b(blockBean);
                if (j == 0) {
                    firstBlock = block;
                }
                o.a(block, i, i2);
                block.setOnTouchListener(this);
            }
        }
        for (BlockBean block : arrayList2) {
            if (block.id != null && !block.id.isEmpty()) {
                a(block, false);
            }
        }
        if (firstBlock != null && z) {
            firstBlock.p().k();
            o.b();
        }
        return arrayList2;
    }

    @Override
    public void a(int i, int i2) {
        extraPaletteBlock.setBlock(i, i2);
    }

    public void a(int i, String str) {
        jC.a(scId).b(M.getJavaName(), i, str);
        a(1, 0xffcc5b22);
    }

    public void a(Rs rs) {
        w = null;
        y = -1;
        x = 0;
        int[] iArr = new int[2];
        Rs rs2 = rs.E;
        if (rs2 != null) {
            w = rs2;
            if (savedBlockBean.isEmpty()) {
                savedBlockBean = o.getBlocks();
            }
        }
        Rs rs3 = w;
        if (rs3 == null) {
            return;
        }
        if (rs3.ha == (Integer) rs.getTag()) {
            x = 0;
        } else if (w.ia == (Integer) rs.getTag()) {
            x = 2;
        } else if (w.ja == (Integer) rs.getTag()) {
            x = 3;
        } else if (w.V.contains(rs)) {
            x = 5;
            y = w.V.indexOf(rs);
        }
    }

    public void a(Rs rs, float f, float f2) {
        for (View next : rs.V) {
            if ((next instanceof Ss) && next.getX() < f && next.getX() + next.getWidth() > f && next.getY() < f2 && next.getY() + next.getHeight() > f2) {
                new ExtraMenuBean(this).defineMenuSelector((Ss) next);
                return;
            }
        }
    }

    public void a(Ss ss, Object obj) {
        BlockBean clone = ss.E.getBean().clone();
        ss.setArgValue(obj);
        ss.E.m();
        ss.E.p().k();
        ss.E.pa.b();
        bC.d(scId).a(s(), clone, ss.E.getBean().clone());
        C();
    }

    public void pickImage(Ss ss, String str) {
        boolean selectingBackgroundImage = "property_background_resource".equals(str);
        boolean selectingImage = !selectingBackgroundImage && "property_image".equals(str);
        AtomicReference<String> selectedImage = new AtomicReference<>("");

        SearchWithRecyclerViewBinding binding = SearchWithRecyclerViewBinding.inflate(getLayoutInflater());

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        if (selectingImage) {
            dialog.setTitle(R.string.logic_editor_title_select_image);
        } else if (selectingBackgroundImage) {
            dialog.setTitle(R.string.logic_editor_title_select_image_background);
        }

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));

        ArrayList<String> images = jC.d(scId).m();
        images.addAll(new VectorDrawableLoader().getVectorDrawables(DesignActivity.sc_id));
        if (selectingImage) {
            images.add(0, "default_image");
        } else if (selectingBackgroundImage) {
            images.add(0, "NONE");
        }

        ImagePickerAdapter adapter = new ImagePickerAdapter(images, (String) ss.getArgValue(), selectedImage::set);
        binding.recyclerView.setAdapter(adapter);

        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().toLowerCase();
                adapter.filter(query);
            }
        });

        dialog.setPositiveButton(R.string.common_word_save, (v, which) -> {
            String selectedImg = selectedImage.get();
            if (!selectedImg.isEmpty()) {
                a(ss, selectedImage.get());
            }
        });

        dialog.setView(binding.getRoot());
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    public void a(Ss ss, boolean z) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(z ? R.string.logic_editor_title_enter_number_value : R.string.logic_editor_title_enter_string_value);
        View a2 = wB.a(this, R.layout.property_popup_input_text);
        EditText editText = a2.findViewById(R.id.ed_input);
        if (z) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            editText.setMaxLines(1);
        } else {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            editText.setImeOptions(EditorInfo.IME_ACTION_NONE);
        }
        editText.setText(ss.getArgValue().toString());
        dialog.setView(a2);
        dialog.setPositiveButton(R.string.common_word_save, (v, which) -> {
            String text = Helper.getText(editText);
            emptyStringSetter:
            {
                if (z) {
                    try {
                        double d = Double.parseDouble(text);
                        if (!Double.isNaN(d) && !Double.isInfinite(d)) {
                            break emptyStringSetter;
                        }
                    } catch (NumberFormatException e) {
                        LogUtil.e("LogicEditor", "", e);
                    }
                } else if (!text.isEmpty()) {
                    if (text.charAt(0) == '@') {
                        text = " " + text;
                        break emptyStringSetter;
                    }
                }

                text = "";
            }

            a(ss, text);
            v.dismiss();
        });
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    public void a(BlockBean blockBean, boolean z) {
        Rs block = o.a(blockBean.id);
        if (block != null) {
            block.ia = -1;
            block.ja = -1;
            block.ha = -1;

            for (int i = 0; i < blockBean.parameters.size(); i++) {
                String parameter = blockBean.parameters.get(i);
                if (parameter != null) {
                    if (!parameter.isEmpty() && parameter.charAt(0) == '@') {
                        int blockId = Integer.parseInt(parameter.substring(1));
                        if (blockId > 0) {
                            Rs parameterBlock = o.a(blockId);
                            if (parameterBlock != null) {
                                block.a((Ts) block.V.get(i), parameterBlock);
                            }
                        }
                    } else {
                        if (block.V.get(i) instanceof Ss ss) {
                            String javaName = M.getJavaName();
                            String xmlName = M.getXmlName();
                            
                            if (eventName.equals("onBindCustomView")) {
                                var eC = jC.a(scId);
                                var view = eC.c(xmlName, id);
                                if (view == null) {
                                    view = eC.c("_drawer_" + xmlName, id);
                                }
                                if (view != null) {
                                    String customView = view.customView;
                                    if (customView != null && !customView.equals("NONE") && !customView.equals("none") && !customView.isEmpty()) {
                                        xmlName = ProjectFileBean.getXmlName(customView);
                                    }
                                }
                            }

                            if (!parameter.isEmpty()) {
                                if (ss.b.equals("m")) {
                                    eC eC = jC.a(scId);

                                    switch (ss.c) {
                                        case "varInt":
                                            eC.f(javaName, ExtraMenuBean.VARIABLE_TYPE_NUMBER, parameter);
                                            break;

                                        case "varBool":
                                            eC.f(javaName, ExtraMenuBean.VARIABLE_TYPE_BOOLEAN, parameter);
                                            break;

                                        case "varStr":
                                            eC.f(javaName, ExtraMenuBean.VARIABLE_TYPE_STRING, parameter);
                                            break;

                                        case "varFloat":
                                            eC.f(javaName, ExtraMenuBean.VARIABLE_TYPE_FLOAT, parameter);
                                            break;

                                        case "varIntNum":
                                            eC.f(javaName, ExtraMenuBean.VARIABLE_TYPE_INT_NUM, parameter);
                                            break;

                                        case "listInt":
                                            eC.e(javaName, ExtraMenuBean.LIST_TYPE_NUMBER, parameter);
                                            break;

                                        case "listStr":
                                            eC.e(javaName, ExtraMenuBean.LIST_TYPE_STRING, parameter);
                                            break;

                                        case "listMap":
                                            eC.e(javaName, ExtraMenuBean.LIST_TYPE_MAP, parameter);
                                            break;

                                        case "list":
                                            boolean b = eC.e(javaName, ExtraMenuBean.LIST_TYPE_NUMBER, parameter);
                                            if (!b) {
                                                b = eC.e(javaName, ExtraMenuBean.LIST_TYPE_STRING, parameter);
                                            }

                                            if (!b) {
                                                eC.e(javaName, ExtraMenuBean.LIST_TYPE_MAP, parameter);
                                            }
                                            break;

                                        case "view":
                                            eC.h(xmlName, parameter);
                                            break;

                                        case "textview":
                                            eC.g(xmlName, parameter);
                                            break;

                                        case "checkbox":
                                            eC.e(xmlName, parameter);
                                            break;

                                        case "imageview":
                                            eC.g(xmlName, ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW, parameter);
                                            break;

                                        case "seekbar":
                                            eC.g(xmlName, ViewBean.VIEW_TYPE_WIDGET_SEEKBAR, parameter);
                                            break;

                                        case "calendarview":
                                            eC.g(xmlName, ViewBean.VIEW_TYPE_WIDGET_CALENDARVIEW, parameter);
                                            break;

                                        case "adview":
                                            eC.g(xmlName, ViewBean.VIEW_TYPE_WIDGET_ADVIEW, parameter);
                                            break;

                                        case "listview":
                                            eC.g(xmlName, ViewBean.VIEW_TYPE_WIDGET_LISTVIEW, parameter);
                                            break;

                                        case "spinner":
                                            eC.g(xmlName, ViewBean.VIEW_TYPE_WIDGET_SPINNER, parameter);
                                            break;

                                        case "webview":
                                            eC.g(xmlName, ViewBean.VIEW_TYPE_WIDGET_WEBVIEW, parameter);
                                            break;

                                        case "switch":
                                            eC.g(xmlName, ViewBean.VIEW_TYPE_WIDGET_SWITCH, parameter);
                                            break;

                                        case "progressbar":
                                            eC.g(xmlName, ViewBean.VIEW_TYPE_WIDGET_PROGRESSBAR, parameter);
                                            break;

                                        case "mapview":
                                            eC.g(xmlName, ViewBean.VIEW_TYPE_WIDGET_MAPVIEW, parameter);
                                            break;

                                        case "intent":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_INTENT, parameter);
                                            break;

                                        case "file":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_SHAREDPREF, parameter);
                                            break;

                                        case "calendar":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_CALENDAR, parameter);
                                            break;

                                        case "timer":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_TIMERTASK, parameter);
                                            break;

                                        case "vibrator":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_VIBRATOR, parameter);
                                            break;

                                        case "dialog":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_DIALOG, parameter);
                                            break;

                                        case "mediaplayer":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_MEDIAPLAYER, parameter);
                                            break;

                                        case "soundpool":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_SOUNDPOOL, parameter);
                                            break;

                                        case "objectanimator":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_OBJECTANIMATOR, parameter);
                                            break;

                                        case "firebase":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_FIREBASE, parameter);
                                            break;

                                        case "firebaseauth":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_FIREBASE_AUTH, parameter);
                                            break;

                                        case "firebasestorage":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_FIREBASE_STORAGE, parameter);
                                            break;

                                        case "gyroscope":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_GYROSCOPE, parameter);
                                            break;

                                        case "interstitialad":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_INTERSTITIAL_AD, parameter);
                                            break;

                                        case "unityads":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_UNITY_ADS, parameter);
                                            break;

                                        case "requestnetwork":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_REQUEST_NETWORK, parameter);
                                            break;

                                        case "texttospeech":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_TEXT_TO_SPEECH, parameter);
                                            break;

                                        case "speechtotext":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_SPEECH_TO_TEXT, parameter);
                                            break;

                                        case "bluetoothconnect":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_BLUETOOTH_CONNECT, parameter);
                                            break;

                                        case "locationmanager":
                                            eC.d(javaName, ComponentBean.COMPONENT_TYPE_LOCATION_MANAGER, parameter);
                                            break;

                                        case "resource_bg":
                                        case "resource":
                                            for (String str : jC.d(scId).m()) {
                                                if (parameter.equals(str)) {
                                                }
                                            }
                                            break;

                                        case "activity":
                                            for (String str : jC.b(scId).d()) {
                                                if (parameter.equals(str.substring(str.indexOf(".java")))) {
                                                }
                                            }
                                            break;

                                        case "sound":
                                            for (String str : jC.d(scId).p()) {
                                                if (parameter.equals(str)) {
                                                }
                                            }
                                            break;

                                        case "videoad":
                                            eC.d(xmlName, ComponentBean.COMPONENT_TYPE_REWARDED_VIDEO_AD, parameter);
                                            break;

                                        case "progressdialog":
                                            eC.d(xmlName, ComponentBean.COMPONENT_TYPE_PROGRESS_DIALOG, parameter);
                                            break;

                                        case "datepickerdialog":
                                            eC.d(xmlName, ComponentBean.COMPONENT_TYPE_DATE_PICKER_DIALOG, parameter);
                                            break;

                                        case "timepickerdialog":
                                            eC.d(xmlName, ComponentBean.COMPONENT_TYPE_TIME_PICKER_DIALOG, parameter);
                                            break;

                                        case "notification":
                                            eC.d(xmlName, ComponentBean.COMPONENT_TYPE_NOTIFICATION, parameter);
                                            break;

                                        case "radiobutton":
                                            eC.g(xmlName, 19, parameter);
                                            break;

                                        case "ratingbar":
                                            eC.g(xmlName, 20, parameter);
                                            break;

                                        case "videoview":
                                            eC.g(xmlName, 21, parameter);
                                            break;

                                        case "searchview":
                                            eC.g(xmlName, 22, parameter);
                                            break;

                                        case "actv":
                                            eC.g(xmlName, 23, parameter);
                                            break;

                                        case "mactv":
                                            eC.g(xmlName, 24, parameter);
                                            break;

                                        case "gridview":
                                            eC.g(xmlName, 25, parameter);
                                            break;

                                        case "tablayout":
                                            eC.g(xmlName, 30, parameter);
                                            break;

                                        case "viewpager":
                                            eC.g(xmlName, 31, parameter);
                                            break;

                                        case "bottomnavigation":
                                            eC.g(xmlName, 32, parameter);
                                            break;

                                        case "badgeview":
                                            eC.g(xmlName, 33, parameter);
                                            break;

                                        case "patternview":
                                            eC.g(xmlName, 34, parameter);
                                            break;

                                        case "sidebar":
                                            eC.g(xmlName, 35, parameter);
                                            break;

                                        default:
                                            extraPaletteBlock.e(ss.c, parameter);
                                    }
                                }
                            }

                            ss.setArgValue(parameter);
                            block.m();
                        }
                    }
                }
            }

            int subStack1RootBlockId = blockBean.subStack1;
            if (subStack1RootBlockId >= 0) {
                Rs subStack1RootBlock = o.a(subStack1RootBlockId);
                if (subStack1RootBlock != null) {
                    block.e(subStack1RootBlock);
                }
            }

            int subStack2RootBlockId = blockBean.subStack2;
            if (subStack2RootBlockId >= 0) {
                Rs subStack2RootBlock = o.a(subStack2RootBlockId);
                if (subStack2RootBlock != null) {
                    block.f(subStack2RootBlock);
                }
            }

            int nextBlockId = blockBean.nextBlock;
            if (nextBlockId >= 0) {
                Rs nextBlock = o.a(nextBlockId);
                if (nextBlock != null) {
                    block.b(nextBlock);
                }
            }

            block.m();
            if (z) {
                block.p().k();
                o.b();
            }
        }
    }

    public void a(String str, int i) {
        m.a(str, i);
    }

    public void a(String str, Rs rs) {
        ArrayList<String> arrayList;
        ArrayList<Rs> allChildren = rs.getAllChildren();
        ArrayList<BlockBean> arrayList2 = new ArrayList<>();
        for (Rs child : allChildren) {
            BlockBean blockBean = new BlockBean();
            BlockBean bean = child.getBean();
            blockBean.copy(bean);
            blockBean.id = String.format("99%06d", Integer.valueOf(bean.id));
            int i = bean.subStack1;
            if (i > 0) {
                blockBean.subStack1 = i + 99000000;
            }
            int i2 = bean.subStack2;
            if (i2 > 0) {
                blockBean.subStack2 = i2 + 99000000;
            }
            int i3 = bean.nextBlock;
            if (i3 > 0) {
                blockBean.nextBlock = i3 + 99000000;
            }
            blockBean.parameters.clear();
            for (String next : bean.parameters) {
                if (next.length() <= 1 || next.charAt(0) != '@') {
                    arrayList = blockBean.parameters;
                } else {
                    String format = String.format("99%06d", Integer.valueOf(next.substring(1)));
                    arrayList = blockBean.parameters;
                    next = '@' + format;
                }
                arrayList.add(next);
            }
            arrayList2.add(blockBean);
        }
        try {
            Mp.h().a(str, arrayList2, true);
            O.a(str, arrayList2).setOnTouchListener(this);
        } catch (Exception e) {
            crashlytics.recordException(e);
        }
    }

    public void a(boolean z) {
        logicTopMenu.setCopyActive(z);
    }

    public final boolean a(float f, float f2) {
        return logicTopMenu.isInsideCopyArea(f, f2);
    }

    public final boolean a(BlockBean blockBean) {
        if (blockBean.opCode.equals("getArg")) {
            return true;
        }
        if (blockBean.opCode.equals("definedFunc")) {
            Iterator<Pair<String, String>> it = jC.a(scId).i(M.getJavaName()).iterator();
            boolean z = false;
            while (it.hasNext()) {
                if (blockBean.spec.equals(ReturnMoreblockManager.getMbName(it.next().second))) {
                    z = true;
                }
            }
            return z;
        }
        return true;
    }

    public Rs b(BlockBean blockBean) {
        return new Rs(this, Integer.parseInt(blockBean.id), blockBean.spec, blockBean.type, blockBean.typeName, blockBean.opCode);
    }

    private RadioButton getFontRadioButton(String fontName) {
        RadioButton radioButton = new RadioButton(this);
        radioButton.setText("");
        radioButton.setTag(fontName);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (wB.a(this, 1.0f) * 60.0f));
        radioButton.setGravity(Gravity.CENTER | Gravity.LEFT);
        radioButton.setLayoutParams(layoutParams);
        return radioButton;
    }

    public void b(int i, String str) {
        jC.a(scId).c(M.getJavaName(), i, str);
        a(0, 0xffee7d16);
    }

    public void b(Rs rs) {
        PluginManager.publishEvent(new NeoEvent.BlockRemoved(scId, rs.getBean().id));
        o.b(rs);
    }

    public void b(Ss ss) {
        ColorPickerDialog colorPickerDialog = new ColorPickerDialog(this, (ss.getArgValue() == null || ss.getArgValue().toString().isEmpty()) ? "Color.TRANSPARENT" : ss.getArgValue().toString().replace("0xFF", "#"), true, false, scId);
        colorPickerDialog.a(new ColorPickerDialog.b() {
            @Override
            public void a(int var1) {
                if (var1 == 0) {
                    LogicEditorActivity.this.a(ss, "Color.TRANSPARENT");
                } else {
                    LogicEditorActivity.this.a(ss, String.format("0x%08X", var1 & (Color.WHITE)));
                }
            }

            @Override
            public void a(String var1, int var2) {
                LogicEditorActivity.this.a(ss, "R.color." + var1);
            }
        });
        colorPickerDialog.materialColorAttr((attr, attrColor) -> a(ss, "R.attr." + attr));
        colorPickerDialog.showAtLocation(ss, Gravity.CENTER, 0, 0);
    }

    public void b(String str, String tag) {
        TextView textView = m.a(str);
        textView.setTag(tag);
        textView.setSoundEffectsEnabled(true);
        textView.setOnClickListener(this);
    }

    public void b(String str, String tag, View.OnClickListener onClickListener) {
        TextView textView = m.a(str);
        textView.setTag(tag);
        textView.setSoundEffectsEnabled(true);
        textView.setOnClickListener(onClickListener);
    }

    public void activeIconDelete(boolean showDeleteIcon) {
        logicTopMenu.setDeleteActive(showDeleteIcon);
    }

    public final boolean hitTestIconDelete(float f, float f2) {
        return logicTopMenu.isInsideDeleteArea(f, f2);
    }

    public void c(Rs rs) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(R.string.logic_block_favorites_save_title);
        View a2 = wB.a(this, R.layout.property_popup_save_to_favorite);
        ((TextView) a2.findViewById(R.id.tv_favorites_guide)).setText(R.string.logic_block_favorites_save_guide);
        EditText editText = a2.findViewById(R.id.ed_input);
        editText.setPrivateImeOptions("defaultInputmode=english;");
        editText.setLines(1);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        NB nb = new NB(this, a2.findViewById(R.id.ti_input), Mp.h().g());
        dialog.setView(a2);
        dialog.setPositiveButton(R.string.common_word_save, (v, which) -> {
            if (nb.b()) {
                a(Helper.getText(editText), rs);
                v.dismiss();
            }
        });
        
        dialog.setNeutralButton("Export", (v, which) -> {
        exportBlockPatternForAI();
        v.dismiss();
        });

        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    public void c(Ss ss) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(R.string.logic_editor_title_enter_string_value);
        View a2 = wB.a(this, R.layout.property_popup_input_text);
        ((TextInputLayout) a2.findViewById(R.id.ti_input)).setHint(getString(R.string.property_hint_enter_value));
        EditText editText = a2.findViewById(R.id.ed_input);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setText(ss.getArgValue().toString());
        dialog.setView(a2);
        dialog.setPositiveButton(R.string.common_word_save, (v, which) -> {
            a(ss, Helper.getText(editText));
            v.dismiss();
        });
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    public void c(String str, String str2) {
        jC.a(scId).a(M.getJavaName(), str, str2);
        a(8, 0xff8a55d7);
    }

    public void c(boolean z) {
        logicTopMenu.setDetailActive(z);
    }

    public final boolean c(float f, float f2) {
        return logicTopMenu.isInsideDetailArea(f, f2);
    }

    private LinearLayout getFontPreview(String fontName) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (wB.a(this, 1.0f) * 60.0f)));
        linearLayout.setGravity(Gravity.CENTER | Gravity.LEFT);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = new TextView(this);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.weight = 1.0f;
        name.setLayoutParams(layoutParams);
        name.setText(fontName);
        linearLayout.addView(name);
        TextView preview = new TextView(this);
        preview.setLayoutParams(layoutParams);
        preview.setText("Preview");

        Typeface typeface;
        if (fontName.equalsIgnoreCase("default_font")) {
            typeface = Typeface.DEFAULT;
        } else {
            try {
                typeface = Typeface.createFromFile(jC.d(scId).d(fontName));
            } catch (RuntimeException e) {
                crashlytics.log("Loading font preview");
                crashlytics.recordException(e);
                typeface = Typeface.DEFAULT;
                preview.setText("Couldn't load font");
            }
        }

        preview.setTypeface(typeface);
        linearLayout.addView(preview);
        return linearLayout;
    }

    public RadioButton d(String type, String id) {
        if (isViewBindingEnabled) {
            id = ViewBindingBuilder.generateParameterFromId(id);
        }
        RadioButton radioButton = new RadioButton(this);
        radioButton.setText(type + " : " + id);
        radioButton.setTag(id);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (wB.a(this, 1.0f) * 40.0f));
        radioButton.setGravity(Gravity.CENTER | Gravity.LEFT);
        radioButton.setLayoutParams(layoutParams);
        return radioButton;
    }

    public void d(Ss ss) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(R.string.logic_editor_title_select_font);

        View customView = wB.a(this, R.layout.property_popup_selector_color);
        RadioGroup radioGroup = customView.findViewById(R.id.rg);
        LinearLayout linearLayout = customView.findViewById(R.id.content);
        ArrayList<String> fontNames = jC.d(scId).k();
        fontNames.add(0, "default_font");
        for (String fontName : fontNames) {
            RadioButton font = getFontRadioButton(fontName);
            radioGroup.addView(font);
            if (fontName.equals(ss.getArgValue())) {
                font.setChecked(true);
            }
            LinearLayout fontPreview = getFontPreview(fontName);
            fontPreview.setOnClickListener(v -> font.setChecked(true));
            linearLayout.addView(fontPreview);
        }

        dialog.setView(customView);
        dialog.setPositiveButton(R.string.common_word_select, (v, which) -> {
            for (int i = 0; i < radioGroup.getChildCount(); i++) {
                RadioButton radioButton = (RadioButton) radioGroup.getChildAt(i);
                if (radioButton.isChecked()) {
                    a(ss, radioButton.getTag());
                    break;
                }
            }
            v.dismiss();
        });
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    public void d(boolean z) {
        logicTopMenu.setFavoriteActive(z);
    }

    public final boolean d(float f, float f2) {
        return logicTopMenu.isInsideFavoriteArea(f, f2);
    }

    public final RadioButton e(String str) {
        RadioButton radioButton = new RadioButton(this);
        radioButton.setText(str);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.topMargin = (int) wB.a(getContext(), 4.0f);
        layoutParams.bottomMargin = (int) wB.a(getContext(), 4.0f);
        radioButton.setGravity(Gravity.CENTER | Gravity.LEFT);
        radioButton.setLayoutParams(layoutParams);
        return radioButton;
    }

    public final CheckBox createCheckBox(String str) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(str);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.topMargin = (int) wB.a(getContext(), 4.0f);
        layoutParams.bottomMargin = (int) wB.a(getContext(), 4.0f);
        checkBox.setGravity(Gravity.CENTER | Gravity.LEFT);
        checkBox.setLayoutParams(layoutParams);
        return checkBox;
    }

    public void e(Ss ss) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(R.string.logic_editor_title_enter_data_value);
        View a2 = wB.a(this, R.layout.property_popup_input_intent_data);
        ((TextView) a2.findViewById(R.id.tv_desc_intent_usage)).setText(getString(R.string.property_description_component_intent_usage));
        EditText editText = a2.findViewById(R.id.ed_input);
        ((TextInputLayout) a2.findViewById(R.id.ti_input)).setHint(getString(R.string.property_hint_enter_value));
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setText(ss.getArgValue().toString());
        dialog.setView(a2);
        dialog.setPositiveButton(R.string.common_word_save, (v, which) -> {
            a(ss, Helper.getText(editText));
            v.dismiss();
        });
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    public void e(boolean z) {
        ObjectAnimator objectAnimator;
        if (!W) {
            h(getResources().getConfiguration().orientation);
        }
        if (X == z) {
            return;
        }
        X = z;
        n();
        if (z) {
            g(false);
            objectAnimator = U;
        } else {
            objectAnimator = V;
        }
        objectAnimator.start();
        f(getResources().getConfiguration().orientation);
    }

    public void f(int i) {
        // ViewLogicEditor is a direct child of R.id.editor_surface (FrameLayout).
        // Giving it LinearLayout.LayoutParams crashes FrameLayout.onMeasure() when
        // the event editor opens or the palette orientation changes.
        FrameLayout.LayoutParams layoutParams;
        int a2;
        int i2 = ViewGroup.LayoutParams.MATCH_PARENT;
        if (X) {
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;
            if (width <= height) {
                width = height;
            }
            if (2 == i) {
                i2 = width - ((int) wB.a(this, 320.0f));
                a2 = ViewGroup.LayoutParams.MATCH_PARENT;
            } else {
                a2 = viewLogicEditor.getHeight() - K.getHeight();
            }
            layoutParams = new FrameLayout.LayoutParams(i2, a2);
        } else {
            layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        viewLogicEditor.setLayoutParams(layoutParams);
        viewLogicEditor.requestLayout();
    }

    public void f(Ss ss) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        View customView = wB.a(this, R.layout.property_popup_selector_single);
        ViewGroup viewGroup = customView.findViewById(R.id.rg_content);
        String xmlName = M.getXmlName();

        if (eventName.equals("onBindCustomView")) {
            var eC = jC.a(scId);
            var view = eC.c(xmlName, id);
            if (view == null) {
                view = eC.c("_drawer_" + xmlName, id);
            }
            if (view != null) {
                String customViewName = view.customView;
                if (customViewName != null && !customViewName.equals("NONE") && !customViewName.equals("none") && !customViewName.isEmpty()) {
                    xmlName = ProjectFileBean.getXmlName(customViewName);
                }
            }
        }

        dialog.setTitle(R.string.logic_editor_title_select_view);
        ArrayList<ViewBean> views = jC.a(scId).d(xmlName);
        for (ViewBean viewBean : views) {
            String convert = viewBean.convert;
            String typeName = convert.isEmpty() ? ViewBean.getViewTypeName(viewBean.type) : IdGenerator.getLastPath(convert);
            if (!convert.equals("include")) {
                Set<String> toNotAdd = new Ox(new jq(), M).readAttributesToReplace(viewBean);
                if (!toNotAdd.contains("android:id")) {
                    String classInfo = ss.getClassInfo().getClassName();
                    if ((classInfo.equals("CheckBox") && viewBean.getClassInfo().a("CompoundButton")) || viewBean.getClassInfo().a(classInfo)) {
                        viewGroup.addView(d(typeName, viewBean.id));
                    }
                }
                ExtraMenuBean.setupSearchView(customView, viewGroup);
            }
        }

        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            RadioButton radioButton = (RadioButton) viewGroup.getChildAt(i);
            String argValue = ss.getArgValue().toString();
            if (argValue.equals(radioButton.getTag().toString())) {
                radioButton.setChecked(true);
                break;
            }
        }

        dialog.setView(customView);
        dialog.setNeutralButton("Code Editor", (v, which) -> {
            AsdDialog editor = new AsdDialog(this);
            editor.setContent(ss.getArgValue().toString());
            editor.show();
            editor.setOnSaveClickListener(this, false, ss, editor);
            editor.setOnCancelClickListener(editor);
            v.dismiss();
        });
        dialog.setPositiveButton(R.string.common_word_select, (v, which) -> {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                RadioButton radioButton = (RadioButton) viewGroup.getChildAt(i);
                if (radioButton.isChecked()) {
                    a(ss, radioButton.getTag());
                    break;
                }
            }
            v.dismiss();
        });
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    public void f(boolean z) {
        logicTopMenu.toggleLayoutVisibility(z);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        clearInputHighlightAnimations();
        if (blockMorePopup != null && blockMorePopup.isShowing()) blockMorePopup.dismiss();
        blockMorePopup = null;
        if (blockActionPopup != null && blockActionPopup.isShowing()) blockActionPopup.dismiss();
        blockActionPopup = null;
        super.onDestroy();
    }

    @Override
    public void finish() {
        bC.d(scId).b(s());
        super.finish();
    }

    private Context getContext() {
        return this;
    }

    public void g(int i) {
        RelativeLayout.LayoutParams layoutParams;
        int orientation;
        if (2 == i) {
            K.setLayoutParams(new LinearLayout.LayoutParams((int) wB.a(this, 320.0f), ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER | Gravity.BOTTOM;
            int dimension = (int) getResources().getDimension(R.dimen.action_button_margin);
            params.setMargins(dimension, dimension, dimension, dimension);
            openBlocksMenuButton.setLayoutParams(params);
            layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            layoutParams.topMargin = GB.a(getContext());
            orientation = LinearLayout.HORIZONTAL;
        } else {
            K.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) wB.a(this, 240.0f)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER | Gravity.RIGHT;
            int dimension2 = (int) getResources().getDimension(R.dimen.action_button_margin);
            params.setMargins(dimension2, dimension2, dimension2, dimension2);
            openBlocksMenuButton.setLayoutParams(params);
            layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            orientation = LinearLayout.VERTICAL;
        }
        J.setOrientation(orientation);
        J.setLayoutParams(layoutParams);
        h(i);
        f(i);
    }

    public void g(boolean z) {
        if (!ha) {
            t();
        }
        if (ia != z) {
            ia = z;
            l();
            (z ? fa : ga).start();
        }
    }

    public void h(int i) {
        boolean var2 = X;
        if (i == 2) {
            if (!var2) {
                J.setTranslationX(wB.a(this, 320.0F));
            } else {
                J.setTranslationX(0.0F);
            }
            J.setTranslationY(0.0F);
        } else {
            if (!var2) {
                J.setTranslationX(0.0F);
                J.setTranslationY(wB.a(this, 240.0F));
            } else {
                J.setTranslationX(0.0F);
                J.setTranslationY(0.0F);
            }
        }

        if (i == 2) {
            U = ObjectAnimator.ofFloat(J, View.TRANSLATION_X, 0.0F);
            V = ObjectAnimator.ofFloat(J, View.TRANSLATION_X, wB.a(this, 320.0F));
        } else {
            U = ObjectAnimator.ofFloat(J, View.TRANSLATION_Y, 0.0F);
            V = ObjectAnimator.ofFloat(J, View.TRANSLATION_Y, wB.a(this, 240.0F));
        }

        U.setDuration(500L);
        U.setInterpolator(new DecelerateInterpolator());
        V.setDuration(300L);
        V.setInterpolator(new DecelerateInterpolator());
        W = true;
    }

    public void h(Ss ss) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(R.string.logic_editor_title_select_sound);

        View customView = wB.a(this, R.layout.property_popup_selector_single);
        RadioGroup radioGroup = customView.findViewById(R.id.rg_content);
        SoundPool soundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .build();
        soundPool.setOnLoadCompleteListener((soundPool1, sampleId, status) -> {
            if (soundPool1 != null) {
                soundPool1.play(sampleId, 1, 1, 1, 0, 1);
            }
        });

        for (String soundName : jC.d(scId).p()) {
            RadioButton sound = e(soundName);
            radioGroup.addView(sound);
            if (soundName.equals(ss.getArgValue())) {
                sound.setChecked(true);
            }
            sound.setOnClickListener(v -> soundPool.load(jC.d(scId).i(Helper.getText(sound)), 1));
        }
        dialog.setView(customView);
        dialog.setPositiveButton(R.string.common_word_select, (v, which) -> {
            RadioButton checkedRadioButton = radioGroup.findViewById(radioGroup.getCheckedRadioButtonId());
            a(ss, Helper.getText(checkedRadioButton));
            v.dismiss();
        });
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    public void h(boolean z) {
        logicTopMenu.setDeleteActive(false);
        logicTopMenu.setCopyActive(false);
        logicTopMenu.setFavoriteActive(false);
        logicTopMenu.setDetailActive(false);
        if (!da) {
            x();
        }
        if (ea == z) {
            return;
        }
        ea = z;
        m();
        (z ? ba : ca).start();
    }

    public void i(Ss ss) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(R.string.logic_editor_title_select_typeface);
        View a3 = wB.a(this, R.layout.property_popup_selector_single);
        RadioGroup radioGroup = a3.findViewById(R.id.rg_content);
        for (Pair<Integer, String> pair : sq.a("property_text_style")) {
            RadioButton e = e(pair.second);
            radioGroup.addView(e);
            if (pair.second.equals(ss.getArgValue())) {
                e.setChecked(true);
            }
        }
        dialog.setView(a3);
        dialog.setPositiveButton(R.string.common_word_save, (v, which) -> {
            int childCount = radioGroup.getChildCount();
            for (int i = 0; i < childCount; i++) {
                RadioButton radioButton = (RadioButton) radioGroup.getChildAt(i);
                if (radioButton.isChecked()) {
                    a(ss, Helper.getText(radioButton));
                    break;
                }
            }

            v.dismiss();
        });
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    public void l() {
        if (fa.isRunning()) {
            fa.cancel();
        }
        if (ga.isRunning()) {
            ga.cancel();
        }
    }

    public void l(String str) {
        jC.a(scId).o(M.getJavaName(), str);
        a(1, 0xffcc5b22);
    }

    public void m() {
        if (ba.isRunning()) {
            ba.cancel();
        }
        if (ca.isRunning()) {
            ca.cancel();
        }
    }

    public void m(String str) {
        jC.a(scId).p(M.getJavaName(), str);
        a(0, 0xffee7d16);
    }

    public void n() {
        if (U.isRunning()) {
            U.cancel();
        }
        if (V.isRunning()) {
            V.cancel();
        }
    }

    public void n(String str) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(R.string.logic_block_favorites_delete_title);
        dialog.setMessage(R.string.logic_block_favorites_delete_message);
        dialog.setPositiveButton(R.string.common_word_delete, (v, which) -> {
            Mp.h().a(str, true);
            O.a(str);
            v.dismiss();
        });
        dialog.setNegativeButton(R.string.common_word_cancel, null);
        dialog.show();
    }

    public void o(String str) {
        Intent intent = new Intent(getContext(), ShowBlockCollectionActivity.class);
        intent.putExtra("block_name", str);
        startActivity(intent);
    }

    public boolean o() {
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 222) {
                String blockName = data.getStringExtra("block_name");
                String blockSpec = data.getStringExtra("block_spec");
                c(blockName, blockSpec);
                if (blockName != null && blockSpec != null) {
                    // Default values of the parameters. The legacy creator sends none, which only clears stale entries.
                    MoreBlockDefaultsStore.save(scId, M.getJavaName(), blockName, blockSpec,
                            MoreBlockDefaultsStore.decode(data.getStringExtra("block_defaults")));
                }
            } else if (requestCode == 224) {
                a(7, 0xff2ca5e2);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (ia) {
            g(false);
            return;
        }
        if (X) {
            e(false);
            return;
        }
        k();
        if (!p()) {
            return;
        }
        L();
    }

    @Override
    public void onClick(View v) {
        if (!mB.a()) {
            Object tag = v.getTag();
            if (tag != null) {
                if (tag.equals("variableAdd")) {
                    showAddNewVariableDialog();
                } else if (tag.equals("variableRemove")) {
                    K();
                } else if (tag.equals("openResourcesEditor")) {
                    openResourcesEditor();
                } else if (tag.equals("listAdd")) {
                    G();
                } else if (tag.equals("listRemove")) {
                    J();
                } else if (tag.equals("blockAdd")) {
                    // Feature flag: the legacy creator stays the default; both use the same extras and result contract.
                    Class<?> moreBlockCreator = ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_NEW_MOREBLOCK_CREATOR)
                            ? NewMoreBlockCreatorActivity.class
                            : MakeBlockActivity.class;
                    Intent intent = new Intent(this, moreBlockCreator);
                    intent.putExtra("sc_id", scId);
                    intent.putExtra("project_file", M);
                    intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivityForResult(intent, 222);
                } else if (tag.equals("componentAdd")) {
                    AddComponentBottomSheet addComponentBottomSheet = AddComponentBottomSheet.newInstance(scId, M, () -> a(7, 0xff2ca5e2));
                    addComponentBottomSheet.show(getSupportFragmentManager(), null);
                } else if (tag.equals("blockImport")) {
                    I();
                }
            }
            int id = v.getId();
            if (id == R.id.btn_delete) {
                setResult(Activity.RESULT_OK, new Intent());
                finish();
            } else if (id == R.id.btn_cancel) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration configuration) {
        super.onConfigurationChanged(configuration);
        g(configuration.orientation);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.logic_editor);
        if (!super.isStoragePermissionGranted()) {
            finish();
        }
        Parcelable parcelable;
        if (savedInstanceState == null) {
            scId = getIntent().getStringExtra("sc_id");
            id = getIntent().getStringExtra("id");
            eventName = getIntent().getStringExtra("event");
            parcelable = getIntent().getParcelableExtra("project_file");
        } else {
            scId = savedInstanceState.getString("sc_id");
            id = savedInstanceState.getString("id");
            eventName = savedInstanceState.getString("event");
            parcelable = savedInstanceState.getParcelable("project_file");
        }
        isViewBindingEnabled = new ProjectSettings(scId).getValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, "false").equals("true");
        M = (ProjectFileBean) parcelable;
        T = (int) wB.a(getContext(), (float) T);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(null);
        toolbar.setSubtitle(null);
        setupEventToolbar(toolbar, getIntent().getStringExtra("event_text"));
        toolbar.setNavigationOnClickListener(v -> {
            if (!mB.a()) {
                onBackPressed();
            }
        });
        G = new DB(getContext(), "P12").a("P12I0", true);
        minDist = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        String eventText = getIntent().getStringExtra("event_text");
        paletteSelector = findViewById(R.id.palette_selector);
        paletteSelector.setOnBlockCategorySelectListener(this);
        m = findViewById(R.id.palette_block);
        dummy = findViewById(R.id.dummy);
        viewLogicEditor = findViewById(R.id.editor);
        o = viewLogicEditor.getBlockPane();
        editorStatusText = findViewById(R.id.editor_status_text);
        editorStatusChangesText = findViewById(R.id.editor_status_changes);
        editorStatusProblemText = findViewById(R.id.editor_status_problem_text);
        editorStatusCard = findViewById(R.id.editor_status_card);
        editorStatusProblemChip = findViewById(R.id.editor_status_problem_chip);
        editorNotesOverlay = findViewById(R.id.editor_notes_overlay);
        if (editorStatusProblemChip != null) {
            editorStatusProblemChip.setOnClickListener(v -> {
                if (countEmptyInputs() > 0) highlightAllEmptyInputs();
            });
            editorStatusProblemChip.setContentDescription("Show all block input problems");
        }
        if (viewLogicEditor != null) viewLogicEditor.setOnEditorScrollChangedListener(this::updateBlockNotePositions);
        zoomPercentText = findViewById(R.id.editor_zoom_percent);
        findViewById(R.id.editor_zoom_in).setOnClickListener(v -> setEditorZoom(editorZoom + 0.1f));
        findViewById(R.id.editor_zoom_out).setOnClickListener(v -> setEditorZoom(editorZoom - 0.1f));
        findViewById(R.id.editor_zoom_reset).setOnClickListener(v -> {
            setEditorZoom(1.0f);
            viewLogicEditor.scrollTo(0, 0);
        });
        J = findViewById(R.id.layout_palette);
        K = findViewById(R.id.area_palette);
        openBlocksMenuButton = findViewById(R.id.fab_toggle_palette);
        openBlocksMenuButton.setOnClickListener(v -> e(!X));
        logicTopMenu = findViewById(R.id.top_menu);
        O = findViewById(R.id.right_drawer);
        findViewById(R.id.search_header).setOnClickListener(v -> paletteSelector.showSearchDialog());
        extraPaletteBlock = new ExtraPaletteBlock(this, isViewBindingEnabled);

        svgUtils = new SvgUtils(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logic_menu, menu);
        menu.findItem(R.id.menu_logic_redo).setEnabled(M != null && bC.d(scId).g(s()));
        menu.findItem(R.id.menu_logic_undo).setEnabled(M != null && bC.d(scId).h(s()));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {
        int itemId = menuItem.getItemId();

        if (itemId == R.id.menu_logic_redo) {
            redo();
        } else if (itemId == R.id.menu_logic_undo) {
            undo();
        } else if (itemId == R.id.menu_logic_showsource) {
            View anchor = findViewById(R.id.menu_logic_showsource);
            if (anchor == null) anchor = findViewById(R.id.toolbar);
            PopupMenu popup = new PopupMenu(this, anchor);
            popup.setGravity(Gravity.END);
            popup.getMenu().add(Menu.NONE, 1, Menu.NONE, "Show Source Code");
            popup.getMenu().add(Menu.NONE, 2, Menu.NONE, "Go to event");
            popup.getMenu().add(Menu.NONE, 3, Menu.NONE, "Blocks collection");
            MenuItem pasteItem = popup.getMenu().add(Menu.NONE, 4, Menu.NONE, "Paste blocks");
            pasteItem.setEnabled(hasBlockClipboardData());
            popup.getMenu().add(Menu.NONE, 5, Menu.NONE, "Show empty inputs");
            popup.getMenu().add(Menu.NONE, 6, Menu.NONE, "Add a note");
            popup.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1 -> showSourceCode();
                    case 2 -> showEventPicker();
                    case 3 -> { e(false); g(!ia); }
                    case 4 -> pasteBlocksFromClipboard();
                    case 5 -> showEmptyInputs();
                    case 6 -> editEventNote();
                    default -> { return false; }
                }
                return true;
            });
            popup.show();
        } else {
            return super.onOptionsItemSelected(menuItem);
        }
        return true;
    }

    private void setupEventToolbar(Toolbar toolbar, String subtitle) {
        LinearLayout container = new LinearLayout(this);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setClickable(true);
        container.setFocusable(true);
        container.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);

        eventToolbarTitle = new TextView(this);
        eventToolbarTitle.setText(eventName == null ? "" : eventName);
        eventToolbarTitle.setTextSize(20);
        eventToolbarTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        eventToolbarTitle.setTextColor(ThemeUtils.getColor(this,
                R.attr.colorOnSurface));
        eventToolbarTitle.setSingleLine(true);

        eventToolbarSubtitle = new TextView(this);
        eventToolbarSubtitle.setText(subtitle == null || subtitle.isEmpty() ? id : subtitle);
        eventToolbarSubtitle.setTextSize(14);
        eventToolbarSubtitle.setTextColor(ThemeUtils.getColor(this,
                R.attr.colorOnSurfaceVariant));
        eventToolbarSubtitle.setSingleLine(true);

        labels.addView(eventToolbarTitle, new LinearLayout.LayoutParams(-2, -2));
        labels.addView(eventToolbarSubtitle, new LinearLayout.LayoutParams(-2, -2));
        container.addView(labels, new LinearLayout.LayoutParams(-2, -2));

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_mtrl_chevron_down);
        arrow.setColorFilter(ThemeUtils.getColor(this,
                R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(
                (int) (24 * getResources().getDisplayMetrics().density),
                (int) (24 * getResources().getDisplayMetrics().density));
        arrowParams.leftMargin = (int) (4 * getResources().getDisplayMetrics().density);
        container.addView(arrow, arrowParams);
        container.setOnClickListener(v -> showEventPicker());

        Toolbar.LayoutParams params = new Toolbar.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START | Gravity.CENTER_VERTICAL);
        toolbar.addView(container, params);
    }

    private void showEventPicker() {
        ArrayList<EventBean> allEvents;
        try {
            ArrayList<EventBean> storedEvents = jC.a(scId).g(M.getJavaName());
            allEvents = storedEvents == null ? new ArrayList<>() : new ArrayList<>(storedEvents);
        } catch (RuntimeException error) {
            crashlytics.recordException(error);
            SketchwareUtil.toastError("Could not load events");
            return;
        }
        boolean hasInitializeLogic = false;
        for (EventBean event : allEvents) {
            if (event != null && TextUtils.equals("onCreate", event.targetId)
                    && TextUtils.equals("initializeLogic", event.eventName)) {
                hasInitializeLogic = true;
                break;
            }
        }
        if (!hasInitializeLogic) {
            EventBean initializeLogic = new EventBean(EventBean.EVENT_TYPE_ACTIVITY, -1,
                    "onCreate", "initializeLogic");
            initializeLogic.initValue();
            allEvents.add(0, initializeLogic);
        }
        if (allEvents.isEmpty()) {
            SketchwareUtil.toast("No events are available in this file");
            return;
        }

        int maxSheetHeight = Math.round(getResources().getDisplayMetrics().heightPixels * 0.86f);
        int maxEventListHeight = Math.round(getResources().getDisplayMetrics().heightPixels * 0.56f);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_switch_event, null, false);
        RecyclerView list = sheet.findViewById(R.id.event_list);
        TextInputEditText search = sheet.findViewById(R.id.event_search_input);
        View empty = sheet.findViewById(R.id.event_empty_state);
        Chip currentChip = sheet.findViewById(R.id.current_event_chip);
        String currentEventLabel = TextUtils.equals(id, "onCreate")
                && TextUtils.equals(eventName, "initializeLogic") ? "onCreate"
                : (eventName == null ? "Event" : eventName);
        currentChip.setText("CURRENT  ·  " + currentEventLabel);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setNestedScrollingEnabled(true);
        DefaultItemAnimator listAnimator = new DefaultItemAnimator();
        listAnimator.setAddDuration(140);
        listAnimator.setRemoveDuration(120);
        listAnimator.setMoveDuration(140);
        listAnimator.setChangeDuration(0);
        list.setItemAnimator(listAnimator);

        ArrayList<EventBean> filtered = new ArrayList<>(allEvents);
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerView.ViewHolder(getLayoutInflater().inflate(R.layout.item_switch_event, parent, false)) { };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                EventBean event = filtered.get(position);
                TextView name = holder.itemView.findViewById(R.id.event_name);
                TextView subtitle = holder.itemView.findViewById(R.id.event_subtitle);
                ImageView icon = holder.itemView.findViewById(R.id.event_icon);
                ImageView selected = holder.itemView.findViewById(R.id.event_selected);
                ImageView arrow = holder.itemView.findViewById(R.id.event_arrow);
                MaterialCardView eventCard = holder.itemView.findViewById(R.id.event_card);
                String target = event.targetId == null ? "" : event.targetId;
                boolean isInitializeLogic = TextUtils.equals("onCreate", target)
                        && TextUtils.equals("initializeLogic", event.eventName);
                String eventTitle = isInitializeLogic ? "onCreate"
                        : (event.eventName == null ? "Unnamed event" : event.eventName);
                boolean isCurrent = TextUtils.equals(event.eventName, eventName)
                        && TextUtils.equals(target, id);
                name.setText(eventTitle);
                String targetLabel = event.eventType == EventBean.EVENT_TYPE_ACTIVITY
                        || target.isEmpty() ? M.getActivityName() : target;
                subtitle.setText(targetLabel + "  ·  " + EventBean.getEventTypeName(event.eventType));
                icon.setImageResource(EventBean.getEventIconResource(event.eventType, event.targetType));
                selected.setVisibility(isCurrent ? View.VISIBLE : View.GONE);
                arrow.setVisibility(isCurrent ? View.GONE : View.VISIBLE);
                if (eventCard != null) {
                    eventCard.setCardBackgroundColor(ThemeUtils.getColor(LogicEditorActivity.this,
                            isCurrent ? R.attr.colorSecondaryContainer : R.attr.colorSurfaceContainerLow));
                    eventCard.setStrokeColor(ThemeUtils.getColor(LogicEditorActivity.this,
                            isCurrent ? R.attr.colorPrimary : R.attr.colorOutlineVariant));
                    eventCard.setStrokeWidth(SketchwareUtil.dpToPx(isCurrent ? 2 : 1));
                }
                icon.setColorFilter(ThemeUtils.getColor(LogicEditorActivity.this,
                        isCurrent ? R.attr.colorOnSecondaryContainer : R.attr.colorPrimary));
                holder.itemView.setAlpha(isCurrent ? 1f : 0.96f);
                holder.itemView.setOnClickListener(v -> {
                    v.animate().scaleX(0.985f).scaleY(0.985f).alpha(0.88f).setDuration(70)
                            .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).alpha(1f)
                                    .setDuration(110).withEndAction(() -> {
                                        dialog.dismiss();
                                        if (!isCurrent) switchToEvent(event);
                                    }).start()).start();
                });
            }

            @Override
            public int getItemCount() { return filtered.size(); }
        };
        list.setAdapter(adapter);
        updateEventSheetEmptyState(list, empty, filtered.size(), maxEventListHeight);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                filtered.clear();
                for (EventBean event : allEvents) {
                    String title = event.eventName == null ? "" : event.eventName;
                    String displayTitle = TextUtils.equals("onCreate", event.targetId)
                            && TextUtils.equals("initializeLogic", event.eventName) ? "onCreate" : title;
                    String target = event.targetId == null ? "" : event.targetId;
                    String type = EventBean.getEventTypeName(event.eventType);
                    if (title.toLowerCase(Locale.ROOT).contains(query)
                            || displayTitle.toLowerCase(Locale.ROOT).contains(query)
                            || target.toLowerCase(Locale.ROOT).contains(query)
                            || type.toLowerCase(Locale.ROOT).contains(query)) filtered.add(event);
                }
                adapter.notifyDataSetChanged();
                updateEventSheetEmptyState(list, empty, filtered.size(), maxEventListHeight);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        dialog.setContentView(sheet);
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            BottomSheetBehavior<?> behavior = dialog.getBehavior();
            behavior.setMaxHeight(maxSheetHeight);
            behavior.setFitToContents(true);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        dialog.show();
    }

    private void updateEventSheetEmptyState(RecyclerView list, View empty, int itemCount,
                                            int maxListHeight) {
        if (list == null || empty == null) return;
        boolean isEmpty = itemCount == 0;
        list.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        ViewGroup.LayoutParams listParams = list.getLayoutParams();
        if (listParams != null) {
            int rowHeight = SketchwareUtil.dpToPx(62);
            listParams.height = isEmpty ? 0 : Math.min(maxListHeight, itemCount * rowHeight);
            list.setLayoutParams(listParams);
        }
        ViewGroup.LayoutParams emptyParams = empty.getLayoutParams();
        if (emptyParams != null) {
            emptyParams.height = isEmpty ? SketchwareUtil.dpToPx(116) : 0;
            empty.setLayoutParams(emptyParams);
        }
    }

    private void switchToEvent(EventBean event) {
        if (event == null || (TextUtils.equals(id, event.targetId)
                && TextUtils.equals(eventName, event.eventName))) return;
        E();
        jC.a(scId).k();
        Intent intent = new Intent(this, LogicEditorActivity.class);
        intent.putExtra("sc_id", scId);
        intent.putExtra("id", event.targetId);
        intent.putExtra("event", event.eventName);
        intent.putExtra("project_file", M);
        intent.putExtra("event_text", event.targetId);
        startActivity(intent);
        finish();
    }

    private void copyEventBlocksToClipboard() {
        ArrayList<BlockBean> blocks = o.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            SketchwareUtil.toastError("There are no blocks to copy");
            return;
        }
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(blocks);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Sketchware Neo blocks",
                    "RAW BLOCK BEANS (JSON PATTERN)\n" + json));
            SketchwareUtil.toast("Event blocks copied");
        }
    }

    private boolean hasBlockClipboardData() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip().getItemCount() == 0) return false;
        CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (text == null) return false;
        String value = text.toString();
        return value.trim().startsWith("[") || value.contains("RAW BLOCK BEANS (JSON PATTERN)");
    }

    private void pasteBlocksFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip().getItemCount() == 0) {
            SketchwareUtil.toastError("Clipboard is empty");
            return;
        }
        CharSequence copied = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (copied == null) return;
        String value = copied.toString().trim();
        String marker = "RAW BLOCK BEANS (JSON PATTERN)";
        int markerIndex = value.indexOf(marker);
        if (markerIndex >= 0) value = value.substring(markerIndex + marker.length()).trim();
        int start = value.indexOf('[');
        int end = value.lastIndexOf(']');
        if (start < 0 || end <= start) {
            SketchwareUtil.toastError("Clipboard does not contain block data");
            return;
        }
        try {
            Type type = new TypeToken<ArrayList<BlockBean>>() {}.getType();
            ArrayList<BlockBean> blocks = new Gson().fromJson(value.substring(start, end + 1), type);
            if (blocks == null || blocks.isEmpty()) {
                SketchwareUtil.toastError("No blocks found in clipboard");
                return;
            }
            remapClipboardBlockIds(blocks);
            insertConvertedBlocks(blocks);
        } catch (RuntimeException error) {
            crashlytics.recordException(error);
            SketchwareUtil.toastError("Could not read copied blocks");
        }
    }

    private void remapClipboardBlockIds(ArrayList<BlockBean> blocks) {
        HashMap<Integer, Integer> remappedIds = new HashMap<>();
        int nextClipboardId = 99000000;
        for (BlockBean block : blocks) {
            if (block == null) throw new IllegalArgumentException("Clipboard contains an empty block entry");
            if (block.parameters == null) block.parameters = new ArrayList<>();
            if (block.id == null || block.id.isEmpty()) throw new IllegalArgumentException("Clipboard block is missing its ID");
            try {
                int oldId = Integer.parseInt(block.id);
                remappedIds.put(oldId, nextClipboardId++);
            } catch (NumberFormatException invalidId) {
                throw new IllegalArgumentException("Invalid block ID in clipboard", invalidId);
            }
        }
        for (BlockBean block : blocks) {
            if (block.id != null && !block.id.isEmpty()) {
                Integer mappedId = remappedIds.get(Integer.parseInt(block.id));
                if (mappedId == null) throw new IllegalArgumentException("Missing block ID mapping");
                block.id = String.valueOf(mappedId);
            }
            if (block.nextBlock >= 0) block.nextBlock = remappedIds.containsKey(block.nextBlock) ? remappedIds.get(block.nextBlock) : -1;
            if (block.subStack1 >= 0) block.subStack1 = remappedIds.containsKey(block.subStack1) ? remappedIds.get(block.subStack1) : -1;
            if (block.subStack2 >= 0) block.subStack2 = remappedIds.containsKey(block.subStack2) ? remappedIds.get(block.subStack2) : -1;
            if (block.parameters != null) {
                for (int i = 0; i < block.parameters.size(); i++) {
                    String parameter = block.parameters.get(i);
                    if (parameter != null && parameter.startsWith("@")) {
                        try {
                            int oldId = Integer.parseInt(parameter.substring(1));
                            Integer newId = remappedIds.get(oldId);
                            block.parameters.set(i, newId == null ? "" : "@" + newId);
                        } catch (NumberFormatException ignored) {
                            block.parameters.set(i, "");
                        }
                    }
                }
            }
        }
    }

    private ArrayList<BlockBean> getDiagnosticBlocks() {
        if (!editorChanged && editorBlocksReady) return cloneBlockBeans(loadedEventBlockBeans);
        return collectCurrentBlockBeans();
    }

    private void showEmptyInputs() {
        ArrayList<BlockBean> blocks = getDiagnosticBlocks();
        ArrayList<BlockBean> emptyBlocks = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        if (blocks != null) {
            for (BlockBean bean : blocks) {
                if (bean == null || bean.id == null) continue;
                final Rs block;
                try {
                    block = o.a(Integer.parseInt(bean.id));
                } catch (RuntimeException invalidId) {
                    continue;
                }
                if (block == null || block.getBlockType() != 0 || block.V == null) continue;
                int emptyCount = 0;
                for (int i = 0; i < block.V.size(); i++) {
                    if (block.V.get(i) instanceof Ss && isEmptyParameter(bean, i)) emptyCount++;
                }
                if (emptyCount > 0) {
                    emptyBlocks.add(bean);
                    String name = bean.spec == null || bean.spec.trim().isEmpty() ? "Block" : bean.spec;
                    labels.add(name + "  ·  " + emptyCount + " empty input" + (emptyCount == 1 ? "" : "s"));
                }
            }
        }
        if (emptyBlocks.isEmpty()) {
            SketchwareUtil.toast("No empty inputs found");
            updateEditorStatus();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Empty inputs (" + emptyBlocks.size() + " blocks)")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    try {
                        BlockBean bean = emptyBlocks.get(which);
                        int blockId = Integer.parseInt(bean.id);
                        Rs block = o.a(blockId);
                        if (block == null || block.V == null) return;
                        View firstInput = null;
                        for (int i = 0; i < block.V.size(); i++) {
                            View inputView = block.V.get(i);
                            if (inputView instanceof Ss && isEmptyParameter(bean, i)) {
                                if (firstInput == null) firstInput = inputView;
                                highlightEmptyInput(inputView);
                            }
                        }
                        if (firstInput != null) scrollEditorToView(firstInput);
                        else scrollEditorToView(block);
                    } catch (RuntimeException ignored) {
                    }
                })
                .setNegativeButton(R.string.common_word_close, null)
                .show();
    }

    private void highlightEmptyInput(View target) {
        if (target == null) return;
        if (!inputHighlightOriginalBackgrounds.containsKey(target)) {
            Drawable original = target.getBackground();
            inputHighlightOriginalBackgrounds.put(target, original);
            inputHighlightHadOriginalBackgrounds.put(target, original != null);
        }
        Drawable original = inputHighlightOriginalBackgrounds.get(target);
        boolean hadOriginalBackground = Boolean.TRUE.equals(inputHighlightHadOriginalBackgrounds.get(target));
        ObjectAnimator previous = inputHighlightAnimations.remove(target);
        if (previous != null) previous.cancel();

        GradientDrawable highlight = new GradientDrawable();
        highlight.setColor(0x22E53935);
        highlight.setCornerRadius(SketchwareUtil.dpToPx(4));
        highlight.setStroke(SketchwareUtil.dpToPx(2), 0xFFE53935);
        target.setBackground(highlight);
        target.setAlpha(1f);

        ObjectAnimator pulse = ObjectAnimator.ofFloat(target, View.ALPHA, 1f, 0.28f, 1f);
        pulse.setDuration(420);
        pulse.setRepeatCount(4);
        pulse.setRepeatMode(ValueAnimator.RESTART);
        final Drawable originalBackground = original;
        final boolean restoreBackground = hadOriginalBackground;
        pulse.addListener(new AnimatorListenerAdapter() {
            private boolean restored;

            private void restoreIfCurrent() {
                if (restored || inputHighlightAnimations.get(target) != pulse) return;
                restored = true;
                inputHighlightAnimations.remove(target);
                inputHighlightOriginalBackgrounds.remove(target);
                inputHighlightHadOriginalBackgrounds.remove(target);
                target.setAlpha(1f);
                target.setBackground(restoreBackground ? originalBackground : null);
            }

            @Override public void onAnimationEnd(Animator animation) { restoreIfCurrent(); }
            @Override public void onAnimationCancel(Animator animation) { restoreIfCurrent(); }
        });
        inputHighlightAnimations.put(target, pulse);
        pulse.start();
    }

    private void scrollEditorToView(View target) {
        if (target == null || viewLogicEditor == null) return;
        viewLogicEditor.post(() -> {
            if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
            int[] targetLocation = new int[2];
            int[] editorLocation = new int[2];
            target.getLocationOnScreen(targetLocation);
            viewLogicEditor.getLocationOnScreen(editorLocation);
            float targetCenterX = targetLocation[0] - editorLocation[0] + target.getWidth() / 2f;
            float targetCenterY = targetLocation[1] - editorLocation[1] + target.getHeight() / 2f;
            int dx = Math.round(targetCenterX - viewLogicEditor.getWidth() * 0.32f);
            int dy = Math.round(targetCenterY - viewLogicEditor.getHeight() * 0.34f);
            viewLogicEditor.smoothScrollBy(dx, dy);
        });
    }

    private void clearInputHighlightAnimations() {
        for (ObjectAnimator animation : new ArrayList<>(inputHighlightAnimations.values())) {
            if (animation != null) animation.cancel();
        }
        inputHighlightAnimations.clear();
        inputHighlightOriginalBackgrounds.clear();
        inputHighlightHadOriginalBackgrounds.clear();
    }

    private void setEditorZoom(float requested) {
        editorZoom = Math.max(0.4f, Math.min(2.0f, requested));
        o.setPivotX(0f);
        o.setPivotY(0f);
        o.setScaleX(editorZoom);
        o.setScaleY(editorZoom);
        if (zoomPercentText != null) zoomPercentText.setText(Math.round(editorZoom * 100f) + "%");
        viewLogicEditor.invalidate();
        updateBlockNotePositions();
    }

    private BlockBean cloneBlockBean(BlockBean bean) {
        if (bean.parameters != null) return bean.clone();
        BlockBean cloned = new BlockBean(bean.id, bean.spec, bean.type, bean.typeName, bean.opCode);
        cloned.color = bean.color;
        cloned.parameters = new ArrayList<>();
        cloned.subStack1 = bean.subStack1;
        cloned.subStack2 = bean.subStack2;
        cloned.nextBlock = bean.nextBlock;
        return cloned;
    }

    private ArrayList<BlockBean> cloneBlockBeans(ArrayList<BlockBean> blocks) {
        ArrayList<BlockBean> copy = new ArrayList<>();
        if (blocks == null) return copy;
        for (BlockBean bean : blocks) if (bean != null) copy.add(cloneBlockBean(bean));
        return copy;
    }

    private int countBlockBeans(ArrayList<BlockBean> blocks) {
        if (blocks == null) return 0;
        int count = 0;
        for (BlockBean bean : blocks) {
            if (bean == null || bean.id == null || o == null) continue;
            try {
                Rs block = o.a(Integer.parseInt(bean.id));
                if (block != null && block.getBlockType() == 0) count++;
            } catch (RuntimeException ignored) {
                // Invalid/stale IDs are not counted as visible logic blocks.
            }
        }
        return count;
    }

    /** Returns a de-duplicated view of the current logic graph, including nested children. */
    private ArrayList<BlockBean> collectCurrentBlockBeans() {
        LinkedHashMap<String, BlockBean> collected = new LinkedHashMap<>();
        if (o == null) return new ArrayList<>();
        ArrayList<BlockBean> roots;
        try {
            roots = o.getBlocks();
        } catch (RuntimeException error) {
            return new ArrayList<>();
        }
        if (roots == null) return new ArrayList<>();
        for (BlockBean bean : roots) {
            if (bean == null || bean.id == null) continue;
            try {
                Rs root = o.a(Integer.parseInt(bean.id));
                if (root != null) {
                    if (root.getBlockType() == 0) collected.put(root.getBean().id, root.getBean());
                    ArrayList<Rs> descendants = root.getAllChildren();
                    if (descendants != null) {
                        for (Rs child : descendants) {
                            if (child != null && child.getBlockType() == 0 && child.getBean() != null
                                    && child.getBean().id != null) {
                                collected.put(child.getBean().id, child.getBean());
                            }
                        }
                    }
                } else {
                    collected.put(bean.id, bean);
                }
            } catch (RuntimeException ignored) {
                collected.put(bean.id, bean);
            }
        }
        return new ArrayList<>(collected.values());
    }

    private int countLogicBlocks() {
        // On first open, rely on the completed loader count. The serializer may expose only
        // top-level roots before any user edit, which previously produced the transient "1 block".
        if (!editorChanged && editorBlocksReady) return Math.max(0, loadedEventBlockCount);
        return countBlockBeans(collectCurrentBlockBeans());
    }

    private boolean isEmptyParameter(BlockBean bean, int index) {
        if (bean == null || bean.parameters == null || index < 0 || index >= bean.parameters.size()) return true;
        String value = bean.parameters.get(index);
        return value == null || value.trim().isEmpty();
    }

    private int countEmptyInputs() {
        if (o == null) return 0;
        ArrayList<BlockBean> blocks;
        try {
            blocks = !editorChanged && editorBlocksReady
                    ? loadedEventBlockBeans : collectCurrentBlockBeans();
        } catch (RuntimeException error) {
            Log.w("LogicEditorActivity", "Unable to collect blocks for input diagnostics", error);
            return 0;
        }
        if (blocks == null) return 0;
        int count = 0;
        for (BlockBean bean : blocks) {
            if (bean == null || bean.id == null) continue;
            try {
                Rs block = o.a(Integer.parseInt(bean.id));
                if (block == null || block.getBlockType() != 0 || block.V == null) continue;
                for (int i = 0; i < block.V.size(); i++) {
                    if (block.V.get(i) instanceof Ss && isEmptyParameter(bean, i)) count++;
                }
            } catch (RuntimeException invalidBlock) {
                // One malformed/stale bean must not hide problems in the remaining blocks.
                Log.w("LogicEditorActivity", "Skipping invalid block during input diagnostics: " + bean.id);
            }
        }
        return count;
    }

    private void updateEditorStatus() {
        if (editorStatusText == null || o == null) return;
        if (!editorBlocksReady) {
            if (editorStatusCard != null) editorStatusCard.setVisibility(View.INVISIBLE);
            return;
        }
        try {
            int blocks = countLogicBlocks();
            int problems = countEmptyInputs();
            editorStatusText.setText(blocks + (blocks == 1 ? " block" : " blocks"));
            if (editorStatusChangesText != null) {
                editorStatusChangesText.setText(editorChanged ? "Changed" : "No changes");
                editorStatusChangesText.setTextColor(ThemeUtils.getColor(this, editorChanged
                        ? R.attr.colorPrimary : R.attr.colorOnSurfaceVariant));
            }
            if (editorStatusCard instanceof MaterialCardView card) {
                card.setCardBackgroundColor(ColorUtils.setAlphaComponent(
                        ThemeUtils.getColor(this, R.attr.colorSurfaceContainer), 228));
                card.setStrokeColor(ColorUtils.setAlphaComponent(
                        ThemeUtils.getColor(this, R.attr.colorOutlineVariant), 112));
                card.setStrokeWidth(SketchwareUtil.dpToPx(1));
            }
            if (editorStatusCard != null) {
                editorStatusCard.setVisibility(View.VISIBLE);
                editorStatusCard.setContentDescription(blocks + (blocks == 1 ? " block. " : " blocks. ")
                        + (editorChanged ? "Changed. " : "No changes. ")
                        + problems + (problems == 1 ? " problem." : " problems."));
                editorStatusCard.setAlpha(1f);
            }

            if (editorStatusProblemChip != null) {
                int previousVisibility = editorStatusProblemChip.getVisibility();
                boolean hasProblems = problems > 0;
                int chipBackgroundColor = ThemeUtils.getColor(this, hasProblems
                        ? R.attr.colorErrorContainer : R.attr.colorSurfaceContainerHigh);
                int chipOutlineColor = ThemeUtils.getColor(this, hasProblems
                        ? R.attr.colorError : R.attr.colorOutlineVariant);
                int chipForegroundColor = ThemeUtils.getColor(this, hasProblems
                        ? R.attr.colorError : R.attr.colorOnSurfaceVariant);
                GradientDrawable problemBackground = makeRoundedBackground(
                        ColorUtils.setAlphaComponent(chipBackgroundColor, hasProblems ? 226 : 176),
                        SketchwareUtil.dpToPx(14));
                problemBackground.setStroke(SketchwareUtil.dpToPx(1),
                        ColorUtils.setAlphaComponent(chipOutlineColor, hasProblems ? 92 : 82));
                editorStatusProblemChip.setBackground(problemBackground);
                editorStatusProblemChip.setVisibility(View.VISIBLE);
                editorStatusProblemChip.setClickable(hasProblems);
                editorStatusProblemChip.setFocusable(hasProblems);
                editorStatusProblemChip.setContentDescription(hasProblems
                        ? problems + " empty inputs. Tap to highlight them."
                        : "No empty inputs.");
                if (editorStatusProblemText != null) {
                    editorStatusProblemText.setText(problems + (problems == 1 ? " problem" : " problems"));
                    editorStatusProblemText.setTextColor(chipForegroundColor);
                }
                ImageView problemIcon = editorStatusProblemChip.findViewById(R.id.editor_status_problem_icon);
                if (problemIcon != null) {
                    problemIcon.setImageResource(hasProblems
                            ? R.drawable.ic_mtrl_warning : R.drawable.ic_mtrl_check);
                    problemIcon.setColorFilter(ThemeUtils.getColor(this,
                            hasProblems ? R.attr.colorError : R.attr.colorPrimary));
                }
                if (hasProblems && previousVisibility != View.VISIBLE) {
                    editorStatusProblemChip.setScaleX(0.92f);
                    editorStatusProblemChip.setScaleY(0.92f);
                    editorStatusProblemChip.setAlpha(0f);
                    editorStatusProblemChip.animate().scaleX(1f).scaleY(1f).alpha(1f)
                            .setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
                } else {
                    editorStatusProblemChip.animate().cancel();
                    editorStatusProblemChip.setScaleX(1f);
                    editorStatusProblemChip.setScaleY(1f);
                    editorStatusProblemChip.setAlpha(1f);
                }
            }
        } catch (RuntimeException error) {
            editorStatusText.setText("Blocks");
            if (editorStatusChangesText != null) editorStatusChangesText.setText("Status unavailable");
            if (editorStatusProblemChip != null) editorStatusProblemChip.setVisibility(View.GONE);
            Log.w("LogicEditorActivity", "Unable to refresh editor status", error);
        }
    }

    private void highlightAllEmptyInputs() {
        ArrayList<BlockBean> blocks = getDiagnosticBlocks();
        if (blocks == null || blocks.isEmpty()) return;
        View firstInput = null;
        for (BlockBean bean : blocks) {
            if (bean == null || bean.id == null) continue;
            final Rs block;
            try { block = o.a(Integer.parseInt(bean.id)); } catch (RuntimeException ignored) { continue; }
            if (block == null || block.getBlockType() != 0 || block.V == null) continue;
            for (int i = 0; i < block.V.size(); i++) {
                View input = block.V.get(i);
                if (input instanceof Ss && isEmptyParameter(bean, i)) {
                    if (firstInput == null) firstInput = input;
                    highlightEmptyInput(input);
                }
            }
        }
        if (firstInput != null) scrollEditorToView(firstInput);
    }

    private Ss findBlockInputAtScreenPosition(Rs block, float screenX, float screenY) {
        if (block == null || block.V == null) return null;
        for (int i = block.V.size() - 1; i >= 0; i--) {
            View slot = block.V.get(i);
            if (!(slot instanceof Ss input) || slot.getVisibility() != View.VISIBLE
                    || slot.getWidth() <= 0 || slot.getHeight() <= 0 || !slot.isShown()) {
                continue;
            }
            int[] location = new int[2];
            slot.getLocationOnScreen(location);
            if (screenX >= location[0] && screenX < location[0] + slot.getWidth()
                    && screenY >= location[1] && screenY < location[1] + slot.getHeight()) {
                return input;
            }
        }
        return null;
    }

    private void showBlockActionBar(Rs block) {
        if (block == null || block.getBlockType() != 0) return;
        selectedBlock = block;
        if (blockActionPopup != null && blockActionPopup.isShowing()) blockActionPopup.dismiss();
        int dp = Math.max(1, Math.round(getResources().getDisplayMetrics().density));
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(22f * dp);
        card.setCardElevation(8f * dp);
        card.setStrokeWidth(dp);
        card.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceContainer));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(4 * dp, 4 * dp, 4 * dp, 4 * dp);
        card.addView(row);
        addBlockAction(row, R.drawable.ic_mtrl_edit, "Edit", () -> showBlockDetails(block));
        addBlockAction(row, R.drawable.ic_mtrl_copy, "Duplicate", () -> duplicateBlock(block));
        addBlockAction(row, R.drawable.ic_mtrl_toggle, "Turn off", () -> showTurnOffLimitation());
        addBlockAction(row, R.drawable.ic_mtrl_delete, "Delete", () -> deleteBlockWithHistory(block));
        addBlockAction(row, R.drawable.ic_mtrl_more_vertical, "More", () -> showBlockMoreMenu(block, block));
        blockActionPopup = new PopupWindow(card, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        blockActionPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        blockActionPopup.setFocusable(false);
        blockActionPopup.setTouchable(true);
        blockActionPopup.setOutsideTouchable(false);
        blockActionPopup.setClippingEnabled(true);
        blockActionPopup.setElevation(10f * dp);
        card.setAlpha(0f);
        card.setScaleX(0.96f);
        card.setScaleY(0.96f);
        int[] location = new int[2];
        block.getLocationOnScreen(location);
        card.measure(View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().widthPixels, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().heightPixels, View.MeasureSpec.AT_MOST));
        int x = Math.max(8 * dp, Math.min(location[0], getResources().getDisplayMetrics().widthPixels - card.getMeasuredWidth() - 8 * dp));
        int y = Math.min(location[1] + block.getHeight() + 8 * dp, getResources().getDisplayMetrics().heightPixels - card.getMeasuredHeight() - 24 * dp);
        blockActionPopup.showAtLocation(viewLogicEditor, Gravity.TOP | Gravity.START, x, Math.max(8 * dp, y));
        card.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(170).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void addBlockAction(LinearLayout row, int iconRes, String label, Runnable action) {
        int dp = (int) getResources().getDisplayMetrics().density;
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(8 * dp, 8 * dp, 8 * dp, 6 * dp);
        item.setBackground(makeActionItemBackground(false, 14 * dp));
        item.setClickable(true);
        item.setFocusable(false);
        item.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                view.setBackground(makeActionItemBackground(true, 14 * dp));
                view.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.88f).setDuration(90).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.setBackground(makeActionItemBackground(false, 14 * dp));
                view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).start();
            }
            return false;
        });
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        int actionColor = "Delete".equals(label)
                ? ThemeUtils.getColor(this, R.attr.colorError)
                : ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant);
        icon.setColorFilter(actionColor);
        item.addView(icon, new LinearLayout.LayoutParams(24 * dp, 24 * dp));
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(11);
        text.setSingleLine(false);
        text.setMaxLines(2);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setGravity(Gravity.CENTER);
        text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        text.setTextColor(actionColor);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
        textParams.topMargin = 3 * dp;
        item.addView(text, textParams);
        row.addView(item, new LinearLayout.LayoutParams(68 * dp, -2));
        item.setOnClickListener(v -> {
            if ("More".equals(label)) {
                showBlockMoreMenu(selectedBlock, item);
                return;
            }
            if (blockMorePopup != null && blockMorePopup.isShowing()) blockMorePopup.dismiss();
            if (blockActionPopup != null && blockActionPopup.isShowing()) blockActionPopup.dismiss();
            action.run();
        });
    }

    private GradientDrawable makeActionItemBackground(boolean pressed, int cornerRadius) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(cornerRadius);
        background.setColor(ThemeUtils.getColor(this, pressed
                ? R.attr.colorSurfaceContainerHigh : R.attr.colorSurfaceContainerLow));
        return background;
    }

    private void deleteBlockWithHistory(Rs block) {
        if (block == null) return;
        ArrayList<BlockBean> removed = copyBlockTree(block);
        if (removed.isEmpty()) return;
        int[] blockLocation = new int[2];
        int[] paneLocation = new int[2];
        block.getLocationOnScreen(blockLocation);
        o.getLocationOnScreen(paneLocation);
        Rs parent = block.E;
        BlockBean parentBefore = parent == null ? null : parent.getBean().clone();
        b(block);
        BlockBean parentAfter = parent == null ? null : parent.getBean().clone();
        bC.d(scId).b(s(), removed, blockLocation[0] - paneLocation[0],
                blockLocation[1] - paneLocation[1], parentBefore, parentAfter);
        C();
    }

    private void showTurnOffLimitation() {
        new MaterialAlertDialogBuilder(this).setTitle("Turn off block")
                .setMessage("This project's BlockBean format does not store a disabled state. Fading a block would still generate and run its Java code, so no unsafe visual-only toggle was applied.")
                .setPositiveButton(R.string.common_word_ok, null).show();
    }

    private void showBlockDetails(Rs block) {
        if (block == null || isFinishing()) return;
        BlockBean bean = block.getBean();
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_block_details, null, false);
        NestedScrollView scroll = sheet.findViewById(R.id.block_details_scroll);
        LinearLayout content = sheet.findViewById(R.id.block_details_content);

        content.addView(createBlockDetailsHeader(bean));
        addDetailsSectionTitle(content, "INPUTS");
        int inputCount = block.V == null ? 0 : block.V.size();
        if (inputCount == 0) {
            content.addView(detailsBody("This block has no editable inputs."));
        } else {
            for (int i = 0; i < inputCount; i++) {
                View inputView = block.V.get(i);
                if (!(inputView instanceof Ss)) continue;
                Ss input = (Ss) inputView;
                String value = "";
                try {
                    Object arg = input.getArgValue();
                    value = arg == null ? "" : arg.toString();
                } catch (RuntimeException ignored) {
                }

                String parameterType = "Input " + (i + 1);
                try {
                    ArrayList<Gx> parameterInfo = bean.getParamClassInfo();
                    if (parameterInfo != null && i < parameterInfo.size() && parameterInfo.get(i) != null) {
                        String className = parameterInfo.get(i).getClassName();
                        if (className != null && !className.trim().isEmpty()) {
                            parameterType = (parameterInfo.get(i).c() ? "Var " : "") + className;
                        }
                    }
                } catch (RuntimeException ignored) {
                }

                View inputCard = getLayoutInflater().inflate(
                        R.layout.item_block_detail_input, content, false);
                TextView label = inputCard.findViewById(R.id.input_type);
                TextView currentValue = inputCard.findViewById(R.id.input_value);
                TextView hint = inputCard.findViewById(R.id.input_hint);
                MaterialButton change = inputCard.findViewById(R.id.input_change);
                boolean isEmpty = value.trim().isEmpty();
                label.setText(parameterType);
                currentValue.setText(isEmpty ? "Not set" : value);
                currentValue.setTextColor(ThemeUtils.getColor(this,
                        isEmpty ? R.attr.colorError : R.attr.colorOnSurface));
                hint.setText(isEmpty ? "Choose a value to complete this block."
                        : "Change the current input value.");
                change.setOnClickListener(v -> {
                    dialog.dismiss();
                    new ExtraMenuBean(this).defineMenuSelector(input);
                });
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
                cardParams.bottomMargin = SketchwareUtil.dpToPx(10);
                content.addView(inputCard, cardParams);
            }
        }

        if (bean.getClassInfo() != null && bean.getClassInfo().c()) {
            addVariableUsageSection(content, bean);
        }

        addDetailsSectionTitle(content, "THE JAVA IT MAKES");
        String javaCode = generateBlockJava(block);
        if (javaCode == null || javaCode.trim().isEmpty()) {
            content.addView(detailsBody("This block makes no Java on its own."));
        } else {
            HorizontalScrollView codeScroll = new HorizontalScrollView(this);
            codeScroll.setFillViewport(false);
            codeScroll.setHorizontalScrollBarEnabled(true);
            TextView code = detailsText(javaCode, 12, R.attr.colorOnSurface, false);
            code.setTypeface(Typeface.MONOSPACE);
            code.setTextIsSelectable(true);
            code.setLineSpacing(SketchwareUtil.dpToPx(2), 1f);
            code.setPadding(SketchwareUtil.dpToPx(14), SketchwareUtil.dpToPx(12),
                    SketchwareUtil.dpToPx(14), SketchwareUtil.dpToPx(12));
            code.setBackground(makeRoundedBackground(
                    ThemeUtils.getColor(this, R.attr.colorSurfaceContainer),
                    SketchwareUtil.dpToPx(14)));
            codeScroll.addView(code, new HorizontalScrollView.LayoutParams(-2, -2));
            content.addView(codeScroll);
            MaterialButton copyJava = new MaterialButton(this, null, R.attr.materialButtonOutlinedStyle);
            copyJava.setText("Copy Java");
            copyJava.setMinHeight(SketchwareUtil.dpToPx(38));
            copyJava.setInsetTop(0);
            copyJava.setInsetBottom(0);
            copyJava.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Block Java", javaCode));
                SketchwareUtil.toast("Java copied");
            });
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-2, -2);
            copyParams.topMargin = SketchwareUtil.dpToPx(8);
            content.addView(copyJava, copyParams);
        }

        addDetailsSectionTitle(content, "ACTIONS");
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.START);
        actions.setPadding(0, SketchwareUtil.dpToPx(4), 0, 0);
        addDetailAction(actions, "Duplicate", () -> { dialog.dismiss(); duplicateBlock(block); });
        addDetailAction(actions, "Copy", () -> copyBlockTreeToClipboard(block));
        addDetailAction(actions, "Turn off", this::showTurnOffLimitation);
        content.addView(actions);
        LinearLayout actions2 = new LinearLayout(this);
        actions2.setOrientation(LinearLayout.HORIZONTAL);
        addDetailAction(actions2, "Save as a collection", () -> { dialog.dismiss(); c(block); });
        addDetailAction(actions2, hasBlockNote(block) ? "Edit note" : "Add a note",
                () -> { dialog.dismiss(); showBlockNote(block); });
        content.addView(actions2);
        LinearLayout actions3 = new LinearLayout(this);
        actions3.setOrientation(LinearLayout.HORIZONTAL);
        addDetailAction(actions3, "Delete", () -> { dialog.dismiss(); deleteBlockWithHistory(block); });
        addDetailAction(actions3, "About this block", () -> { dialog.dismiss(); showBlockAbout(block); });
        content.addView(actions3);

        scroll.setFillViewport(true);
        scroll.setNestedScrollingEnabled(true);
        dialog.setContentView(sheet);
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            BottomSheetBehavior<?> behavior = dialog.getBehavior();
            behavior.setMaxHeight(Math.round(getResources().getDisplayMetrics().heightPixels * 0.90f));
            behavior.setFitToContents(true);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        dialog.show();
    }

    private void addVariableUsageSection(LinearLayout content, BlockBean variableBean) {
        String variableName = variableBean.spec == null ? "" : variableBean.spec.trim();
        ArrayList<String> usageEvents = new ArrayList<>();
        try {
            ArrayList<EventBean> events = jC.a(scId).g(M.getJavaName());
            if (events != null) {
                for (EventBean event : events) {
                    if (event == null || event.eventName == null) continue;
                    String eventKey = event.targetId + "_" + event.eventName;
                    ArrayList<BlockBean> eventBlocks = jC.a(scId).a(M.getJavaName(), eventKey);
                    boolean used = false;
                    if (eventBlocks != null) {
                        for (BlockBean candidate : eventBlocks) {
                            if (candidate == null || TextUtils.equals(variableBean.id, candidate.id)) continue;
                            if (variableName.equals(candidate.spec)) { used = true; break; }
                            if (candidate.parameters != null) {
                                for (String parameter : candidate.parameters) {
                                    if (variableName.equals(parameter)) { used = true; break; }
                                }
                            }
                            if (used) break;
                        }
                    }
                    if (used && !usageEvents.contains(event.eventName)) usageEvents.add(event.eventName);
                }
            }
        } catch (RuntimeException error) {
            crashlytics.recordException(error);
        }

        String title = variableName + "  ·  USED IN " + usageEvents.size()
                + (usageEvents.size() == 1 ? " EVENT" : " EVENTS");
        addDetailsSectionTitle(content, title);
        if (usageEvents.isEmpty()) {
            content.addView(detailsBody("No event references were found for this variable."));
            return;
        }
        for (String eventNameUsed : usageEvents) {
            MaterialCardView usageCard = new MaterialCardView(this);
            usageCard.setRadius(SketchwareUtil.dpToPx(15));
            usageCard.setCardElevation(0);
            usageCard.setStrokeWidth(SketchwareUtil.dpToPx(1));
            usageCard.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
            usageCard.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceContainerLow));
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(SketchwareUtil.dpToPx(14), SketchwareUtil.dpToPx(12),
                    SketchwareUtil.dpToPx(14), SketchwareUtil.dpToPx(12));
            TextView bolt = detailsText("ϟ", 22, R.attr.colorPrimary, true);
            row.addView(bolt);
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, -2, 1f);
            labelsParams.leftMargin = SketchwareUtil.dpToPx(12);
            labels.addView(detailsText(eventNameUsed, 14, R.attr.colorOnSurface, true));
            labels.addView(detailsText("Variable reference", 12, R.attr.colorOnSurfaceVariant, false));
            row.addView(labels, labelsParams);
            usageCard.addView(row);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.bottomMargin = SketchwareUtil.dpToPx(8);
            content.addView(usageCard, cardParams);
        }
    }

    private View createBlockDetailsHeader(BlockBean bean) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View accent = new View(this);
        accent.setBackground(makeRoundedBackground(ThemeUtils.getColor(this, R.attr.colorPrimary), SketchwareUtil.dpToPx(8)));
        row.addView(accent, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(8), SketchwareUtil.dpToPx(54)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelsParams.leftMargin = SketchwareUtil.dpToPx(14);
        labels.addView(detailsText(bean.spec == null || bean.spec.trim().isEmpty() ? "Block" : bean.spec,
                22, R.attr.colorOnSurface, true));
        TextView type = detailsText((bean.typeName == null ? "Block" : bean.typeName) + "  ·  "
                + (bean.opCode == null ? "" : bean.opCode), 13, R.attr.colorOnSurfaceVariant, false);
        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(-1, -2);
        typeParams.topMargin = SketchwareUtil.dpToPx(3);
        labels.addView(type, typeParams);
        row.addView(labels, labelsParams);
        return row;
    }

    private void addDetailsSectionTitle(LinearLayout parent, String title) {
        TextView view = detailsText(title, 12, R.attr.colorOnSurfaceVariant, true);
        view.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = SketchwareUtil.dpToPx(22);
        params.bottomMargin = SketchwareUtil.dpToPx(10);
        parent.addView(view, params);
    }

    private TextView detailsBody(String value) {
        TextView view = detailsText(value, 14, R.attr.colorOnSurfaceVariant, false);
        view.setPadding(SketchwareUtil.dpToPx(4), SketchwareUtil.dpToPx(8),
                SketchwareUtil.dpToPx(4), SketchwareUtil.dpToPx(8));
        return view;
    }

    private TextView detailsText(String value, int size, int colorAttr, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ThemeUtils.getColor(this, colorAttr));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable makeRoundedBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private void addDetailAction(LinearLayout parent, String label, Runnable action) {
        MaterialButton button = new MaterialButton(
                this, null, R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        button.setTextSize(11);
        button.setMinHeight(SketchwareUtil.dpToPx(38));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setOnClickListener(v -> {
            v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70)
                    .withEndAction(() -> { v.animate().scaleX(1f).scaleY(1f).setDuration(110).start(); action.run(); }).start();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.rightMargin = SketchwareUtil.dpToPx(6);
        params.bottomMargin = SketchwareUtil.dpToPx(6);
        parent.addView(button, params);
    }

    private String generateBlockJava(Rs block) {
        try {
            ArrayList<BlockBean> beans = copyBlockCodeTree(block);
            yq holder = new yq(this, scId);
            holder.a(jC.c(scId), jC.b(scId), jC.a(scId));
            return new Fx(M.getActivityName(), "preview", holder.N, beans, isViewBindingEnabled).a();
        } catch (RuntimeException error) {
            crashlytics.recordException(error);
            return "";
        }
    }

    private ArrayList<BlockBean> copyBlockTree(Rs block) {
        ArrayList<BlockBean> data = new ArrayList<>();
        if (block == null) return data;
        for (Rs child : block.getAllChildren()) data.add(child.getBean().clone());
        if (data.isEmpty()) data.add(block.getBean().clone());
        return data;
    }

    private ArrayList<BlockBean> copyBlockCodeTree(Rs block) {
        ArrayList<BlockBean> data = new ArrayList<>();
        if (block == null || o == null) return data;
        appendBlockCodeTree(block, false, data, new HashSet<>());
        return data;
    }

    private void appendBlockCodeTree(Rs block, boolean includeNext,
                                     ArrayList<BlockBean> destination, HashSet<Integer> visited) {
        if (block == null || block.getBean() == null) return;
        BlockBean source = block.getBean();
        int blockId;
        try {
            blockId = Integer.parseInt(source.id);
        } catch (RuntimeException invalidId) {
            return;
        }
        if (!visited.add(blockId)) return;

        BlockBean copy = cloneBlockBean(source);
        if (!includeNext) copy.nextBlock = -1;
        destination.add(copy);

        if (source.parameters != null) {
            for (String parameter : source.parameters) {
                if (parameter == null || parameter.length() < 2 || parameter.charAt(0) != '@') continue;
                try {
                    appendBlockCodeTreeById(Integer.parseInt(parameter.substring(1)),
                            destination, visited);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (source.subStack1 >= 0) appendBlockCodeTreeById(source.subStack1, destination, visited);
        if (source.subStack2 >= 0) appendBlockCodeTreeById(source.subStack2, destination, visited);
        if (includeNext && source.nextBlock >= 0) {
            appendBlockCodeTreeById(source.nextBlock, destination, visited);
        }
    }

    private void appendBlockCodeTreeById(int blockId, ArrayList<BlockBean> destination,
                                         HashSet<Integer> visited) {
        try {
            appendBlockCodeTree(o.a(blockId), true, destination, visited);
        } catch (RuntimeException ignored) {
        }
    }

    private void copyBlockTreeToClipboard(Rs block) {
        ArrayList<BlockBean> data = copyBlockTree(block);
        if (data.isEmpty()) return;
        String json = new Gson().toJson(data);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Sketchware Neo blocks", "RAW BLOCK BEANS (JSON PATTERN)\n" + json));
            SketchwareUtil.toast("Block copied");
        }
    }

    private void duplicateBlock(Rs block) {
        ArrayList<BlockBean> data = copyBlockTree(block);
        if (data.isEmpty()) return;
        remapClipboardBlockIds(data);
        int[] location = new int[2];
        block.getLocationOnScreen(location);
        insertConvertedBlocksAt(data, location[0] + block.getWidth() + SketchwareUtil.dpToPx(20), location[1] + SketchwareUtil.dpToPx(20));
    }

    private void showBlockMoreMenu(Rs block, View anchor) {
        if (block == null || anchor == null) return;
        if (blockMorePopup != null && blockMorePopup.isShowing()) blockMorePopup.dismiss();

        int dp = Math.max(1, Math.round(getResources().getDisplayMetrics().density));
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(18f * dp);
        card.setCardElevation(8f * dp);
        card.setStrokeWidth(dp);
        card.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceContainer));

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(4 * dp, 4 * dp, 4 * dp, 4 * dp);
        card.addView(menu);

        addBlockMoreItem(menu, R.drawable.ic_mtrl_copy, "Copy", () -> copyBlockTreeToClipboard(block));
        addBlockMoreItem(menu, R.drawable.ic_mtrl_text_select, "Select several", this::showMultiBlockSelector);
        addBlockMoreItem(menu, R.drawable.ic_mtrl_folder_open, "Save as a collection", () -> c(block));
        addBlockMoreItem(menu, R.drawable.ic_mtrl_edit,
                hasBlockNote(block) ? "Edit note" : "Add a note", () -> showBlockNote(block));
        addBlockMoreItem(menu, R.drawable.ic_mtrl_code, "Show its Java", () -> {
            dismissBlockPopups();
            showBlockJava(block);
        });
        addBlockMoreItem(menu, R.drawable.ic_mtrl_info, "About this block", () -> showBlockAbout(block));

        int menuWidth = 216 * dp;
        blockMorePopup = new PopupWindow(card, menuWidth, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        blockMorePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        blockMorePopup.setFocusable(false);
        blockMorePopup.setTouchable(true);
        blockMorePopup.setOutsideTouchable(false);
        blockMorePopup.setElevation(10f * dp);
        card.measure(View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().heightPixels, View.MeasureSpec.AT_MOST));

        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int x = anchorLocation[0] + anchor.getWidth() - menuWidth;
        x = Math.max(8 * dp, Math.min(x, screenWidth - menuWidth - 8 * dp));
        int y = anchorLocation[1] + anchor.getHeight() + 6 * dp;
        int maxY = getResources().getDisplayMetrics().heightPixels - card.getMeasuredHeight() - 12 * dp;
        y = Math.max(8 * dp, Math.min(y, maxY));
        blockMorePopup.showAtLocation(viewLogicEditor, Gravity.TOP | Gravity.START, x, y);
        card.setAlpha(0f);
        card.setScaleX(0.97f);
        card.setScaleY(0.97f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(150).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void addBlockMoreItem(LinearLayout parent, int iconRes, String label, Runnable action) {
        int dp = Math.max(1, Math.round(getResources().getDisplayMetrics().density));
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setPadding(12 * dp, 2 * dp, 14 * dp, 2 * dp);
        item.setMinimumHeight(46 * dp);
        item.setBackground(makeActionItemBackground(false, 12 * dp));
        item.setClickable(true);
        item.setFocusable(false);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        item.addView(icon, new LinearLayout.LayoutParams(20 * dp, 20 * dp));
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(13);
        title.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = 12 * dp;
        item.addView(title, titleParams);
        item.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                view.setBackground(makeActionItemBackground(true, 12 * dp));
                view.animate().scaleX(0.985f).scaleY(0.985f).setDuration(70).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.setBackground(makeActionItemBackground(false, 12 * dp));
                view.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
            }
            return false;
        });
        item.setOnClickListener(v -> {
            if (blockMorePopup != null && blockMorePopup.isShowing()) blockMorePopup.dismiss();
            action.run();
        });
        parent.addView(item, new LinearLayout.LayoutParams(-1, 46 * dp));
    }

    private void dismissBlockPopups() {
        if (blockMorePopup != null && blockMorePopup.isShowing()) blockMorePopup.dismiss();
        if (blockActionPopup != null && blockActionPopup.isShowing()) blockActionPopup.dismiss();
        blockMorePopup = null;
        blockActionPopup = null;
    }

    private boolean isInsidePopup(PopupWindow popup, float rawX, float rawY) {
        if (popup == null || !popup.isShowing() || popup.getContentView() == null) return false;
        int[] location = new int[2];
        popup.getContentView().getLocationOnScreen(location);
        View content = popup.getContentView();
        return rawX >= location[0] && rawX <= location[0] + content.getWidth()
                && rawY >= location[1] && rawY <= location[1] + content.getHeight();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN
                && blockActionPopup != null && blockActionPopup.isShowing()) {
            boolean insideAction = isInsidePopup(blockActionPopup, event.getRawX(), event.getRawY());
            boolean insideMore = isInsidePopup(blockMorePopup, event.getRawX(), event.getRawY());
            if (!insideAction && !insideMore) {
                dismissBlockPopups();
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void showMultiBlockSelector() {
        ArrayList<BlockBean> candidates = getDiagnosticBlocks();
        ArrayList<BlockBean> all = new ArrayList<>();
        if (candidates != null) {
            for (BlockBean bean : candidates) {
                if (bean == null || bean.id == null) continue;
                try {
                    Rs block = o.a(Integer.parseInt(bean.id));
                    if (block != null && block.getBlockType() == 0) all.add(bean);
                } catch (RuntimeException ignored) { }
            }
        }
        if (all.isEmpty()) return;
        String[] labels = new String[all.size()];
        boolean[] checked = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            BlockBean bean = all.get(i);
            String name = bean.spec == null || bean.spec.trim().isEmpty() ? "Block" : bean.spec;
            labels[i] = name + "  ·  #" + bean.id;
        }
        new MaterialAlertDialogBuilder(this).setTitle("Select blocks to copy")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Copy selected", (dialog, which) -> {
                    ArrayList<BlockBean> selected = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) selected.add(all.get(i).clone());
                    }
                    if (selected.isEmpty()) { SketchwareUtil.toast("No blocks selected"); return; }
                    // Keep links between selected blocks, but clear references to blocks
                    // that were not selected so a later paste cannot attach to old IDs.
                    remapClipboardBlockIds(selected);
                    String json = new Gson().toJson(selected);
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(
                            "Sketchware Neo blocks", "RAW BLOCK BEANS (JSON PATTERN)\n" + json));
                    SketchwareUtil.toast(selected.size() + " blocks copied");
                }).setNegativeButton(R.string.common_word_cancel, null).show();
    }

    private String getBlockNoteKey(Rs block) {
        return "block_note_" + scId + "_" + M.getJavaName() + "_" + id + "_" + eventName + "_" + block.getBean().id;
    }

    private boolean hasBlockNote(Rs block) {
        if (block == null || block.getBean() == null) return false;
        return !getSharedPreferences("logic_block_notes", MODE_PRIVATE)
                .getString(getBlockNoteKey(block), "").trim().isEmpty();
    }

    private void showBlockNote(Rs block) {
        if (block == null || block.getBean() == null) return;
        String key = getBlockNoteKey(block);
        SharedPreferences prefs = getSharedPreferences("logic_block_notes", MODE_PRIVATE);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = SketchwareUtil.dpToPx(20);
        root.setPadding(pad, pad, pad, pad * 2);
        boolean editingExistingNote = hasBlockNote(block);
        TextView title = new TextView(this); title.setText(editingExistingNote ? "Edit note" : "Add a note"); title.setTextSize(22); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        root.addView(title);
        EditText note = new EditText(this); note.setHint("Write a note..."); note.setMinLines(3); note.setGravity(Gravity.TOP | Gravity.START);
        note.setText(prefs.getString(key, ""));
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, SketchwareUtil.dpToPx(112)); noteParams.topMargin = pad; root.addView(note, noteParams);
        final int[] colors = {0xFFFFE082, 0xFFBBDEFB, 0xFFC8E6C9, 0xFFF8BBD0};
        final int[] selectedColor = {prefs.getInt(key + "_color", colors[0])};
        LinearLayout swatches = new LinearLayout(this); swatches.setOrientation(LinearLayout.HORIZONTAL);
        for (int color : colors) {
            TextView swatch = new TextView(this);
            GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setShape(GradientDrawable.OVAL);
            if (selectedColor[0] == color) bg.setStroke(SketchwareUtil.dpToPx(3), ThemeUtils.getColor(this, R.attr.colorPrimary));
            swatch.setBackground(bg); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(pad, pad); sp.setMargins(0, pad, pad, pad); swatches.addView(swatch, sp);
            swatch.setOnClickListener(v -> { selectedColor[0] = color; for (int i = 0; i < swatches.getChildCount(); i++) { View child = swatches.getChildAt(i); GradientDrawable g = new GradientDrawable(); g.setColor(colors[i]); g.setShape(GradientDrawable.OVAL); if (colors[i] == color) g.setStroke(SketchwareUtil.dpToPx(3), ThemeUtils.getColor(this, R.attr.colorPrimary)); child.setBackground(g); } });
        }
        root.addView(swatches);
        TextView hint = new TextView(this); hint.setText("This note is attached to the selected block and stays local to this installation."); hint.setTextSize(13); hint.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant)); root.addView(hint);
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.END); actions.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton cancel = new MaterialButton(this); cancel.setText("Cancel"); cancel.setOnClickListener(v -> dialog.dismiss());
        MaterialButton save = new MaterialButton(this); save.setText(editingExistingNote ? "Save changes" : "Save note"); save.setOnClickListener(v -> { String value = note.getText().toString().trim(); if (value.isEmpty()) prefs.edit().remove(key).remove(key + "_color").apply(); else prefs.edit().putString(key, value).putInt(key + "_color", selectedColor[0]).apply(); dialog.dismiss(); updateBlockNotePositions(); SketchwareUtil.toast("Note saved"); });
        actions.addView(cancel); actions.addView(save); root.addView(actions);
        dialog.setContentView(root); dialog.show();
    }

    private String getBlockNotePrefix() {
        return "block_note_" + scId + "_" + M.getJavaName() + "_" + id + "_" + eventName + "_";
    }

    private void updateBlockNotePositions() {
        if (editorNotesOverlay == null || o == null || M == null || !editorBlocksReady) return;
        editorNotesOverlay.post(() -> {
            if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
            SharedPreferences prefs = getSharedPreferences("logic_block_notes", MODE_PRIVATE);
            String prefix = getBlockNotePrefix();
            int[] overlayLocation = new int[2];
            editorNotesOverlay.getLocationOnScreen(overlayLocation);
            if (editorNotesOverlay.getWidth() <= 0 || editorNotesOverlay.getHeight() <= 0) return;
            HashSet<String> activeKeys = new HashSet<>();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith(prefix) || key.endsWith("_color") || !(entry.getValue() instanceof String)) continue;
                String note = ((String) entry.getValue()).trim();
                if (note.isEmpty()) continue;
                String blockIdText = key.substring(prefix.length());
                int blockId;
                try { blockId = Integer.parseInt(blockIdText); } catch (NumberFormatException ignored) { continue; }
                Rs block = o.a(blockId);
                if (block == null) continue;
                activeKeys.add(key);
                int color = prefs.getInt(key + "_color", 0xFFFFE082);
                NoteBadgeView badge = blockNoteBadges.get(key);
                if (badge == null) {
                    badge = new NoteBadgeView(this);
                    blockNoteBadges.put(key, badge);
                    editorNotesOverlay.addView(badge);
                    badge.setOnTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start();
                        else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
                        return false;
                    });
                }
                final Rs currentNoteBlock = block;
                badge.setOnClickListener(v -> showBlockNote(currentNoteBlock));
                int[] blockLocation = new int[2];
                block.getLocationOnScreen(blockLocation);
                int maxWidth = SketchwareUtil.dpToPx(206);
                int height = SketchwareUtil.dpToPx(34);
                int blockLeft = blockLocation[0] - overlayLocation[0];
                int scaledBlockWidth = Math.round(block.getWidth() * editorZoom);
                int scaledBlockHeight = Math.round(block.getHeight() * editorZoom);
                int blockRight = blockLeft + scaledBlockWidth;
                int rightSpace = Math.max(0, editorNotesOverlay.getWidth() - blockRight - SketchwareUtil.dpToPx(4));
                // Notes are consistently attached on the right side of their block.
                // If the viewport is tight, the badge narrows rather than jumping left.
                int width = Math.min(maxWidth, Math.max(SketchwareUtil.dpToPx(64), rightSpace));
                width = Math.min(width, Math.max(SketchwareUtil.dpToPx(64), editorNotesOverlay.getWidth() - SketchwareUtil.dpToPx(8)));
                badge.bind(note, color, true);
                int left = Math.max(0, blockRight - SketchwareUtil.dpToPx(1));
                int top = blockLocation[1] - overlayLocation[1] + (scaledBlockHeight - height) / 2;
                top = Math.max(0, Math.min(top, editorNotesOverlay.getHeight() - height));
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
                params.leftMargin = Math.max(0, left);
                params.topMargin = top;
                badge.setLayoutParams(params);
                badge.setVisibility(View.VISIBLE);
            }
            Iterator<Map.Entry<String, NoteBadgeView>> iterator = blockNoteBadges.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, NoteBadgeView> badge = iterator.next();
                if (!activeKeys.contains(badge.getKey())) {
                    editorNotesOverlay.removeView(badge.getValue());
                    iterator.remove();
                }
            }
        });
    }

    private static final class NoteBadgeView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        private final int surfaceColor;
        private final int outlineColor;
        private String note = "";
        private int tint = Color.YELLOW;
        private boolean noteOnRight = true;

        NoteBadgeView(Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            surfaceColor = ThemeUtils.getColor(context, R.attr.colorSurface);
            outlineColor = ThemeUtils.getColor(context, R.attr.colorOutlineVariant);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setClickable(true);
            setFocusable(true);
            setContentDescription("Edit block note");
        }

        void bind(String value, int color, boolean drawToRight) {
            note = value;
            tint = color;
            noteOnRight = drawToRight;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float h = getHeight();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * density);
            paint.setColor(ColorUtils.setAlphaComponent(tint, 208));
            paint.setPathEffect(new DashPathEffect(new float[]{3f * density, 3f * density}, 0));
            float connector = 24f * density;
            if (noteOnRight) {
                canvas.drawLine(0, h / 2f, connector, h / 2f, paint);
            } else {
                canvas.drawLine(getWidth() - connector, h / 2f, getWidth(), h / 2f, paint);
            }
            paint.setPathEffect(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ColorUtils.setAlphaComponent(tint, 76));
            RectF chip = noteOnRight
                    ? new RectF(22f * density, 2f * density, getWidth() - 2f * density, h - 2f * density)
                    : new RectF(2f * density, 2f * density, getWidth() - 22f * density, h - 2f * density);
            canvas.drawRoundRect(chip, 8f * density, 8f * density, paint);
            paint.setColor(ColorUtils.setAlphaComponent(outlineColor, 150));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f * density);
            canvas.drawRoundRect(chip, 8f * density, 8f * density, paint);
            paint.setStyle(Paint.Style.FILL);
            int compositeBackground = ColorUtils.compositeColors(
                    ColorUtils.setAlphaComponent(tint, 76), surfaceColor);
            paint.setColor(ColorUtils.calculateLuminance(compositeBackground) > 0.179
                    ? Color.rgb(28, 28, 30) : Color.WHITE);
            paint.setTextSize(11f * density);
            paint.setFakeBoldText(true);
            float available = Math.max(0, chip.width() - 18f * density);
            CharSequence label = TextUtils.ellipsize(note.replace('\n', ' '),
                    new TextPaint(paint), available, TextUtils.TruncateAt.END);
            canvas.drawText(label.toString(), chip.left + 9f * density,
                    chip.centerY() - (paint.ascent() + paint.descent()) / 2f, paint);
        }
    }

    private void showBlockJava(Rs block) {
        try {
            ArrayList<BlockBean> beans = copyBlockCodeTree(block);
            yq holder = new yq(this, scId); holder.a(jC.c(scId), jC.b(scId), jC.a(scId));
            String code = new Fx(M.getActivityName(), "preview", holder.N, beans, isViewBindingEnabled).a();
            Intent intent = new Intent(this, CodeViewerActivity.class); intent.putExtra("code", code); intent.putExtra("sc_id", scId); intent.putExtra("scheme", CodeViewerActivity.SCHEME_JAVA); startActivity(intent);
        } catch (RuntimeException error) { crashlytics.recordException(error); SketchwareUtil.toastError("Could not generate Java for this block"); }
    }

    private void showBlockAbout(Rs block) {
        BlockBean bean = block.getBean();
        String message = "Type: " + bean.type + "\nType name: " + bean.typeName + "\nOpcode: " + bean.opCode + "\nBlock ID: " + bean.id + "\nSpec: " + bean.spec;
        new MaterialAlertDialogBuilder(this).setTitle("About this block").setMessage(message).setPositiveButton(R.string.common_word_ok, null).show();
    }

    private void insertConvertedBlocksAt(ArrayList<BlockBean> blocks, int screenX, int screenY) {
        if (blocks == null || blocks.isEmpty()) return;
        int[] paneLocation = new int[2]; o.getLocationOnScreen(paneLocation);
        ArrayList<BlockBean> inserted = a(blocks, screenX, screenY, true);
        bC.d(scId).a(s(), inserted, screenX - paneLocation[0], screenY - paneLocation[1], null, null);
        C();
    }

    private void editEventNote() {
        String key = scId + "_" + M.getJavaName() + "_" + id + "_" + eventName;
        SharedPreferences prefs = getSharedPreferences("logic_event_notes", MODE_PRIVATE);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = SketchwareUtil.dpToPx(20);
        root.setPadding(pad, pad, pad, pad * 2);
        TextView title = new TextView(this);
        title.setText("Note");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        root.addView(title);
        EditText input = new EditText(this);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(3);
        input.setHint("Write a note...");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(prefs.getString(key, ""));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, SketchwareUtil.dpToPx(112));
        inputParams.topMargin = pad;
        root.addView(input, inputParams);
        final int[] colors = {0xFFFFE082, 0xFFBBDEFB, 0xFFC8E6C9, 0xFFF8BBD0};
        final int[] selectedColor = {prefs.getInt(key + "_color", colors[0])};
        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        for (int color : colors) {
            TextView swatch = new TextView(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(color);
            shape.setShape(GradientDrawable.OVAL);
            if (selectedColor[0] == color) shape.setStroke(SketchwareUtil.dpToPx(3), ThemeUtils.getColor(this, R.attr.colorPrimary));
            swatch.setBackground(shape);
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(pad, pad);
            swatchParams.setMargins(0, pad, pad, pad);
            swatches.addView(swatch, swatchParams);
            swatch.setOnClickListener(v -> {
                selectedColor[0] = color;
                for (int i = 0; i < swatches.getChildCount(); i++) {
                    GradientDrawable g = new GradientDrawable();
                    g.setColor(colors[i]); g.setShape(GradientDrawable.OVAL);
                    if (colors[i] == color) g.setStroke(SketchwareUtil.dpToPx(3), ThemeUtils.getColor(this, R.attr.colorPrimary));
                    swatches.getChildAt(i).setBackground(g);
                }
            });
        }
        root.addView(swatches);
        TextView hint = new TextView(this);
        hint.setText("Free on the canvas. Notes are local to this installation.");
        hint.setTextSize(13);
        hint.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        root.addView(hint);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        MaterialButton delete = new MaterialButton(this);
        delete.setText("Delete");
        delete.setOnClickListener(v -> { prefs.edit().remove(key).remove(key + "_color").apply(); dialog.dismiss(); updateBlockNotePositions(); });
        MaterialButton save = new MaterialButton(this);
        save.setText("Save note");
        save.setOnClickListener(v -> { prefs.edit().putString(key, input.getText().toString().trim()).putInt(key + "_color", selectedColor[0]).apply(); dialog.dismiss(); SketchwareUtil.toast("Note saved"); });
        actions.addView(delete);
        actions.addView(save);
        root.addView(actions);
        dialog.setContentView(root);
        dialog.show();
    }

    @Override
    public void onPostCreate(Bundle bundle) {
        super.onPostCreate(bundle);

        String title;
        if (eventName.equals("moreBlock")) {
            title = getString(R.string.root_spec_common_define) + " " + ReturnMoreblockManager.getLogicEditorTitle(jC.a(scId).b(M.getJavaName(), id));
        } else if (id.equals("_fab")) {
            title = xB.b().a(getContext(), "fab", eventName);
        } else {
            title = xB.b().a(getContext(), id, eventName);
        }
        String e1 = title;
        if (eventToolbarTitle != null) eventToolbarTitle.setText(eventName == null || eventName.isEmpty() ? title : eventName);
        if (eventToolbarSubtitle != null) eventToolbarSubtitle.setText(M != null ? M.getActivityName() : id);

        o.a(e1, eventName);

        ArrayList<String> spec = FB.c(e1);
        int blockId = 0;
        for (int i = 0; i < spec.size(); i++) {
            String specBit = spec.get(i);
            if (specBit.charAt(0) == '%') {
                Rs block = BlockUtil.getVariableBlock(getContext(), blockId + 1, specBit, "getArg");
                if (block != null) {
                    block.setBlockType(1);
                    o.addView(block);
                    o.getRoot().a((Ts) o.getRoot().V.get(blockId), block);
                    block.setOnTouchListener(this);
                    blockId++;
                }
            }
        }

        o.getRoot().k();
        updateEditorStatus();
        g(getResources().getConfiguration().orientation);
        a(0, 0xffee7d16);

        LoadEventBlocksTask loadEventBlocksTask = new LoadEventBlocksTask(this);
        loadEventBlocksTask.execute();

        z();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!super.isStoragePermissionGranted()) {
            finish();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        bundle.putString("sc_id", scId);
        bundle.putString("id", id);
        bundle.putString("event", eventName);
        bundle.putParcelable("project_file", M);
        super.onSaveInstanceState(bundle);
        ArrayList<BlockBean> blocks = o.getBlocks();
        eC a2 = jC.a(scId);
        String javaName = M.getJavaName();
        a2.a(javaName, id + "_" + eventName, blocks);
        loadedEventBlockBeans = cloneBlockBeans(collectCurrentBlockBeans());
        loadedEventBlockCount = countBlockBeans(loadedEventBlockBeans);
        editorChanged = false;
        updateEditorStatus();
        jC.a(scId).k();
    }

    @Override
    public void onSelected(MoreBlockCollectionBean moreBlockCollectionBean) {
        new MoreblockImporter(this, scId, M).importMoreblock(moreBlockCollectionBean, () -> a(8, 0xff8a55d7));
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int actionMasked = event.getActionMasked();
        if (event.getPointerId(event.getActionIndex()) > 0) {
            return true;
        }
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            isDragged = false;
            touchedBlockInput = null;
            touchStartedOnInput = false;
            int[] locationOnScreen = new int[2];
            v.getLocationOnScreen(locationOnScreen);
            s = locationOnScreen[0];
            t = locationOnScreen[1];
            posInitX = event.getRawX();
            posInitY = event.getRawY();

            if (v instanceof Rs block && block.getBlockType() == 0) {
                touchedBlockInput = findBlockInputAtScreenPosition(
                        block, posInitX, posInitY);
                if (touchedBlockInput != null) {
                    touchStartedOnInput = true;
                    currentTouchedView = null;
                    handler.removeCallbacks(longPressed);
                    return true;
                }
            }

            currentTouchedView = v;
            handler.postDelayed(longPressed, ViewConfiguration.getLongPressTimeout() / 2);
            return true;
        }

        if (touchStartedOnInput) {
            if (actionMasked == MotionEvent.ACTION_MOVE) {
                int touchSlop = Math.max(1,
                        ViewConfiguration.get(this).getScaledTouchSlop());
                if (Math.abs(event.getRawX() - posInitX) >= touchSlop
                        || Math.abs(event.getRawY() - posInitY) >= touchSlop) {
                    touchedBlockInput = null;
                    return false;
                }
                return true;
            }
            if (actionMasked == MotionEvent.ACTION_UP) {
                Ss input = touchedBlockInput;
                touchedBlockInput = null;
                touchStartedOnInput = false;
                currentTouchedView = null;
                handler.removeCallbacks(longPressed);
                if (input != null && input.isShown()) {
                    new ExtraMenuBean(this).defineMenuSelector(input);
                }
                return true;
            }
            if (actionMasked == MotionEvent.ACTION_CANCEL) {
                touchedBlockInput = null;
                touchStartedOnInput = false;
                currentTouchedView = null;
                handler.removeCallbacks(longPressed);
                return true;
            }
        }

        if (actionMasked == MotionEvent.ACTION_MOVE) {
            if (!isDragged) {
                if (Math.abs(posInitX - s - event.getX()) >= minDist
                        || Math.abs(posInitY - t - event.getY()) >= minDist) {
                    currentTouchedView = null;
                    handler.removeCallbacks(longPressed);
                }
                return false;
            }
            handler.removeCallbacks(longPressed);
            float rawX = event.getRawX();
            float rawY = event.getRawY();
            dummy.a(v, rawX - s, rawY - t, posInitX - s, posInitY - t, S, T);
            if (hitTestIconDelete(rawX, rawY)) {
                dummy.setAllow(true);
                activeIconDelete(true);
                a(false);
                d(false);
                c(false);
                return true;
            }
            activeIconDelete(false);
            if (a(rawX, rawY)) {
                dummy.setAllow(true);
                a(true);
                d(false);
                c(false);
                return true;
            }
            a(false);
            if (d(rawX, rawY)) {
                dummy.setAllow(true);
                d(true);
                c(false);
                return true;
            }
            d(false);
            if (c(rawX, rawY)) {
                dummy.setAllow(true);
                c(true);
                return true;
            }
            c(false);
            dummy.a(this.v);
            if (viewLogicEditor.hitTest(this.v[0], this.v[1])) {
                dummy.setAllow(true);
                o.c((Rs) v, this.v[0], this.v[1]);
            } else {
                dummy.setAllow(false);
                o.d();
            }
            return true;
        }

        if (actionMasked == MotionEvent.ACTION_UP) {
            currentTouchedView = null;
            handler.removeCallbacks(longPressed);
            if (!isDragged) {
                if (v instanceof Rs block && block.getBlockType() == 0) {
                    showBlockActionBar(block);
                }
                return true;
            }
            m.setDragEnabled(true);
            viewLogicEditor.setScrollEnabled(true);
            O.setDragEnabled(true);
            dummy.setDummyVisibility(View.GONE);
            if (!dummy.getAllow()) {
                Rs rs2 = (Rs) v;
                if (rs2.getBlockType() == 0) {
                    o.a(rs2, 0);
                    if (w != null) {
                        if (x == 0) {
                            w.ha = (Integer) v.getTag();
                        }
                        if (x == 2) {
                            w.ia = (Integer) v.getTag();
                        }
                        if (x == 3) {
                            w.ja = (Integer) v.getTag();
                        }
                        if (x == 5) {
                            w.a((Ts) w.V.get(y), rs2);
                        }
                        rs2.E = w;
                        w.p().k();
                    } else {
                        rs2.p().k();
                    }
                }
                q();
            } else if (logicTopMenu.isDeleteActive) {
                Rs rs5 = (Rs) v;
                if (rs5.getBlockType() == 2) {
                    g(true);
                    n(rs5.T);
                } else {
                    activeIconDelete(false);
                    int id;
                    try {
                        id = Integer.parseInt(rs5.getBean().id);
                    } catch (NumberFormatException e) {
                        id = -1;
                    }
                    BlockBean blockBean2;
                    if (w != null && id != -1) {
                        BlockBean clone = w.getBean().clone();
                        if (x == 0) {
                            clone.nextBlock = id;
                        } else if (x == 2) {
                            clone.subStack1 = id;
                        } else if (x == 3) {
                            clone.subStack2 = id;
                        } else if (x == 5) {
                            clone.parameters.set(y, "@" + id);
                        }
                        blockBean2 = clone;
                    } else {
                        blockBean2 = null;
                    }
                    ArrayList<BlockBean> arrayList = new ArrayList<>();
                    for (Rs allChild : rs5.getAllChildren()) {
                        arrayList.add(allChild.getBean().clone());
                    }
                    b(rs5);
                    BlockBean blockBean3 = null;
                    if (w != null) {
                        blockBean3 = w.getBean().clone();
                    }
                    int[] oLocationOnScreen = new int[2];
                    o.getLocationOnScreen(oLocationOnScreen);
                    bC.d(scId).b(s(), arrayList, ((int) s) - oLocationOnScreen[0],
                            ((int) t) - oLocationOnScreen[1], blockBean2, blockBean3);
                    C();
                }
            } else if (logicTopMenu.isFavoriteActive) {
                d(false);
                Rs rs7 = (Rs) v;
                o.a(rs7, 0);
                if (w != null) {
                    if (x == 0) {
                        w.ha = (Integer) v.getTag();
                    }
                    if (x == 2) {
                        w.ia = (Integer) v.getTag();
                    }
                    if (x == 3) {
                        w.ja = (Integer) v.getTag();
                    }
                    if (x == 5) {
                        w.a((Ts) w.V.get(y), rs7);
                    }
                    rs7.E = w;
                    w.p().k();
                } else {
                    rs7.p().k();
                }
                c(rs7);
            } else if (logicTopMenu.isDetailActive) {
                c(false);
                if (v instanceof Us) {
                    o(((Us) v).T);
                }
            } else if (logicTopMenu.isCopyActive) {
                a(false);
                Rs rs10 = (Rs) v;
                o.a(rs10, 0);
                if (w != null) {
                    if (x == 0) {
                        w.ha = (Integer) v.getTag();
                    }
                    if (x == 2) {
                        w.ia = (Integer) v.getTag();
                    }
                    if (x == 3) {
                        w.ja = (Integer) v.getTag();
                    }
                    if (x == 5) {
                        w.a((Ts) w.V.get(y), rs10);
                    }
                    rs10.E = w;
                    w.p().k();
                }
                ArrayList<BlockBean> arrayList2 = new ArrayList<>();
                for (Rs rs : rs10.getAllChildren()) {
                    BlockBean clone2 = rs.getBean().clone();
                    int id;
                    try {
                        id = Integer.parseInt(clone2.id);
                    } catch (NumberFormatException e) {
                        id = -1;
                    }
                    if (id != -1) {
                        clone2.id = String.valueOf(id + 99000000);
                        if (clone2.nextBlock > 0) {
                            clone2.nextBlock = clone2.nextBlock + 99000000;
                        }
                        if (clone2.subStack1 > 0) {
                            clone2.subStack1 = clone2.subStack1 + 99000000;
                        }
                        if (clone2.subStack2 > 0) {
                            clone2.subStack2 = clone2.subStack2 + 99000000;
                        }
                        for (int i = 0; i < clone2.parameters.size(); i++) {
                            String parameter = clone2.parameters.get(i);
                            if (parameter != null && !parameter.isEmpty() && parameter.charAt(0) == '@') {
                                clone2.parameters.set(i, "@" + (Integer.parseInt(parameter.substring(1)) + 99000000));
                            }
                        }
                        arrayList2.add(clone2);
                    }
                }
                int[] nLocationOnScreen = new int[2];
                viewLogicEditor.getLocationOnScreen(nLocationOnScreen);
                int width = nLocationOnScreen[0] + (viewLogicEditor.getWidth() / 2);
                int a2 = nLocationOnScreen[1] + ((int) wB.a(getContext(), 4.0f));
                ArrayList<BlockBean> a3 = a(arrayList2, width, a2, true);
                int[] oLocationOnScreen = new int[2];
                o.getLocationOnScreen(oLocationOnScreen);
                bC.d(scId).a(s(), a3, width - oLocationOnScreen[0], a2 - oLocationOnScreen[1], null, null);
                C();
            } else if (v instanceof Rs rs13) {
                dummy.a(this.v);
                if (rs13.getBlockType() == 1) {
                    int addTargetId = o.getAddTargetId();
                    BlockBean clone3 = addTargetId >= 0 ? o.a(addTargetId).getBean().clone() : null;
                    Rs a4 = a(rs13, this.v[0], this.v[1], false);
                    BlockBean blockBean3 = null;
                    if (addTargetId >= 0) {
                        blockBean3 = o.a(addTargetId).getBean().clone();
                    }
                    int[] locationOnScreen = new int[2];
                    o.getLocationOnScreen(locationOnScreen);
                    bC.d(scId).a(s(), a4.getBean().clone(), this.v[0] - locationOnScreen[0],
                            this.v[1] - locationOnScreen[1], clone3, blockBean3);
                    if (clone3 != null) {
                        clone3.print();
                    }
                    if (blockBean3 != null) {
                        blockBean3.print();
                    }
                } else if (rs13.getBlockType() == 2) {
                    int addTargetId2 = o.getAddTargetId();
                    BlockBean clone5 = addTargetId2 >= 0 ? o.a(addTargetId2).getBean().clone() : null;
                    ArrayList<BlockBean> data = ((Us) v).getData();
                    ArrayList<BlockBean> a5 = a(data, this.v[0], this.v[1], true);
                    if (!a5.isEmpty()) {
                        Rs a6 = o.a(a5.get(0).id);
                        a(a6, this.v[0], this.v[1], true);
                        BlockBean blockBean3 = null;
                        if (addTargetId2 >= 0) {
                            blockBean3 = o.a(addTargetId2).getBean().clone();
                        }
                        int[] locationOnScreen = new int[2];
                        o.getLocationOnScreen(locationOnScreen);
                        bC.d(scId).a(s(), a5, this.v[0] - locationOnScreen[0],
                                this.v[1] - locationOnScreen[1], clone5, blockBean3);
                    }
                    o.c();
                } else {
                    o.a(rs13, 0);
                    int id = Integer.parseInt(rs13.getBean().id);
                    BlockBean blockBean;
                    if (w != null) {
                        blockBean = w.getBean().clone();
                        if (x == 0) {
                            blockBean.nextBlock = id;
                        } else if (x == 2) {
                            blockBean.subStack1 = id;
                        } else if (x == 3) {
                            blockBean.subStack2 = id;
                        } else if (x == 5) {
                            blockBean.parameters.set(y, "@" + id);
                        }
                    } else {
                        blockBean = null;
                    }
                    Rs a7 = o.a(o.getAddTargetId());
                    BlockBean clone6 = a7 != null ? a7.getBean().clone() : null;
                    ArrayList<BlockBean> arrayList3 = new ArrayList<>();
                    ArrayList<Rs> allChildren3 = rs13.getAllChildren();
                    for (Rs rs : allChildren3) {
                        arrayList3.add(rs.getBean().clone());
                    }
                    a(rs13, this.v[0], this.v[1], true);
                    ArrayList<BlockBean> arrayList4 = new ArrayList<>();
                    for (Rs rs : allChildren3) {
                        arrayList4.add(rs.getBean().clone());
                    }
                    BlockBean clone7 = w != null ? w.getBean().clone() : null;
                    BlockBean blockBean3 = null;
                    if (a7 != null) {
                        blockBean3 = a7.getBean().clone();
                    }
                    if (blockBean == null || clone7 == null || !blockBean.isEqual(clone7)) {
                        int[] locationOnScreen = new int[2];
                        o.getLocationOnScreen(locationOnScreen);
                        int x = locationOnScreen[0];
                        int y = locationOnScreen[1];
                        bC.d(scId).a(s(), arrayList3, arrayList4,
                                ((int) s) - x, ((int) t) - y,
                                this.v[0] - x, this.v[1] - y,
                                blockBean, clone7, clone6, blockBean3);
                    }
                    o.c();
                }
                C();
                o.c();
            }
            dummy.setAllow(false);
            h(false);
            isDragged = false;
            return true;
        }

        if (actionMasked == MotionEvent.ACTION_CANCEL) {
            handler.removeCallbacks(longPressed);
            currentTouchedView = null;
            touchedBlockInput = null;
            touchStartedOnInput = false;
            if (isDragged) {
                m.setDragEnabled(true);
                viewLogicEditor.setScrollEnabled(true);
                O.setDragEnabled(true);
                dummy.setDummyVisibility(View.GONE);
                activeIconDelete(false);
                a(false);
                d(false);
                c(false);
                h(false);
            }
            isDragged = false;
            return true;
        }
        if (actionMasked == MotionEvent.ACTION_SCROLL) {
            handler.removeCallbacks(longPressed);
            currentTouchedView = null;
            isDragged = false;
            return false;
        }
        return true;
    }

    public boolean p() {
        return true;
    }

    public void q() {
    }

    private void r() {
        if (currentTouchedView != null) {
            m.setDragEnabled(false);
            viewLogicEditor.setScrollEnabled(false);
            O.setDragEnabled(false);
            if (ia) {
                g(false);
            }

            if (G) {
                vibrator.vibrate(100L);
            }

            isDragged = true;
            if (((Rs) currentTouchedView).getBlockType() == 0) {
                a((Rs) currentTouchedView);
                f(true);
                h(true);
                dummy.a((Rs) currentTouchedView);
                o.a((Rs) currentTouchedView, 8);
                o.c((Rs) currentTouchedView);
                PluginManager.publishEvent(new NeoEvent.BlockDisconnected(scId, ((Rs) currentTouchedView).getBean().id));
                o.a((Rs) currentTouchedView);
            } else if (((Rs) currentTouchedView).getBlockType() == 2) {
                f(false);
                h(true);
                dummy.a((Rs) currentTouchedView);
                o.a((Rs) currentTouchedView, ((Us) currentTouchedView).getData());
            } else {
                dummy.a((Rs) currentTouchedView);
                o.a((Rs) currentTouchedView);
            }

            float a = posInitX - s;
            float b = posInitY - t;
            dummy.a(currentTouchedView, a, b, a, b, S, T);
            dummy.a(v);
            if (viewLogicEditor.hitTest(v[0], v[1])) {
                dummy.setAllow(true);
                o.c((Rs) currentTouchedView, v[0], v[1]);
            } else {
                dummy.setAllow(false);
                o.d();
            }
        }
    }

    public final String s() {
        return bC.a(M.getJavaName(), id, eventName);
    }

    public void showSourceCode() {
        yq yq = new yq(this, scId);
        yq.a(jC.c(scId), jC.b(scId), jC.a(scId));
        String code = new Fx(M.getActivityName(), "preview", yq.N, o.getBlocks(), isViewBindingEnabled).a();
        var intent = new Intent(this, CodeViewerActivity.class);
        intent.putExtra("code", code);
        intent.putExtra("sc_id", scId);
        intent.putExtra("scheme", CodeViewerActivity.SCHEME_JAVA);
        startActivity(intent);
    }

            private void showCodeToBlocksDialog() {
        int dp24 = (int) (24 * getResources().getDisplayMetrics().density);
        int dp16 = (int) (16 * getResources().getDisplayMetrics().density);
        int dp8 = (int) (8 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp24, dp24, dp24, dp8);

        TextView title = new TextView(this);
        title.setText("Code to Blocks");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Paste your Java snippet below. Supported syntax (loops, variables, APIs) will become blocks. Unrecognized code is wrapped safely.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.setMargins(0, dp8, 0, dp16);
        subtitle.setLayoutParams(subParams);
        root.addView(subtitle);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardElevation(0);
        card.setRadius(dp8);
        card.setStrokeWidth((int)(1 * getResources().getDisplayMetrics().density));
        card.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceVariant));

        EditText editText = new EditText(this);
        editText.setHint("public void myLogic() {\n    // Code here\n}");
        editText.setBackground(null); // Transparent background
        editText.setPadding(dp16, dp16, dp16, dp16);
        editText.setInputType(InputType.TYPE_CLASS_TEXT 
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE 
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setMinLines(8);
        editText.setMaxLines(18);
        editText.setGravity(Gravity.TOP | Gravity.START);
        editText.setVerticalScrollBarEnabled(true);
        editText.setTypeface(Typeface.MONOSPACE);
        editText.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        editText.setHintTextColor(ThemeUtils.getColor(this, R.attr.colorOutline));

        card.addView(editText, new ViewGroup.LayoutParams(-1, -2));
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));

        new MaterialAlertDialogBuilder(this)
                .setView(root)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Convert", (dialog, which) -> {
                    String code = editText.getText() == null ? "" : editText.getText().toString().trim();
                    
                    if (code.isEmpty()) {
                        SketchwareUtil.toastError("Oops! You forgot to paste the code.");
                        return;
                    }

                    List<NeoBlockConverter> pluginConverters =
                            PluginManager.getAllBlockConverters();

                    if (pluginConverters.isEmpty()) {
                        runConversion(code, () -> BlocksConverter.convert(code));
                        return;
                    }

                    String[] options = new String[pluginConverters.size() + 1];
                    options[0] = "Built-in converter";
                    for (int i = 0; i < pluginConverters.size(); i++) {
                        options[i + 1] = pluginConverters.get(i).getConverterName();
                    }

                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Choose Converter")
                            .setItems(options, (d3, chosen) -> {
                                if (chosen == 0) {
                                    runConversion(code, () -> BlocksConverter.convert(code));
                                } else {
                                    NeoBlockConverter converter = pluginConverters.get(chosen - 1);
                                    runConversion(code, () -> converter.convertJavaToBlocks(code));
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .show();
    }


    private void showGenerateWithAiDialog() {
        int dp24 = (int) (24 * getResources().getDisplayMetrics().density);
        int dp16 = (int) (16 * getResources().getDisplayMetrics().density);
        int dp8 = (int) (8 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp24, dp24, dp24, dp8);

        TextView title = new TextView(this);
        title.setText("Generate with AI");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Describe what this \"" + eventName + "\" event should do. AI will write the logic and convert it to blocks.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.setMargins(0, dp8, 0, dp16);
        subtitle.setLayoutParams(subParams);
        root.addView(subtitle);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardElevation(0);
        card.setRadius(dp8);
        card.setStrokeWidth((int) (1 * getResources().getDisplayMetrics().density));
        card.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceVariant));

        EditText editText = new EditText(this);
        editText.setHint("e.g. show a toast saying Hello when this runs");
        editText.setBackground(null);
        editText.setPadding(dp16, dp16, dp16, dp16);
        editText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setMinLines(4);
        editText.setMaxLines(10);
        editText.setGravity(Gravity.TOP | Gravity.START);
        editText.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        editText.setHintTextColor(ThemeUtils.getColor(this, R.attr.colorOutline));

        card.addView(editText, new ViewGroup.LayoutParams(-1, -2));
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));

        new MaterialAlertDialogBuilder(this)
                .setView(root)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Generate", (dialog, which) -> {
                    String prompt = editText.getText() == null ? "" : editText.getText().toString().trim();
                    if (prompt.isEmpty()) {
                        SketchwareUtil.toastError("Describe what this event should do first.");
                        return;
                    }
                    runAiLogicGeneration(prompt);
                })
                .show();
    }

    private void runAiLogicGeneration(String prompt) {
        AlertDialog loadingDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Generating logic")
                .setMessage("Asking AI to write the code...")
                .setCancelable(false)
                .show();

        String viewContext = buildViewContext();
        String existingCode = buildExistingEventCode();

        LogicGenTask.generate(this, M.getActivityName(), eventName, viewContext, existingCode, prompt, new AiResponseCallback() {
            @Override
            public void onSuccess(String generatedCode) {
                loadingDialog.dismiss();
                runConversion(generatedCode, () -> BlocksConverter.convert(generatedCode));
            }

            @Override
            public void onFailure(String errorMessage) {
                loadingDialog.dismiss();
                SketchwareUtil.toastError("AI generation failed: " + errorMessage);
            }
        });
    }

    private String buildViewContext() {
        StringBuilder sb = new StringBuilder();
        ArrayList<ViewBean> views = jC.a(scId).d(M.getXmlName());
        if (views == null) return "";
        for (ViewBean view : views) {
            if (view.id == null || view.id.isEmpty()) continue;
            sb.append(view.id)
                    .append(": ")
                    .append(ViewBean.getViewTypeName(view.type))
                    .append("\n");
        }
        return sb.toString();
    }

    private String buildExistingEventCode() {
        try {
            yq yqExporter = new yq(this, scId);
            yqExporter.a(jC.c(scId), jC.b(scId), jC.a(scId));
            String code = new Fx(M.getActivityName(), "preview", yqExporter.N, o.getBlocks(), isViewBindingEnabled).a();
            return code == null ? "" : code.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void runConversion(String code, java.util.function.Supplier<BlocksConverter.ConversionResult> converterCall) {
        BlocksConverter.ConversionResult result;
        try {
            result = converterCall.get();
        } catch (Throwable t) {
            SketchwareUtil.toastError("Converter failed: " + t.getMessage());
            return;
        }

        if (result == null || result.error != null) {
            SketchwareUtil.toastError("Couldn't convert the code: " + (result == null ? "no result" : result.error));
            return;
        }

        int total    = result.blocks.size();
        int rec      = result.recognizedCount;
        int fallback = result.fallbackCount;

        String blockWord = total == 1 ? "block" : "blocks";
        String msg = "Here's what we found:\n\n"
                + "✅  " + rec + " native " + (rec == 1 ? "block" : "blocks") + "\n"
                + "⚠️  " + fallback + " \"Add Source Directly\" " + (fallback == 1 ? "block" : "blocks") + "\n\n"
                + "Ready to add " + total + " " + blockWord + " to your project?";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Ready to Insert?")
                .setMessage(msg)
                .setNegativeButton("Wait, go back", null)
                .setPositiveButton("Yes, insert them", (d2, w2) -> insertConvertedBlocks(result.blocks))
                .show();
    }

    private void insertConvertedBlocks(ArrayList<BlockBean> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            SketchwareUtil.toastError("No blocks to insert.");
            return;
        }
        int[] oLoc = new int[2];
        o.getLocationOnScreen(oLoc);
        int insertX = oLoc[0] + SketchwareUtil.dpToPx(16);
        int insertY = oLoc[1] + SketchwareUtil.dpToPx(80);

        ArrayList<BlockBean> inserted =
                a(blocks, insertX, insertY, true);

        bC.d(scId).a(s(), inserted,
                insertX - oLoc[0], insertY - oLoc[1], null, null);

        C();

        SketchwareUtil.toast(
                inserted.size() + " block" + (inserted.size() != 1 ? "s" : "") + " inserted.");
    }
    
    private void exportBlockPatternForAI() {
    try {
        ArrayList<BlockBean> currentBlocks = o.getBlocks();
        if (currentBlocks == null || currentBlocks.isEmpty()) {
            SketchwareUtil.toast("No blocks to export!");
            return;
        }

        yq logicHolder = new yq(this, scId);
        logicHolder.a(jC.c(scId), jC.b(scId), jC.a(scId));
        String generatedJava = new Fx(M.getActivityName(), "preview", logicHolder.N, currentBlocks, isViewBindingEnabled).a();

        String blockJson = new GsonBuilder().setPrettyPrinting().create().toJson(currentBlocks);

        String finalOutput = "=== GENERATED JAVA ===\n" 
                + generatedJava.trim() 
                + "\n\n=== RAW BLOCK BEANS (JSON PATTERN) ===\n" 
                + blockJson;

        Helper.copyToClipboard(finalOutput);
        SketchwareUtil.toast("Pattern copied to clipboard! Paste it to AI.");

    } catch (Exception e) {
        SketchwareUtil.toastError("Export failed: " + e.getMessage());
    }
}

    public void t() {
        fa = ObjectAnimator.ofFloat(O, View.TRANSLATION_X, 0.0f);
        fa.setDuration(500L);
        fa.setInterpolator(new DecelerateInterpolator());
        ga = ObjectAnimator.ofFloat(O, View.TRANSLATION_X, O.getHeight());
        ga.setDuration(300L);
        ga.setInterpolator(new DecelerateInterpolator());
        ha = true;
    }

    public void x() {
        ba = ObjectAnimator.ofFloat(logicTopMenu, View.TRANSLATION_Y, 0.0f);
        ba.setDuration(500L);
        ba.setInterpolator(new DecelerateInterpolator());
        ca = ObjectAnimator.ofFloat(logicTopMenu, View.TRANSLATION_Y, logicTopMenu.getHeight() * (-1));
        ca.setDuration(300L);
        ca.setInterpolator(new DecelerateInterpolator());
        da = true;
    }

    public void z() {
        O.a();
        for (BlockCollectionBean next : Mp.h().f()) {
            O.a(next.name, next.blocks).setOnTouchListener(this);
        }
    }

    private static class ProjectSaver extends MA {
        private final WeakReference<LogicEditorActivity> activity;
        private final String projectId;
        private final String javaName;
        private final String eventKey;
        private final ArrayList<BlockBean> blockSnapshot;

        public ProjectSaver(LogicEditorActivity logicEditorActivity) {
            super(logicEditorActivity);
            activity = new WeakReference<>(logicEditorActivity);
            projectId = logicEditorActivity.scId;
            javaName = logicEditorActivity.M.getJavaName();
            eventKey = logicEditorActivity.id + "_" + logicEditorActivity.eventName;
            ArrayList<BlockBean> currentBlocks = logicEditorActivity.o == null
                    ? new ArrayList<>() : logicEditorActivity.o.getBlocks();
            blockSnapshot = logicEditorActivity.cloneBlockBeans(currentBlocks);
            logicEditorActivity.addTask(this);
        }

        @Override
        public void a() {
            LogicEditorActivity target = activity.get();
            if (target == null) return;
            target.onProjectSaveCompleted();
            target.h();
            target.finish();
        }

        @Override
        public void a(String str) {
            LogicEditorActivity target = activity.get();
            if (target == null) return;
            Toast.makeText(target, R.string.common_error_failed_to_save, Toast.LENGTH_SHORT).show();
            target.h();
        }

        @Override
        public void b() {
            publishProgress("Now saving..");
            jC.a(projectId).a(javaName, eventKey, blockSnapshot);
        }
    }

    private void onProjectSaveCompleted() {
        loadedEventBlockBeans = cloneBlockBeans(collectCurrentBlockBeans());
        loadedEventBlockCount = countBlockBeans(loadedEventBlockBeans);
        editorChanged = false;
        updateEditorStatus();
    }

    public static class LoadEventBlocksTask {
        private final WeakReference<LogicEditorActivity> activityRef;
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public LoadEventBlocksTask(LogicEditorActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        public void execute() {
            LogicEditorActivity activity = getActivity();
            if (activity == null) {
                executorService.shutdownNow();
                return;
            }
            activity.k();
            executorService.execute(() -> {
                ArrayList<BlockBean> eventBlocks = null;
                RuntimeException loadError = null;
                try {
                    LogicEditorActivity target = getActivity();
                    if (target != null) {
                        eventBlocks = jC.a(target.scId).a(target.M.getJavaName(), target.id + "_" + target.eventName);
                    }
                } catch (RuntimeException error) {
                    loadError = error;
                }

                ArrayList<BlockBean> result = eventBlocks;
                RuntimeException error = loadError;
                LogicEditorActivity target = getActivity();
                if (target != null) {
                    target.runOnUiThread(() -> {
                        LogicEditorActivity current = getActivity();
                        if (current == null) return;
                        if (error != null) {
                            current.crashlytics.recordException(error);
                            current.editorBlocksReady = true;
                            current.h();
                            current.updateEditorStatus();
                            SketchwareUtil.toastError("Could not load blocks for this event");
                        } else {
                            current.attachEventBlocks(result);
                        }
                    });
                }
                executorService.shutdown();
            });
        }

        private LogicEditorActivity getActivity() {
            return activityRef.get();
        }
    }

    public class ImagePickerAdapter extends RecyclerView.Adapter<ImagePickerAdapter.ViewHolder> {

        private final ArrayList<String> images;
        private final OnImageSelectedListener listener;
        private final ArrayList<String> filteredImages;
        private String selectedImage;

        public ImagePickerAdapter(ArrayList<String> images, String selectedImage, OnImageSelectedListener listener) {
            this.images = images;
            this.selectedImage = selectedImage;
            this.listener = listener;
            filteredImages = new ArrayList<>(images);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImagePickerItemBinding binding = ImagePickerItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String image = filteredImages.get(position);

            holder.binding.textView.setText(image);

            holder.binding.layoutImg.removeAllViews();
            holder.binding.layoutImg.addView(setImageViewContent(image));

            holder.binding.radioButton.setChecked(image.equals(selectedImage));

            holder.binding.transparentOverlay.setOnClickListener(v -> {
                if (!image.equals(selectedImage)) {
                    selectedImage = image;
                    listener.onImageSelected(image);
                    notifyDataSetChanged();
                }
            });
        }

        @Override
        public int getItemCount() {
            return filteredImages.size();
        }

        public void filter(String query) {
            filteredImages.clear();
            if (query.isEmpty()) {
                filteredImages.addAll(images);
            } else {
                for (String image : images) {
                    if (image.toLowerCase().contains(query)) {
                        filteredImages.add(image);
                    }
                }
            }
            notifyDataSetChanged();
        }

        public interface OnImageSelectedListener {
            void onImageSelected(String image);
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            public final ImagePickerItemBinding binding;

            public ViewHolder(@NonNull ImagePickerItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
