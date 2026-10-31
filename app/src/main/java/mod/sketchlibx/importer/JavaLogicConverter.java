package mod.sketchlibx.importer;


import android.util.Log;

import com.besome.sketch.beans.BlockBean;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import mod.sketchlibx.project.editor.BlocksConverter;

/**
 * Converts supported Android Studio Java patterns into Sketchware Neo blocks.
 * Unsupported code is reported and kept in addSourceDirectly blocks. Only verified
 * block formats are generated. Parsing is pattern-based, so unusual formatting can
 * affect statement splitting. BlockSection reserves statement IDs before nested
 * blocks so the lowest ID remains the section entry point.
 */
public class JavaLogicConverter {

    private static final String TAG = "JavaLogicConverter";
    private static final Gson BLOCK_JSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

     

    public static class ConversionResult {
        /** Section name -> block-list content (one JSON object per line, no [] wrapper). */
        public final Map<String, String> sections = new LinkedHashMap<>();
        /** "<code>:<name>" lines for _var (deduplicated, insertion order). */
        public final List<String> varLines = new ArrayList<>();
        /** _components content (one JSON object per line). Always present, may be empty. */
        public String componentsSection = "";
        /** _events content (one JSON object per line, one per section actually registered). */
        public String eventsSection = "";
        /** Human-readable list of everything NOT converted to native blocks, with why. */
        public final List<String> unsupported = new ArrayList<>();
        /** Function declaration lines stored in data/logic @<Activity>.java_func. */
        public String functionDeclarations = "";
    }

     

    /** Declared class fields this converter is confident are simple scalars, by name -> typeCode. */
    private final Map<String, Integer> knownScalarFields = new LinkedHashMap<>();
    /** The view id for each local variable or ViewBinding reference. */
    private final Map<String, String> localVarToViewId = new LinkedHashMap<>();   // e.g. "btnSave" -> "btnSave"
    private final Map<String, String> bindingRefToViewId = new LinkedHashMap<>(); // "binding.btnSave" -> "btnSave"
    private final Map<String, Integer> knownViewTypes = new LinkedHashMap<>();
    private final Map<String, HelperMethod> helperMethods = new LinkedHashMap<>();
    private final Map<String, String> helperSpecs = new LinkedHashMap<>();
    private final java.util.Set<String> ambiguousHelperNames = new java.util.HashSet<>();

    private final List<String> unsupported = new ArrayList<>();
    private final Map<String, String> componentsById = new LinkedHashMap<>(); // componentId -> JSON

    private int intentComponentCounter = 0;

     

    /** Converts source using only view IDs found in the imported layout. */
    public ConversionResult convert(String javaSource, java.util.Set<String> knownViewIds) {
        Map<String, Integer> defaultTypes = new LinkedHashMap<>();
        if (knownViewIds != null) for (String id : knownViewIds) defaultTypes.put(id, 0);
        return convert(javaSource, knownViewIds, defaultTypes, true);
    }

    /** Uses imported widget types and the MoreBlocks setting. Unsupported code is kept as source. */
    public ConversionResult convert(String javaSource, java.util.Set<String> knownViewIds,
                                    Map<String, Integer> viewTypes, boolean extractCustomMethods) {
        ConversionResult result = new ConversionResult();
        unsupported.clear();
        knownScalarFields.clear();
        localVarToViewId.clear();
        bindingRefToViewId.clear();
        knownViewTypes.clear();
        if (viewTypes != null) knownViewTypes.putAll(viewTypes);
        helperMethods.clear();
        helperSpecs.clear();
        ambiguousHelperNames.clear();
        componentsById.clear();

        try {
            scanClassFields(javaSource, result);
            scanViewBindingsAndFindViewById(javaSource, knownViewIds);
            if (extractCustomMethods) discoverCustomMethods(javaSource, result);

            String onCreateBody = extractMethodBody(javaSource, "onCreate");
            if (onCreateBody != null) {
                BlockSection section = new BlockSection();
                // Sketchware Neo owns Activity view inflation and its content view. Remove
                // framework setup declarations before translating, while retaining ordinary
                // statements that merely mention a view lookup inside a larger expression.
                List<String> logicStatements = removeManagedViewSetupStatements(splitStatements(onCreateBody),
                        "initializeLogic");
                // Click listener registration is emitted as its own Sketchware event
                // section. Remove it before alias rewriting, otherwise a local Java name
                // such as `buttonSave` would be changed to the widget id and the listener
                // would no longer be recognized as already handled.
                List<String> eventFilteredStatements = removeResolvedClickListenerStatementList(logicStatements, knownViewIds);
                List<String> normalizedStatements = rewriteViewAliases(eventFilteredStatements);
                convertStatements(normalizedStatements, section, result);
                String rendered = section.render();
                // Prefer Neo's real BlocksConverter after replacing Java local/binding view
                // references with the actual Sketchware widget ids. This prevents stale
                // variables from producing source blocks that reference no generated field.
                String richInput = joinStatements(normalizedStatements);
                String rich = containsCustomMethodCall(richInput)
                        ? null : convertWithNativeBlockConverter(richInput, java.util.Collections.emptyMap(), result, "onCreate");
                result.sections.put("onCreate_initializeLogic", rich != null ? rich : rendered);
            }

            String onBackPressedBody = extractMethodBody(javaSource, "onBackPressed");
            if (onBackPressedBody != null) {
                List<String> backStatements = rewriteViewAliases(removeManagedViewSetupStatements(
                        splitStatements(onBackPressedBody), "onBackPressed"));
                String normalizedBackBody = joinStatements(backStatements);
                BlockSection section = new BlockSection();
                convertStatements(backStatements, section, result);
                String rendered = section.render();
                String rich = containsCustomMethodCall(normalizedBackBody) ? null
                        : convertWithNativeBlockConverter(normalizedBackBody, java.util.Collections.emptyMap(), result, "onBackPressed");
                result.sections.put("onBackPressed_onBackPressed", rich != null ? rich : rendered);
            }

            convertSupportedActivityCallbacks(javaSource, result, knownViewIds);

            // onClickListener registrations found anywhere in the class (field-level
            // setup in onCreate is the common case, but this scans the whole file so
            // listeners attached in helper methods are still found).
            findAndConvertClickListeners(javaSource, result, knownViewIds);

            for (String unsupportedCallback : new String[]{"onRestart", "onActivityResult", "onRequestPermissionsResult", "onNewIntent",
                    "onSaveInstanceState", "onRestoreInstanceState", "onCreateOptionsMenu", "onOptionsItemSelected",
                    "onNavigationItemSelected", "onCheckedChanged", "onTextChanged", "onItemClick", "onItemSelected",
                    "onNothingSelected", "onLongClick", "onTouch", "onKey", "onConfigurationChanged",
                    "onWindowFocusChanged"}) {
                if (extractMethodBody(javaSource, unsupportedCallback) != null) {
                    String msg = "UNSUPPORTED: " + unsupportedCallback + "() has no verified native Sketchware event-section mapping in this importer. Original Java is preserved, but this callback is not wired to blocks.";
                    result.unsupported.add(msg);
                    Log.w(TAG, msg);
                }
            }

        } catch (Exception e) {
            String msg = "UNSUPPORTED: whole-file conversion aborted (" + e.getMessage()
                    + ") — original .java source is still preserved as copied, unconverted.";
            result.unsupported.add(msg);
            Log.w(TAG, msg, e);
        }

         
        for (Map.Entry<String, Integer> f : knownScalarFields.entrySet()) {
            result.varLines.add(f.getValue() + ":" + f.getKey());
        }

        // _components (always present, even empty)
        StringBuilder comp = new StringBuilder();
        for (String json : componentsById.values()) comp.append(json).append("\n");
        result.componentsSection = comp.toString();

        // _events (always present, even empty) — one entry per registered event section,
        // excluding the two special always-exists sections.
        StringBuilder events = new StringBuilder();
        for (String sectionName : result.sections.keySet()) {
            if (sectionName.equals("onCreate_initializeLogic") || sectionName.endsWith("_moreBlock")) continue;
            int lastUnderscore = sectionName.lastIndexOf('_');
            if (lastUnderscore <= 0) continue;
            String targetId = sectionName.substring(0, lastUnderscore);
            String eventName = sectionName.substring(lastUnderscore + 1);
            boolean activityEvent = isActivityEvent(targetId, eventName);
            int targetType = activityEvent ? 0 : knownViewTypes.containsKey(targetId) ? knownViewTypes.get(targetId) : 0;
            int eventType = activityEvent ? 3 : 1;
            events.append("{\"eventName\":\"").append(eventName).append("\",")
                  .append("\"eventType\":").append(eventType).append(",\"targetId\":\"").append(targetId).append("\",")
                  .append("\"targetType\":").append(targetType).append("}\n");
        }
        result.eventsSection = events.toString();
        result.functionDeclarations = buildFunctionDeclarations();

        result.unsupported.addAll(0, unsupported);
        return result;
    }

