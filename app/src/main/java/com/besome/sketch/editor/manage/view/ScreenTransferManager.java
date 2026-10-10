package com.besome.sketch.editor.manage.view;

import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.jC;

public final class ScreenTransferManager {
    private static final Gson GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    private ScreenTransferManager() {
    }

    public static final class Result {
        public final boolean success;
        public final String message;

        private Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    /**
     * Renames an existing activity-style screen within one project. The filename suffix is the
     * stored type discriminator for Activity/Fragment/DialogFragment/BottomSheet screens.
     * This method validates all destination keys before writing and restores changed keys and
     * metadata if persistence fails.
     */
    @SuppressWarnings("unchecked")
    public static Result renameScreen(String projectId, ProjectFileBean requestedSource, ProjectFileBean requestedTarget) {
        if (isEmpty(projectId) || requestedSource == null || requestedTarget == null
                || isEmpty(requestedSource.fileName) || isEmpty(requestedTarget.fileName)) {
            return failure("Project and screen names are required.");
        }
        if (requestedSource.fileType != ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                || requestedTarget.fileType != ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) {
            return failure("Only activity-style screens can be renamed here.");
        }
        if (requestedSource.fileName.equals(requestedTarget.fileName)) {
            return new Result(true, "No screen rename was required.");
        }
        if ("main".equalsIgnoreCase(requestedSource.fileName)) {
            return failure("The main screen cannot be renamed or changed to another screen type.");
        }
        if (requestedSource.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)
                != requestedTarget.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
            return failure("Save the Drawer option separately before renaming the screen.");
        }

        hC projectFiles = null;
        eC projectData = null;
        ProjectFileBean storedScreen = null;
        ProjectFileBean storedDrawer = null;
        String originalStoredName = null;
        int originalOrientation = 0;
        int originalKeyboardSetting = 0;
        int originalOptions = 0;
        String originalPresetName = null;
        int originalTheme = ProjectFileBean.THEME_NONE;
        String originalDrawerName = null;
        ArrayList<MapCopy> copies = new ArrayList<>();
        ArrayList<BlockReferenceChange> referenceChanges = new ArrayList<>();
        boolean metadataChanged = false;
        boolean mapsChanged = false;

