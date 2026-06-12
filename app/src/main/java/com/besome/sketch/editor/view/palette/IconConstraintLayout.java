package com.besome.sketch.editor.view.palette;

import android.content.Context;

import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.ViewBean;

import pro.sketchware.R;

public class IconConstraintLayout extends IconBase {
    public IconConstraintLayout(Context context) {
        super(context);
        initialize();
    }

    private void initialize() {
        setWidgetImage(R.drawable.ic_mtrl_view_constraint);
        setWidgetName("ConstraintLayout");
    }

    @Override
    public ViewBean getBean() {
        ViewBean viewBean = new ViewBean();
        viewBean.type = ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT;
        LayoutBean layoutBean = viewBean.layout;
        layoutBean.width = -1;
        layoutBean.height = -1;
        layoutBean.paddingLeft = 8;
        layoutBean.paddingTop = 8;
        layoutBean.paddingRight = 8;
        layoutBean.paddingBottom = 8;
        viewBean.convert = "androidx.constraintlayout.widget.ConstraintLayout";
        viewBean.isCustomWidget = false;
        return viewBean;
    }
}
