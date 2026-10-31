package mod.sketchlibx.importer;

import com.google.gson.Gson;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AdapterLogicImporter {
    private static final String TAG = "AdapterLogicImporter";
    private static final Gson GSON = new Gson();
    private static final Pattern DIRECT_ADAPTER = Pattern.compile(
            "([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*\\.\\s*setAdapter\\s*\\(\\s*new\\s+([A-Za-z_$][\\w$]*)\\s*\\(",
            Pattern.DOTALL);
    private static final Pattern ADAPTER_VARIABLE = Pattern.compile(
            "\\b([A-Za-z_$][\\w$]*)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*new\\s+([A-Za-z_$][\\w$]*)\\s*\\(",
            Pattern.DOTALL);
    private static final Pattern VARIABLE_ADAPTER = Pattern.compile(
            "([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*\\.\\s*setAdapter\\s*\\(\\s*([A-Za-z_$][\\w$]*)\\s*\\)",
            Pattern.DOTALL);
    private static final Pattern HOLDER_INIT = Pattern.compile(
            "(?m)^\\s*(?:(?:final)\\s+)?(?:android\\.view\\.)?View\\s+_view\\s*=\\s*[A-Za-z_$][\\w$]*\\.itemView\\s*;\\s*");
    private static final Pattern BINDING_INIT = Pattern.compile(
            "(?m)^\\s*(?:(?:final)\\s+)?[A-Za-z_$][\\w$]*Binding\\s+binding\\s*=\\s*[A-Za-z_$][\\w$]*Binding\\.bind\\s*\\(\\s*_view\\s*\\)\\s*;\\s*");

    static final class ImportedEvent {
        final String targetId;
        final int targetType;
        final String adapterClass;
        final String blockJson;
        final String sourceBody;
        final List<String> helperDeclarations;

        ImportedEvent(String targetId, int targetType, String adapterClass, String blockJson,
                      String sourceBody, List<String> helperDeclarations) {
            this.targetId = targetId;
            this.targetType = targetType;
            this.adapterClass = adapterClass;
            this.blockJson = blockJson;
            this.sourceBody = sourceBody;
            this.helperDeclarations = helperDeclarations;
        }

        String eventJson() {
            return "{\"eventName\":\"onBindCustomView\",\"eventType\":1,\"targetId\":\""
                    + targetId + "\",\"targetType\":" + targetType + "}";
        }
    }

    private AdapterLogicImporter() {
    }

    static List<ImportedEvent> collect(ClassifiedSource activity, List<ClassifiedSource> sources,
                                       Set<String> viewIds, Map<String, Integer> viewTypes,
                                       boolean viewBindingEnabled, String functionDeclarations) {
        List<ImportedEvent> result = new ArrayList<>();
        if (activity == null || activity.file == null || !activity.file.isFile()
                || viewIds == null || viewTypes == null) return result;
        if (!viewBindingEnabled) {
            Log.i(TAG, "RecyclerView adapter block import skipped because ViewBinding is disabled.");
            return result;
        }
        try {
            String activitySource = new String(Files.readAllBytes(activity.file.toPath()), StandardCharsets.UTF_8);
            Map<String, String> adapterByVariable = new LinkedHashMap<>();
            Matcher variableMatcher = ADAPTER_VARIABLE.matcher(activitySource);
            while (variableMatcher.find()) adapterByVariable.put(variableMatcher.group(2), variableMatcher.group(3));

            Map<String, Set<String>> targetsByAdapter = new LinkedHashMap<>();
            Matcher direct = DIRECT_ADAPTER.matcher(activitySource);
            while (direct.find()) {
                String target = simpleName(direct.group(1));
                String adapter = direct.group(2);
                if (viewIds.contains(target) && isRecyclerView(viewTypes, target)) {
                    targetsByAdapter.computeIfAbsent(adapter, key -> new java.util.LinkedHashSet<>()).add(target);
                }
            }
            Matcher variable = VARIABLE_ADAPTER.matcher(activitySource);
            while (variable.find()) {
                String target = simpleName(variable.group(1));
                String adapter = adapterByVariable.get(variable.group(2));
                if (adapter != null && viewIds.contains(target) && isRecyclerView(viewTypes, target)) {
                    targetsByAdapter.computeIfAbsent(adapter, key -> new java.util.LinkedHashSet<>()).add(target);
                }
            }
            if (targetsByAdapter.isEmpty()) return result;

            Map<String, String> classSources = new LinkedHashMap<>();
            classSources.put(activity.simpleClassName, activitySource);
            if (sources != null) for (ClassifiedSource source : sources) {
                if (source == null || source.file == null || !source.file.isFile() || source.simpleClassName == null
                        || classSources.containsKey(source.simpleClassName)) continue;
                if (targetsByAdapter.containsKey(source.simpleClassName)) {
                    classSources.put(source.simpleClassName,
                            new String(Files.readAllBytes(source.file.toPath()), StandardCharsets.UTF_8));
                }
            }

            Map<String, ImportedEvent> byTarget = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> mapping : targetsByAdapter.entrySet()) {
                String adapterName = mapping.getKey();
                String source = classSources.get(adapterName);
                if (source == null) continue;
                String body = extractBindingBody(source, adapterName);
                if (body == null || body.trim().isEmpty()) {
                    Log.w(TAG, "Could not safely map onBindViewHolder for " + adapterName
                            + " to Neo's _data/_position/_view binding contract.");
                    continue;
                }
                List<String> helpers = extractHelperDeclarations(source, adapterName,
                        parseMoreBlockNames(functionDeclarations));
                if (helpers == null) {
                    Log.w(TAG, "Skipped adapter conversion because helper method resolution was ambiguous: " + adapterName);
                    continue;
                }
                for (String targetId : mapping.getValue()) {
                    ImportedEvent event = new ImportedEvent(targetId, viewTypes.get(targetId), adapterName,
                            sourceBlockJson(body.trim(), helpers), body.trim(), helpers);
                    ImportedEvent previous = byTarget.get(targetId);
                    if (previous == null) {
                        byTarget.put(targetId, event);
                    } else if (!previous.adapterClass.contains(adapterName)) {
                        String mergedBody = previous.sourceBody + "\n" + body.trim();
                        List<String> mergedHelpers = new ArrayList<>(previous.helperDeclarations);
                        for (String declaration : event.helperDeclarations) {
                            if (!mergedHelpers.contains(declaration)) mergedHelpers.add(declaration);
                        }
                        byTarget.put(targetId, new ImportedEvent(targetId, event.targetType,
                                previous.adapterClass + ", " + adapterName,
                                sourceBlockJson(mergedBody, mergedHelpers), mergedBody, mergedHelpers));
                    }
                }
            }
            result.addAll(byTarget.values());
        } catch (Exception error) {
            Log.w(TAG, "Could not import RecyclerView adapter logic for " + activity.simpleClassName, error);
        }
        return result;
    }

    private static String extractBindingBody(String source, String adapterName) {
        try {
            CompilationUnit unit = StaticJavaParser.parse(source);
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (!adapterName.equals(type.getNameAsString())) continue;
                for (MethodDeclaration method : type.getMethodsByName("onBindViewHolder")) {
                    if (!method.getBody().isPresent()) continue;
                    String body = method.getBody().get().toString();
                    if (body.length() >= 2) body = body.substring(1, body.length() - 1);
                    body = HOLDER_INIT.matcher(body).replaceAll("");
                    body = BINDING_INIT.matcher(body).replaceAll("");
                    body = body.replace("_holder.getAdapterPosition()", "_position");
                    if (body.contains("_holder.") || Pattern.compile("\\bholder\\.").matcher(body).find()) continue;
                    if (!body.contains("_data") || !body.contains("_position") || !body.contains("_view")) continue;
                    return body.trim();
                }
            }
        } catch (Exception error) {
            Log.w(TAG, "Could not parse adapter source for " + adapterName, error);
        }
        return null;
    }

    private static List<String> extractHelperDeclarations(String source, String adapterName,
                                                           Set<String> moreBlockNames) {
        List<String> declarations = new ArrayList<>();
        try {
            CompilationUnit unit = StaticJavaParser.parse(source);
            ClassOrInterfaceDeclaration adapterType = null;
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (adapterName.equals(type.getNameAsString())) {
                    adapterType = type;
                    break;
                }
            }
            if (adapterType == null) return declarations;
            List<MethodDeclaration> candidates = new ArrayList<>(adapterType.getMethods());
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (type != adapterType) candidates.addAll(type.getMethods());
            }
            MethodDeclaration bindMethod = null;
            for (MethodDeclaration method : adapterType.getMethodsByName("onBindViewHolder")) {
                if (method.getBody().isPresent()) {
                    bindMethod = method;
                    break;
                }
            }
            if (bindMethod == null) return declarations;
            java.util.LinkedHashSet<String> calledNames = new java.util.LinkedHashSet<>();
            for (MethodCallExpr call : bindMethod.getBody().get().findAll(MethodCallExpr.class)) {
                calledNames.add(call.getNameAsString());
            }
            for (String name : calledNames) {
                String normalizedName = name.startsWith("_") && name.length() > 1
                        ? name.substring(1) : name;
                if ("onBindViewHolder".equals(name) || "onCreateViewHolder".equals(name)
                        || "getItemCount".equals(name)
                        || (moreBlockNames != null && moreBlockNames.contains(normalizedName))) continue;
                MethodDeclaration match = null;
                boolean ambiguous = false;
                for (MethodDeclaration candidate : candidates) {
                    if (!name.equals(candidate.getNameAsString()) || !candidate.getBody().isPresent()) continue;
                    if (match != null) {
                        ambiguous = true;
                        break;
                    }
                    match = candidate;
                }
                if (ambiguous) return null;
                if (match != null) declarations.add(match.toString());
            }
        } catch (Exception error) {
            Log.w(TAG, "Could not resolve helper methods for adapter " + adapterName, error);
            return null;
        }
        return declarations;
    }

    private static Set<String> parseMoreBlockNames(String declarations) {
        Set<String> names = new java.util.HashSet<>();
        if (declarations == null) return names;
        for (String line : declarations.split("\\r?\\n")) {
            String value = line.trim();
            int colon = value.indexOf(':');
            if (colon <= 0) continue;
            String name = value.substring(0, colon);
            int bracket = name.indexOf('[');
            if (bracket >= 0) name = name.substring(0, bracket);
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }

    private static String sourceBlockJson(String body, List<String> helperDeclarations) {
        StringBuilder out = new StringBuilder();
        int id = 1;
        if (helperDeclarations != null) for (String declaration : helperDeclarations) {
            int next = id + 1;
            out.append("{\"color\":-12435108,\"id\":\"").append(id)
                    .append("\",\"nextBlock\":").append(next)
                    .append(",\"opCode\":\"addCustomVariable\",\"parameters\":[")
                    .append(GSON.toJson(declaration)).append("],\"spec\":\"Custom Variable Block: add variable %s\","
                    + "\"subStack1\":-1,\"subStack2\":-1,\"type\":\" \",\"typeName\":\"\"}\n");
            id++;
        }
        out.append("{\"color\":-16729254,\"id\":\"").append(id)
                .append("\",\"nextBlock\":-1,\"opCode\":\"addSourceDirectly\",\"parameters\":[")
                .append(GSON.toJson(body)).append("],\"spec\":\"add source directly %s.inputOnly\","
                        + "\"subStack1\":-1,\"subStack2\":-1,\"type\":\" \",\"typeName\":\"\"}");
        return out.toString();
    }

    private static boolean isRecyclerView(Map<String, Integer> viewTypes, String id) {
        Integer type = viewTypes.get(id);
        return type != null && type == 48;
    }

    private static String simpleName(String value) {
        if (value == null) return "";
        int dot = value.lastIndexOf('.');
        return dot >= 0 ? value.substring(dot + 1) : value;
    }
}