        try {
            projectFiles = jC.b(projectId);
            if (projectFiles == null || projectFiles.b() == null || projectFiles.c() == null) {
                return failure("The project screen registry could not be loaded. Nothing was changed.");
            }
            ArrayList<ProjectFileBean> activities = projectFiles.b();
            ArrayList<ProjectFileBean> otherFiles = projectFiles.c();
            storedScreen = findFile(activities, otherFiles, requestedSource.fileName, requestedSource.fileType);
            if (storedScreen == null) {
                return failure("The original screen no longer exists. Nothing was changed.");
            }

            for (ProjectFileBean existing : activities) {
                if (existing == null || existing == storedScreen || isEmpty(existing.fileName)) continue;
                if (sameName(existing.fileName, requestedTarget.fileName)) {
                    return failure("A screen with this name already exists in the project.");
                }
                if (ProjectFileBean.getActivityName(existing.fileName)
                        .equalsIgnoreCase(ProjectFileBean.getActivityName(requestedTarget.fileName))) {
                    return failure("This name would generate a duplicate Java screen class.");
                }
            }
            for (ProjectFileBean existing : otherFiles) {
                if (existing != null && sameName(existing.fileName, requestedTarget.fileName)) {
                    return failure("A screen or custom view with this name already exists in the project.");
                }
            }

            originalStoredName = storedScreen.fileName;
            originalOrientation = storedScreen.orientation;
            originalKeyboardSetting = storedScreen.keyboardSetting;
            originalOptions = storedScreen.options;
            originalPresetName = storedScreen.presetName;
            originalTheme = storedScreen.theme;

            ProjectFileBean storedTarget = copyBean(requestedTarget, requestedTarget.fileName);
            ProjectFileBean targetDrawer = null;
            if (storedScreen.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
                storedDrawer = findFile(activities, otherFiles, storedScreen.getDrawerName(), ProjectFileBean.PROJECT_FILE_TYPE_DRAWER);
                if (storedDrawer == null) {
                    return failure("This screen has a Drawer enabled, but its stored Drawer data is missing. Nothing was changed.");
                }
                originalDrawerName = storedDrawer.fileName;
                targetDrawer = copyBean(storedDrawer, ProjectFileBean.getDrawerName(storedTarget.fileName));
                for (ProjectFileBean existing : otherFiles) {
                    if (existing != null && existing != storedDrawer && sameName(existing.fileName, targetDrawer.fileName)) {
                        return failure("The destination Drawer name is already used by another project file.");
                    }
                }
            }

            projectData = jC.a(projectId);
            if (projectData == null) {
                return failure("The project screen data could not be loaded. Nothing was changed.");
            }
            Map<String, String> keyMap = buildKeyMap(storedScreen, storedTarget, storedDrawer, targetDrawer);
            copies = collectMapCopies(projectData, keyMap, storedScreen, storedTarget);
            boolean hasTargetLayout = false;
            for (MapCopy copy : copies) {
                if ("c".equals(copy.fieldName) && storedTarget.getXmlName().equals(copy.targetKey)) {
                    hasTargetLayout = true;
                }
            }
            if (!hasTargetLayout) {
                return failure("The screen layout data could not be found safely. Nothing was changed.");
            }

            for (MapCopy copy : copies) {
                Field field = findField(projectData.getClass(), copy.fieldName);
                if (field == null) return failure("A required screen-data field is unavailable. Nothing was changed.");
                field.setAccessible(true);
                Object fieldValue = field.get(projectData);
                if (!(fieldValue instanceof Map<?, ?>)) return failure("A required screen-data map is unavailable. Nothing was changed.");
                Map<Object, Object> dataMap = (Map<Object, Object>) fieldValue;
                if (!copy.sourceKey.equals(copy.targetKey) && dataMap.containsKey(copy.targetKey)) {
                    return failure("The renamed screen would overwrite existing project data. Nothing was changed.");
                }
                copy.targetMap = dataMap;
                copy.sourceValue = dataMap.get(copy.sourceKey);
                if (copy.sourceValue == null && !dataMap.containsKey(copy.sourceKey)) {
                    return failure("A required original screen-data entry is missing. Nothing was changed.");
                }
            }

            // Add the new entries only after every destination key passed validation.
            mapsChanged = true;
            for (MapCopy copy : copies) {
                copy.targetMap.put(copy.targetKey, copy.value);
            }

            // Update navigation references in all screens, not just the renamed screen's blocks.
            rewriteProjectReferences(projectData, storedScreen, storedTarget, referenceChanges);

            for (MapCopy copy : copies) {
                if (!copy.sourceKey.equals(copy.targetKey)) {
                    copy.targetMap.remove(copy.sourceKey);
                }
            }

            storedScreen.fileName = storedTarget.fileName;
            storedScreen.fileType = storedTarget.fileType;
            storedScreen.orientation = storedTarget.orientation;
            storedScreen.keyboardSetting = storedTarget.keyboardSetting;
            storedScreen.options = storedTarget.options;
            storedScreen.presetName = storedTarget.presetName;
            storedScreen.theme = ProjectFileBean.THEME_NONE;
            if (storedDrawer != null && targetDrawer != null) {
                storedDrawer.fileName = targetDrawer.fileName;
            }
            metadataChanged = true;

            projectFiles.l();
            projectFiles.j();
            projectData.a(projectFiles);
            saveScreenData(projectData);
            return new Result(true, "Screen name/type updated and linked screen data was migrated.");
        } catch (Exception e) {
            try {
                for (int i = referenceChanges.size() - 1; i >= 0; i--) {
                    BlockReferenceChange change = referenceChanges.get(i);
                    if (change.block.parameters != null && change.index < change.block.parameters.size()) {
                        change.block.parameters.set(change.index, change.oldValue);
                    }
                }
                if (mapsChanged) {
                    for (int i = copies.size() - 1; i >= 0; i--) {
                        MapCopy copy = copies.get(i);
                        if (copy.targetMap == null) continue;
                        if (!copy.sourceKey.equals(copy.targetKey)) copy.targetMap.remove(copy.targetKey);
                        copy.targetMap.put(copy.sourceKey, copy.sourceValue);
                    }
                }
                if (metadataChanged && storedScreen != null) {
                    storedScreen.fileName = originalStoredName;
                    storedScreen.orientation = originalOrientation;
                    storedScreen.keyboardSetting = originalKeyboardSetting;
                    storedScreen.options = originalOptions;
                    storedScreen.presetName = originalPresetName;
                    storedScreen.theme = originalTheme;
                    if (storedDrawer != null) storedDrawer.fileName = originalDrawerName;
                }
                if (projectFiles != null && projectData != null) {
                    projectFiles.l();
                    projectFiles.j();
                    projectData.a(projectFiles);
                    saveScreenData(projectData);
                }
            } catch (Exception rollbackException) {
                return failure("The screen update failed and rollback could not be confirmed. Reopen and inspect the project before building.");
            }
            return failure("The screen could not be updated safely: " + safeMessage(e));
        }
    }

