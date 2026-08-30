package com.besome.sketch.editor.property;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Editable;
import android.util.Xml;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.design.DesignActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import a.a.a.Kw;
import a.a.a.jC;
import a.a.a.kC;
import a.a.a.mB;
import a.a.a.wB;
import mod.bobur.VectorDrawableLoader;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ImagePickerItemBinding;
import pro.sketchware.databinding.SearchWithRecyclerViewBinding;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.SvgUtils;

public class PropertyResourceItem extends RelativeLayout implements View.OnClickListener {

    private final SvgUtils svgUtils;
    private final FilePathUtil fpu = new FilePathUtil();
    private final Map<String, View> imageCache = new HashMap<>();

    public String a;
    public String b;
    public String c;
    public boolean d;
    public TextView e;
    public TextView f;
    public ImageView g;
    public ImageView h;
    public RadioGroup i;
    public LinearLayout j;
    public View k;
    public View l;
    public int m;
    public Kw n;

    public PropertyResourceItem(Context context, boolean z, String str, boolean z2) {
        super(context);
        d = false;
        a = str;
        svgUtils = new SvgUtils(context);
        svgUtils.initImageLoader();
        a(context, z, z2);
    }

    public String getKey() {
        return b;
    }

    public void setKey(String str) {
        b = str;
        int identifier = getResources().getIdentifier(str, "string", getContext().getPackageName());
        if (identifier > 0) {
            e.setText(getResources().getString(identifier));
            if ("property_image".equals(b)) {
                m = R.drawable.ic_mtrl_image;
            } else if ("property_background_resource".equals(b)) {
                m = R.drawable.ic_mtrl_background_dots;
            }
            if (l.getVisibility() == VISIBLE) {
                ((ImageView) findViewById(R.id.img_icon)).setImageResource(m);
                ((TextView) findViewById(R.id.tv_title)).setText(getContext().getString(identifier));
            } else {
                h.setImageResource(m);
            }
        }
    }

    public String getValue() {
        return c;
    }

    public void setValue(String str) {
        Uri fromFile;
        if (str != null && !str.equalsIgnoreCase("NONE")) {
            c = str;
            f.setText(str);
            if (jC.d(a).h(str) == ProjectResourceBean.PROJECT_RES_TYPE_RESOURCE) {
                g.setImageResource(getContext().getResources().getIdentifier(str, "drawable", getContext().getPackageName()));
                return;
            } else if (str.equals("default_image")) {
                g.setImageResource(getContext().getResources().getIdentifier(str, "drawable", getContext().getPackageName()));
                return;
            } else {
                File file = new File(jC.d(a).f(str));
                if (file.exists()) {
                    Context context = getContext();
                    fromFile = FileProvider.getUriForFile(context, getContext().getPackageName() + ".provider", file);
                    if (file.getAbsolutePath().endsWith(".xml")) {
                        svgUtils.loadImage(g, fpu.getSvgFullPath(a, str));
                        return;
                    }
                    Glide.with(getContext()).load(fromFile).signature(kC.n()).error(R.drawable.ic_remove_grey600_24dp).into(g);
                    return;
                }
                g.setImageResource(getContext().getResources().getIdentifier(str, "drawable", getContext().getPackageName()));
                return;
            }
        }
        c = str;
        f.setText("NONE");
        g.setImageDrawable(null);
        g.setBackgroundColor(Color.WHITE);
    }

    @Override
    public void onClick(View view) {
        if (mB.a()) {
            return;
        }
        a();
    }

    public void setOnPropertyValueChangeListener(Kw kw) {
        n = kw;
    }

    public void setOrientationItem(int i) {
        if (i == 0) {
            k.setVisibility(GONE);
            l.setVisibility(VISIBLE);
            k.setOnClickListener(null);
            l.setOnClickListener(this);
        } else {
            k.setVisibility(VISIBLE);
            l.setVisibility(GONE);
            k.setOnClickListener(this);
            l.setOnClickListener(null);
        }
    }

    public final void a(Context context, boolean z, boolean z2) {
        wB.a(context, this, R.layout.property_resource_item);
        e = findViewById(R.id.tv_name);
        f = findViewById(R.id.tv_value);
        g = findViewById(R.id.view_image);
        h = findViewById(R.id.img_left_icon);
        k = findViewById(R.id.property_item);
        l = findViewById(R.id.property_menu_item);
        d = z2;
    }

    public final void a() {
        showPickerDialog(Helper.getText(e), m, c, selected -> {
            setValue(selected);
            if (n != null) {
                n.a(b, selected);
            }
        });
    }

    public interface ResourcePickCallback {
        void onSelected(String resourceName);
    }

