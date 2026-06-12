package com.besome.sketch.editor.property;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ViewBean;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import a.a.a.Kw;
import a.a.a.mB;
import a.a.a.wB;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.PropertyInputItemBinding;
import pro.sketchware.databinding.PropertyPopupInputTextBinding;
import pro.sketchware.databinding.PropertyPopupParentAttrBinding;
import pro.sketchware.databinding.PropertySwitchItemSinglelineBinding;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.relativelayout.CircularDependencyDetector;

@SuppressLint("ViewConstructor")
public class PropertyAttributesItem extends LinearLayout implements View.OnClickListener {

    private static final String[] PARENT_RELATIVE = {
            "android:layout_centerInParent",
            "android:layout_centerVertical", "android:layout_centerHorizontal",
            "android:layout_toStartOf", "android:layout_toEndOf",
            "android:layout_toLeftOf", "android:layout_toRightOf",
            "android:layout_above", "android:layout_below",
            "android:layout_alignStart", "android:layout_alignEnd",
            "android:layout_alignLeft", "android:layout_alignRight",
            "android:layout_alignTop", "android:layout_alignBottom",
            "android:layout_alignParentStart", "android:layout_alignParentEnd",
            "android:layout_alignParentLeft", "android:layout_alignParentRight",
            "android:layout_alignParentTop", "android:layout_alignParentBottom",
            "android:layout_alignBaseline"
    };

    public static List<String> RELATIVE_IDS = Arrays.asList(
            "android:layout_alignStart", "android:layout_alignEnd",
            "android:layout_alignLeft", "android:layout_alignRight",
            "android:layout_alignTop", "android:layout_alignBottom",
            "android:layout_alignBaseline",
            "android:layout_toStartOf", "android:layout_toEndOf",
            "android:layout_toLeftOf", "android:layout_toRightOf",
            "android:layout_above", "android:layout_below"
    );

    public static List<String> CONSTRAINT_IDS = Arrays.asList(
            "app:layout_constraintTop_toTopOf", "app:layout_constraintTop_toBottomOf",
            "app:layout_constraintBottom_toTopOf", "app:layout_constraintBottom_toBottomOf",
            "app:layout_constraintStart_toStartOf", "app:layout_constraintStart_toEndOf",
            "app:layout_constraintEnd_toStartOf", "app:layout_constraintEnd_toEndOf",
            "app:layout_constraintLeft_toLeftOf", "app:layout_constraintLeft_toRightOf",
            "app:layout_constraintRight_toLeftOf", "app:layout_constraintRight_toRightOf",
            "app:layout_constraintBaseline_toBaselineOf", "app:layout_constraintCircle"
    );

    public static List<String> CONSTRAINT_VALUES = Arrays.asList(
            "app:layout_constraintHorizontal_bias", "app:layout_constraintVertical_bias",
            "app:layout_constraintDimensionRatio", "app:layout_constraintWidth_percent",
            "app:layout_constraintHeight_percent", "app:layout_constraintHorizontal_weight",
            "app:layout_constraintVertical_weight", "app:layout_constraintHorizontal_chainStyle",
            "app:layout_constraintVertical_chainStyle", "app:layout_editor_absoluteX",
            "app:layout_editor_absoluteY", "app:layout_constraintCircleRadius",
            "app:layout_constraintCircleAngle"
    );

    public static List<String> CONSTRAINT_BOOLEANS = Arrays.asList(
            "app:layout_constrainedWidth", "app:layout_constrainedHeight"
    );

    private static final String[] PARENT_CONSTRAINT;

    static {
        List<String> combinedConstraints = new ArrayList<>();
        combinedConstraints.addAll(CONSTRAINT_IDS);
        combinedConstraints.addAll(CONSTRAINT_VALUES);
        combinedConstraints.addAll(CONSTRAINT_BOOLEANS);
        PARENT_CONSTRAINT = combinedConstraints.toArray(new String[0]);
    }

    private final ArrayList<ViewBean> beans = new ArrayList<>();
    private String key = "";
    private HashMap<String, String> value = new HashMap<>();
    private TextView tvName;
    private TextView tvValue;
    private View propertyItem;
    private View propertyMenuItem;
    private ImageView imgLeftIcon;
    private Kw valueChangeListener;
    private ViewBean bean;
    private List<String> ids = new ArrayList<>();

    public PropertyAttributesItem(Context context, boolean z) {
        super(context);
        initialize(context, z);
    }