    private static void rewriteProjectReferences(eC data, ProjectFileBean sourceScreen, ProjectFileBean targetScreen,
                                                 ArrayList<BlockReferenceChange> changes) throws Exception {
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        for (Class<?> type = data.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !isScreenDataField(field.getName())) continue;
                field.setAccessible(true);
                Object fieldValue = field.get(data);
                if (!(fieldValue instanceof Map<?, ?>)) continue;
                for (Object value : ((Map<?, ?>) fieldValue).values()) {
                    rewriteBlockReferences(value, sourceScreen, targetScreen, changes, visited);
                }
            }
        }
    }

    private static void rewriteBlockReferences(Object value, ProjectFileBean sourceScreen, ProjectFileBean targetScreen,
                                               ArrayList<BlockReferenceChange> changes, IdentityHashMap<Object, Boolean> visited) {
        if (value == null || visited.put(value, Boolean.TRUE) != null) return;
        if (value instanceof BlockBean) {
            BlockBean block = (BlockBean) value;
            if (sourceScreen.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                    && "intentSetScreen".equals(block.opCode) && block.parameters != null && block.parameters.size() > 1
                    && sourceScreen.getActivityName().equals(block.parameters.get(1))
                    && !sourceScreen.getActivityName().equals(targetScreen.getActivityName())) {
                changes.add(new BlockReferenceChange(block, 1, block.parameters.get(1)));
                block.parameters.set(1, targetScreen.getActivityName());
            }
            return;
        }
        if (value instanceof ViewBean) return;
        if (value instanceof Map<?, ?>) {
            for (Object item : ((Map<?, ?>) value).values()) rewriteBlockReferences(item, sourceScreen, targetScreen, changes, visited);
        } else if (value instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) value) rewriteBlockReferences(item, sourceScreen, targetScreen, changes, visited);
        } else if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) rewriteBlockReferences(Array.get(value, i), sourceScreen, targetScreen, changes, visited);
        }
    }

    @SuppressWarnings("unchecked")
    public static Result transfer(String sourceProjectId, String targetProjectId, String sourceFileName, int sourceFileType, String targetFileName) {
        if (isEmpty(sourceProjectId) || isEmpty(targetProjectId) || isEmpty(sourceFileName) || isEmpty(targetFileName)) {
            return failure("Project and screen names are required.");
        }

        ArrayList<MapCopy> copies = new ArrayList<>();
        ArrayList<ProjectFileBean> sourceActivities;
        ArrayList<ProjectFileBean> sourceOtherFiles;
        ProjectFileBean sourceScreen;
        ProjectFileBean sourceDrawer = null;
        ProjectFileBean targetScreen = null;
        ProjectFileBean targetDrawer = null;
        hC sourceFiles;
        hC targetFiles = null;
        eC sourceData;
        eC targetData = null;
        boolean targetScreenAdded = false;
        boolean targetDrawerAdded = false;

        try {
            sourceFiles = jC.b(sourceProjectId);
            sourceActivities = new ArrayList<>(sourceFiles.b());
            sourceOtherFiles = new ArrayList<>(sourceFiles.c());
            sourceScreen = findFile(sourceActivities, sourceOtherFiles, sourceFileName, sourceFileType);
            if (sourceScreen == null) {
                return failure("The selected screen no longer exists in the source project.");
            }

            if (sourceScreen.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                    && sourceScreen.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) {
                sourceDrawer = findFile(sourceActivities, sourceOtherFiles, sourceScreen.getDrawerName(), ProjectFileBean.PROJECT_FILE_TYPE_DRAWER);
                if (sourceDrawer == null) {
                    return failure("This activity has a drawer enabled, but its drawer data is missing. Nothing was imported.");
                }
            }

            targetScreen = copyBean(sourceScreen, targetFileName);
            if (sourceDrawer != null) {
                targetDrawer = copyBean(sourceDrawer, "_drawer_" + targetFileName);
            }

            sourceData = jC.a(sourceProjectId);
            Map<String, String> keyMap = buildKeyMap(sourceScreen, targetScreen, sourceDrawer, targetDrawer);
            copies = collectMapCopies(sourceData, keyMap, sourceScreen, targetScreen);
            boolean hasLayoutData = false;
            for (MapCopy copy : copies) {
                if ("c".equals(copy.fieldName) && targetScreen.getXmlName().equals(copy.targetKey)) {
                    hasLayoutData = true;
                    break;
                }
            }
            if (!hasLayoutData) {
                return failure("The screen layout data could not be read safely. Nothing was changed.");
            }

            targetFiles = jC.b(targetProjectId);
            ArrayList<ProjectFileBean> targetActivities = new ArrayList<>(targetFiles.b());
            ArrayList<ProjectFileBean> targetOtherFiles = new ArrayList<>(targetFiles.c());
            String nameProblem = validateTargetName(targetScreen, targetDrawer, targetActivities, targetOtherFiles);
            if (nameProblem != null) {
                return failure(nameProblem);
            }

            String missingCustomView = findMissingCustomView(sourceData, sourceScreen, targetOtherFiles, targetScreen);
            if (missingCustomView != null) {
                return failure("This screen uses the custom view '" + missingCustomView + "', which is not in the target project. Import that custom view first. Nothing was changed.");
            }

            targetData = jC.a(targetProjectId);
            for (MapCopy copy : copies) {
                Field field = findField(targetData.getClass(), copy.fieldName);
                if (field == null) {
                    return failure("A required screen-data field is unavailable. Nothing was changed.");
                }
                field.setAccessible(true);
                Object value = field.get(targetData);
                if (!(value instanceof Map)) {
                    return failure("A required screen-data map is unavailable. Nothing was changed.");
                }
                if (((Map<?, ?>) value).containsKey(copy.targetKey)) {
                    return failure("A screen-data entry with the new name already exists. Choose another name.");
                }
                copy.targetMap = (Map<Object, Object>) value;
            }

            for (MapCopy copy : copies) {
                copy.targetMap.put(copy.targetKey, copy.value);
            }
            targetFiles.a(targetScreen);
            targetScreenAdded = true;
            if (targetDrawer != null) {
                targetFiles.a(targetDrawer);
                targetDrawerAdded = true;
            }
            targetFiles.l();
            targetFiles.j();
            targetData.a(targetFiles);
            saveScreenData(targetData);
            return new Result(true, "Screen imported successfully. Check that any project-specific libraries, fonts, images, and sounds used by the screen are available in the target project.");
        } catch (Exception e) {
            try {
                for (MapCopy copy : copies) {
                    if (copy.targetMap != null) {
                        copy.targetMap.remove(copy.targetKey);
                    }
                }
                if (targetFiles != null) {
                    if (targetDrawerAdded && targetDrawer != null) {
                        targetFiles.b(targetDrawer.fileType, targetDrawer.fileName);
                    }
                    if (targetScreenAdded && targetScreen != null) {
                        targetFiles.b(targetScreen.fileType, targetScreen.fileName);
                    }
                    if (targetScreenAdded || targetDrawerAdded) {
                        targetFiles.l();
                        targetFiles.j();
                        if (targetData != null) {
                            targetData.a(targetFiles);
                            saveScreenData(targetData);
                        }
                    }
                }
            } catch (Exception rollbackException) {
                return failure("The screen could not be imported and rollback could not be confirmed. Do not build this project until it has been reopened and checked.");
            }
            return failure("The screen could not be imported safely: " + safeMessage(e));
        }
    }

    private static ArrayList<MapCopy> collectMapCopies(eC sourceData, Map<String, String> keyMap, ProjectFileBean sourceScreen, ProjectFileBean targetScreen) throws Exception {
        ArrayList<MapCopy> copies = new ArrayList<>();
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        for (Class<?> type = sourceData.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !isScreenDataField(field.getName())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(sourceData);
                if (!(value instanceof Map)) {
                    continue;
                }
                Map<?, ?> sourceMap = (Map<?, ?>) value;
                for (Map.Entry<String, String> key : keyMap.entrySet()) {
                    if (sourceMap.containsKey(key.getKey())) {
                        Object sourceValue = sourceMap.get(key.getKey());
                        Object copiedValue = deepCopy(sourceValue, seen);
                        rewriteReferences(copiedValue, sourceScreen, targetScreen, new IdentityHashMap<>());
                        copies.add(new MapCopy(field.getName(), key.getKey(), key.getValue(), sourceValue, copiedValue));
                    }
                }
            }
        }
        return copies;
    }

    private static boolean isScreenDataField(String name) {
        return "c".equals(name) || "d".equals(name) || "e".equals(name) || "f".equals(name)
                || "g".equals(name) || "h".equals(name) || "i".equals(name) || "j".equals(name);
    }

    private static Object deepCopy(Object value, IdentityHashMap<Object, Boolean> seen) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value.getClass().isEnum()) {
            return value;
        }
        if (seen.containsKey(value)) {
            throw new IllegalStateException("A cyclic screen-data structure was found.");
        }
        seen.put(value, Boolean.TRUE);
        try {
            if (value instanceof Pair<?, ?>) {
                Pair<?, ?> pair = (Pair<?, ?>) value;
                return new Pair<>(deepCopy(pair.first, seen), deepCopy(pair.second, seen));
            }
            if (value instanceof Map<?, ?>) {
                Map<Object, Object> result = new HashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    result.put(deepCopy(entry.getKey(), seen), deepCopy(entry.getValue(), seen));
                }
                return result;
            }
            if (value instanceof Collection<?>) {
                Collection<Object> result = value instanceof Set<?> ? new LinkedHashSet<>() : new ArrayList<>();
                for (Object item : (Collection<?>) value) {
                    result.add(deepCopy(item, seen));
                }
                return result;
            }
            if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                Object result = Array.newInstance(value.getClass().getComponentType(), length);
                for (int i = 0; i < length; i++) {
                    Array.set(result, i, deepCopy(Array.get(value, i), seen));
                }
                return result;
            }
            return GSON.fromJson(GSON.toJson(value), value.getClass());
        } finally {
            seen.remove(value);
        }
    }

    private static void rewriteReferences(Object value, ProjectFileBean sourceScreen, ProjectFileBean targetScreen, IdentityHashMap<Object, Boolean> visited) {
        if (value == null || visited.put(value, Boolean.TRUE) != null) {
            return;
        }
        if (value instanceof ViewBean) {
            ViewBean view = (ViewBean) value;
            if (sourceScreen.fileType == ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW
                    && sourceScreen.fileName.equals(view.customView)) {
                view.customView = targetScreen.fileName;
            }
            return;
        }
        if (value instanceof BlockBean) {
            BlockBean block = (BlockBean) value;
            if (sourceScreen.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                    && "intentSetScreen".equals(block.opCode) && block.parameters != null && block.parameters.size() > 1
                    && sourceScreen.getActivityName().equals(block.parameters.get(1))) {
                block.parameters.set(1, targetScreen.getActivityName());
            }
            return;
        }
        if (value instanceof Map<?, ?>) {
            for (Object item : ((Map<?, ?>) value).values()) {
                rewriteReferences(item, sourceScreen, targetScreen, visited);
            }
        } else if (value instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) value) {
                rewriteReferences(item, sourceScreen, targetScreen, visited);
            }
        } else if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                rewriteReferences(Array.get(value, i), sourceScreen, targetScreen, visited);
            }
        }
    }

    private static Map<String, String> buildKeyMap(ProjectFileBean sourceScreen, ProjectFileBean targetScreen, ProjectFileBean sourceDrawer, ProjectFileBean targetDrawer) {
        Map<String, String> result = new LinkedHashMap<>();
        addKey(result, sourceScreen.fileName, targetScreen.fileName);
        addKey(result, sourceScreen.getXmlName(), targetScreen.getXmlName());
        if (sourceScreen.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) {
            addKey(result, sourceScreen.getJavaName(), targetScreen.getJavaName());
            addKey(result, sourceScreen.getActivityName(), targetScreen.getActivityName());
        }
        if (sourceDrawer != null && targetDrawer != null) {
            addKey(result, sourceDrawer.fileName, targetDrawer.fileName);
            addKey(result, sourceDrawer.getXmlName(), targetDrawer.getXmlName());
            addKey(result, sourceDrawer.getDrawersJavaName(), targetDrawer.getDrawersJavaName());
        }
        return result;
    }

    private static void addKey(Map<String, String> keys, String oldKey, String newKey) {
        if (oldKey != null && !oldKey.isEmpty() && newKey != null && !newKey.isEmpty()) {
            keys.put(oldKey, newKey);
        }
    }

    private static ProjectFileBean copyBean(ProjectFileBean source, String targetFileName) {
        ProjectFileBean result = new ProjectFileBean(source.fileType, targetFileName, source.orientation, source.keyboardSetting, source.options);
        result.presetName = source.presetName;
        result.theme = source.theme;
        return result;
    }

    private static ProjectFileBean findFile(List<ProjectFileBean> activities, List<ProjectFileBean> otherFiles, String fileName, int fileType) {
        for (ProjectFileBean bean : activities) {
            if (bean.fileType == fileType && bean.fileName.equals(fileName)) {
                return bean;
            }
        }
        for (ProjectFileBean bean : otherFiles) {
            if (bean.fileType == fileType && bean.fileName.equals(fileName)) {
                return bean;
            }
        }
        return null;
    }

    private static String validateTargetName(ProjectFileBean targetScreen, ProjectFileBean targetDrawer, List<ProjectFileBean> targetActivities, List<ProjectFileBean> targetOtherFiles) {
        for (ProjectFileBean existing : targetActivities) {
            if (sameName(existing.fileName, targetScreen.fileName)) {
                return "A screen with this name already exists in the target project.";
            }
            if (targetScreen.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                    && existing.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                    && ProjectFileBean.getActivityName(existing.fileName).equalsIgnoreCase(ProjectFileBean.getActivityName(targetScreen.fileName))) {
                return "This name would generate a duplicate Java activity class. Choose another name.";
            }
        }
        for (ProjectFileBean existing : targetOtherFiles) {
            if (sameName(existing.fileName, targetScreen.fileName)) {
                return "A screen or custom view with this name already exists in the target project.";
            }
            if (targetDrawer != null && sameName(existing.fileName, targetDrawer.fileName)) {
                return "The drawer layout name is already in use in the target project.";
            }
        }
        return null;
    }

    private static String findMissingCustomView(eC sourceData, ProjectFileBean sourceScreen, List<ProjectFileBean> targetOtherFiles, ProjectFileBean targetScreen) {
        ArrayList<ViewBean> screenViews = sourceData.d(sourceScreen.getXmlName());
        if (screenViews == null) {
            return null;
        }
        for (ViewBean view : screenViews) {
            if (view == null || !usesCustomView(view)) {
                continue;
            }
            String name = view.customView;
            if (name == null || name.isEmpty() || "none".equalsIgnoreCase(name)
                    || (sourceScreen.fileType == ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW && name.equals(sourceScreen.fileName))) {
                continue;
            }
            boolean existsInTarget = containsFileName(targetOtherFiles, name, ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW);
            if (!existsInTarget) {
                return name;
            }
        }
        return null;
    }

    private static boolean usesCustomView(ViewBean view) {
        return view.type == 9 || view.type == 10 || view.type == 25 || view.type == 48 || view.type == 31;
    }

    private static boolean containsFileName(List<ProjectFileBean> files, String name, int fileType) {
        for (ProjectFileBean bean : files) {
            if (bean.fileType == fileType && sameName(bean.fileName, name)) {
                return true;
            }
        }
        return false;
    }

    private static void saveScreenData(eC data) throws Exception {
        for (Class<?> type = data.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod("k");
                method.setAccessible(true);
                method.invoke(data);
                return;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException("The screen data save method is unavailable.");
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static boolean sameName(String first, String second) {
        return first != null && second != null && first.equalsIgnoreCase(second);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    private static Result failure(String message) {
        return new Result(false, message);
    }

    private static final class MapCopy {
        final String fieldName;
        final Object sourceKey;
        final Object targetKey;
        final Object value;
        Object sourceValue;
        Map<Object, Object> targetMap;

        MapCopy(String fieldName, Object sourceKey, Object targetKey, Object sourceValue, Object value) {
            this.fieldName = fieldName;
            this.sourceKey = sourceKey;
            this.targetKey = targetKey;
            this.sourceValue = sourceValue;
            this.value = value;
        }
    }

    private static final class BlockReferenceChange {
        final BlockBean block;
        final int index;
        final String oldValue;

        BlockReferenceChange(BlockBean block, int index, String oldValue) {
            this.block = block;
            this.index = index;
            this.oldValue = oldValue;
        }
    }
}