    private void convertSupportedActivityCallbacks(String javaSource, ConversionResult result,
                                                    java.util.Set<String> knownViewIds) {
        for (String callback : new String[]{"onStart", "onResume", "onPause", "onStop", "onDestroy", "onPostCreate"}) {
            String body = extractSupportedLifecycleBody(javaSource, callback);
            if (body == null) continue;
            List<String> statements = rewriteViewAliases(removeManagedViewSetupStatements(
                    splitStatements(body), callback));
            String normalized = joinStatements(statements);
            BlockSection section = new BlockSection();
            convertStatements(statements, section, result);
            String fallback = section.render();
            String rich = containsCustomMethodCall(normalized) ? null
                    : convertWithNativeBlockConverter(normalized, java.util.Collections.emptyMap(), result, callback);
            result.sections.put(callback + "_" + callback, rich != null ? rich : fallback);
        }
    }

    private String extractSupportedLifecycleBody(String javaSource, String callback) {
        try {
            CompilationUnit unit = StaticJavaParser.parse(javaSource);
            for (TypeDeclaration<?> type : unit.getTypes()) {
                for (MethodDeclaration method : type.getMethods()) {
                    if (!callback.equals(method.getNameAsString()) || !method.getBody().isPresent()) continue;
                    if ("onPostCreate".equals(callback)) {
                        if (method.getParameters().size() != 1
                                || !method.getParameter(0).getType().asString().endsWith("Bundle")) continue;
                        String parameterName = method.getParameter(0).getNameAsString();
                        for (com.github.javaparser.ast.expr.NameExpr name : method.getBody().get().findAll(com.github.javaparser.ast.expr.NameExpr.class)) {
                            if (parameterName.equals(name.getNameAsString())) name.setName("_savedInstanceState");
                        }
                    } else if (!method.getParameters().isEmpty()) {
                        continue;
                    }
                    method.getBody().get().getStatements().removeIf(statement -> {
                        if (!statement.isExpressionStmt()) return false;
                        com.github.javaparser.ast.expr.Expression expression = statement.asExpressionStmt().getExpression();
                        if (!expression.isMethodCallExpr()) return false;
                        com.github.javaparser.ast.expr.MethodCallExpr call = expression.asMethodCallExpr();
                        return call.getScope().isPresent() && call.getScope().get().isSuperExpr()
                                && callback.equals(call.getNameAsString());
                    });
                    String block = method.getBody().get().toString();
                    return block.length() >= 2 ? block.substring(1, block.length() - 1).trim() : "";
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not parse lifecycle callback " + callback + "; original callback remains in source.", e);
        }
        return null;
    }

    private boolean isActivityEvent(String targetId, String eventName) {
        return targetId.equals(eventName) && ("onBackPressed".equals(eventName)
                || "onPostCreate".equals(eventName) || "onStart".equals(eventName)
                || "onResume".equals(eventName) || "onPause".equals(eventName)
                || "onStop".equals(eventName) || "onDestroy".equals(eventName));
    }

     

    private static final class HelperMethod {
        final String name;
        final String body;
        final List<String> parameters = new ArrayList<>();
        final List<String> parameterTypes = new ArrayList<>();
        final String declaration;
        final String returnType;
        HelperMethod(String name, String body, List<String> names, List<String> types,
                     String declaration, String returnType) {
            this.name = name; this.body = body; this.parameters.addAll(names);
            this.parameterTypes.addAll(types); this.declaration = declaration;
            this.returnType = returnType == null ? "" : returnType;
        }
    }

    private static final class MoreBlockCallArgument {
        final String value;
        final String expressionType;
        final String expression;

        private MoreBlockCallArgument(String value, String expressionType, String expression) {
            this.value = value;
            this.expressionType = expressionType;
            this.expression = expression;
        }

        static MoreBlockCallArgument value(String value) {
            return new MoreBlockCallArgument(value, null, null);
        }

        static MoreBlockCallArgument expression(String type, String expression) {
            return new MoreBlockCallArgument(null, type, expression);
        }

        boolean isExpression() { return expressionType != null; }
    }

    private static final java.util.Set<String> CALLBACK_METHODS = new java.util.HashSet<>(java.util.Arrays.asList(
            "onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy", "onRestart",
            "onBackPressed", "onPostCreate", "onActivityResult", "onRequestPermissionsResult", "onNewIntent",
            "onSaveInstanceState", "onRestoreInstanceState", "onClick", "onLongClick", "onItemClick",
            "onItemSelected", "onNothingSelected", "onTextChanged", "beforeTextChanged", "afterTextChanged",
            "onCheckedChanged", "onTouch", "onKey", "onOptionsItemSelected", "onCreateOptionsMenu",
            "onNavigationItemSelected", "onBind", "onUnbind", "onReceive", "onHandleIntent",
            "onConfigurationChanged", "onWindowFocusChanged", "onAttachedToWindow", "onDetachedFromWindow",
            "onCreateView", "onViewCreated", "onViewStateRestored", "onDestroyView", "onAttach", "onDetach"));

    private void discoverCustomMethods(String source, ConversionResult result) {
        try {
            CompilationUnit unit = StaticJavaParser.parse(source);
            if (unit.getTypes().isEmpty()) return;
            TypeDeclaration<?> primary = unit.getTypes().get(0);
            for (MethodDeclaration method : primary.getMethods()) {
                String name = method.getNameAsString();
                if (CALLBACK_METHODS.contains(name) || !method.getBody().isPresent()
                        || method.isNative() || method.isAbstract()) continue;
                String returnType = method.getType().isVoidType() ? "" : moreBlockReturnType(method.getTypeAsString());
                if (!method.getType().isVoidType() && returnType == null) {
                    result.unsupported.add("UNSUPPORTED: method " + name + " returns " + method.getTypeAsString()
                            + "; this return type has no confirmed native MoreBlock representation. Original Java source is preserved.");
                    continue;
                }
                if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    result.unsupported.add("UNSUPPORTED: method name '" + name + "' is not safe for a Sketchware MoreBlock identifier; original Java source is preserved.");
                    continue;
                }
                List<String> names = new ArrayList<>();
                List<String> types = new ArrayList<>();
                boolean compatible = true;
                for (com.github.javaparser.ast.body.Parameter parameter : method.getParameters()) {
                    String token = moreBlockType(parameter.getType().asString());
                    if (token == null || parameter.isVarArgs()
                            || !parameter.getNameAsString().matches("[A-Za-z_][A-Za-z0-9_]*")) { compatible = false; break; }
                    names.add(parameter.getNameAsString());
                    types.add(token);
                }
                if (!compatible) {
                    result.unsupported.add("UNSUPPORTED: method " + name + " has parameters that cannot be represented as MoreBlock inputs; copied original source only.");
                    continue;
                }
                if (ambiguousHelperNames.contains(name)) {
                    continue;
                }
                if (helperMethods.containsKey(name)) {
                    helperMethods.remove(name);
                    helperSpecs.remove(name);
                    ambiguousHelperNames.add(name);
                    result.unsupported.add("UNSUPPORTED: overloaded method " + name + " cannot be represented unambiguously as a single MoreBlock; all overloads remain in the preserved Java source.");
                    continue;
                }
                String declaration = buildFunctionDeclaration(name, returnType, names, types);
                String body = method.getBody().get().toString();
                if (body.length() >= 2) body = body.substring(1, body.length() - 1);
                helperMethods.put(name, new HelperMethod(name, body, names, types, declaration, returnType));
                helperSpecs.put(name, declaration);
            }
            for (HelperMethod helper : helperMethods.values()) {
                List<String> helperStatements = rewriteViewAliases(removeManagedViewSetupStatements(
                        splitStatements(helper.body), "MoreBlock " + helper.name));
                String normalizedHelperBody = joinStatements(helperStatements);
                BlockSection section = new BlockSection();
                convertStatements(helperStatements, section, result);
                String legacy = section.render();
                Map<String, String> argumentTypes = new LinkedHashMap<>();
                for (int i = 0; i < helper.parameters.size(); i++) {
                    argumentTypes.put(helper.parameters.get(i), helper.parameterTypes.get(i));
                }
                String rich = containsCustomMethodCall(normalizedHelperBody) ? null
                        : convertWithNativeBlockConverter(normalizedHelperBody, argumentTypes, result, "MoreBlock " + helper.name);
                result.sections.put(helper.name + "_moreBlock", rich != null ? rich : legacy);
            }
        } catch (Exception e) {
            String warning = "UNSUPPORTED: Java AST parsing failed; custom helper methods were not extracted (" + e.getMessage() + "). Original source remains copied.";
            result.unsupported.add(warning);
            Log.w(TAG, warning, e);
        }
    }

    private String buildFunctionDeclarations() {
        StringBuilder out = new StringBuilder();
        for (HelperMethod helper : helperMethods.values()) out.append(helper.declaration).append("\n");
        return out.toString();
    }

    private String buildFunctionDeclaration(String name, String returnType, List<String> params, List<String> types) {
        StringBuilder out = new StringBuilder(name);
        if (returnType != null && !returnType.isEmpty()) out.append("[").append(returnType).append("]");
        out.append(":").append(name);
        if (returnType != null && !returnType.isEmpty()) out.append("[").append(returnType).append("]");
        for (int i = 0; i < params.size(); i++) out.append(" %").append(types.get(i)).append(".").append(params.get(i));
        return out.toString();
    }

    private String moreBlockReturnType(String type) {
        if ("String".equals(type) || "java.lang.String".equals(type)) return "String";
        if ("boolean".equals(type) || "Boolean".equals(type) || "java.lang.Boolean".equals(type)) return "boolean";
        if ("double".equals(type) || "Double".equals(type) || "java.lang.Double".equals(type)) return "double";
        return null;
    }

    private String moreBlockType(String type) {
        if (type == null) return null;
        String declared = type.trim();
        if (declared.isEmpty() || declared.endsWith("...")) return null;
        String genericArguments = "";
        int generic = declared.indexOf('<');
        if (generic >= 0 && declared.endsWith(">")) {
            genericArguments = declared.substring(generic + 1, declared.length() - 1);
            declared = declared.substring(0, generic).trim();
        }
        int dot = declared.lastIndexOf('.');
        String simple = dot >= 0 ? declared.substring(dot + 1) : declared;
        if ("String".equals(simple) || "CharSequence".equals(simple)) return "s";
        if ("boolean".equals(simple) || "Boolean".equals(simple)) return "b";
        if ("byte".equals(simple) || "short".equals(simple) || "int".equals(simple)
                || "float".equals(simple) || "double".equals(simple) || "Integer".equals(simple)
                || "Float".equals(simple) || "Double".equals(simple)) return "d";
        if ("long".equals(simple) || "Long".equals(simple)) return "m.long";

        String lower = simple.toLowerCase(java.util.Locale.ROOT);
        if ("map".equals(lower) || "hashmap".equals(lower)) return "m.varMap";
        if ("arraylist".equals(lower) || "list".equals(lower)) {
            String itemType = genericArguments.toLowerCase(java.util.Locale.ROOT);
            if (itemType.contains("hashmap") || itemType.contains("map<")) return "m.listMap";
            if (itemType.contains("string") || itemType.contains("charsequence")) return "m.listStr";
            if (itemType.contains("integer") || itemType.contains("long") || itemType.contains("double")
                    || itemType.contains("float") || itemType.contains("number")) return "m.listInt";
            return "m.list";
        }
        if ("context".equals(lower) || "activity".equals(lower) || "appcompatactivity".equals(lower)
                || "fragmentactivity".equals(lower)) return "m.activity";
        if ("intent".equals(lower)) return "m.intent";
        if ("sharedpreferences".equals(lower)) return "m.file";
        if ("calendar".equals(lower)) return "m.calendar";
        if ("vibrator".equals(lower)) return "m.vibrator";
        if ("mediaplayer".equals(lower)) return "m.mediaplayer";
        if ("soundpool".equals(lower)) return "m.soundpool";
        if ("objectanimator".equals(lower)) return "m.objectanimator";

        if ("view".equals(lower) || "textview".equals(lower) || "edittext".equals(lower)
                || "imageview".equals(lower) || "compoundbutton".equals(lower) || "checkbox".equals(lower)
                || "switch".equals(lower) || "listview".equals(lower) || "spinner".equals(lower)
                || "webview".equals(lower) || "seekbar".equals(lower) || "progressbar".equals(lower)
                || "radiobutton".equals(lower) || "ratingbar".equals(lower) || "videoview".equals(lower)
                || "searchview".equals(lower) || "gridview".equals(lower) || "tablayout".equals(lower)
                || "viewpager".equals(lower) || "bottomnavigationview".equals(lower) || "recyclerview".equals(lower)
                || "cardview".equals(lower) || "radiogroup".equals(lower) || "textinputlayout".equals(lower)
                || "textinputedittext".equals(lower) || "materialbutton".equals(lower)
                || "materialtextview".equals(lower) || "materialswitch".equals(lower)
                || "materialcheckbox".equals(lower) || "materialradiobutton".equals(lower) || "chip".equals(lower)
                || "materialprogressindicator".equals(lower) || "materialdivider".equals(lower)
                || "floatingactionbutton".equals(lower) || "slider".equals(lower)
                || "shapeableimageview".equals(lower) || "materialcardview".equals(lower)
                || "chipgroup".equals(lower) || "extendedfloatingactionbutton".equals(lower)) return "m." + lower;
        return null;
    }

    private boolean tryConvertCustomCall(String stmt, BlockSection section, ConversionResult result) {
        Matcher call = Pattern.compile("^\\s*(?:this\\.)?([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\((.*)\\)\\s*;?$", Pattern.DOTALL).matcher(stmt.trim());
        if (!call.matches()) return false;
        String name = call.group(1);
        String declaration = helperSpecs.get(name);
        if (declaration == null) return false;
        List<String> args = splitCommaArguments(call.group(2));
        HelperMethod helper = helperMethods.get(name);
        if (helper == null) return false;
        if (args.size() != helper.parameters.size()) {
            result.unsupported.add("UNSUPPORTED: call to MoreBlock '" + name + "' has " + args.size()
                    + " argument(s), expected " + helper.parameters.size() + "; original statement is preserved as source.");
            return false;
        }
        List<MoreBlockCallArgument> normalizedArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            MoreBlockCallArgument normalized = normalizeMoreBlockCallArgument(
                    args.get(i), helper.parameterTypes.get(i));
            if (normalized == null) {
                result.unsupported.add("UNSUPPORTED: call to MoreBlock '" + name + "' uses an argument that cannot be represented safely for '"
                        + helper.parameters.get(i) + "'; original statement is preserved as source.");
                return false;
            }
            normalizedArgs.add(normalized);
        }
        section.addDefinedFunction(declaration, normalizedArgs, this);
        return true;
    }

