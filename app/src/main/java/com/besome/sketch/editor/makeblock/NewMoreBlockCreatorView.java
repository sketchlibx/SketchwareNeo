package com.besome.sketch.editor.makeblock;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.besome.sketch.beans.ProjectFileBean;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import a.a.a.Rs;
import a.a.a.ZB;
import a.a.a.bB;
import a.a.a.jC;
import a.a.a.mB;
import a.a.a.uq;
import mod.hey.studios.moreblock.MoreBlockDefaultValues;
import mod.hey.studios.moreblock.MoreBlockDefaultsStore;
import mod.hey.studios.moreblock.MoreblockValidator;
import mod.hey.studios.moreblock.ReturnMoreblockManager;
import mod.jbk.util.BlockUtil;
import mod.jbk.util.LogUtil;
import pro.sketchware.R;
import pro.sketchware.databinding.DialogNewMoreblockParameterBinding;
import pro.sketchware.databinding.DialogNewMoreblockVariableBinding;
import pro.sketchware.databinding.ItemNewMoreblockEntryBinding;
import pro.sketchware.databinding.ItemNewMoreblockTypeCardBinding;
import pro.sketchware.databinding.ItemNewMoreblockTypeMoreBinding;
import pro.sketchware.databinding.VarTypeItemBinding;
import pro.sketchware.databinding.VarTypeSelectorDialogBinding;
import pro.sketchware.databinding.ViewNewMoreblockCreatorContentBinding;
import pro.sketchware.lib.base.BaseTextWatcher;
import pro.sketchware.utility.SketchwareUtil;

/**
 * The Material 3 Moreblock creator UI (preview, name, return type, variables, custom parameters and
 * the two add/edit dialogs). It is a plain view so the very same UI is used by
 * {@link NewMoreBlockCreatorActivity} (Logic Editor) and embedded in the Moreblock tab of
 * {@code AddEventActivity}.
 * <p>
 * Host contract (mirrors the legacy {@link MoreBlockBuilderView}):
 * {@link #init}, {@link #isEmpty()} (legacy {@code a()}), {@link #validate()} (legacy {@code b()}) and
 * {@link #getBlockInformation()} which returns the {@code block_name} / {@code block_spec} pair.
 * Persistence stays with the host through {@code jC}.
 */
public class NewMoreBlockCreatorView extends LinearLayout {
    private static final String STATE_VARIABLE_COUNT = "nmb_variable_count";
    private static final String STATE_PARAMETER_COUNT = "nmb_parameter_count";
    private static final String STATE_VARIABLE_PREFIX = "nmb_variable_";
    private static final String STATE_PARAMETER_PREFIX = "nmb_parameter_";
    private static final String STATE_BLOCK_NAME = "nmb_block_name";
    private static final String STATE_CHECKED_CHIP = "nmb_checked_chip";

    /** Typing in the name field only re-renders the preview after this quiet period. */
    private static final long PREVIEW_DEBOUNCE_MS = 60;

    private final ViewNewMoreblockCreatorContentBinding binding;
    private final ArrayList<MoreBlockEntry> variables = new ArrayList<>();
    private final ArrayList<MoreBlockEntry> parameters = new ArrayList<>();
    private final List<Dialog> openDialogs = new ArrayList<>();
    private final EntryAdapter variableAdapter;
    private final EntryAdapter parameterAdapter;
    private final Runnable previewRunnable = this::renderPreviewNow;

    private MoreblockValidator blockNameValidator;
    private Rs previewBlock;
    private String lastPreviewKey;
    private List<MoreBlockSpecAdapter.Token> previewTokens = new ArrayList<>();
    @Nullable
    private View snackbarAnchor;

    public NewMoreBlockCreatorView(@NonNull Context context) {
        this(context, null);
    }

    public NewMoreBlockCreatorView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        binding = ViewNewMoreblockCreatorContentBinding.inflate(LayoutInflater.from(context), this);

        variableAdapter = new EntryAdapter(variables, false);
        parameterAdapter = new EntryAdapter(parameters, true);
        setupList(binding.listVariables, variableAdapter);
        setupList(binding.listParameters, parameterAdapter);

