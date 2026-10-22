package pro.sketchware.activities.resourceseditor.components.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

import pro.sketchware.activities.resourceseditor.ResourcesEditorActivity;
import pro.sketchware.databinding.PalletCustomviewBinding;
import pro.sketchware.databinding.ResourcesEditorFragmentBinding;
import pro.sketchware.utility.FileUtil;

/** Editor for app/project values/dimens*.xml resources. */
public class DimensionsEditor extends Fragment {

    private static final Pattern RESOURCE_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private ResourcesEditorActivity activity;
    private ResourcesEditorFragmentBinding binding;
    private DimensionAdapter adapter;
    private final ArrayList<Dimension> dimensions = new ArrayList<>();
    private final ArrayList<Dimension> filteredDimensions = new ArrayList<>();
    private String filterQuery = "";
    private String filePath;
    public boolean hasUnsavedChanges;
    public boolean isDataLoadingFailed;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (ResourcesEditorActivity) requireActivity();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = ResourcesEditorFragmentBinding.inflate(inflater, container, false);
        adapter = new DimensionAdapter();
        binding.recyclerView.setAdapter(adapter);
        return binding.getRoot();
    }

    public void updateDimensionsList(String path, boolean hasUnsavedChangesStatus) {
        updateDimensionsList(path, 0, hasUnsavedChangesStatus);
    }

    /**
     * Loads values/dimens*.xml. updateMode matches the other resource editors:
     * 0 replaces the list, 1 appends only unseen names, and 2 replaces matching names.
     */
    public void updateDimensionsList(String path, int updateMode, boolean hasUnsavedChangesStatus) {
        filePath = path;
        hasUnsavedChanges = hasUnsavedChangesStatus;
        String xml = FileUtil.readFileIfExist(path);
        if (xml == null || xml.trim().isEmpty()) xml = "<resources/>";

        ArrayList<Dimension> incoming = parse(xml);
        if (isDataLoadingFailed) {
            updateUi();
            return;
        }

        if (updateMode == 1) {
            Set<String> existingNames = new HashSet<>();
            for (Dimension dimension : dimensions) existingNames.add(dimension.name);
            for (Dimension dimension : incoming) {
                if (existingNames.add(dimension.name)) dimensions.add(dimension);
            }
        } else if (updateMode == 2) {
            Set<String> incomingNames = new HashSet<>();
            for (Dimension dimension : incoming) incomingNames.add(dimension.name);
            dimensions.removeIf(dimension -> incomingNames.contains(dimension.name));
            dimensions.addAll(incoming);
        } else {
            dimensions.clear();
            dimensions.addAll(incoming);
        }

        if (hasUnsavedChangesStatus && activity != null) {
            filePath = activity.dimensionFilePath;
        }
        updateUi();
    }

    private ArrayList<Dimension> parse(String xml) {
        ArrayList<Dimension> result = new ArrayList<>();
        isDataLoadingFailed = false;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Apply parser hardening when the current Android XML implementation supports it.
            // Optional XML features vary across Android releases; their absence must not make
            // every valid dimens.xml fail to load.
            try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); }
            catch (Exception ignored) { }
            try { factory.setFeature("http://xml.org/sax/features/external-general-entities", false); }
            catch (Exception ignored) { }
            try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); }
            catch (Exception ignored) { }
            try { factory.setXIncludeAware(false); }
            catch (Exception ignored) { }
            try { factory.setExpandEntityReferences(false); }
            catch (Exception ignored) { }
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element root = document.getDocumentElement();
            if (root == null || !"resources".equals(root.getTagName())) {
                throw new IllegalArgumentException("Expected a <resources> root element.");
            }
            NodeList children = root.getChildNodes();
            Set<String> seenNames = new HashSet<>();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                Element element = (Element) node;
                boolean dimenTag = "dimen".equals(node.getNodeName());
                boolean dimenItem = "item".equals(node.getNodeName())
                        && "dimen".equals(element.getAttribute("type"));
                if (!dimenTag && !dimenItem) continue;
                String name = element.getAttribute("name").trim();
                String value = element.getTextContent() == null ? "" : element.getTextContent().trim();
                if (!name.isEmpty()) {
                    if (!seenNames.add(name)) {
                        throw new IllegalArgumentException("Duplicate dimension resource name: " + name);
                    }
                    result.add(new Dimension(name, value));
                }
            }
        } catch (Exception exception) {
            isDataLoadingFailed = xml != null && !xml.trim().isEmpty() && !"<resources/>".equals(xml.trim());
            if (isDataLoadingFailed) android.util.Log.w("DimensionsEditor", "Cannot parse dimens.xml", exception);
        }
        return result;
    }

    private void updateUi() {
        if (!isAdded() || binding == null) return;
        requireActivity().runOnUiThread(() -> {
            if (binding == null) return;
            applyFilter();
            if (adapter != null) adapter.notifyDataSetChanged();
            if (dimensions.isEmpty()) {
                binding.noContentLayout.setVisibility(View.VISIBLE);
                binding.noContentTitle.setText("No dimensions yet");
                binding.noContentBody.setText("You don't have any dimensions here yet. Try creating one.");
            } else if (filteredDimensions.isEmpty()) {
                binding.noContentLayout.setVisibility(View.VISIBLE);
                binding.noContentTitle.setText("No matching dimensions");
                binding.noContentBody.setText("Try a different search term.");
            } else {
                binding.noContentLayout.setVisibility(View.GONE);
            }
            activity.checkForInvalidResources();
        });
    }

    public void filter(String query) {
        filterQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        updateUi();
    }

    private void applyFilter() {
        filteredDimensions.clear();
        for (Dimension dimension : dimensions) {
            if (filterQuery.isEmpty()
                    || dimension.name.toLowerCase(java.util.Locale.ROOT).contains(filterQuery)
                    || dimension.value.toLowerCase(java.util.Locale.ROOT).contains(filterQuery)) {
                filteredDimensions.add(dimension);
            }
        }
    }

    public void showAddDimensionDialog() {
        showDimensionDialog(null, -1);
    }

    private void showDimensionDialog(@Nullable Dimension existing, int position) {
        Context context = requireContext();
        LinearLayout fields = new LinearLayout(context);
        fields.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(context, 24);
        int top = dp(context, 8);
        fields.setPadding(horizontal, top, horizontal, 0);

        TextInputLayout nameLayout = new TextInputLayout(context);
        nameLayout.setHint("Resource name");
        nameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextInputEditText nameInput = new TextInputEditText(context);
        nameInput.setSingleLine(true);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        nameInput.setHint(null);
        nameLayout.addView(nameInput);
        fields.addView(nameLayout);

        LinearLayout valueRow = new LinearLayout(context);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams valueRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueRowParams.topMargin = dp(context, 12);
        valueRow.setLayoutParams(valueRowParams);

        TextInputLayout valueLayout = new TextInputLayout(context);
        valueLayout.setHint("Dimension value");
        valueLayout.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextInputEditText valueInput = new TextInputEditText(valueLayout.getContext());
        valueInput.setSingleLine(true);
        valueInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        valueLayout.addView(valueInput);
        valueRow.addView(valueLayout);

        TextInputLayout unitLayout = new TextInputLayout(context);
        unitLayout.setHint("Unit");
        unitLayout.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        LinearLayout.LayoutParams unitParams = new LinearLayout.LayoutParams(
                dp(context, 104), ViewGroup.LayoutParams.WRAP_CONTENT);
        unitParams.leftMargin = dp(context, 10);
        unitLayout.setLayoutParams(unitParams);
        AutoCompleteTextView unitInput = new AutoCompleteTextView(context);
        unitInput.setSingleLine(true);
        unitInput.setInputType(InputType.TYPE_NULL);
        unitInput.setText("dp", false);
        unitInput.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_1, new String[]{"dp", "sp"}));
        unitLayout.addView(unitInput);
        valueRow.addView(unitLayout);
        fields.addView(valueRow);

        if (existing != null) {
            nameInput.setText(existing.name);
            String existingValue = existing.value == null ? "" : existing.value.trim();
            if (existingValue.endsWith("dp") || existingValue.endsWith("sp")) {
                unitInput.setText(existingValue.substring(existingValue.length() - 2), false);
                valueInput.setText(existingValue.substring(0, existingValue.length() - 2));
            } else {
                unitLayout.setVisibility(View.GONE);
                valueInput.setText(existingValue);
            }
        }

        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(existing == null ? "Create dimension" : "Edit dimension")
                .setView(fields)
                .setPositiveButton(existing == null ? "Create" : "Save", null)
                .setNegativeButton("Cancel", null);
        if (existing != null) {
            dialog.setNeutralButton("Delete", null);
        }
        androidx.appcompat.app.AlertDialog shown = dialog.create();
        shown.setOnShowListener(unused -> {
            shown.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
                String rawValue = valueInput.getText() == null ? "" : valueInput.getText().toString().trim();
                String unit = unitInput.getText() == null ? "dp" : unitInput.getText().toString().trim();
                String value = rawValue;
                if (!rawValue.startsWith("@") && !rawValue.equals("0")) {
                    if (!"dp".equals(unit) && !"sp".equals(unit)) {
                        valueLayout.setError(null);
                        unitLayout.setError("Choose dp or sp.");
                        return;
                    }
                    value = rawValue + unit;
                }
                unitLayout.setError(null);
                if (!RESOURCE_NAME.matcher(name).matches()) {
                    nameLayout.setError("Use letters, digits and underscores; start with a letter or underscore.");
                    return;
                }
                nameLayout.setError(null);
                if (!isValidDimensionValue(value)) {
                    valueLayout.setError("Use a dimension such as 16dp, 14sp, 0dp, or @dimen/spacing_small.");
                    return;
                }
                valueLayout.setError(null);
                for (int i = 0; i < dimensions.size(); i++) {
                    if (i != position && dimensions.get(i).name.equals(name)) {
                        nameLayout.setError("A dimension with this name already exists.");
                        return;
                    }
                }
                if (existing == null) {
                    dimensions.add(new Dimension(name, value));
                } else {
                    existing.name = name;
                    existing.value = value;
                }
                hasUnsavedChanges = true;
                updateUi();
                shown.dismiss();
            });
            if (existing != null) {
                shown.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    new MaterialAlertDialogBuilder(context)
                            .setTitle("Delete dimension?")
                            .setMessage("Removing @dimen/" + existing.name + " may break layouts or styles that reference it.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (d, which) -> {
                                int safePosition = position;
                                if (safePosition >= 0 && safePosition < dimensions.size()
                                        && dimensions.get(safePosition) == existing) {
                                    dimensions.remove(safePosition);
                                    hasUnsavedChanges = true;
                                    updateUi();
                                }
                            }).show();
                });
            }
        });
        shown.show();
    }

    public void saveDimensionsFile() {
        if (!hasUnsavedChanges || filePath == null || filePath.trim().isEmpty()) return;
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n");
        for (Dimension dimension : dimensions) {
            xml.append("    <dimen name=\"")
                    .append(ResourcesEditorActivity.escapeXml(dimension.name))
                    .append("\">")
                    .append(ResourcesEditorActivity.escapeXml(dimension.value))
                    .append("</dimen>\n");
        }
        xml.append("</resources>\n");
        FileUtil.writeFile(filePath, xml.toString());
        hasUnsavedChanges = false;
    }

    private static boolean isValidDimensionValue(String value) {
        if (value == null || value.isEmpty()) return false;
        if ("0".equals(value)) return true;
        if (Pattern.matches("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:dp|dip|sp|px|pt|in|mm)", value)) {
            return true;
        }
        return Pattern.matches("@(?:android:)?dimen/[a-zA-Z_][a-zA-Z0-9_]*", value);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class Dimension {
        String name;
        String value;
        Dimension(String name, String value) { this.name = name; this.value = value; }
    }

    private final class DimensionAdapter extends RecyclerView.Adapter<DimensionViewHolder> {
        @NonNull
        @Override
        public DimensionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PalletCustomviewBinding row = PalletCustomviewBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new DimensionViewHolder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull DimensionViewHolder holder, int position) {
            Dimension item = filteredDimensions.get(position);
            holder.binding.title.setText(item.name);
            holder.binding.sub.setText(item.value);
            holder.binding.tvTitle.setVisibility(View.GONE);
            holder.binding.backgroundCard.setOnClickListener(v -> {
                int current = holder.getBindingAdapterPosition();
                if (current != RecyclerView.NO_POSITION && current < filteredDimensions.size()) {
                    Dimension selected = filteredDimensions.get(current);
                    int sourcePosition = dimensions.indexOf(selected);
                    if (sourcePosition >= 0) showDimensionDialog(selected, sourcePosition);
                }
            });
        }

        @Override
        public int getItemCount() { return filteredDimensions.size(); }
    }

    private static final class DimensionViewHolder extends RecyclerView.ViewHolder {
        final PalletCustomviewBinding binding;
        DimensionViewHolder(PalletCustomviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