    private MoreBlockCallArgument normalizeMoreBlockCallArgument(String value, String type) {
        if (value == null || type == null) return null;
        String expression = value.trim();
        if (!isSafeInlineJavaExpression(expression)) return null;
        if ("b".equals(type) || "d".equals(type) || "s".equals(type)) {
            return MoreBlockCallArgument.expression(type, expression);
        }
        if (type.startsWith("m.")) {
            if ("m.long".equals(type)) {
                if (expression.matches("-?\\d+[lL]?")) return MoreBlockCallArgument.value(expression);
                return expression.matches("[A-Za-z_$][A-Za-z0-9_$]*")
                        ? MoreBlockCallArgument.value(expression) : null;
            }
            if ("m.view".equals(type) || type.startsWith("m.textview") || type.startsWith("m.edittext")
                    || type.startsWith("m.imageview") || type.startsWith("m.recyclerview")
                    || type.startsWith("m.materialbutton") || type.startsWith("m.shapeableimageview")) {
                Matcher binding = Pattern.compile("^(?:binding\\.)?([A-Za-z_$][A-Za-z0-9_$]*)$").matcher(expression);
                return binding.matches() ? MoreBlockCallArgument.value(binding.group(1)) : null;
            }
            if ("m.activity".equals(type)) {
                return ("this".equals(expression) || "getApplicationContext()".equals(expression))
                        ? MoreBlockCallArgument.value(expression) : null;
            }
            if ("m.intent".equals(type) || "m.file".equals(type) || "m.calendar".equals(type)
                    || "m.vibrator".equals(type) || "m.mediaplayer".equals(type)
                    || "m.soundpool".equals(type) || "m.objectanimator".equals(type)
                    || "m.varMap".equals(type) || "m.list".equals(type) || "m.listMap".equals(type)
                    || "m.listInt".equals(type) || "m.listStr".equals(type)) {
                return expression.matches("[A-Za-z_$][A-Za-z0-9_$]*")
                        ? MoreBlockCallArgument.value(expression) : null;
            }
        }
        return null;
    }