        binding.chipsType.setOnCheckedStateChangeListener((group, ids) -> renderPreviewNow());
        binding.btnAddVariable.setOnClickListener(v -> {
            if (!mB.a()) showVariableDialog(null);
        });
        binding.btnAddParameter.setOnClickListener(v -> {
            if (!mB.a()) showParameterDialog(null);
        });
        binding.btnParametersHelp.setOnClickListener(v -> {
            if (!mB.a()) {
                new MaterialAlertDialogBuilder(getContext())
                        .setTitle(R.string.nmb_parameters_help_title)
                        .setMessage(R.string.nmb_parameters_help_message)
                        .setPositiveButton(R.string.nmb_ok, null)
                        .show();
            }
        });
        updateListVisibility();
    }

    // ---------------------------------------------------------------------------------------------
    // Host API
    // ---------------------------------------------------------------------------------------------

    /** Must be called once before the view is used. */
    public void init(@NonNull String scId, @NonNull ProjectFileBean project) {
        // Same validator + reserved lists as the legacy creator (keywords, event names, existing functions).
        blockNameValidator = new MoreblockValidator(getContext(), binding.tilBlockName, uq.b, uq.a(), jC.a(scId).a(project));
        binding.edBlockName.setPrivateImeOptions("defaultInputmode=english;");
        // Added after the validator's own watcher, so the validator state is current when this runs.
        binding.edBlockName.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                schedulePreview();
            }
        });
        renderPreviewNow();
    }

    public void setSnackbarAnchor(@Nullable View anchor) {
        snackbarAnchor = anchor;
    }

    /** True when nothing has been entered yet (legacy {@code MoreBlockBuilderView.a()}). */
    public boolean isEmpty() {
        return text(binding.edBlockName).isEmpty() && variables.isEmpty() && parameters.isEmpty();
    }

    /**
     * Validates the whole block and shows errors (legacy {@code MoreBlockBuilderView.b()}).
     *
     * @return true if {@link #getBlockInformation()} can be called.
     */
    public boolean validate() {
        if (blockNameValidator == null) return false;

        String rawName = text(binding.edBlockName);
        // Re-run the legacy validator so its error is shown and its state is current.
        blockNameValidator.onTextChanged(rawName, 0, 0, rawName.length());
        if (rawName.trim().isEmpty() || !blockNameValidator.b()) {
            bB.b(getContext(), getContext().getString(R.string.logic_editor_message_name_requied), Toast.LENGTH_SHORT).show();
            binding.edBlockName.requestFocus();
            binding.tilBlockName.requestRectangleOnScreen(new Rect(0, 0, binding.tilBlockName.getWidth(), binding.tilBlockName.getHeight()), true);
            return false;
        }
        if (!renderPreviewNow() || previewBlock == null) {
            bB.b(getContext(), getContext().getString(R.string.nmb_preview_failed), Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * Same rules as {@code MoreBlockBuilderView.getBlockInformation()}: (block_name, block_spec). Call after {@link #validate()}.
     * The spec is built from the real type codes (Float / Int stay {@code m.varFloat} / {@code m.varIntNum});
     * the preview uses a display-only variant.
     */
    @NonNull
    public Pair<String, String> getBlockInformation() {
        String name = text(binding.edBlockName).trim();
        String type = currentType();
        String spec = MoreBlockSpecAdapter.buildSpec(name, MoreBlockSpecAdapter.flatten(variables, parameters));
        return new Pair<>(
                ReturnMoreblockManager.injectMbType(name, name, type),
                ReturnMoreblockManager.injectMbType(spec, name, type));
    }

    /**
     * Default values of this block's parameters: Java variable name to a compile-valid Java expression.
     * Only parameters whose declared Java type can have a default are included.
     */
    @NonNull
    public Map<String, String> getDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();
        for (List<MoreBlockEntry> list : List.of(variables, parameters)) {
            for (MoreBlockEntry entry : list) {
                MoreBlockDefaultValues.Kind kind = MoreBlockDefaultValues.kindForCode(entry.typeCode);
                if (kind == null || entry.defaultValue.isEmpty()) continue;
                String expression = MoreBlockDefaultValues.normalize(kind, entry.defaultValue);
                if (expression != null) defaults.put(entry.name, expression);
            }
        }
        return defaults;
    }

    /** {@link #getDefaults()} encoded for an Intent extra ({@code block_defaults}). */
    @NonNull
    public String getEncodedDefaults() {
        return MoreBlockDefaultsStore.encode(getDefaults());
    }

    public void saveState(@NonNull Bundle out) {
        out.putInt(STATE_VARIABLE_COUNT, variables.size());
        for (int i = 0; i < variables.size(); i++) {
            out.putBundle(STATE_VARIABLE_PREFIX + i, variables.get(i).toBundle());
        }
        out.putInt(STATE_PARAMETER_COUNT, parameters.size());
        for (int i = 0; i < parameters.size(); i++) {
            out.putBundle(STATE_PARAMETER_PREFIX + i, parameters.get(i).toBundle());
        }
        out.putString(STATE_BLOCK_NAME, text(binding.edBlockName));
        out.putInt(STATE_CHECKED_CHIP, binding.chipsType.getCheckedChipId());
    }

    public void restoreState(@Nullable Bundle in) {
        if (in == null || !in.containsKey(STATE_VARIABLE_COUNT)) return;

        variables.clear();
        parameters.clear();
        int variableCount = in.getInt(STATE_VARIABLE_COUNT, 0);
        for (int i = 0; i < variableCount; i++) {
            MoreBlockEntry entry = MoreBlockEntry.fromBundle(in.getBundle(STATE_VARIABLE_PREFIX + i));
            if (entry != null) variables.add(entry);
        }
        int parameterCount = in.getInt(STATE_PARAMETER_COUNT, 0);
        for (int i = 0; i < parameterCount; i++) {
            MoreBlockEntry entry = MoreBlockEntry.fromBundle(in.getBundle(STATE_PARAMETER_PREFIX + i));
            if (entry != null) parameters.add(entry);
        }

        String name = in.getString(STATE_BLOCK_NAME);
        if (name != null && !name.equals(text(binding.edBlockName))) {
            binding.edBlockName.setText(name);
        }
        int chipId = in.getInt(STATE_CHECKED_CHIP, View.NO_ID);
        if (chipId != View.NO_ID && binding.chipsType.findViewById(chipId) != null) {
            binding.chipsType.check(chipId);
        }

        variableAdapter.notifyDataSetChanged();
        parameterAdapter.notifyDataSetChanged();
        updateListVisibility();
        renderPreviewNow();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(previewRunnable);
        for (Dialog dialog : new ArrayList<>(openDialogs)) {
            if (dialog.isShowing()) dialog.dismiss();
        }
        openDialogs.clear();
        super.onDetachedFromWindow();
    }

    private static String text(@Nullable TextView view) {
        if (view == null || view.getText() == null) return "";
        return view.getText().toString();
    }

    // ---------------------------------------------------------------------------------------------
    // Return type
    // ---------------------------------------------------------------------------------------------

    /**
     * Int and Float use the existing custom return type syntax {@code name[typeChar|javaType]} with the
     * Number block shape ({@code d}); everything else goes through the unchanged legacy mapping.
     */
    private String currentType() {
        int checked = binding.chipsType.getCheckedChipId();
        if (checked == R.id.chip_type_int) return "d|int";
        if (checked == R.id.chip_type_float) return "d|float";
        return ReturnMoreblockManager.getMbTypeFromChipGroup(binding.chipsType);
    }

    private String previewType(String type) {
        if (type.equals("d|int") || type.equals("d|float")) return "d";
        return ReturnMoreblockManager.getPreviewType(type);
    }

    // ---------------------------------------------------------------------------------------------
    // Preview (real Rs / BlockUtil pipeline, same as the legacy creator)
    // ---------------------------------------------------------------------------------------------

    private void schedulePreview() {
        removeCallbacks(previewRunnable);
        postDelayed(previewRunnable, PREVIEW_DEBOUNCE_MS);
    }

    /**
     * Rebuilds the preview from the current state.
     *
     * @return false if the block renderer rejected the spec (e.g. an unknown custom parameter type).
     */
    private boolean renderPreviewNow() {
        removeCallbacks(previewRunnable);
        if (blockNameValidator == null) return false;

        String rawName = text(binding.edBlockName);
        // Like the legacy creator, an invalid name is not previewed.
        String previewName = (rawName.isEmpty() || !blockNameValidator.b()) ? "" : rawName;

        List<MoreBlockSpecAdapter.Token> tokens = MoreBlockSpecAdapter.flatten(variables, parameters);
        String spec = MoreBlockSpecAdapter.buildPreviewSpec(previewName, tokens);
        String type = previewType(currentType());
        previewTokens = tokens;

        String key = type + '\n' + spec + '\n' + (previewName.isEmpty() ? '0' : '1');
        if (previewBlock != null && key.equals(lastPreviewKey)) {
            return true; // nothing visible changed, skip the expensive rebuild
        }

        binding.blockArea.removeAllViews();
        binding.removeArea.removeAllViews();
        try {
            Rs rs = new Rs(getContext(), 0, "", type, "definedFunc");
            binding.blockArea.addView(rs);
            rs.setSpec(spec);
            BlockUtil.loadPreviewBlockVariables(binding.blockArea, rs, spec);
            rs.k();
            previewBlock = rs;
            lastPreviewKey = key;
            buildRemoveIcons(rs, !previewName.isEmpty());
            return true;
        } catch (RuntimeException e) {
            LogUtil.e("NewMoreBlockCreator", "Failed to render Moreblock preview for spec: " + spec, e);
            previewBlock = null;
            lastPreviewKey = null;
            binding.blockArea.removeAllViews();
            binding.removeArea.removeAllViews();
            return false;
        }
    }

    /** One close icon above every block part, same width logic as the legacy creator. */
    private void buildRemoveIcons(Rs rs, boolean hasName) {
        int offset = hasName ? 1 : 0;
        int tint = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int padding = SketchwareUtil.dpToPx(5);

        for (int i = 0; i < rs.ka.size(); i++) {
            View view = rs.ka.get(i);

            int width;
            if (rs.la.get(i).equals("label")) {
                TextView textView = (TextView) view;
                Rect rect = new Rect();
                textView.getPaint().getTextBounds(text(textView), 0, textView.getText().length(), rect);
                width = rect.width();
            } else if (view instanceof Rs) {
                width = ((Rs) view).getWidthSum();
            } else {
                width = 0;
            }
            width += SketchwareUtil.dpToPx(4);

            ImageView removeIcon = new ImageView(getContext());
            removeIcon.setImageResource(R.drawable.ic_mtrl_close);
            removeIcon.setColorFilter(tint);
            removeIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            removeIcon.setPadding(0, padding, 0, padding);
            removeIcon.setLayoutParams(new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT));

            int tokenIndex = i - offset;
            if (tokenIndex < 0 || tokenIndex >= previewTokens.size()) {
                removeIcon.setVisibility(View.INVISIBLE);
                removeIcon.setEnabled(false);
            } else {
                MoreBlockSpecAdapter.Token token = previewTokens.get(tokenIndex);
                removeIcon.setContentDescription(getContext().getString(R.string.nmb_remove_from_block));
                removeIcon.setOnClickListener(v -> {
                    if (!mB.a()) removeToken(token);
                });
            }
            binding.removeArea.addView(removeIcon);
        }
    }

    private void removeToken(MoreBlockSpecAdapter.Token token) {
        MoreBlockEntry owner = token.owner;
        boolean parameter = owner.kind == MoreBlockEntry.KIND_PARAMETER;
        ArrayList<MoreBlockEntry> list = parameter ? parameters : variables;
        EntryAdapter adapter = parameter ? parameterAdapter : variableAdapter;
        int index = list.indexOf(owner);
        if (index < 0) return;

        if (token.isLabel) {
            owner.label = "";
            adapter.notifyItemChanged(index);
        } else {
            list.remove(index);
            adapter.notifyItemRemoved(index);
            updateListVisibility();
        }
        renderPreviewNow();
    }

    // ---------------------------------------------------------------------------------------------
    // Lists
    // ---------------------------------------------------------------------------------------------

    private void updateListVisibility() {
        binding.emptyVariables.setVisibility(variables.isEmpty() ? View.VISIBLE : View.GONE);
        binding.listVariables.setVisibility(variables.isEmpty() ? View.GONE : View.VISIBLE);
        binding.emptyParameters.setVisibility(parameters.isEmpty() ? View.VISIBLE : View.GONE);
        binding.listParameters.setVisibility(parameters.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setupList(RecyclerView recyclerView, EntryAdapter adapter) {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.setNestedScrollingEnabled(false);
        // Cross-fade "change" animations flicker when only the text of a row changed.
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false; // drag only starts from the handle
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                int fromPosition = from.getBindingAdapterPosition();
                int toPosition = to.getBindingAdapterPosition();
                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) return false;
                adapter.items.add(toPosition, adapter.items.remove(fromPosition));
                adapter.notifyItemMoved(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(rv, viewHolder);
                renderPreviewNow(); // the order changed, so the spec changes
            }
        });
        helper.attachToRecyclerView(recyclerView);
        adapter.touchHelper = helper;
    }

    private void deleteEntry(boolean parameter, int position) {
        ArrayList<MoreBlockEntry> list = parameter ? parameters : variables;
        EntryAdapter adapter = parameter ? parameterAdapter : variableAdapter;
        if (position < 0 || position >= list.size()) return;

        MoreBlockEntry removed = list.remove(position);
        adapter.notifyItemRemoved(position);
        updateListVisibility();
        renderPreviewNow();

        Snackbar snackbar = Snackbar.make(this, parameter ? R.string.nmb_parameter_removed : R.string.nmb_variable_removed, Snackbar.LENGTH_LONG)
                .setAction(R.string.nmb_undo, v -> {
                    int index = Math.min(position, list.size());
                    list.add(index, removed);
                    adapter.notifyItemInserted(index);
                    updateListVisibility();
                    renderPreviewNow();
                });
        if (snackbarAnchor != null) snackbar.setAnchorView(snackbarAnchor);
        snackbar.show();
    }

    private void onEntrySaved(boolean parameter, MoreBlockEntry entry, boolean isNew) {
        ArrayList<MoreBlockEntry> list = parameter ? parameters : variables;
        EntryAdapter adapter = parameter ? parameterAdapter : variableAdapter;
        if (isNew) {
            list.add(entry);
            adapter.notifyItemInserted(list.size() - 1);
        } else {
            int index = list.indexOf(entry);
            if (index >= 0) adapter.notifyItemChanged(index);
        }
        updateListVisibility();
        renderPreviewNow();
    }

    private final class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.Holder> {
        private final ArrayList<MoreBlockEntry> items;
        private final boolean parameterList;
        private ItemTouchHelper touchHelper;

        EntryAdapter(ArrayList<MoreBlockEntry> items, boolean parameterList) {
            this.items = items;
            this.parameterList = parameterList;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemNewMoreblockEntryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            MoreBlockEntry entry = items.get(position);
            ItemNewMoreblockEntryBinding b = holder.binding;

            String labelText = entry.label.isEmpty() ? getContext().getString(R.string.nmb_no_label) : entry.label;
            if (parameterList) {
                b.typePill.setVisibility(View.GONE);
                b.paramIcon.setVisibility(View.VISIBLE);
                b.caption.setVisibility(View.VISIBLE);
                b.title.setText(entry.typeCode);
                labelText = entry.name;
            } else {
                b.typePill.setVisibility(View.VISIBLE);
                b.paramIcon.setVisibility(View.GONE);
                b.caption.setVisibility(View.GONE);
                b.title.setText(entry.name);
                MoreBlockVariableTypes.Type type = MoreBlockVariableTypes.find(entry.typeCode);
                if (type != null) {
                    b.typeIcon.setImageResource(type.icon());
                    b.typeName.setText(MoreBlockVariableTypes.title(getContext(), type));
                } else {
                    b.typeIcon.setImageDrawable(null);
                    b.typeName.setText(entry.typeCode);
                }
            }
            b.subtitle.setText(entry.defaultValue.isEmpty()
                    ? getContext().getString(R.string.nmb_label_format, labelText)
                    : getContext().getString(R.string.nmb_label_default_format, labelText, entry.defaultValue));

            b.btnEdit.setOnClickListener(v -> {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (mB.a() || adapterPosition == RecyclerView.NO_POSITION) return;
                if (parameterList) showParameterDialog(items.get(adapterPosition));
                else showVariableDialog(items.get(adapterPosition));
            });
            b.btnDelete.setOnClickListener(v -> {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (mB.a() || adapterPosition == RecyclerView.NO_POSITION) return;
                deleteEntry(parameterList, adapterPosition);
            });
            b.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && touchHelper != null) {
                    touchHelper.startDrag(holder);
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemNewMoreblockEntryBinding binding;

            Holder(ItemNewMoreblockEntryBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Dialogs
    // ---------------------------------------------------------------------------------------------

    private void hideKeyboardFrom(@Nullable View view) {
        if (view == null) return;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showDialog(MaterialAlertDialogBuilder builder, View content, boolean showKeyboard) {
        AlertDialog dialog = builder.setView(content).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | (showKeyboard ? WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE : WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN));
        }
        dialog.setOnDismissListener(d -> openDialogs.remove(d));
        openDialogs.add(dialog);
        dialog.show();
    }

    private void closeLastDialog(View content) {
        hideKeyboardFrom(content);
        if (openDialogs.isEmpty()) return;
        openDialogs.get(openDialogs.size() - 1).dismiss();
    }

    private void styleTypeCard(View card, View check, boolean selected) {
        card.setSelected(selected);
        check.setVisibility(selected ? View.VISIBLE : View.GONE);
    }

    private GridLayout.LayoutParams gridParams(View child) {
        // Inflated with the grid as parent, so these already are GridLayout.LayoutParams (margins preserved).
        return (GridLayout.LayoutParams) child.getLayoutParams();
    }

    private void showVariableDialog(@Nullable MoreBlockEntry editing) {
        final boolean isEdit = editing != null;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        LayoutInflater inflater = LayoutInflater.from(builder.getContext());
        DialogNewMoreblockVariableBinding d = DialogNewMoreblockVariableBinding.inflate(inflater);

        if (isEdit) {
            d.tvTitle.setText(R.string.nmb_var_dialog_edit_title);
            d.tvSubtitle.setText(R.string.nmb_var_dialog_edit_subtitle);
            d.btnConfirm.setText(R.string.nmb_save);
            d.btnConfirm.setIconResource(R.drawable.ic_mtrl_check);
        }

        // Same validators as the legacy variable-name / label fields.
        ZB nameValidator = new ZB(getContext(), d.tilName, uq.b, MoreBlockSpecAdapter.reservedNames(variables, parameters, editing), new ArrayList<>());
        ZB labelValidator = new ZB(getContext(), d.tilLabel, uq.b, uq.a(), new ArrayList<>());
        d.edName.setPrivateImeOptions("defaultInputmode=english;");
        d.edLabel.setPrivateImeOptions("defaultInputmode=english;");

        final MoreBlockVariableTypes.Type[] selected = new MoreBlockVariableTypes.Type[1];
        selected[0] = isEdit ? MoreBlockVariableTypes.find(editing.typeCode) : null;
        if (selected[0] == null) selected[0] = MoreBlockVariableTypes.find("b");

        // Type grid: 3 (or 2 on very narrow screens) equal columns, vertical cards, full-width "More types" row.
        List<MoreBlockVariableTypes.Type> primary = MoreBlockVariableTypes.primary();
        List<ItemNewMoreblockTypeCardBinding> cards = new ArrayList<>();
        int columns = getResources().getConfiguration().screenWidthDp >= 340 ? 3 : 2;
        d.typeGrid.setColumnCount(columns);
        Runnable[] refresh = new Runnable[1];
        for (MoreBlockVariableTypes.Type type : primary) {
            ItemNewMoreblockTypeCardBinding card = ItemNewMoreblockTypeCardBinding.inflate(inflater, d.typeGrid, false);
            GridLayout.LayoutParams params = gridParams(card.getRoot());
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            card.icon.setImageResource(type.icon());
            card.name.setText(MoreBlockVariableTypes.title(getContext(), type));
            card.desc.setText(MoreBlockVariableTypes.shortDescription(type));
            card.getRoot().setOnClickListener(v -> {
                selected[0] = type;
                refresh[0].run();
            });
            d.typeGrid.addView(card.getRoot(), params);
            cards.add(card);
        }
        ItemNewMoreblockTypeMoreBinding moreCard = ItemNewMoreblockTypeMoreBinding.inflate(inflater, d.typeGrid, false);
        GridLayout.LayoutParams moreParams = gridParams(moreCard.getRoot());
        moreParams.width = 0;
        moreParams.columnSpec = GridLayout.spec(0, columns, 1f);
        moreCard.getRoot().setOnClickListener(v -> {
            if (mB.a()) return;
            showMoreTypesDialog(type -> {
                selected[0] = type;
                refresh[0].run();
            });
        });
        d.typeGrid.addView(moreCard.getRoot(), moreParams);

        refresh[0] = () -> {
            MoreBlockVariableTypes.Type current = selected[0];
            boolean inPrimary = false;
            for (int i = 0; i < primary.size(); i++) {
                boolean on = primary.get(i).code().equals(current.code());
                inPrimary |= on;
                styleTypeCard(cards.get(i).getRoot(), cards.get(i).check, on);
            }
            styleTypeCard(moreCard.getRoot(), moreCard.check, !inPrimary);
            if (inPrimary) {
                moreCard.icon.setImageResource(R.drawable.ic_mtrl_view_module);
                moreCard.icon.setColorFilter(MaterialColors.getColor(moreCard.icon, com.google.android.material.R.attr.colorOnSurfaceVariant));
                moreCard.name.setText(R.string.nmb_more_types);
            } else {
                moreCard.icon.clearColorFilter();
                moreCard.icon.setImageResource(current.icon());
                moreCard.name.setText(MoreBlockVariableTypes.title(getContext(), current));
            }
            moreCard.desc.setText(R.string.nmb_more_types_desc);
            d.tvTypeInfo.setText(MoreBlockVariableTypes.info(current));

            // Only types that become a Java primitive / String can have a default value.
            boolean canHaveDefault = MoreBlockDefaultValues.kindForCode(current.code()) != null;
            d.defaultHeader.setVisibility(canHaveDefault ? View.VISIBLE : View.GONE);
            d.tilDefault.setVisibility(canHaveDefault ? View.VISIBLE : View.GONE);
            d.tilDefault.setError(null);
        };
        refresh[0].run();

        // Prefill (before the mirroring watchers exist).
        if (isEdit) {
            d.edName.setText(editing.name);
            d.edLabel.setText(editing.label);
            d.edDefault.setText(editing.defaultValue);
        }

        // Label mirrors the name until the user edits it, as in the reference design.
        final boolean[] labelCustom = {isEdit && !editing.label.equals(editing.name)};
        final boolean[] syncing = {false};
        d.edName.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!labelCustom[0]) {
                    syncing[0] = true;
                    d.edLabel.setText(s.toString());
                    syncing[0] = false;
                }
            }
        });
        d.edLabel.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!syncing[0]) labelCustom[0] = !s.toString().equals(text(d.edName));
                if (s.length() == 0) {
                    // The label is optional; the validator's "minimum length" error doesn't apply to it.
                    d.tilLabel.setError(null);
                    d.tilLabel.setErrorEnabled(false);
                }
            }
        });

        d.edDefault.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                d.tilDefault.setError(null);
            }
        });

        d.btnClose.setOnClickListener(v -> closeLastDialog(d.getRoot()));
        d.btnCancel.setOnClickListener(v -> closeLastDialog(d.getRoot()));
        d.btnConfirm.setOnClickListener(v -> {
            if (mB.a()) return;
            String name = text(d.edName);
            nameValidator.onTextChanged(name, 0, 0, name.length());
            boolean valid = nameValidator.b();

            String label = text(d.edLabel);
            if (!label.isEmpty() && !label.equals(name)) {
                labelValidator.onTextChanged(label, 0, 0, label.length());
                valid &= labelValidator.b();
            }

            // Default value: validated against the declared type, kept as typed so editing round-trips.
            String defaultValue = "";
            MoreBlockDefaultValues.Kind kind = MoreBlockDefaultValues.kindForCode(selected[0].code());
            if (kind != null) {
                defaultValue = text(d.edDefault).trim();
                if (!defaultValue.isEmpty() && MoreBlockDefaultValues.normalize(kind, defaultValue) == null) {
                    d.tilDefault.setError(defaultError(kind));
                    valid = false;
                }
            }
            if (!valid) return;

            MoreBlockEntry entry = isEdit ? editing : new MoreBlockEntry(MoreBlockEntry.KIND_VARIABLE);
            entry.typeCode = selected[0].code();
            entry.name = name;
            entry.label = label;
            entry.defaultValue = defaultValue;
            closeLastDialog(d.getRoot());
            onEntrySaved(false, entry, !isEdit);
        });

        showDialog(builder, d.getRoot(), false);
    }

    private void showParameterDialog(@Nullable MoreBlockEntry editing) {
        final boolean isEdit = editing != null;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        LayoutInflater inflater = LayoutInflater.from(builder.getContext());
        DialogNewMoreblockParameterBinding d = DialogNewMoreblockParameterBinding.inflate(inflater);

        if (isEdit) {
            d.tvTitle.setText(R.string.nmb_param_dialog_edit_title);
            d.tvSubtitle.setText(R.string.nmb_param_dialog_edit_subtitle);
            d.btnConfirm.setText(R.string.nmb_save);
            d.btnConfirm.setIconResource(R.drawable.ic_mtrl_check);
        }

        // The label is the legacy pair's second element, i.e. the Java variable name in generated code.
        ZB labelValidator = new ZB(getContext(), d.tilLabel, uq.b, MoreBlockSpecAdapter.reservedNames(variables, parameters, editing), new ArrayList<>());
        d.edCode.setPrivateImeOptions("defaultInputmode=english;");
        d.edLabel.setPrivateImeOptions("defaultInputmode=english;");

        String[] typeLabels = {
                getContext().getString(R.string.nmb_param_type_none),
                getContext().getString(R.string.nmb_param_type_string),
                getContext().getString(R.string.nmb_param_type_number),
                getContext().getString(R.string.nmb_param_type_boolean),
                getContext().getString(R.string.nmb_param_type_map)
        };
        final int[] typeIndex = {isEdit ? Math.max(0, Math.min(editing.parameterTypeIndex, typeLabels.length - 1)) : 0};
        d.ddType.setSimpleItems(typeLabels);
        d.ddType.setText(typeLabels[typeIndex[0]], false);
        d.ddType.setOnItemClickListener((parent, view, position, id) -> typeIndex[0] = position);

        if (isEdit) {
            d.edCode.setText(editing.typeCode);
            d.edLabel.setText(editing.name);
            d.edDefault.setText(editing.defaultValue);
        }

        Runnable updateDefaultVisibility = () -> {
            boolean canHaveDefault = MoreBlockDefaultValues.kindForCode(text(d.edCode)) != null;
            d.defaultHeader.setVisibility(canHaveDefault ? View.VISIBLE : View.GONE);
            d.tilDefault.setVisibility(canHaveDefault ? View.VISIBLE : View.GONE);
            if (!canHaveDefault) d.tilDefault.setError(null);
        };
        updateDefaultVisibility.run();

        Runnable updateUsage = () -> {
            String code = text(d.edCode);
            String label = text(d.edLabel);
            d.tvUsage.setText(getContext().getString(R.string.nmb_param_usage_message,
                    "%" + (code.isEmpty() ? "m.view" : code) + "." + (label.isEmpty() ? "label" : label)));
        };
        updateUsage.run();

        d.edCode.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validateParameterCode(d, false);
                updateUsage.run();
                updateDefaultVisibility.run();
            }
        });
        d.edLabel.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateUsage.run();
            }
        });

        d.edDefault.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                d.tilDefault.setError(null);
            }
        });

        d.btnClose.setOnClickListener(v -> closeLastDialog(d.getRoot()));
        d.btnCancel.setOnClickListener(v -> closeLastDialog(d.getRoot()));
        d.btnConfirm.setOnClickListener(v -> {
            if (mB.a()) return;
            boolean valid = validateParameterCode(d, true);

            String label = text(d.edLabel);
            labelValidator.onTextChanged(label, 0, 0, label.length());
            valid &= labelValidator.b();

            String defaultValue = "";
            MoreBlockDefaultValues.Kind kind = MoreBlockDefaultValues.kindForCode(text(d.edCode));
            if (kind != null) {
                defaultValue = text(d.edDefault).trim();
                if (!defaultValue.isEmpty() && MoreBlockDefaultValues.normalize(kind, defaultValue) == null) {
                    d.tilDefault.setError(defaultError(kind));
                    valid = false;
                }
            }
            if (!valid) return;

            MoreBlockEntry entry = isEdit ? editing : new MoreBlockEntry(MoreBlockEntry.KIND_PARAMETER);
            entry.typeCode = text(d.edCode);
            entry.name = label;
            entry.label = "";
            entry.defaultValue = defaultValue;
            entry.parameterTypeIndex = typeIndex[0];
            closeLastDialog(d.getRoot());
            onEntrySaved(true, entry, !isEdit);
        });

        if (!isEdit) {
            d.edCode.requestFocus();
        }
        showDialog(builder, d.getRoot(), !isEdit);
    }

    private String defaultError(MoreBlockDefaultValues.Kind kind) {
        if (kind == MoreBlockDefaultValues.Kind.BOOLEAN) {
            return getContext().getString(R.string.nmb_default_invalid_boolean);
        }
        return getContext().getString(R.string.nmb_default_invalid, kind.javaType);
    }

    /** Legacy custom parameter rule: {@code [mldb]\.[a-zA-Z]+}. */
    private boolean validateParameterCode(DialogNewMoreblockParameterBinding d, boolean showEmptyError) {
        String code = text(d.edCode);
        boolean valid = code.length() <= 100 && MoreBlockSpecAdapter.CUSTOM_PARAMETER_PATTERN.matcher(code).matches();
        boolean showError = !valid && (showEmptyError || !code.isEmpty());
        d.tilCode.setError(showError ? getContext().getString(R.string.invalid_value_format) : null);
        return valid;
    }

    private void showMoreTypesDialog(Consumer<MoreBlockVariableTypes.Type> onPicked) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        VarTypeSelectorDialogBinding selector = VarTypeSelectorDialogBinding.inflate(LayoutInflater.from(builder.getContext()));
        builder.setTitle(R.string.logic_editor_more_block_title_add_variable_type);

        TypeListAdapter adapter = new TypeListAdapter();
        selector.list.setLayoutManager(new LinearLayoutManager(getContext()));
        selector.list.setAdapter(adapter);
        adapter.setData(MoreBlockVariableTypes.variables());
        selector.navRail.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.variables) {
                adapter.setData(MoreBlockVariableTypes.variables());
            } else if (itemId == R.id.views) {
                adapter.setData(MoreBlockVariableTypes.views());
            } else {
                adapter.setData(MoreBlockVariableTypes.components());
            }
            return true;
        });

        builder.setView(selector.getRoot());
        AlertDialog dialog = builder.create();
        adapter.onPick = type -> {
            onPicked.accept(type);
            dialog.dismiss();
        };
        dialog.setOnDismissListener(dismissed -> openDialogs.remove(dismissed));
        openDialogs.add(dialog);
        dialog.show();
    }

    private final class TypeListAdapter extends RecyclerView.Adapter<TypeListAdapter.Holder> {
        private List<MoreBlockVariableTypes.Type> data = new ArrayList<>();
        private Consumer<MoreBlockVariableTypes.Type> onPick;

        void setData(List<MoreBlockVariableTypes.Type> data) {
            this.data = data;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(VarTypeItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            MoreBlockVariableTypes.Type type = data.get(position);
            holder.binding.name.setText(MoreBlockVariableTypes.title(getContext(), type));
            holder.binding.icon.setImageResource(type.icon());
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final VarTypeItemBinding binding;

            Holder(VarTypeItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
                binding.cardView.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && onPick != null) {
                        onPick.accept(data.get(position));
                    }
                });
            }
        }
    }
}