    private void initialize(Context context, boolean z) {
        wB.a(context, this, R.layout.property_input_item);
        tvName = findViewById(R.id.tv_name);
        tvValue = findViewById(R.id.tv_value);
        imgLeftIcon = findViewById(R.id.img_left_icon);
        propertyItem = findViewById(R.id.property_item);
        propertyMenuItem = findViewById(R.id.property_menu_item);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        mB.a(this);
        this.key = key;
        int identifier = getResources().getIdentifier(key, "string", getContext().getPackageName());
        if (identifier > 0) {
            tvName.setText(Helper.getResString(identifier));
            int icon = R.drawable.ic_property_parent_attr;
            if (propertyMenuItem.getVisibility() == VISIBLE) {
                ((ImageView) findViewById(R.id.img_icon)).setImageResource(icon);
                ((TextView) findViewById(R.id.tv_title)).setText(Helper.getResString(identifier));
                return;
            }
            tvValue.setText("Configure parent attributes");
            imgLeftIcon.setImageResource(icon);
        }
    }

    public void setOrientationItem(int orientationItem) {
        if (orientationItem == 0) {
            propertyItem.setVisibility(GONE);
            propertyMenuItem.setVisibility(VISIBLE);
            propertyItem.setOnClickListener(null);
            propertyMenuItem.setOnClickListener(this);
        } else {
            propertyItem.setVisibility(VISIBLE);
            propertyMenuItem.setVisibility(GONE);
            propertyItem.setOnClickListener(this);
            propertyMenuItem.setOnClickListener(null);
        }
    }

    public void setBean(ViewBean bean) {
        this.bean = bean.clone();
    }

    public void setBeans(ArrayList<ViewBean> beans) {
        this.beans.clear();
        this.beans.addAll(beans);
    }

    public void setAvailableIds(List<String> ids) {
        this.ids = ids;
    }

    public void setOnPropertyValueChangeListener(Kw onPropertyValueChangeListener) {
        valueChangeListener = onPropertyValueChangeListener;
    }

    public HashMap<String, String> getValue() {
        return value;
    }

    public void setValue(HashMap<String, String> value) {
        this.value = new HashMap<>(value);
    }

    @Override
    public void onClick(View v) {
        showParentAttributes();
    }

    private boolean isParentConstraintLayout() {
        if (bean == null) return false;

        if (bean.parentType == ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT || bean.parentType == 50) return true;

        if (bean.parent == null) return false;
        for (ViewBean b : beans) {
            if (b.id.equals(bean.parent)) {
                if (b.type == ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT || b.type == 50) return true;
                String className = ViewBean.getViewTypeName(b.type);
                if (b.convert != null && b.convert.contains("ConstraintLayout")) return true;
                return (className != null && className.contains("ConstraintLayout")) ||
                        (b.customView != null && b.customView.contains("ConstraintLayout"));
            }
        }
        return false;
    }