    private boolean isSafeInlineJavaExpression(String expression) {
        if (expression == null || expression.isEmpty() || expression.length() > 1200
                || expression.indexOf(';') >= 0 || expression.indexOf('\n') >= 0
                || expression.indexOf('\r') >= 0) return false;
        int parentheses = 0;
        int brackets = 0;
        int braces = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (escaped) { escaped = false; continue; }
            if ((inString || inChar) && c == '\\') { escaped = true; continue; }
            if (inString) { if (c == '\"') inString = false; continue; }
            if (inChar) { if (c == '\'') inChar = false; continue; }
            if (c == '\"') { inString = true; continue; }
            if (c == '\'') { inChar = true; continue; }
            if (c == '(') parentheses++;
            else if (c == ')' && --parentheses < 0) return false;
            else if (c == '[') brackets++;
            else if (c == ']' && --brackets < 0) return false;
            else if (c == '{') braces++;
            else if (c == '}' && --braces < 0) return false;
        }
        return !inString && !inChar && parentheses == 0 && brackets == 0 && braces == 0;
    }

    private List<String> splitCommaArguments(String text) {
        List<String> values = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return values;
        int start = 0, depth = 0; boolean inString = false, inChar = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) { if (c == '\\') i++; else if (c == '"') inString = false; continue; }
            if (inChar) { if (c == '\'') inChar = false; continue; }
            if (c == '"') { inString = true; continue; }
            if (c == '\'') { inChar = true; continue; }
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == ',' && depth == 0) { values.add(text.substring(start, i).trim()); start = i + 1; }
        }
        values.add(text.substring(start).trim());
        return values;
    }

    /** Uses Neo's block converter; unsupported statements stay in source blocks. */
    private String convertWithNativeBlockConverter(String body, Map<String, String> argumentTypes,
                                                   ConversionResult result, String context) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            BlocksConverter.ConversionResult converted = BlocksConverter.convert(body, argumentTypes);
            if (converted == null || converted.error != null || converted.blocks == null || converted.blocks.isEmpty()) {
                if (converted != null && converted.error != null) {
                    result.unsupported.add("Block engine could not parse " + context + ": " + converted.error
                            + ". Conservative conversion was used instead.");
                }
                return null;
            }
            StringBuilder out = new StringBuilder();
            for (BlockBean block : converted.blocks) {
                if (block == null || block.id == null || block.opCode == null || block.parameters == null) {
                    result.unsupported.add("Block engine emitted an incomplete block in " + context
                            + "; conservative conversion was used instead.");
                    return null;
                }
                out.append(BLOCK_JSON.toJson(block)).append('\n');
            }
            if (converted.fallbackCount > 0) {
                result.unsupported.add(context + ": " + converted.fallbackCount
                        + " statement(s) were preserved as addSourceDirectly blocks by the native block converter.");
            }
            return out.toString();
        } catch (Throwable e) {
            Log.w(TAG, "Native BlocksConverter failed for " + context + "; keeping conservative conversion", e);
            result.unsupported.add("Native BlocksConverter failed for " + context + " (" + e.getClass().getSimpleName()
                    + "); conservative conversion was used and raw Java remains copied.");
            return null;
        }
    }

    private boolean containsCustomMethodCall(String body) {
        if (body == null || helperMethods.isEmpty()) return false;
        for (String methodName : helperMethods.keySet()) {
            if (Pattern.compile("\\b(?:this\\s*\\.\\s*)?" + Pattern.quote(methodName) + "\\s*\\(")
                    .matcher(body).find()) return true;
        }
        return false;
    }

    /** Remove top-level listener statements that are separately written as event sections. */
    private List<String> removeResolvedClickListenerStatementList(List<String> statements,
                                                                  java.util.Set<String> knownViewIds) {
        List<String> out = new ArrayList<>();
        for (String statement : statements) {
            Matcher listener = ON_CLICK.matcher(statement);
            if (listener.find() && listener.start() == 0) {
                String target = listener.group(1);
                String id = localVarToViewId.get(target);
                if (id == null) id = bindingRefToViewId.get(target);
                if (id != null && (knownViewIds == null || knownViewIds.contains(id))) continue;
                // Keep unresolved listeners as source so no Java code is lost.
                 
                 
                 
                 
                unsupported.add("UNSUPPORTED: omitted unresolved listener registration '" + truncate(statement, 90)
                        + "' from initializeLogic because its target is not a known imported widget; original Java is preserved.");
                continue;
            }
            out.add(statement);
        }
        return out;
    }

    private String joinStatements(List<String> statements) {
        StringBuilder out = new StringBuilder();
        for (String statement : statements) out.append(statement).append('\n');
        return out.toString();
    }

    /** Replaces view references only in Java code, not in strings, chars or comments. */
    private List<String> rewriteViewAliases(List<String> statements) {
        List<String> rewritten = new ArrayList<>(statements.size());
        for (String statement : statements) rewritten.add(rewriteViewAliases(statement));
        return rewritten;
    }

    private String rewriteViewAliases(String source) {
        if (source == null || source.isEmpty() || (localVarToViewId.isEmpty() && bindingRefToViewId.isEmpty())) return source;
        StringBuilder out = new StringBuilder(source.length());
        boolean string = false, character = false, lineComment = false, blockComment = false, escaped = false;
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i);
            char n = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (lineComment) {
                out.append(c); i++;
                if (c == '\n') lineComment = false;
                continue;
            }
            if (blockComment) {
                out.append(c); i++;
                if (c == '*' && n == '/') { out.append('/'); i++; blockComment = false; }
                continue;
            }
            if (string || character) {
                out.append(c); i++;
                if (escaped) { escaped = false; continue; }
                if (c == '\\') { escaped = true; continue; }
                if (string && c == '"') string = false;
                else if (character && c == '\'') character = false;
                continue;
            }
            if (c == '/' && n == '/') { out.append("//"); i += 2; lineComment = true; continue; }
            if (c == '/' && n == '*') { out.append("/*"); i += 2; blockComment = true; continue; }
            if (c == '"') { out.append(c); i++; string = true; continue; }
            if (c == '\'') { out.append(c); i++; character = true; continue; }
            if (Character.isJavaIdentifierStart(c) || c == '$') {
                int start = i++;
                while (i < source.length() && (Character.isJavaIdentifierPart(source.charAt(i)) || source.charAt(i) == '$')) i++;
                String token = source.substring(start, i);
                if ("binding".equals(token) && i < source.length() && source.charAt(i) == '.') {
                    int fieldStart = i + 1;
                    int fieldEnd = fieldStart;
                    if (fieldStart < source.length() && (Character.isJavaIdentifierStart(source.charAt(fieldStart)) || source.charAt(fieldStart) == '$')) {
                        fieldEnd++;
                        while (fieldEnd < source.length() && (Character.isJavaIdentifierPart(source.charAt(fieldEnd)) || source.charAt(fieldEnd) == '$')) fieldEnd++;
                        String field = source.substring(fieldStart, fieldEnd);
                        String mapped = bindingRefToViewId.get("binding." + field);
                        if (mapped != null) { out.append(mapped); i = fieldEnd; continue; }
                    }
                }
                String mapped = localVarToViewId.get(token);
                out.append(mapped == null ? token : mapped);
                continue;
            }
            out.append(c); i++;
        }
        return out.toString();
    }

    private String resolveBindingViewId(String fieldName, java.util.Set<String> knownViewIds) {
        if (fieldName == null || knownViewIds == null || knownViewIds.isEmpty()) return null;
        if (knownViewIds.contains(fieldName)) return fieldName;
        // Android ViewBinding converts an XML id such as `title_text` to `titleText`.
        for (String id : knownViewIds) {
            if (fieldName.equals(snakeToCamel(id))) return id;
        }
        return null;
    }

    private String snakeToCamel(String id) {
        StringBuilder result = new StringBuilder(id.length());
        boolean upperNext = false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '_' || c == '-') { upperNext = true; continue; }
            result.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return result.toString();
    }


     

    /** Returns true for Activity view-inflation/setup statements owned by Sketchware. */
    private boolean isSketchwareManagedViewSetup(String statement) {
        if (statement == null) return false;
        String s = statement.trim();
        if (s.isEmpty()) return false;
        // splitStatements() keeps complete statements together even where the source
        // uses multiple lines; preserve application logic while omitting the framework setup.
        if (Pattern.compile("(?s)^\\s*setContentView\\s*\\(.*$", Pattern.CASE_INSENSITIVE).matcher(s).find()) return true;
        if (Pattern.compile("(?is)^\\s*(?:(?:final\\s+)?[\\w.$<>?\\[\\]]+\\s+)?[\\w$]+\\s*=\\s*(?:\\([^)]*\\)\\s*)?findViewById\\s*\\(.*$").matcher(s).matches()) return true;
        if (Pattern.compile("(?is)^\\s*findViewById\\s*\\(.*$").matcher(s).matches()) return true;
        if (s.contains("Binding") && s.contains("=") && s.contains(".inflate(")) return true;
        return Pattern.compile("(?s)^\\s*(?:final\\s+)?[\\w.$<>?]+Binding\\s+[\\w$]+\\s*=.*$", Pattern.CASE_INSENSITIVE).matcher(s).find();
    }

    /** Removes only Activity setup code handled by Sketchware. View aliases are mapped separately. */
    private List<String> removeManagedViewSetupStatements(List<String> statements, String context) {
        List<String> retained = new ArrayList<>();
        if (statements == null || statements.isEmpty()) return retained;
        for (String statement : statements) {
            if (isSketchwareManagedViewSetup(statement)) {
                String label = context == null || context.trim().isEmpty() ? "screen logic" : context;
                unsupported.add("Omitted framework view setup from " + label
                        + " because Sketchware Neo generates the Activity content view itself: "
                        + truncate(statement == null ? "" : statement.trim(), 160));
                continue;
            }
            retained.add(statement);
        }
        return retained;
    }

    private static final Pattern FIELD_DECL = Pattern.compile(
            "(?m)^\\s*(?:private|public|protected)?\\s*(int|boolean|String|float)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:=[^;]*)?;");

    private void scanClassFields(String javaSource, ConversionResult result) {
        Matcher m = FIELD_DECL.matcher(javaSource);
        while (m.find()) {
            String type = m.group(1);
            String name = m.group(2);
            // Java int now uses Sketchware's real int type (code 7), not the older Number/double type (code 1).
            // (code 1) since there was no real int type. Now that one exists (code 7),
            // int fields are routed there instead — this is a deliberate behavior change.
            int code = "int".equals(type) ? 7 : "boolean".equals(type) ? 0
                    : "float".equals(type) ? 4 : 2; // 2 = String
            knownScalarFields.put(name, code);
        }
    }

    private static final Pattern FIND_VIEW_BY_ID = Pattern.compile(
            "([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(?:\\([A-Za-z0-9_$.<>]+\\)\\s*)?findViewById\\(\\s*R\\.id\\.([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\)");
    private static final Pattern BINDING_REF = Pattern.compile(
            "\\bbinding\\.([A-Za-z_$][A-Za-z0-9_$]*)\\b");

    private void scanViewBindingsAndFindViewById(String javaSource, java.util.Set<String> knownViewIds) {
        Matcher m = FIND_VIEW_BY_ID.matcher(javaSource);
        while (m.find()) {
            String localVar = m.group(1);
            String viewId = m.group(2);
            if (knownViewIds == null || knownViewIds.contains(viewId)) {
                localVarToViewId.put(localVar, viewId);
            }
        }
        Matcher b = BINDING_REF.matcher(javaSource);
        while (b.find()) {
            String camelId = b.group(1);
            // Match ViewBinding names to real XML IDs exactly; do not guess camelCase IDs.
            // an exact (case-sensitive) match against the real xml id, no camelCase guessing,
            // to avoid wiring an event to the wrong view.
            String resolvedId = resolveBindingViewId(camelId, knownViewIds);
            if (resolvedId != null) {
                bindingRefToViewId.put("binding." + camelId, resolvedId);
            }
        }
    }

     

    private String extractMethodBody(String source, String methodName) {
        Pattern sig = Pattern.compile("\\b" + Pattern.quote(methodName) + "\\s*\\([^)]*\\)\\s*\\{");
        Matcher m = sig.matcher(source);
        if (!m.find()) return null;
        int braceStart = m.end() - 1;
        int depth = 0;
        for (int i = braceStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(braceStart + 1, i);
            }
        }
        return null;
    }

     

    private static final Pattern ON_CLICK = Pattern.compile(
            "([A-Za-z_$][A-Za-z0-9_$.]*)\\.setOnClickListener\\s*\\(");

    private void findAndConvertClickListeners(String javaSource, ConversionResult result,
                                               java.util.Set<String> knownViewIds) {
        Matcher m = ON_CLICK.matcher(javaSource);
        while (m.find()) {
            String target = m.group(1);
            String viewId = localVarToViewId.get(target);
            if (viewId == null) viewId = bindingRefToViewId.get(target);
            if (viewId == null) {
                unsupported.add("UNSUPPORTED: " + target + ".setOnClickListener(...) — could not resolve "
                        + "\"" + target + "\" to a known view id on this screen; listener body left "
                        + "unconverted in source only.");
                continue;
            }
             
            int callParenIdx = m.end() - 1;
            String body = extractLambdaOrAnonymousBody(javaSource, callParenIdx);
            String sectionName = viewId + "_onClick";
            if (body == null) {
                unsupported.add("UNSUPPORTED: " + target + ".setOnClickListener(...) — listener body form "
                        + "not recognized (expected a lambda or anonymous View.OnClickListener); left "
                        + "unconverted in source only.");
                continue;
            }
            List<String> eventStatements = rewriteViewAliases(removeManagedViewSetupStatements(
                    splitStatements(body), "event " + viewId + ".onClick"));
            String normalizedBody = joinStatements(eventStatements);
            BlockSection section = new BlockSection();
            convertStatements(eventStatements, section, result);
            String legacy = section.render();
            String rich = containsCustomMethodCall(normalizedBody) ? null
                    : convertWithNativeBlockConverter(normalizedBody, java.util.Collections.emptyMap(), result,
                    "event " + viewId + ".onClick");
            result.sections.put(sectionName, rich != null ? rich : legacy);
        }
    }

    /** Handles both `v -> { ... }` and `new View.OnClickListener(){ public void onClick(View v){ ... } }`. */
    private String extractLambdaOrAnonymousBody(String source, int openParenIdx) {
         
        int depth = 1;
        int i = openParenIdx + 1;
        for (; i < source.length() && depth > 0; i++) {
            char c = source.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
        }
        String argText = source.substring(openParenIdx + 1, i - 1);

         
        int arrow = argText.indexOf("->");
        if (arrow >= 0) {
            String after = argText.substring(arrow + 2).trim();
            if (after.startsWith("{")) {
                int close = findMatchingBrace(after, 0);
                return close > 0 ? after.substring(1, close) : null;
            } else {
                return after.endsWith(";") ? after : after + ";";
            }
        }

         
        String onClickBody = extractMethodBody(argText, "onClick");
        return onClickBody;
    }

    private int findMatchingBrace(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

     

    private List<String> splitStatements(String body) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean inString = false, inChar = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                if (c == '\\') { i++; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (inChar) {
                if (c == '\\') { i++; continue; }
                if (c == '\'') inChar = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '\'') { inChar = true; continue; }
            if (c == '{' || c == '(') depth++;
            else if (c == '}' || c == ')') depth--;
            else if (c == ';' && depth == 0) {
                out.add(body.substring(start, i + 1).trim());
                start = i + 1;
            } else if (c == '}' && depth == 0) {
                // end of a block statement (if/else/etc. handled by caller pre-slicing) — should
                // not normally be reached here since block statements are consumed whole below.
            }
        }
        // Handle brace-delimited statements (if/else) that don't end in ';' by re-scanning
        // for constructs starting with "if (" / "} else" at depth 0 and consuming through
        // their matching closing brace(s) as a single statement, replacing the naive split.
        return regroupBraceStatements(body);
    }

    /** Keeps each if/else chain as one statement and splits other statements at top-level semicolons. */
    private List<String> regroupBraceStatements(String body) {
        List<String> out = new ArrayList<>();
        int i = 0;
        int n = body.length();
        while (i < n) {
             
            while (i < n && Character.isWhitespace(body.charAt(i))) i++;
            if (i >= n) break;

            if (body.startsWith("if", i) && i + 2 < n && !Character.isLetterOrDigit(body.charAt(i + 2))) {
                int stmtStart = i;
                int j = i;
                 
                j = consumeIfChain(body, j);
                out.add(body.substring(stmtStart, j).trim());
                i = j;
                continue;
            }

             
            int j = i;
            int depth = 0;
            boolean inString = false, inChar = false;
            while (j < n) {
                char c = body.charAt(j);
                if (inString) { if (c == '\\') { j += 2; continue; } if (c == '"') inString = false; j++; continue; }
                if (inChar)   { if (c == '\\') { j += 2; continue; } if (c == '\'') inChar = false; j++; continue; }
                if (c == '"') { inString = true; j++; continue; }
            if (c == '\'') { inChar = true; continue; }
                if (c == '(' || c == '{') depth++;
                else if (c == ')' || c == '}') depth--;
                else if (c == ';' && depth == 0) { j++; break; }
                j++;
            }
            String stmt = body.substring(i, Math.min(j, n)).trim();
            if (!stmt.isEmpty()) out.add(stmt);
            i = j;
        }
        return out;
    }

    private int consumeIfChain(String body, int i) {
        int n = body.length();
        i = consumeOneIfOrElse(body, i); // the initial "if (...) { ... }"
        while (true) {
            int save = i;
            while (i < n && Character.isWhitespace(body.charAt(i))) i++;
            if (body.startsWith("else", i)) {
                i = consumeOneIfOrElse(body, i);
            } else {
                return save;
            }
        }
    }

    /** Consumes one "if (...) { ... }" or "else if (...) { ... }" or "else { ... }" starting at i. */
    private int consumeOneIfOrElse(String body, int i) {
        int n = body.length();
        if (body.startsWith("else", i)) i += 4;
        while (i < n && Character.isWhitespace(body.charAt(i))) i++;
        if (body.startsWith("if", i)) {
            i += 2;
            while (i < n && Character.isWhitespace(body.charAt(i))) i++;
            if (i < n && body.charAt(i) == '(') {
                int depth = 0;
                do {
                    if (body.charAt(i) == '(') depth++;
                    else if (body.charAt(i) == ')') depth--;
                    i++;
                } while (i < n && depth > 0);
            }
            while (i < n && Character.isWhitespace(body.charAt(i))) i++;
        }
        if (i < n && body.charAt(i) == '{') {
            int close = findMatchingBrace(body, i);
            i = close >= 0 ? close + 1 : n;
        } else {
            // brace-less single statement body — consume through next ';'
            int semi = body.indexOf(';', i);
            i = semi >= 0 ? semi + 1 : n;
        }
        return i;
    }

     

    private static final Pattern FINISH_CALL = Pattern.compile("^finish\\s*\\(\\s*\\)\\s*;$");
    private static final Pattern START_ACTIVITY = Pattern.compile(
            "^startActivity\\s*\\(\\s*new\\s+Intent\\s*\\([^,]+,\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\.class\\s*\\)\\s*\\)\\s*;$");
    private static final Pattern SIMPLE_ASSIGN = Pattern.compile(
            "^([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(.+);$");
    private static final Pattern IF_ELSE_HEAD = Pattern.compile(
            "^if\\s*\\((.+?)\\)\\s*\\{", Pattern.DOTALL);
    private static final Pattern NUMERIC_EQ = Pattern.compile("^\\s*(.+?)\\s*==\\s*(.+?)\\s*$");
    private static final Pattern STRING_EQUALS = Pattern.compile(
            "^\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\.equals\\(\\s*(.+?)\\s*\\)\\s*$");

    private void convertStatements(List<String> statements, BlockSection section, ConversionResult result) {
        for (String stmt : statements) {
            convertOneStatement(stmt, section, result);
        }
    }

    private void convertOneStatement(String stmt, BlockSection section, ConversionResult result) {
        if (stmt.isEmpty()) return;

        // setOnClickListener(...) statements are handled separately by
        // findAndConvertClickListeners(), which converts the listener body into its own
        // real "_<viewId>_onClick" section. Skip them here ONLY when that resolution
        // actually succeeded — otherwise (unresolvable target) fall through to the normal
        // addSourceDirectly fallback below so the statement is never silently dropped.
        Matcher onClickHead = ON_CLICK.matcher(stmt);
        if (onClickHead.find() && onClickHead.start() == 0) {
            String target = onClickHead.group(1);
            String viewId = localVarToViewId.get(target);
            if (viewId == null) viewId = bindingRefToViewId.get(target);
            if (viewId != null) {
                return; // already converted into its own section — do not also emit here
            }
        }

        if (FINISH_CALL.matcher(stmt).matches()) {
            section.addStatement("finishActivity", "Finish Activity", new String[]{}, "f");
            return;
        }

        Matcher startAct = START_ACTIVITY.matcher(stmt);
        if (startAct.matches()) {
            String targetClass = startAct.group(1);
            String componentId = "intent" + (++intentComponentCounter);
            componentsById.put(componentId,
                    "{\"componentId\":\"" + componentId + "\",\"param1\":\"\",\"param2\":\"\",\"param3\":\"\",\"type\":1}");
            int setScreenId = section.reserveId();
            section.finalizeBlock(setScreenId, "intentSetScreen", "%m.intent setScreen %m.activity",
                    new String[]{componentId, targetClass}, "");
            int startId = section.reserveId();
            section.finalizeBlock(startId, "startActivity", "StartActivity %m.intent",
                    new String[]{componentId}, "");
            return;
        }

        if (tryConvertCustomCall(stmt, section, result)) return;

        Matcher assign = SIMPLE_ASSIGN.matcher(stmt);
        if (assign.matches()) {
            String var = assign.group(1);
            String rhs = assign.group(2).trim();
            Integer typeCode = knownScalarFields.get(var);
            if (typeCode != null) {
                String valueParam = buildScalarValueParam(rhs, typeCode);
                if (valueParam != null) {
                    String opCode = typeCode == 1 ? "setVarInt" : typeCode == 0 ? "setVarBoolean"
                            : typeCode == 4 ? "setVarFloat" : typeCode == 7 ? "setVarIntNum" : "setVarString";
                    String spec = typeCode == 1 ? "set %m.varInt to %d"
                                : typeCode == 0 ? "set %m.varBool to %b"
                                : typeCode == 4 ? "set %m.varFloat to %d"
                                : typeCode == 7 ? "set %m.varIntNum to %d" : "set %m.varStr to %s";
                    section.addStatement(opCode, spec, new String[]{var, valueParam}, "");
                    return;
                }
            }
        }

        Matcher ifHead = IF_ELSE_HEAD.matcher(stmt);
        if (ifHead.find() && ifHead.start() == 0) {
            if (tryConvertIfElse(stmt, section, result)) return;
        }

        // Fallback: preserve verbatim as a real addSourceDirectly block (confirmed opCode).
        section.addStatement("addSourceDirectly", "add source directly %s.inputOnly",
                new String[]{stmt}, "");
        unsupported.add("Preserved as addSourceDirectly (not converted to native blocks): "
                + truncate(stmt, 80));
    }

    /** Builds a value param for setVarInt/Bool/String/Float: a literal, or "@ref" to another known var. */
    private String buildScalarValueParam(String rhs, int typeCode) {
        rhs = rhs.trim();
        if (typeCode == 1) { // int
            if (rhs.matches("-?\\d+")) return rhs;
            if (knownScalarFields.containsKey(rhs) && knownScalarFields.get(rhs) == 1) return "@" + rhs;
            // simple a + b / a - b / a * b / a / b of two known-int operands or literals
            Matcher bin = Pattern.compile("^(.+?)\\s*([+\\-*/])\\s*(.+)$").matcher(rhs);
            if (bin.matches()) return null; // composed math needs nested blocks — out of this method's scope
            return null;
        } else if (typeCode == 0) { // boolean
            if (rhs.equals("true") || rhs.equals("false")) return rhs;
            return null;
        } else if (typeCode == 7) { // real int (Sketchware's own true int type)
            if (rhs.matches("-?\\d+")) return rhs;
            if (knownScalarFields.containsKey(rhs) && knownScalarFields.get(rhs) == 7) return "@" + rhs;
            Matcher bin = Pattern.compile("^(.+?)\\s*([+\\-*/])\\s*(.+)$").matcher(rhs);
            if (bin.matches()) return null; // composed math needs nested blocks — out of this method's scope
            return null;
        } else if (typeCode == 4) { // float
            // Strip a Java float-literal suffix ("1.5f" / "1.5F") — the block param itself is
            // stored as a plain numeric literal; Fx's setVarFloat codegen re-adds the "f"
            // suffix (and handles the empty -> "0f" case) when the project is built.
            String literal = rhs;
            if (literal.endsWith("f") || literal.endsWith("F")) {
                literal = literal.substring(0, literal.length() - 1);
            }
            if (literal.matches("-?\\d+(\\.\\d+)?")) return literal;
            if (knownScalarFields.containsKey(rhs) && knownScalarFields.get(rhs) == 4) return "@" + rhs;
            return null;
        } else { // String
            if (rhs.startsWith("\"") && rhs.endsWith("\"")) return rhs.substring(1, rhs.length() - 1);
            if (knownScalarFields.containsKey(rhs) && knownScalarFields.get(rhs) == 2) return "@" + rhs;
            return null;
        }
    }

    private boolean tryConvertIfElse(String stmt, BlockSection section, ConversionResult result) {
        // Parse "if (COND) { TRUE_BODY } [else { FALSE_BODY }]" — single if/else only,
        // no else-if chains (those fall back to addSourceDirectly for the whole chain).
        Matcher head = Pattern.compile("^if\\s*\\(", Pattern.DOTALL).matcher(stmt);
        if (!head.find()) return false;
        int condStart = head.end() - 1;
        int condEnd = findMatchingParen(stmt, condStart);
        if (condEnd < 0) return false;
        String cond = stmt.substring(condStart + 1, condEnd).trim();

        int i = condEnd + 1;
        while (i < stmt.length() && Character.isWhitespace(stmt.charAt(i))) i++;
        if (i >= stmt.length() || stmt.charAt(i) != '{') return false;
        int trueClose = findMatchingBrace(stmt, i);
        if (trueClose < 0) return false;
        String trueBody = stmt.substring(i + 1, trueClose);

        String falseBody = null;
        int j = trueClose + 1;
        while (j < stmt.length() && Character.isWhitespace(stmt.charAt(j))) j++;
        if (stmt.startsWith("else", j)) {
            j += 4;
            while (j < stmt.length() && Character.isWhitespace(stmt.charAt(j))) j++;
            if (j < stmt.length() && stmt.charAt(j) == '{') {
                int falseClose = findMatchingBrace(stmt, j);
                if (falseClose < 0) return false;
                falseBody = stmt.substring(j + 1, falseClose);
            } else {
                return false; // else-if chain — not handled, fall back to addSourceDirectly
            }
        }

        String[] condBlock = buildSimpleConditionParam(cond);
        if (condBlock == null) return false; // unconfirmed comparison operator — fall back

        int ownId = section.reserveId(); // reserve BEFORE building nested branch statements
        int trueEntry = section.buildBranch(splitStatements(trueBody), this, result);
        int falseEntry = falseBody != null
                ? section.buildBranch(splitStatements(falseBody), this, result)
                : -1;

        section.finalizeIfElse(ownId, condBlock[0], condBlock[1], trueEntry, falseEntry);
        return true;
    }

    /** Returns {opCode, valueOrRefParam} for a confirmed condition shape, or null if unconfirmed. */
    private String[] buildSimpleConditionParam(String cond) {
        Matcher strEq = STRING_EQUALS.matcher(cond);
        if (strEq.matches()) {
            return new String[]{"stringEquals", strEq.group(1) + "|" + strEq.group(2).replaceAll("^\"|\"$", "")};
        }
        Matcher numEq = NUMERIC_EQ.matcher(cond);
        if (numEq.matches() && !cond.contains("!=") && !cond.contains(">=") && !cond.contains("<=")) {
            return new String[]{"=", numEq.group(1).trim() + "|" + numEq.group(2).trim()};
        }
        return null; // >, <, >=, <=, !=, &&, || — all unconfirmed (doc §11) — do not fake
    }

    private int findMatchingParen(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private String truncate(String s, int max) {
        s = s.replace("\n", " ");
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

     

    private String buildMoreBlockArgumentBlockJson(int id, MoreBlockCallArgument argument) {
        if ("b".equals(argument.expressionType)
                && ("true".equals(argument.expression) || "false".equals(argument.expression))) {
            return "{\"id\":\"" + id + "\",\"nextBlock\":-1,\"subStack1\":-1,\"subStack2\":-1,"
                    + "\"opCode\":" + jsonString(argument.expression) + ",\"spec\":" + jsonString(argument.expression)
                    + ",\"parameters\":[],\"color\":-16729254,\"type\":\"b\",\"typeName\":\"\"}";
        }
        String opcode;
        String type;
        if ("s".equals(argument.expressionType)) {
            opcode = "asdString";
            type = "s";
        } else if ("b".equals(argument.expressionType)) {
            opcode = "asdBoolean";
            type = "b";
        } else {
            opcode = "asdNumber";
            type = "d";
        }
        return "{\"id\":\"" + id + "\",\"nextBlock\":-1,\"subStack1\":-1,\"subStack2\":-1,"
                + "\"opCode\":" + jsonString(opcode) + ",\"spec\":"
                + jsonString("add source directly %s.inputOnly")
                + ",\"parameters\":[" + jsonString(argument.expression) + "],"
                + "\"color\":-16729254,\"type\":\"" + type + "\",\"typeName\":\"\"}";
    }

    private class BlockSection {
        private int counter = 1;
        private final Map<Integer, String> blocksById = new LinkedHashMap<>();
        private final List<Integer> topLevelOrder = new ArrayList<>();
        private Integer lastTopLevelId = null;

        int reserveId() {
            return counter++;
        }

        /** Adds a simple, non-nesting statement block, chained after the previous top-level block. */
        void addStatement(String opCode, String spec, String[] params, String color) {
            int id = reserveId();
            finalizeBlock(id, opCode, spec, params, color);
        }

        /** Writes a block after its ID and nested blocks have been created. */
        void finalizeBlock(int id, String opCode, String spec, String[] params, String color) {
            if (lastTopLevelId != null) {
                String prev = blocksById.get(lastTopLevelId);
                blocksById.put(lastTopLevelId, setNextBlock(prev, id));
            }
            StringBuilder paramsJson = new StringBuilder("[");
            for (int i = 0; i < params.length; i++) {
                if (i > 0) paramsJson.append(",");
                paramsJson.append(jsonString(params[i]));
            }
            paramsJson.append("]");
            String json = "{\"id\":\"" + id + "\",\"nextBlock\":-1,\"subStack1\":-1,\"subStack2\":-1,"
                    + "\"opCode\":\"" + opCode + "\",\"spec\":" + jsonString(spec) + ","
                    + "\"parameters\":" + paramsJson + ",\"color\":-10701022,\"type\":\""
                    + (color.isEmpty() ? "" : color) + "\",\"typeName\":\"\"}";
            blocksById.put(id, json);
            topLevelOrder.add(id);
            lastTopLevelId = id;
        }

        void addDefinedFunction(String spec, List<MoreBlockCallArgument> args, JavaLogicConverter converter) {
            int callId = reserveId();
            String[] parameters = new String[args.size()];
            List<Map.Entry<Integer, String>> expressionBlocks = new ArrayList<>();
            for (int i = 0; i < args.size(); i++) {
                MoreBlockCallArgument argument = args.get(i);
                if (!argument.isExpression()) {
                    parameters[i] = argument.value;
                    continue;
                }
                int expressionId = reserveId();
                parameters[i] = "@" + expressionId;
                expressionBlocks.add(new java.util.AbstractMap.SimpleImmutableEntry<>(expressionId,
                        converter.buildMoreBlockArgumentBlockJson(expressionId, argument)));
            }
            finalizeBlock(callId, "definedFunc", spec, parameters, " ");
            for (Map.Entry<Integer, String> expressionBlock : expressionBlocks) {
                blocksById.put(expressionBlock.getKey(), expressionBlock.getValue());
            }
        }

        void finalizeIfElse(int id, String condOpCode, String condRef, int trueEntry, int falseEntry) {
            if (lastTopLevelId != null) {
                String prev = blocksById.get(lastTopLevelId);
                blocksById.put(lastTopLevelId, setNextBlock(prev, id));
            }
            String[] parts = condRef.split("\\|", 2);
            String p0 = parts.length > 0 ? parts[0] : "";
            String p1 = parts.length > 1 ? parts[1] : "";
            String json = "{\"id\":\"" + id + "\",\"nextBlock\":-1,"
                    + "\"subStack1\":" + trueEntry + ",\"subStack2\":" + falseEntry + ","
                    + "\"opCode\":\"" + (falseEntry >= 0 ? "ifElse" : "if") + "\","
                    + "\"spec\":\"if %b then\",\"parameters\":[" + "\"@__cond" + id + "\"" + "],"
                    + "\"color\":-9993985,\"type\":\"" + (falseEntry >= 0 ? "e" : "") + "\",\"typeName\":\"\"}";
            // Condition itself becomes its own block (a value-only opcode) referenced by the
            // if/ifElse block's parameter — allocated AFTER the statement's own id per the
            // entry-point rule, so it never becomes the section's (lowest-id) entry point.
            int condId = reserveId();
            String condJson = "{\"id\":\"" + condId + "\",\"nextBlock\":-1,\"subStack1\":-1,\"subStack2\":-1,"
                    + "\"opCode\":\"" + condOpCode + "\","
                    + "\"spec\":\"" + (condOpCode.equals("=") ? "%d = %d" : "%s equals %s") + "\","
                    + "\"parameters\":[" + jsonString(p0) + "," + jsonString(p1) + "],"
                    + "\"color\":-10701022,\"type\":\"b\",\"typeName\":\"\"}";
            blocksById.put(condId, condJson);
            json = json.replace("\"@__cond" + id + "\"", "\"@" + condId + "\"");
            blocksById.put(id, json);
            topLevelOrder.add(id);
            lastTopLevelId = id;
        }

        /** Builds branch blocks in the same section, with a shared ID counter and a separate nextBlock chain. */
        int buildBranch(List<String> statements, JavaLogicConverter outer, ConversionResult result) {
            if (statements.isEmpty()) return -1;
            int entryCandidate = counter;
            Integer savedCursor = lastTopLevelId;
            lastTopLevelId = null;
            outer.convertStatements(statements, this, result);
            boolean producedAnything = counter > entryCandidate;
            lastTopLevelId = savedCursor;
            return producedAnything ? entryCandidate : -1;
        }

        String render() {
            StringBuilder sb = new StringBuilder();
            for (String json : blocksById.values()) sb.append(json).append("\n");
            return sb.toString();
        }

        private String setNextBlock(String blockJson, int nextId) {
            return blockJson.replaceFirst("\"nextBlock\":-1", "\"nextBlock\":" + nextId);
        }
    }

    private String jsonString(String s) {
        if (s == null) s = "";
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n");  break;
                case '\r': out.append("\\r");  break;
                case '\t': out.append("\\t");  break;
                default:   out.append(c);
            }
        }
        return out.append("\"").toString();
    }
}
