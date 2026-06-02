package neo.sketchware.tools;

import static neo.sketchware.SketchApplication.getContext;
import static neo.sketchware.utility.PropertiesUtil.parseReferName;

import android.util.Pair;
import android.view.View;

import androidx.annotation.NonNull;

import com.besome.sketch.beans.ViewBean;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import a.a.a.wq;
import neo.sketchware.utility.InvokeUtil;

public class ViewBeanParser {

    private static final int[] viewsCount = new int[100];
    private final XmlPullParser parser;
    private boolean skipRoot;
    private Pair<String, Map<String, String>> rootAttributes;
    private ArrayList<ViewBean> oldLayout;

    public ViewBeanParser(String xml) throws XmlPullParserException {
        this(new StringReader(xml));
    }

    public ViewBeanParser(String xml, ArrayList<ViewBean> oldLayout) throws XmlPullParserException {
        this(new StringReader(xml));
        this.oldLayout = oldLayout;
    }

    public ViewBeanParser(File path) throws XmlPullParserException, FileNotFoundException {
        this(new FileReader(path));
    }

    public ViewBeanParser(Reader reader) throws XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        parser = factory.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(reader);
    }

    public static String generateUniqueId(Set<String> ids, int type, String className) {
        String prefix;
        if (type == ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT || type >= 49) {
            prefix = "constraintLayout";
        } else {
            prefix = wq.b(type);
        }
        
        var name = ViewBean.getViewTypeName(type);
        if (type != ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW
                && type != ViewBean.VIEW_TYPE_LAYOUT_HSCROLLVIEW) {
            if (prefix.equals("linear")
                    && type == ViewBean.VIEW_TYPE_LAYOUT_LINEAR
                    && !name.equals(className)) {
                prefix = getSnakeCaseId(className);
            }
        }
        int count = ++viewsCount[type];
        String id = prefix + count;

        while (ids.contains(id)) {
            count = ++viewsCount[type];
            id = prefix + count;
        }

        return id;
    }

    public static String getSnakeCaseId(String id) {
        StringBuilder snakeCaseId = new StringBuilder();
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i != 0) {
                    snakeCaseId.append("_");
                }
                snakeCaseId.append(Character.toLowerCase(c));
            } else {
                snakeCaseId.append(c);
            }
        }
        return snakeCaseId.toString();
    }

    public static String getNameFromTag(@NonNull String s) {
        try {
            if (s.contains(".")) {
                return s.substring(s.lastIndexOf(".") + 1);
            }
        } catch (Exception ignored) {
        }
        return s;
    }

    public static int getViewTypeByClassName(String name) {
        var className = getNameFromTag(name);
        if (className.equals("HorizontalScrollView")) {
            className = "HScrollView";
        }

        int type = ViewBean.getViewTypeByTypeName(className);

        if (!name.contains(".")) {
            if (type == ViewBean.VIEW_TYPE_LAYOUT_LINEAR && !className.equals("LinearLayout")) {
                if (className.contains("Switch")) type = ViewBean.VIEW_TYPE_WIDGET_SWITCH;
                else if (className.contains("ProgressIndicator") || className.contains("ProgressBar") || className.contains("LoadingIndicator")) type = ViewBean.VIEW_TYPE_WIDGET_PROGRESSBAR;
                else if (className.contains("CheckBox") || className.contains("Chip")) type = ViewBean.VIEW_TYPE_WIDGET_CHECKBOX;
                else if (className.contains("Slider") || className.contains("SeekBar")) type = ViewBean.VIEW_TYPE_WIDGET_SEEKBAR;
                else if (className.contains("Button")) type = ViewBean.VIEW_TYPE_WIDGET_BUTTON;
                else if (className.contains("TextView") || className.contains("AutoComplete")) type = ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW;
                else if (className.contains("ImageView")) type = ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW;
                else if (className.contains("CardView")) type = 36;
                else if (className.contains("RecyclerView")) type = 48;
                else if (className.contains("FloatingActionButton") || className.contains("FAB")) type = ViewBean.VIEW_TYPE_WIDGET_FAB;
                else if (className.contains("ConstraintLayout")) type = ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT;
            }
        }

        return getViewTypeByTag(name, type);
    }

    public static int getViewTypeByTag(String tag, int defaultType) {
        var type = ViewBeanFactory.getConsideredTypeViewByName(getNameFromTag(tag), defaultType);
        if (type == ViewBean.VIEW_TYPE_LAYOUT_LINEAR && !tag.contains(".")) {
            var view = InvokeUtil.createView(getContext(), tag);
            if (view != null) {
                Class<?> clazz = view.getClass();
                Class<?> viewClazz = View.class.getSuperclass();
                while (clazz != viewClazz) {
                    var className = clazz.getSimpleName();
                    if ("View".equals(className) || "LinearLayout".equals(className)) {
                        break;
                    }
                    type = ViewBean.getViewTypeByTypeName(className);
                    clazz = clazz.getSuperclass();
                }
            }
        }
        return type;
    }

    public void setSkipRoot(boolean skipRoot) {
        this.skipRoot = skipRoot;
    }

    public Pair<String, Map<String, String>> getRootAttributes() {
        return rootAttributes;
    }

    public ArrayList<ViewBean> parse() throws XmlPullParserException, IOException {
        Set<String> ids =
                new HashSet<>(
                        Arrays.asList(
                                "root", "_coordinator", "_app_bar", "_toolbar", "_fab", "_drawer"));
        ArrayList<ViewBean> beans = new ArrayList<>();
        Map<String, Map<String, String>> beansAttributes = new HashMap<>();
        Stack<ViewBean> viewStack = new Stack<>();
        int index = 0;
        boolean isRootSkipped = !skipRoot;

        while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
            switch (parser.getEventType()) {
                case XmlPullParser.START_TAG -> {
                    var name = parser.getName();
                    if (!isRootSkipped) {
                        Map<String, String> attributes = new LinkedHashMap<>();
                        for (int i = 0; i < parser.getAttributeCount(); i++) {
                            if (!parser.getAttributeName(i).startsWith("xmlns")) {
                                attributes.put(
                                        parser.getAttributeName(i), parser.getAttributeValue(i));
                            }
                        }
                        rootAttributes = Pair.create(name, attributes);
                        isRootSkipped = true;
                        break; 
                    }
                    
                    var className = getNameFromTag(name);
                    int type = getViewTypeByClassName(name);

                    String attrId = parser.getAttributeValue(null, "android:id");
                    String id = "";
                    
                    if (className.equals("include")) {
                        if (attrId != null && !attrId.isEmpty()) {
                            id = parseReferName(attrId, "/");
                        } else {
                            String layout = parser.getAttributeValue(null, "layout");
                            if (layout != null) {
                                id = parseReferName(layout, "/");
                                // We append a random hash to ensure duplicated layout names without explicit IDs don't collide
                                if (ids.contains(id)) {
                                    id = generateUniqueId(ids, type, className);
                                }
                            } else {
                                id = generateUniqueId(ids, type, className);
                            }
                        }
                    } else {
                        id = attrId != null && !ids.contains(parseReferName(attrId, "/"))
                                    ? parseReferName(attrId, "/")
                                    : generateUniqueId(ids, type, className);
                    }

                    boolean isCustom = false;
                    String customView = "";
                    String convert = name;
                    boolean oldBeanFound = false;

                    if (oldLayout != null) {
                        for (ViewBean oldBean : oldLayout) {
                            if (oldBean.id.equals(id)) {
                                if (type == 0 || type == 14) {
                                    type = oldBean.type;
                                }
                                if (oldBean.convert.equals(convert) || className.equals("include")) {
                                    isCustom = oldBean.isCustomWidget;
                                    customView = oldBean.customView;
                                    oldBeanFound = true;
                                }
                                break;
                            }
                        }
                    } 
                    
                    if (!oldBeanFound && name.contains(".")) {
                        if (type == ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT || className.equals("ConstraintLayout")) {
                            isCustom = false;
                            convert = "androidx.constraintlayout.widget.ConstraintLayout"; 
                        } else if (name.equals("androidx.coordinatorlayout.widget.CoordinatorLayout")) {
                            isCustom = true; 
                            convert = name;
                            type = ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
                        } else {
                            isCustom = true;
                            convert = name;
                        }
                    } else if (type == ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT) {
                         isCustom = false;
                         convert = "androidx.constraintlayout.widget.ConstraintLayout";
                    }

                    ViewBean bean = new ViewBean(id, type);
                    bean.isCustomWidget = isCustom;
                    bean.customView = customView;
                    bean.convert = convert;

                    ViewBean parent = viewStack.isEmpty() ? null : viewStack.peek();
                    int parentType = rootAttributes != null ? getViewTypeByClassName(rootAttributes.first) : ViewBean.VIEW_TYPE_LAYOUT_LINEAR;
                    
                    bean.parent = parent != null ? parent.id : "root";
                    
                    bean.parentType = bean.parent.equals("root") ? parentType : parent.type;
                    
                    bean.index = index;
                    Map<String, String> attributes = new LinkedHashMap<>();
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if (!parser.getAttributeName(i).startsWith("xmlns")) {
                            attributes.put(parser.getAttributeName(i), parser.getAttributeValue(i));
                        }
                    }
                    
                    beansAttributes.put(id, attributes);
                    beans.add(bean);
                    ids.add(id);
                    viewStack.push(bean);
                    index++;
                    break;
                }

                case XmlPullParser.END_TAG -> {
                    if (isRootSkipped && !viewStack.isEmpty()) {
                        viewStack.pop();
                    }
                    break;
                }
            }
            parser.next();
        }
        
        for (ViewBean bean : beans) {
            var attr = beansAttributes.getOrDefault(bean.id, null);
            if (attr != null) {
                new ViewBeanFactory(bean).applyAttributes(attr);
            }
        }
        return beans;
    }
}