    public void showPickerDialog(String title, int iconRes, String currentValue, ResourcePickCallback callback) {
        SearchWithRecyclerViewBinding binding = SearchWithRecyclerViewBinding.inflate(LayoutInflater.from(getContext()));
        imageCache.clear();

        ArrayList<String> images = jC.d(a).m();
        images.addAll(new VectorDrawableLoader().getVectorDrawables(DesignActivity.sc_id));

        ArrayList<String> shapeNames = findShapeDrawables(images);
        if (!shapeNames.isEmpty()) {
            shapeNames.add(0, d ? "default_image" : "NONE");
        }

        images.add(0, d ? "default_image" : "NONE");

        ImagePickerAdapter adapter = new ImagePickerAdapter(images, currentValue);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerView.setAdapter(adapter);

        binding.searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                adapter.filter(s.toString().toLowerCase());
            }
        });

        LinearLayout dialogRoot = new LinearLayout(getContext());
        dialogRoot.setOrientation(LinearLayout.VERTICAL);

        if (!shapeNames.isEmpty()) {
            int dp8 = (int) (8 * wB.a(getContext(), 1f));
            MaterialButtonToggleGroup toggleGroup = new MaterialButtonToggleGroup(getContext());
            toggleGroup.setSingleSelection(true);
            toggleGroup.setSelectionRequired(true);
            LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            toggleParams.gravity = Gravity.CENTER_HORIZONTAL;
            toggleParams.setMargins(dp8, dp8, dp8, dp8);
            toggleGroup.setLayoutParams(toggleParams);

            MaterialButton imagesButton = new MaterialButton(getContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            imagesButton.setText("Images");
            imagesButton.setId(View.generateViewId());
            imagesButton.setCheckable(true);
            imagesButton.setChecked(true);

            MaterialButton shapesButton = new MaterialButton(getContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            shapesButton.setText("Shapes");
            shapesButton.setId(View.generateViewId());
            shapesButton.setCheckable(true);

            toggleGroup.addView(imagesButton);
            toggleGroup.addView(shapesButton);

            imagesButton.setOnClickListener(v -> {
                imagesButton.setChecked(true);
                adapter.setSource(new ArrayList<>(images));
            });
            shapesButton.setOnClickListener(v -> {
                shapesButton.setChecked(true);
                adapter.setSource(new ArrayList<>(shapeNames));
            });

            dialogRoot.addView(toggleGroup);
        }

        dialogRoot.addView(binding.getRoot(), new LinearLayout.LayoutParams(-1, -1));

        new MaterialAlertDialogBuilder(getContext())
                .setTitle(title)
                .setIcon(iconRes)
                .setView(dialogRoot)
                .setPositiveButton(R.string.common_word_select, (v, which) -> {
                    String selected = adapter.getSelected();
                    if (selected != null && !selected.isEmpty()) {
                        callback.onSelected(selected);
                    }
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private static final java.util.Set<String> KNOWN_SHAPE_ROOT_TAGS = new java.util.HashSet<>(Arrays.asList(
            "shape", "selector", "layer-list", "ripple", "inset", "clip",
            "animated-selector", "level-list", "rotate", "scale", "animated-rotate"
    ));

    private ArrayList<String> findShapeDrawables(ArrayList<String> alreadyListed) {
        ArrayList<String> shapeNames = new ArrayList<>();
        try {
            File drawableDir = new File(pro.sketchware.utility.FileUtil.getExternalStorageDir()
                    + "/.sketchware/data/" + a + "/files/resource/drawable/");
            File[] files = drawableDir.listFiles();
            if (files == null) return shapeNames;

            for (File file : files) {
                String name = file.getName();
                if (!name.endsWith(".xml")) continue;
                if (file.length() == 0) continue;

                String nameWithoutExt = name.substring(0, name.length() - 4);
                if (alreadyListed.contains(nameWithoutExt) || alreadyListed.contains(name)) continue;

                String rootTag = readRootTag(file);
                if (rootTag == null) continue;
                if ("vector".equals(rootTag)) continue;
                if (!KNOWN_SHAPE_ROOT_TAGS.contains(rootTag)) continue;

                shapeNames.add(nameWithoutExt);
            }
        } catch (Exception ignored) {
        }
        return shapeNames;
    }

    private Drawable inflateShapeDrawable(File file) {
        if (file == null || !file.exists() || file.length() == 0) return null;
        try (java.io.FileInputStream inputStream = new java.io.FileInputStream(file)) {
            org.xmlpull.v1.XmlPullParser parser = Xml.newPullParser();
            parser.setInput(inputStream, null);
            int eventType = parser.getEventType();
            while (eventType != org.xmlpull.v1.XmlPullParser.START_TAG) {
                eventType = parser.next();
            }
            return Drawable.createFromXml(getResources(), parser);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isVectorXml(File file) {
        return "vector".equals(readRootTag(file));
    }

    private String readRootTag(File file) {
        if (file == null || !file.exists() || file.length() == 0) return null;
        try (java.io.FileInputStream inputStream = new java.io.FileInputStream(file)) {
            org.xmlpull.v1.XmlPullParser parser = Xml.newPullParser();
            parser.setInput(inputStream, null);
            int eventType = parser.getEventType();
            while (eventType != org.xmlpull.v1.XmlPullParser.START_TAG) {
                if (eventType == org.xmlpull.v1.XmlPullParser.END_DOCUMENT) return null;
                eventType = parser.next();
            }
            return parser.getName();
        } catch (Exception e) {
            return null;
        }
    }

    private class ImagePickerAdapter extends RecyclerView.Adapter<ImagePickerAdapter.ViewHolder> {

        private final ArrayList<String> allImages;
        private final ArrayList<String> filteredImages = new ArrayList<>();
        private String selectedImage;

        ImagePickerAdapter(ArrayList<String> images, String selectedImage) {
            this.allImages = images;
            this.selectedImage = selectedImage;
            this.filteredImages.addAll(images);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ImagePickerItemBinding binding = ImagePickerItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            String image = filteredImages.get(position);
            holder.binding.textView.setText(image);
            holder.binding.radioButton.setChecked(image.equals(selectedImage));

            View imageView = imageCache.get(image);
            if (imageView == null) {
                imageView = setImageViewContent(image);
                imageCache.put(image, imageView);
            }

            if (imageView.getParent() != null) {
                ((ViewGroup) imageView.getParent()).removeView(imageView);
            }

            holder.binding.layoutImg.removeAllViews();
            holder.binding.layoutImg.addView(imageView);

            holder.binding.transparentOverlay.setOnClickListener(v -> {
                selectedImage = image;
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return filteredImages.size();
        }

        public void filter(String query) {
            filteredImages.clear();
            for (String s : allImages) {
                if (s.toLowerCase().contains(query)) {
                    filteredImages.add(s);
                }
            }
            notifyDataSetChanged();
        }

        public void setSource(ArrayList<String> newSource) {
            allImages.clear();
            allImages.addAll(newSource);
            filteredImages.clear();
            filteredImages.addAll(newSource);
            notifyDataSetChanged();
        }

        public String getSelected() {
            return selectedImage;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImagePickerItemBinding binding;
            ViewHolder(ImagePickerItemBinding b) {
                super(b.getRoot());
                this.binding = b;
            }
        }
    }

    private File resolveDrawableFile(String image) {
        try {
            String kcPath = jC.d(a).f(image);
            if (kcPath != null) {
                File file = new File(kcPath);
                if (file.exists() && file.length() > 0) return file;
            }
        } catch (Exception ignored) {
        }

        File direct = new File(pro.sketchware.utility.FileUtil.getExternalStorageDir()
                + "/.sketchware/data/" + a + "/files/resource/drawable/" + image + ".xml");
        if (direct.exists() && direct.length() > 0) return direct;

        return null;
    }

    private View setImageViewContent(String image) {
        ImageView imageView = new ImageView(getContext());
        int size = (int) (48 * wB.a(getContext(), 1f));
        imageView.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackgroundResource(R.drawable.bg_outline);

        try {
            if ("default_image".equals(image)) {
                imageView.setImageResource(getResources().getIdentifier(image, "drawable", getContext().getPackageName()));
            } else {
                File file = resolveDrawableFile(image);
                if (file != null && file.exists()) {
                    Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".provider", file);
                    if (file.getAbsolutePath().endsWith(".xml")) {
                        if (isVectorXml(file)) {
                            svgUtils.loadImage(imageView, fpu.getSvgFullPath(a, image));
                        } else {
                            Drawable shapeDrawable = inflateShapeDrawable(file);
                            if (shapeDrawable != null) {
                                imageView.setImageDrawable(shapeDrawable);
                            } else {
                                imageView.setImageResource(R.drawable.ic_remove_grey600_24dp);
                            }
                        }
                    } else {
                        Glide.with(getContext())
                                .load(uri)
                                .signature(kC.n())
                                .error(R.drawable.ic_remove_grey600_24dp)
                                .into(imageView);
                    }
                } else {
                    VectorDrawableLoader vectorDrawableLoader = new VectorDrawableLoader();
                    vectorDrawableLoader.setImageVectorFromFile(imageView, vectorDrawableLoader.getVectorFullPath(DesignActivity.sc_id, image));
                }
            }
        } catch (Exception e) {
            imageView.setImageResource(R.drawable.ic_remove_grey600_24dp);
        }

        return imageView;
    }
}