    private void showParentAttributes() {
        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        var binding = PropertyPopupParentAttrBinding.inflate(LayoutInflater.from(getContext()));
        dialog.setContentView(binding.getRoot());
        dialog.show();

        binding.viewId.setText(bean.id);

        var adapter = new AttributesAdapter();
        binding.recyclerView.setAdapter(adapter);
        var dividerItemDecoration = new DividerItemDecoration(binding.recyclerView.getContext(), LinearLayoutManager.VERTICAL);

        if (binding.recyclerView.getItemDecorationCount() == 0) {
            binding.recyclerView.addItemDecoration(dividerItemDecoration);
        }

        List<String> keys = new ArrayList<>(value.keySet());
        adapter.submitList(keys);

        boolean isConstraint = isParentConstraintLayout();
        String[] attributesToUse = isConstraint ? PARENT_CONSTRAINT : PARENT_RELATIVE;

        binding.add.setOnClickListener(v -> {
            List<String> list = new ArrayList<>();
            for (String attr : attributesToUse) {
                if (!value.containsKey(attr)) {
                    list.add(attr);
                }
            }
            new MaterialAlertDialogBuilder(getContext())
                    .setTitle(isConstraint ? "Choose a Constraint" : "Choose an Attribute")
                    .setAdapter(
                            new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, list), (d, w) -> {
                                var attr = list.get(w);
                                if (RELATIVE_IDS.contains(attr) || CONSTRAINT_IDS.contains(attr)) {

                                    List<String> availableIds = new ArrayList<>(ids);
                                    boolean currentIsConstraint = isConstraint && attr.startsWith("app:layout_constraint");
                                    if (currentIsConstraint) availableIds.add(0, "parent");

                                    new MaterialAlertDialogBuilder(getContext())
                                            .setTitle("Choose a Target")
                                            .setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, availableIds), (d2, w2) -> {
                                                var id = availableIds.get(w2);

                                                if (currentIsConstraint || new CircularDependencyDetector(beans, bean).isLegalAttribute(id, attr)) {
                                                    if (currentIsConstraint) {
                                                        if ("parent".equals(id)) {
                                                            value.put(attr, id);
                                                        } else {
                                                            value.put(attr, "@id/" + id);
                                                        }
                                                    } else {
                                                        value.put(attr, id);
                                                    }

                                                    if (valueChangeListener != null)
                                                        valueChangeListener.a(key, value);
                                                    adapter.submitList(new ArrayList<>(value.keySet()));
                                                } else {
                                                    SketchwareUtil.toastError("IllegalStateException : Circular dependencies cannot exist");
                                                }
                                            })
                                            .setNegativeButton("Cancel", (d2, which) -> d.dismiss())
                                            .show();
                                } else if (CONSTRAINT_VALUES.contains(attr)) {
                                    MaterialAlertDialogBuilder inputDialog = new MaterialAlertDialogBuilder(getContext());
                                    inputDialog.setTitle("Enter value for " + attr);
                                    PropertyPopupInputTextBinding inputBinding = PropertyPopupInputTextBinding.inflate(LayoutInflater.from(getContext()));
                                    inputBinding.tiInput.setHint("Value (e.g. 0.5, 16:9, etc)");
                                    inputDialog.setView(inputBinding.getRoot());
                                    inputDialog.setPositiveButton("Save", (d2, w2) -> {
                                        String text = inputBinding.edInput.getText().toString().trim();
                                        if (!text.isEmpty()) {
                                            value.put(attr, text);
                                            if (valueChangeListener != null) valueChangeListener.a(key, value);
                                            adapter.submitList(new ArrayList<>(value.keySet()));
                                        }
                                    });
                                    inputDialog.setNegativeButton("Cancel", null);
                                    inputDialog.show();
                                } else {
                                    value.put(attr, "false");
                                    if (valueChangeListener != null)
                                        valueChangeListener.a(key, value);
                                    adapter.submitList(new ArrayList<>(value.keySet()));
                                }
                            })
                    .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                    .show();
        });
    }

    private class AttributesAdapter extends ListAdapter<String, RecyclerView.ViewHolder> {

        private static final DiffUtil.ItemCallback<String> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return true;
            }
        };

        public AttributesAdapter() {
            super(DIFF_CALLBACK);
        }

        @Override
        public int getItemViewType(int position) {
            String item = getItem(position);
            if (RELATIVE_IDS.contains(item) || CONSTRAINT_IDS.contains(item)) {
                return 1;
            } else if (CONSTRAINT_VALUES.contains(item)) {
                return 2;
            } else {
                return 0;
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout root = new FrameLayout(parent.getContext());
            if (viewType == 1) {
                return new IdsViewHolder(root);
            } else if (viewType == 2) {
                return new StringViewHolder(root);
            } else {
                return new BooleanViewHolder(root);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof IdsViewHolder idsHolder) {
                idsHolder.bind(getItem(position));
            } else if (holder instanceof BooleanViewHolder booleanHolder) {
                booleanHolder.bind(getItem(position));
            } else if (holder instanceof StringViewHolder stringHolder) {
                stringHolder.bind(getItem(position));
            }
        }

        private class IdsViewHolder extends RecyclerView.ViewHolder {
            private final PropertyInputItemBinding binding;

            public IdsViewHolder(FrameLayout view) {
                super(view);
                binding = PropertyInputItemBinding.inflate(LayoutInflater.from(view.getContext()), view, true);
            }

            void bind(String attr) {
                binding.tvName.setText(attr);

                String val = value.get(attr);
                if (val == null) val = "";

                boolean currentIsConstraint = isParentConstraintLayout() && attr.startsWith("app:layout_constraint");

                if (currentIsConstraint) {
                    binding.tvValue.setText(val);
                } else {
                    if ("parent".equals(val) || "true".equals(val) || "false".equals(val) || val.startsWith("@id/") || val.startsWith("@+id/")) {
                        binding.tvValue.setText(val);
                    } else {
                        binding.tvValue.setText("@id/" + val);
                    }
                }

                binding.imgLeftIcon.setImageResource(R.drawable.ic_mtrl_code);
                binding.getRoot().findViewById(R.id.property_menu_item).setVisibility(View.GONE);

                itemView.setOnClickListener(v -> {
                    var filteredIds = new ArrayList<>(ids);
                    if (currentIsConstraint) {
                        filteredIds.add(0, "parent");
                    }

                    String rawVal = value.get(attr);
                    if (rawVal != null) {
                        if (rawVal.startsWith("@+id/")) {
                            rawVal = rawVal.substring(5);
                        } else if (rawVal.startsWith("@id/")) {
                            rawVal = rawVal.substring(4);
                        }
                        filteredIds.remove(rawVal);
                    }

                    new MaterialAlertDialogBuilder(getContext())
                            .setTitle("Choose a Target")
                            .setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, filteredIds), (d, w) -> {
                                var id = filteredIds.get(w);

                                if (currentIsConstraint) {
                                    if ("parent".equals(id)) {
                                        value.put(attr, id);
                                        binding.tvValue.setText(id);
                                    } else {
                                        value.put(attr, "@id/" + id);
                                        binding.tvValue.setText("@id/" + id);
                                    }
                                } else {
                                    value.put(attr, id);
                                    binding.tvValue.setText("@id/" + id);
                                }

                                if (valueChangeListener != null)
                                    valueChangeListener.a(key, value);
                            })
                            .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                            .show();
                });

                itemView.setOnLongClickListener(v -> {
                    MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(getContext());
                    dialog.setTitle("Delete");
                    dialog.setMessage("Are you sure you want to delete " + attr + "?");
                    dialog.setPositiveButton("Yes", (view, which) -> {
                        value.remove(attr);
                        if (valueChangeListener != null)
                            valueChangeListener.a(key, value);
                        submitList(new ArrayList<>(value.keySet()));
                        view.dismiss();
                    });
                    dialog.setNegativeButton("No", (view, which) -> view.dismiss());
                    dialog.show();
                    return true;
                });
            }
        }

        private class StringViewHolder extends RecyclerView.ViewHolder {
            private final PropertyInputItemBinding binding;

            public StringViewHolder(FrameLayout view) {
                super(view);
                binding = PropertyInputItemBinding.inflate(LayoutInflater.from(view.getContext()), view, true);
            }

            void bind(String attr) {
                binding.tvName.setText(attr);

                String val = value.get(attr);
                binding.tvValue.setText(val != null ? val : "");

                binding.imgLeftIcon.setImageResource(R.drawable.ic_mtrl_code);
                binding.getRoot().findViewById(R.id.property_menu_item).setVisibility(View.GONE);

                itemView.setOnClickListener(v -> {
                    MaterialAlertDialogBuilder inputDialog = new MaterialAlertDialogBuilder(getContext());
                    inputDialog.setTitle("Edit value for " + attr);
                    PropertyPopupInputTextBinding inputBinding = PropertyPopupInputTextBinding.inflate(LayoutInflater.from(getContext()));
                    inputBinding.tiInput.setHint("Value (e.g. 0.5, 16:9, etc)");

                    String currentValue = value.get(attr);
                    inputBinding.edInput.setText(currentValue != null ? currentValue : "");

                    inputDialog.setView(inputBinding.getRoot());
                    inputDialog.setPositiveButton("Save", (d2, w2) -> {
                        String text = inputBinding.edInput.getText().toString().trim();
                        if (!text.isEmpty()) {
                            value.put(attr, text);
                            binding.tvValue.setText(text);
                            if (valueChangeListener != null) valueChangeListener.a(key, value);
                        }
                    });
                    inputDialog.setNegativeButton("Cancel", null);
                    inputDialog.show();
                });

                itemView.setOnLongClickListener(v -> {
                    MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(getContext());
                    dialog.setTitle("Delete");
                    dialog.setMessage("Are you sure you want to delete " + attr + "?");
                    dialog.setPositiveButton("Yes", (view, which) -> {
                        value.remove(attr);
                        if (valueChangeListener != null)
                            valueChangeListener.a(key, value);
                        submitList(new ArrayList<>(value.keySet()));
                        view.dismiss();
                    });
                    dialog.setNegativeButton("No", (view, which) -> view.dismiss());
                    dialog.show();
                    return true;
                });
            }
        }

        private class BooleanViewHolder extends RecyclerView.ViewHolder {
            private final PropertySwitchItemSinglelineBinding binding;

            public BooleanViewHolder(FrameLayout view) {
                super(view);
                binding = PropertySwitchItemSinglelineBinding.inflate(LayoutInflater.from(view.getContext()), view, true);
            }

            void bind(String attr) {
                binding.tvName.setText(attr);
                binding.imgLeftIcon.setImageResource(R.drawable.ic_mtrl_code);
                binding.getRoot().findViewById(R.id.property_menu_item).setVisibility(View.GONE);

                String val = value.get(attr);
                binding.switchValue.setChecked(Boolean.parseBoolean(val != null ? val : "false"));

                itemView.setOnClickListener(v -> {
                    binding.switchValue.setChecked(!binding.switchValue.isChecked());
                    value.put(attr, String.valueOf(binding.switchValue.isChecked()));
                    if (valueChangeListener != null) valueChangeListener.a(key, value);
                });
                itemView.setOnLongClickListener(v -> {
                    MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(getContext());
                    dialog.setTitle("Delete");
                    dialog.setMessage("Are you sure you want to delete " + attr + "?");
                    dialog.setPositiveButton("Yes", (view, which) -> {
                        value.remove(attr);
                        if (valueChangeListener != null)
                            valueChangeListener.a(key, value);
                        submitList(new ArrayList<>(value.keySet()));
                        view.dismiss();
                    });

                    dialog.setNegativeButton("No", (view, which) -> view.dismiss());
                    dialog.show();
                    return true;
                });
            }
        }
    }
}
