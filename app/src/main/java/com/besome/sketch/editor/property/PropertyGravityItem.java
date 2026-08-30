package com.besome.sketch.editor.property;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import a.a.a.Kw;
import a.a.a.mB;
import a.a.a.sq;
import a.a.a.wB;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;

@SuppressLint("ViewConstructor")
public class PropertyGravityItem extends RelativeLayout implements View.OnClickListener {

    private String key = "";
    private int gravityValue = -1;
    private TextView tvName;
    private TextView tvValue;
    private ImageView imgLeftIcon;
    private int icon;
    private View propertyItem;
    private View propertyMenuItem;
    private Kw valueChangeListener;

    public PropertyGravityItem(Context context, boolean z) {
        super(context);
        initialize(context, z);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String str) {
        key = str;
        int identifier = getResources().getIdentifier(str, "string", getContext().getPackageName());
        if (identifier > 0) {
            tvName.setText(Helper.getResString(identifier));
            icon = R.drawable.ic_mtrl_center;
            if (propertyMenuItem.getVisibility() == VISIBLE) {
                ((ImageView) findViewById(R.id.img_icon)).setImageResource(icon);
                ((TextView) findViewById(R.id.tv_title)).setText(Helper.getResString(identifier));
                return;
            }
            imgLeftIcon.setImageResource(icon);
        }
    }

    public int getValue() {
        return gravityValue;
    }

    public void setValue(int value) {
        gravityValue = value;
        tvValue.setText(sq.a(value));
    }

    @Override
    public void onClick(View v) {
        if (!mB.a()) {
            switch (key) {
                case "property_gravity":
                case "property_layout_gravity":
                    showDialog();
                    break;
            }
        }
    }

    public void setOnPropertyValueChangeListener(Kw onPropertyValueChangeListener) {
        valueChangeListener = onPropertyValueChangeListener;
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

    private void initialize(Context context, boolean z) {
        wB.a(context, this, R.layout.property_selector_item);
        tvName = findViewById(R.id.tv_name);
        tvValue = findViewById(R.id.tv_value);
        imgLeftIcon = findViewById(R.id.img_left_icon);
        propertyItem = findViewById(R.id.property_item);
        propertyMenuItem = findViewById(R.id.property_menu_item);
    }

    private void showDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(getContext());
        dialog.setTitle(Helper.getText(tvName));
        dialog.setIcon(icon);

        LinearLayout gridContainer = new LinearLayout(getContext());
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        gridContainer.setGravity(Gravity.CENTER); 
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        gridContainer.setPadding(padding, padding, padding, padding);

        int[][] gravityGrid = {
                {Gravity.TOP | Gravity.LEFT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, Gravity.TOP | Gravity.RIGHT},
                {Gravity.CENTER_VERTICAL | Gravity.LEFT, Gravity.CENTER, Gravity.CENTER_VERTICAL | Gravity.RIGHT},
                {Gravity.BOTTOM | Gravity.LEFT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, Gravity.BOTTOM | Gravity.RIGHT}
        };

        // Initialize tempGravity: agar koi pehle se set nahi hai ya 0 (NO_GRAVITY) hai, toh -1 assign karo.
        final int[] tempGravity = {(gravityValue <= 0) ? -1 : gravityValue};
        List<View> allBoxes = new ArrayList<>();

        int boxSize = (int) (75 * getResources().getDisplayMetrics().density);
        int margin = (int) (4 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < 3; i++) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            for (int j = 0; j < 3; j++) {
                int currentBoxGravity = gravityGrid[i][j];
                
                FrameLayout box = new FrameLayout(getContext());
                LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(boxSize, boxSize);
                boxParams.setMargins(margin, margin, margin, margin);
                box.setLayoutParams(boxParams);
                box.setTag(currentBoxGravity);

                updateBoxDesign(box, currentBoxGravity == tempGravity[0]);

                box.setOnClickListener(view -> {
                    // Double Tap Unselect Logic
                    if (tempGravity[0] == currentBoxGravity) {
                        tempGravity[0] = -1; // Unselect kar do
                    } else {
                        tempGravity[0] = currentBoxGravity; // Naya select karo
                    }
                    
                    for (View b : allBoxes) {
                        updateBoxDesign(b, (int) b.getTag() == tempGravity[0]);
                    }
                });

                allBoxes.add(box);
                row.addView(box);
            }
            gridContainer.addView(row);
        }

        gridContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        
        dialog.setView(gridContainer);

        dialog.setPositiveButton(Helper.getResString(R.string.common_word_select), (v, which) -> {
            // Agar -1 (unselected) hai, toh NO_GRAVITY (0) pass karo taaki XML clean rahe
            int finalValue = (tempGravity[0] == -1) ? Gravity.NO_GRAVITY : tempGravity[0];
            setValue(finalValue);
            if (valueChangeListener != null) {
                valueChangeListener.a(key, finalValue);
            }
            v.dismiss();
        });
        
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    private void updateBoxDesign(View box, boolean isSelected) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(20f); 
        
        if (isSelected) {
            gd.setColor(Color.parseColor("#2196F3")); // Sketchware Neo Material Blue
            gd.setStroke(4, Color.parseColor("#64B5F6")); // Lighter highlight
        } else {
            gd.setColor(Color.parseColor("#252525")); // Dark background
            gd.setStroke(2, Color.parseColor("#3D3D3D")); // Subtle border
        }
        
        box.setBackground(gd);
    }
}
