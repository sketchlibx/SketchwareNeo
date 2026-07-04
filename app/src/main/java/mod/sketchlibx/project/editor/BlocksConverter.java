package mod.sketchlibx.project.editor;

import com.besome.sketch.beans.BlockBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.aldi.sayuti.block.ExtraBlockFile;

/**
 * Converts a Java event method body into Sketchware BlockBeans.
 *
 * CRASH FIX: Annotations (@Override etc.) are skipped in preprocessing.
 * The crash occurred because addSourceDirectly parameters starting with '@'
 * were misinterpreted as block ID references by LogicEditorActivity.a().
 *
 * Architecture:
 * - stmtBlocks  : ordered sequence/control-flow blocks (come first in result)
 * - exprBlocks  : expression sub-blocks referenced via "@id" (appended after)
 * The first block in the result is always the sequence root, which is what
 * LogicEditorActivity.a() uses as the anchor for positioning.
 *
 * Block ID range >= 99_000_000 so LogicEditorActivity.a() auto-remaps them.
 */
public final class BlocksConverter {

    // ── Types ──────────────────────────────────────────────────────────────────
    private enum ExprType { NUMBER, STRING, BOOLEAN, UNKNOWN }

    // ── ID counter ─────────────────────────────────────────────────────────────
    private int idCounter = 99_000_001;
    private int newId() { return idCounter++; }

    // ── Block collections ──────────────────────────────────────────────────────
    // exprBlocks are appended after stmtBlocks so the first block in the
    // final list is always the sequence root (required by LogicEditorActivity.a).
    private final List<BlockBean> exprBlocks = new ArrayList<>();

    // ── Custom block matching ──────────────────────────────────────────────────
    // Loaded once per convert() call via lazy init from ExtraBlockFile.
    // Each CustomBlockMatcher reverse-engineers one custom block's String.format
    // code template into a regex, then maps capture groups back to BlockBean params.

    private static volatile ArrayList<CustomBlockMatcher> customBlockMatchers = null;
    private static final Object CB_LOCK = new Object();

    private static ArrayList<CustomBlockMatcher> getCustomMatchers() {
        if (customBlockMatchers == null) {
            synchronized (CB_LOCK) {
                if (customBlockMatchers == null) {
                    customBlockMatchers = buildCustomMatchers();
                }
            }
        }
        return customBlockMatchers;
    }

    /** Call after the user saves/edits custom blocks so patterns are rebuilt. */
    public static void refreshCustomBlocks() {
        synchronized (CB_LOCK) { customBlockMatchers = null; }
    }

    // ── Pattern spec param type regex ──────────────────────────────────────────
    private static final Pattern SPEC_PARAM_PAT =
            Pattern.compile("%s(?:\\.inputOnly)?|%b|%d|%m\\.\\w+");
    // Detects a Java String.format specifier: %s, %1$s, %2$d, etc.
    private static final Pattern FMT_SPEC_PAT =
            Pattern.compile("%(([0-9]+)\\$)?([sdbf])");

    // ── CustomBlockMatcher ─────────────────────────────────────────────────────
    private static final class CustomBlockMatcher {
        final String   name;           // opCode
        final String   spec;           // spec for BlockBean
        final String   type;           // block type char
        final Pattern  pattern;        // compiled from code template
        final int[]    groupToSpecIdx; // capture group → spec param index
        final int      captureCount;
        final ExprType[] specParamTypes;// per-spec-param expression type

        CustomBlockMatcher(String name, String spec, String type, Pattern pattern,
                           int[] groupToSpecIdx, int captureCount, ExprType[] specParamTypes) {
            this.name           = name;
            this.spec           = spec;
            this.type           = type;
            this.pattern        = pattern;
            this.groupToSpecIdx = groupToSpecIdx;
            this.captureCount   = captureCount;
            this.specParamTypes = specParamTypes;
        }
    }

    // ── Build all custom matchers ──────────────────────────────────────────────
    private static ArrayList<CustomBlockMatcher> buildCustomMatchers() {
        ArrayList<CustomBlockMatcher> list = new ArrayList<>();
        try {
            ArrayList<HashMap<String, Object>> blocks = ExtraBlockFile.getExtraBlockData();
            for (HashMap<String, Object> block : blocks) {
                // Skip recycle-bin blocks
                Object palette = block.get("palette");
                if ("-1".equals(String.valueOf(palette))) continue;

                Object nameObj = block.get("name");
                Object specObj = block.get("spec");
                Object typeObj = block.get("type");
                Object codeObj = block.get("code");
                if (nameObj == null || specObj == null || typeObj == null || codeObj == null) continue;

                String name = nameObj.toString();
                String spec = specObj.toString();
                String bType= typeObj.toString();
                String code = codeObj.toString().trim();
                if (name.isEmpty() || code.isEmpty()) continue;

                // Only handle single-statement (non-multiline) blocks for direct matching.
                // Multi-line / container blocks naturally fall through to addSourceDirectly.
                if (code.contains("\n") || code.contains("\r") ||
                    code.contains("{") || code.contains("}")) continue;

                // Strip trailing semicolon before building the pattern
                if (code.endsWith(";")) code = code.substring(0, code.length() - 1).trim();

                ExprType[] specTypes = buildSpecParamTypes(spec);
                CustomBlockMatcher matcher = buildMatcherForCode(name, spec, bType, code, specTypes);
                if (matcher != null) list.add(matcher);
            }
        } catch (Exception ignored) {
            // Silent fail — custom blocks just won't be recognised
        }
        return list;
    }

    /**
     * Extracts the expression type for each parameter in a custom block spec,
     * in left-to-right order of appearance.
     *
     * %s / %s.inputOnly → STRING
     * %b                → BOOLEAN
     * %d                → NUMBER
     * %m.view etc.      → UNKNOWN (pass-through: strip binding. prefix only)
     */
    private static ExprType[] buildSpecParamTypes(String spec) {
        List<ExprType> types = new ArrayList<>();
        Matcher m = SPEC_PARAM_PAT.matcher(spec);
        while (m.find()) {
            String tok = m.group();
            if (tok.startsWith("%s")) types.add(ExprType.STRING);
            else if (tok.startsWith("%b")) types.add(ExprType.BOOLEAN);
            else if (tok.startsWith("%d")) types.add(ExprType.NUMBER);
            else                           types.add(ExprType.UNKNOWN); // %m.*
        }
        return types.toArray(new ExprType[0]);
    }

    /**
     * Converts a String.format code template into a compiled regex.
     *
     * Supports:
     *   Sequential  — %s, %d, %f, %b → consumed in order
     *   Positional  — %1$s, %2$d …   → arbitrary order, tracked via groupToSpecIdx
     *
     * Returns null if the template is too complex to pattern-match safely.
     */
    private static CustomBlockMatcher buildMatcherForCode(
            String name, String spec, String bType, String code, ExprType[] specTypes) {

        StringBuilder regex = new StringBuilder("^");
        List<Integer> groupToSpecList = new ArrayList<>();
        int captureCount = 0;
        int seqCount     = 0;
        int lastEnd      = 0;

        Matcher m = FMT_SPEC_PAT.matcher(code);
        while (m.find()) {
            // Literal text before this specifier → quote it
            String lit = code.substring(lastEnd, m.start());
            regex.append(Pattern.quote(lit));

            // Add a capture group
            regex.append("(.+?)");
            captureCount++;

            String idxPart = m.group(2);   // null for sequential, "1","2"… for positional
            int specIdx = (idxPart != null)
                    ? (Integer.parseInt(idxPart) - 1)   // 1-based → 0-based
                    : seqCount++;
            groupToSpecList.add(specIdx);

            lastEnd = m.end();
        }

        // If no format specifiers found, the code is a literal — exact match with no params
        // Append trailing literal
        String trailingLit = code.substring(lastEnd);
        regex.append(Pattern.quote(trailingLit));
        regex.append("(?:;)?$");   // optional trailing semicolon

        int[] groupToSpecIdx = new int[captureCount];
        for (int i = 0; i < captureCount; i++) groupToSpecIdx[i] = groupToSpecList.get(i);

        try {
            Pattern pat = Pattern.compile(regex.toString());
            return new CustomBlockMatcher(name, spec, bType, pat, groupToSpecIdx, captureCount, specTypes);
        } catch (Exception e) {
            return null;
        }
    }


    private int recognizedCount = 0;
    private int fallbackCount   = 0;

    // ── Result type ────────────────────────────────────────────────────────────
    public static final class ConversionResult {
        public final ArrayList<BlockBean> blocks;
        public final String error;          // null = success
        public final int recognizedCount;   // native blocks generated
        public final int fallbackCount;     // addSourceDirectly fallbacks

        ConversionResult(ArrayList<BlockBean> b, String e, int r, int f) {
            blocks = b; error = e; recognizedCount = r; fallbackCount = f;
        }
    }

    // ── Parse tree ─────────────────────────────────────────────────────────────
    private static final class Node {
        BlockBean bean;
        List<Node> body1 = new ArrayList<>(); // if-body / loop-body
        List<Node> body2 = new ArrayList<>(); // else-body
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PUBLIC ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════════

    public static ConversionResult convert(String javaCode) {
        if (javaCode == null || javaCode.isBlank())
            return new ConversionResult(new ArrayList<>(), "Input is empty.", 0, 0);
        try {
            BlocksConverter c = new BlocksConverter();
            List<String> lines = c.preprocess(javaCode);
            if (lines.isEmpty())
                return new ConversionResult(new ArrayList<>(),
                        "No statements found after stripping comments and annotations.", 0, 0);

            int[] pos = {0};
            List<Node> nodes = c.parseSequence(lines, pos);
            ArrayList<BlockBean> stmtBlocks = new ArrayList<>();
            c.flatten(nodes, stmtBlocks);

            // Merge Consecutive ASD blocks for clean UI
            c.mergeASDBlocks(stmtBlocks);

            if (stmtBlocks.isEmpty() && c.exprBlocks.isEmpty())
                return new ConversionResult(new ArrayList<>(),
                        "No blocks could be generated from this code.", 0, 0);

            // stmtBlocks first so index-0 is always the sequence root
            ArrayList<BlockBean> all = new ArrayList<>(stmtBlocks);
            all.addAll(c.exprBlocks);
            return new ConversionResult(all, null, c.recognizedCount, c.fallbackCount);
        } catch (Exception e) {
            return new ConversionResult(new ArrayList<>(),
                    "Parser error: " + e.getMessage(), 0, 0);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PREPROCESSOR & TOKENIZER
    // ══════════════════════════════════════════════════════════════════════════

    private static final Pattern P_METHOD_DECL = Pattern.compile(
            "^(?:public|private|protected|static|final|abstract|native|synchronized)"
          + "(?:\\s+(?:public|private|protected|static|final|abstract|native|synchronized))*"
          + "\\s+[\\w<>\\[\\]]+\\s+\\w+\\s*\\(.*");

    private List<String> preprocess(String code) {
        // Strip block comments and line comments safely
        code = code.replaceAll("/\\*[\\s\\S]*?\\*/", "");
        code = code.replaceAll("//.*", "");

        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean inChar = false;
        int parenDepth = 0;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '\\') {
                sb.append(c);
                if (i + 1 < code.length()) sb.append(code.charAt(++i));
                continue;
            }
            if (c == '"' && !inChar) inString = !inString;
            if (c == '\'' && !inString) inChar = !inChar;

            if (!inString && !inChar) {
                if (c == '(') parenDepth++;
                else if (c == ')') parenDepth--;

                if (parenDepth == 0) {
                    if (c == '{' || c == '}') {
                        if (sb.toString().trim().length() > 0) out.add(sb.toString().trim());
                        out.add(String.valueOf(c));
                        sb.setLength(0);
                        continue;
                    } else if (c == ';') {
                        sb.append(c);
                        out.add(sb.toString().trim());
                        sb.setLength(0);
                        continue;
                    }
                }
            }
            sb.append(c);
        }
        if (sb.toString().trim().length() > 0) out.add(sb.toString().trim());

        List<String> finalOut = new ArrayList<>();
        for (String line : out) {
            if (line.isEmpty() || line.startsWith("@") || P_METHOD_DECL.matcher(line).matches()) continue;
            finalOut.add(line);
        }
        return finalOut;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PARSER
    // ══════════════════════════════════════════════════════════════════════════

    private static final Pattern P_IF        = Pattern.compile("^if\\s*\\((.+)\\)\\s*\\{?\\s*$");
    private static final Pattern P_FOR       = Pattern.compile("^for\\s*\\(int\\s+\\w+\\s*=\\s*0\\s*;\\s*\\w+\\s*<\\s*(.+?)\\s*;.*\\)\\s*\\{?\\s*$");
    private static final Pattern P_WHILE_T   = Pattern.compile("^while\\s*\\(\\s*true\\s*\\)\\s*\\{?\\s*$");
    private static final Pattern P_WHILE     = Pattern.compile("^while\\s*\\((.+)\\)\\s*\\{?\\s*$");
    private static final Pattern P_ELSE      = Pattern.compile("^\\}?\\s*else\\s*\\{?\\s*$");
    private static final Pattern P_CLOSE     = Pattern.compile("^\\}\\s*$");
    private static final Pattern P_OPEN      = Pattern.compile("^\\{\\s*$");

    private List<Node> parseSequence(List<String> lines, int[] pos) {
        List<Node> seq = new ArrayList<>();
        while (pos[0] < lines.size()) {
            String line = lines.get(pos[0]);
            if (P_CLOSE.matcher(line).matches() || P_ELSE.matcher(line).matches()) break;
            if (P_OPEN.matcher(line).matches()) { pos[0]++; continue; }
            Node n = parseNode(lines, pos);
            if (n != null) seq.add(n);
        }
        for (int i = 0; i < seq.size() - 1; i++)
            seq.get(i).bean.nextBlock = Integer.parseInt(seq.get(i + 1).bean.id);
        return seq;
    }

    private Node parseNode(List<String> lines, int[] pos) {
        String line = lines.get(pos[0]);
        Matcher m;

        m = P_IF.matcher(line);
        if (m.matches()) return parseIf(lines, pos, m.group(1).trim());

        m = P_FOR.matcher(line);
        if (m.matches()) return parseRepeat(lines, pos, m.group(1).trim());

        if (P_WHILE_T.matcher(line).matches()) return parseForever(lines, pos);

        m = P_WHILE.matcher(line);
        if (m.matches()) return parseWhile(lines, pos, m.group(1).trim());

        // ── View event listeners (container blocks with inline body) ───────────
        m = P_LISTENER_HEADER.matcher(line);
        if (m.matches()) return parseListener(lines, pos, viewName(m.group(1)), m.group(2));

        // Generic block parser for unsupported structures like switch, try, catch, do, or generic for-loops
        if (line.startsWith("switch") || line.startsWith("try") || line.startsWith("catch") || line.startsWith("do ") || (line.startsWith("for") && !P_FOR.matcher(line).matches())) {
            return parseGenericControl(lines, pos, line);
        }

        pos[0]++;
        Node n = new Node();
        n.bean = recognizeLine(line);
        return n;
    }

    /** Parses an already-collected inline listener body (one logical string) into a container Node. */
    /**
     * Parses a multi-line view listener (setOnClickListener / setOnLongClickListener /
     * setOnTouchListener). The header line (already matched by P_LISTENER_HEADER) and
     * the inner method-signature line (e.g. "public void onClick(View _view) {") are
     * consumed, then the body is collected brace-depth-aware until the method's own
     * closing '}', followed by the listener's closing "});".
     *
     * Trailing "return true;" boilerplate (required by OnLongClickListener / OnTouchListener
     * interfaces) is stripped — it carries no user logic and would otherwise generate a
     * spurious returnBoolean block.
     */
    private Node parseListener(List<String> lines, int[] pos, String viewN, String setterMethod) {
        int myId = newId();
        pos[0]++; // consume header line

        if (pos[0] < lines.size() && P_LISTENER_METHOD_SIG.matcher(lines.get(pos[0])).matches()) {
            pos[0]++; // consume "public void onClick(View _view) {" line
        }

        // Collect inner body lines, brace-depth aware (body may itself contain if/for/etc.)
        List<String> bodyLines = new ArrayList<>();
        int depth = 0;
        while (pos[0] < lines.size()) {
            String l = lines.get(pos[0]);
            if (P_CLOSE.matcher(l).matches()) {
                if (depth == 0) { pos[0]++; break; } // method's own closing brace
                depth--;
                bodyLines.add(l);
                pos[0]++;
            } else if (P_OPEN.matcher(l).matches()) {
                depth++;
                bodyLines.add(l);
                pos[0]++;
            } else {
                bodyLines.add(l);
                pos[0]++;
            }
        }

        // Strip trailing "return true;" boilerplate
        while (!bodyLines.isEmpty()
                && P_RETURN_TRUE_BOILERPLATE.matcher(bodyLines.get(bodyLines.size() - 1).trim()).matches()) {
            bodyLines.remove(bodyLines.size() - 1);
        }

        // Consume the listener's closing "});"
        if (pos[0] < lines.size() && P_LISTENER_CLOSE.matcher(lines.get(pos[0])).matches()) {
            pos[0]++;
        }

        int[] innerPos = {0};
        List<Node> body = parseSequence(bodyLines, innerPos);

        String opCode, spec;
        switch (setterMethod) {
            case "setOnLongClickListener":
                opCode = "viewOnLongClick"; spec = "When %m.view long clicked"; break;
            case "setOnTouchListener":
                opCode = "viewOnTouch"; spec = "When %m.view touched"; break;
            default:
                opCode = "viewOnClick"; spec = "When %m.view clicked"; break;
        }

        BlockBean bean = new BlockBean(String.valueOf(myId), spec, "c", opCode);
        bean.parameters.add(viewN);
        if (!body.isEmpty()) bean.subStack1 = Integer.parseInt(body.get(0).bean.id);

        Node node = new Node();
        node.bean  = bean;
        node.body1 = body;
        recognizedCount++;
        return node;
    }

    private Node parseGenericControl(List<String> lines, int[] pos, String header) {
        int myId = newId();
        pos[0]++;
        skipOpen(lines, pos);
        
        StringBuilder raw = new StringBuilder(header).append(" {\n");
        int depth = 1;
        while (pos[0] < lines.size() && depth > 0) {
            String l = lines.get(pos[0]++);
            if (P_OPEN.matcher(l).matches()) { depth++; raw.append("{\n"); }
            else if (P_CLOSE.matcher(l).matches()) { 
                depth--; 
                if (depth > 0) raw.append("}\n"); 
                else raw.append("}");
            }
            else {
                raw.append(l).append("\n");
            }
        }
        Node node = new Node();
        node.bean = asd(myId, raw.toString());
        fallbackCount++;
        return node;
    }

    private Node parseIf(List<String> lines, int[] pos, String rawCond) {
        int myId = newId();
        pos[0]++;
        skipOpen(lines, pos);
        List<Node> body1 = parseSequence(lines, pos);
        skipClose(lines, pos);

        Node node = new Node();
        node.body1 = body1;
        // Parse the condition as a boolean expression block
        String condParam = parseExpr(rawCond, ExprType.BOOLEAN);

        if (pos[0] < lines.size() && P_ELSE.matcher(lines.get(pos[0])).matches()) {
            pos[0]++;
            skipOpen(lines, pos);
            List<Node> body2 = parseSequence(lines, pos);
            skipClose(lines, pos);
            node.body2 = body2;

            node.bean = new BlockBean(String.valueOf(myId), "if %b then / else", "e", "ifElse");
            node.bean.parameters.add(condParam);
            if (!body1.isEmpty()) node.bean.subStack1 = Integer.parseInt(body1.get(0).bean.id);
            if (!body2.isEmpty()) node.bean.subStack2 = Integer.parseInt(body2.get(0).bean.id);
        } else {
            node.bean = new BlockBean(String.valueOf(myId), "if %b then", "c", "if");
            node.bean.parameters.add(condParam);
            if (!body1.isEmpty()) node.bean.subStack1 = Integer.parseInt(body1.get(0).bean.id);
        }
        recognizedCount++;
        return node;
    }

    private Node parseRepeat(List<String> lines, int[] pos, String rawCount) {
        int myId = newId();
        pos[0]++;
        skipOpen(lines, pos);
        List<Node> body = parseSequence(lines, pos);
        skipClose(lines, pos);

        String countParam = parseExpr(rawCount, ExprType.NUMBER);
        Node node = new Node();
        node.body1 = body;
        node.bean = new BlockBean(String.valueOf(myId), "repeat %d times", "c", "repeat");
        node.bean.parameters.add(countParam);
        if (!body.isEmpty()) node.bean.subStack1 = Integer.parseInt(body.get(0).bean.id);
        recognizedCount++;
        return node;
    }

    private Node parseForever(List<String> lines, int[] pos) {
        int myId = newId();
        pos[0]++;
        skipOpen(lines, pos);
        List<Node> body = parseSequence(lines, pos);
        skipClose(lines, pos);

        Node node = new Node();
        node.body1 = body;
        node.bean = new BlockBean(String.valueOf(myId), "forever", "c", "forever");
        if (!body.isEmpty()) node.bean.subStack1 = Integer.parseInt(body.get(0).bean.id);
        recognizedCount++;
        return node;
    }

    private Node parseWhile(List<String> lines, int[] pos, String cond) {
        int myId = newId();
        pos[0]++;
        skipOpen(lines, pos);
        List<Node> body = parseSequence(lines, pos);
        skipClose(lines, pos);

        String condParam = parseExpr(cond, ExprType.BOOLEAN);
        Node node = new Node();
        node.body1 = body;
        node.bean = new BlockBean(String.valueOf(myId), "while %b", "c", "whileLoop");
        node.bean.parameters.add(condParam);
        if (!body.isEmpty()) node.bean.subStack1 = Integer.parseInt(body.get(0).bean.id);
        recognizedCount++;
        return node;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STATEMENT RECOGNIZER
    // ══════════════════════════════════════════════════════════════════════════

    // ── Pre-compiled patterns ──────────────────────────────────────────────────

    // Toast / message
    private static final Pattern P_TOAST1 = Pattern.compile(
            "^Toast\\.makeText\\([^,]+,\\s*(.+?),\\s*Toast\\.LENGTH_\\w+\\)\\.show\\(\\)\\s*;?$");
    private static final Pattern P_TOAST2 = Pattern.compile(
            "^SketchwareUtil\\.showMessage\\([^,]+,\\s*(.+?)\\)\\s*;?$");

    // Control
    private static final Pattern P_FINISH   = Pattern.compile("^finish\\(\\)\\s*;?$");
    private static final Pattern P_BREAK    = Pattern.compile("^break\\s*;?$");

    // Variable increment/decrement
    private static final Pattern P_INC = Pattern.compile("^(\\w+)\\+\\+\\s*;?$");
    private static final Pattern P_DEC = Pattern.compile("^(\\w+)--\\s*;?$");

    // Variable assignment: var = expr;
    private static final Pattern P_ASSIGN_INT  = Pattern.compile("^(int|long|float|double)\\s+(\\w+)\\s*=\\s*(.+?)\\s*;?$");
    private static final Pattern P_ASSIGN_STR  = Pattern.compile("^String\\s+(\\w+)\\s*=\\s*(.+?)\\s*;?$");
    private static final Pattern P_ASSIGN_BOOL = Pattern.compile("^boolean\\s+(\\w+)\\s*=\\s*(.+?)\\s*;?$");
    private static final Pattern P_ASSIGN      = Pattern.compile("^(\\w+)\\s*=\\s*(.+?)\\s*;?$");

    // View: view.method(args)
    private static final Pattern P_SET_TEXT       = Pattern.compile("^([\\w.]+)\\.setText\\((.+)\\)\\s*;?$");
    private static final Pattern P_SET_VISIBLE    = Pattern.compile("^([\\w.]+)\\.setVisibility\\(View\\.(VISIBLE|GONE|INVISIBLE)\\)\\s*;?$");
    private static final Pattern P_SET_ENABLE     = Pattern.compile("^([\\w.]+)\\.setEnabled\\((.+)\\)\\s*;?$");
    private static final Pattern P_SET_ALPHA      = Pattern.compile("^([\\w.]+)\\.setAlpha\\(\\(float\\)\\((.+)\\)\\)\\s*;?$");
    private static final Pattern P_SET_ALPHA2     = Pattern.compile("^([\\w.]+)\\.setAlpha\\((.+)\\)\\s*;?$");
    private static final Pattern P_SET_ROTATE     = Pattern.compile("^([\\w.]+)\\.setRotation\\((?:\\(float\\))?\\(?(.+?)\\)?\\)\\s*;?$");
    private static final Pattern P_SET_BG_COLOR   = Pattern.compile("^([\\w.]+)\\.setBackgroundColor\\((.+)\\)\\s*;?$");
    private static final Pattern P_SET_TXT_COLOR  = Pattern.compile("^([\\w.]+)\\.setTextColor\\((.+)\\)\\s*;?$");
    private static final Pattern P_SET_CHECKED    = Pattern.compile("^([\\w.]+)\\.setChecked\\((.+)\\)\\s*;?$");
    private static final Pattern P_SET_CLICKABLE  = Pattern.compile("^([\\w.]+)\\.setClickable\\((.+)\\)\\s*;?$");
    private static final Pattern P_REQ_FOCUS      = Pattern.compile("^([\\w.]+)\\.requestFocus\\(\\)\\s*;?$");
    private static final Pattern P_SET_TX         = Pattern.compile("^([\\w.]+)\\.setTranslationX\\((?:\\(float\\))?\\(?(.+?)\\)?\\)\\s*;?$");
    private static final Pattern P_SET_TY         = Pattern.compile("^([\\w.]+)\\.setTranslationY\\((?:\\(float\\))?\\(?(.+?)\\)?\\)\\s*;?$");
    private static final Pattern P_SET_SX         = Pattern.compile("^([\\w.]+)\\.setScaleX\\((?:\\(float\\))?\\(?(.+?)\\)?\\)\\s*;?$");
    private static final Pattern P_SET_SY         = Pattern.compile("^([\\w.]+)\\.setScaleY\\((?:\\(float\\))?\\(?(.+?)\\)?\\)\\s*;?$");
    private static final Pattern P_SET_TITLE      = Pattern.compile("^setTitle\\((.+)\\)\\s*;?$");

    // Intent
    private static final Pattern P_START_ACT      = Pattern.compile("^startActivity\\((\\w+)\\)\\s*;?$");
    private static final Pattern P_INTENT_EXTRA   = Pattern.compile("^([\\w.]+)\\.putExtra\\((.+?),\\s*(.+)\\)\\s*;?$");
    private static final Pattern P_INTENT_SCREEN  = Pattern.compile("^([\\w.]+)\\.setClass\\([^,]+,\\s*(\\w+)\\.class\\)\\s*;?$");
    private static final Pattern P_INTENT_ACTION  = Pattern.compile("^([\\w.]+)\\.setAction\\((?:Intent\\.)?(.+)\\)\\s*;?$");
    private static final Pattern P_INTENT_DATA    = Pattern.compile("^([\\w.]+)\\.setData\\(Uri\\.parse\\((.+)\\)\\)\\s*;?$");

    // Collections
    private static final Pattern P_LIST_ADD       = Pattern.compile("^([\\w.]+)\\.add\\((.+)\\)\\s*;?$");
    private static final Pattern P_LIST_CLEAR     = Pattern.compile("^([\\w.]+)\\.clear\\(\\)\\s*;?$");
    private static final Pattern P_LIST_REMOVE    = Pattern.compile("^([\\w.]+)\\.remove\\(\\(int\\)\\(?(.+?)\\)?\\)\\s*;?$");
    private static final Pattern P_MAP_PUT        = Pattern.compile("^([\\w.]+)\\.put\\((.+?),\\s*(.+)\\)\\s*;?$");
    private static final Pattern P_MAP_REMOVE     = Pattern.compile("^([\\w.]+)\\.remove\\((.+)\\)\\s*;?$");
    private static final Pattern P_MAP_NEW        = Pattern.compile("^(\\w+)\\s*=\\s*new HashMap<>\\(\\)\\s*;?$");

    // SharedPreferences
    private static final Pattern P_FILE_SET       = Pattern.compile("^([\\w.]+)\\.edit\\(\\)\\.putString\\((.+?),\\s*(.+)\\)\\.commit\\(\\)\\s*;?$");
    private static final Pattern P_FILE_REMOVE    = Pattern.compile("^([\\w.]+)\\.edit\\(\\)\\.remove\\((.+)\\)\\.commit\\(\\)\\s*;?$");
    private static final Pattern P_FILE_OPEN      = Pattern.compile("^(\\w+)\\s*=\\s*getApplicationContext\\(\\)\\.getSharedPreferences\\((.+?),\\s*Activity\\.MODE_PRIVATE\\)\\s*;?$");

    // Calendar
    private static final Pattern P_CAL_NOW        = Pattern.compile("^(\\w+)\\s*=\\s*Calendar\\.getInstance\\(\\)\\s*;?$");
    private static final Pattern P_CAL_ADD        = Pattern.compile("^([\\w.]+)\\.add\\(Calendar\\.(\\w+),\\s*\\(int\\)\\((.+)\\)\\)\\s*;?$");
    private static final Pattern P_CAL_SET        = Pattern.compile("^([\\w.]+)\\.set\\(Calendar\\.(\\w+),\\s*\\(int\\)\\((.+)\\)\\)\\s*;?$");
    private static final Pattern P_CAL_SET_TIME   = Pattern.compile("^([\\w.]+)\\.setTimeInMillis\\(\\(long\\)\\((.+)\\)\\)\\s*;?$");

    // Clipboard
    private static final Pattern P_CLIPBOARD      = Pattern.compile("^\\(\\(ClipboardManager\\).+?CLIPBOARD_SERVICE\\)\\)\\.setPrimaryClip\\(ClipData\\.newPlainText\\(\"clipboard\",\\s*(.+)\\)\\)\\s*;?$");

    // MediaPlayer
    private static final Pattern P_MP_START       = Pattern.compile("^([\\w.]+)\\.start\\(\\)\\s*;?$");
    private static final Pattern P_MP_PAUSE       = Pattern.compile("^([\\w.]+)\\.pause\\(\\)\\s*;?$");
    private static final Pattern P_MP_RESET       = Pattern.compile("^([\\w.]+)\\.reset\\(\\)\\s*;?$");
    private static final Pattern P_MP_RELEASE     = Pattern.compile("^([\\w.]+)\\.release\\(\\)\\s*;?$");
    private static final Pattern P_MP_SEEK        = Pattern.compile("^([\\w.]+)\\.seekTo\\(\\(int\\)\\((.+)\\)\\)\\s*;?$");
    private static final Pattern P_MP_LOOP        = Pattern.compile("^([\\w.]+)\\.setLooping\\((.+)\\)\\s*;?$");

    // SeekBar
    private static final Pattern P_SB_PROG        = Pattern.compile("^([\\w.]+)\\.setProgress\\(\\(int\\)(.+?)\\)\\s*;?$");
    private static final Pattern P_SB_MAX         = Pattern.compile("^([\\w.]+)\\.setMax\\(\\(int\\)(.+?)\\)\\s*;?$");

    // WebView
    private static final Pattern P_WV_LOAD        = Pattern.compile("^([\\w.]+)\\.loadUrl\\((.+)\\)\\s*;?$");
    private static final Pattern P_WV_BACK        = Pattern.compile("^([\\w.]+)\\.goBack\\(\\)\\s*;?$");
    private static final Pattern P_WV_FWD         = Pattern.compile("^([\\w.]+)\\.goForward\\(\\)\\s*;?$");
    private static final Pattern P_WV_STOP        = Pattern.compile("^([\\w.]+)\\.stopLoading\\(\\)\\s*;?$");
    private static final Pattern P_WV_CLEAR_CACHE = Pattern.compile("^([\\w.]+)\\.clearCache\\(true\\)\\s*;?$");
    private static final Pattern P_WV_CLEAR_HIST  = Pattern.compile("^([\\w.]+)\\.clearHistory\\(\\)\\s*;?$");

    // Dialog
    private static final Pattern P_DLG_TITLE   = Pattern.compile("^([\\w.]+)\\.setTitle\\((.+)\\)\\s*;?$");
    private static final Pattern P_DLG_MSG     = Pattern.compile("^([\\w.]+)\\.setMessage\\((.+)\\)\\s*;?$");
    private static final Pattern P_DLG_SHOW    = Pattern.compile("^([\\w.]+)\\.create\\(\\)\\.show\\(\\)\\s*;?$");

    // Timer
    private static final Pattern P_TIMER_CANCEL = Pattern.compile("^([\\w.]+)\\.cancel\\(\\)\\s*;?$");

    // Spinner
    private static final Pattern P_SPN_SEL      = Pattern.compile("^([\\w.]+)\\.setSelection\\(\\(int\\)\\((.+)\\)\\)\\s*;?$");

    // ListView smooth scroll
    private static final Pattern P_LIST_SCROLL  = Pattern.compile("^([\\w.]+)\\.smoothScrollToPosition\\(\\(int\\)\\((.+)\\)\\)\\s*;?$");

    // ── View event listeners ─────────────────────────────────────────────────
    // ── View event listeners (multi-line, consumed via parseListener) ──────
    private static final Pattern P_LISTENER_HEADER = Pattern.compile(
            "^([\\w.]+)\\.(setOnClickListener|setOnLongClickListener|setOnTouchListener)\\(\\s*new\\s+View\\.(?:OnClickListener|OnLongClickListener|OnTouchListener)\\(\\)\\s*\\{\\s*$");
    private static final Pattern P_LISTENER_METHOD_SIG = Pattern.compile(
            "^public\\s+(?:void|boolean)\\s+on(?:Click|LongClick|Touch)\\([^)]*\\)\\s*\\{\\s*$");
    private static final Pattern P_RETURN_TRUE_BOILERPLATE = Pattern.compile("^return\\s+true\\s*;?\\s*$");
    private static final Pattern P_LISTENER_CLOSE = Pattern.compile("^\\}\\)\\s*;?\\s*$");

    // ── Additional view operations ───────────────────────────────────────────
    private static final Pattern P_SET_ELEVATION    = Pattern.compile("^([\\w.]+)\\.setElevation\\((?:\\(float\\))?\\(?(.+?)\\)?\\)\\s*;?$");
    private static final Pattern P_REMOVE_VIEW      = Pattern.compile("^([\\w.]+)\\.removeView\\(([\\w.]+)\\)\\s*;?$");
    private static final Pattern P_REMOVE_ALL_VIEWS = Pattern.compile("^([\\w.]+)\\.removeAllViews\\(\\)\\s*;?$");
    private static final Pattern P_ADD_VIEW         = Pattern.compile("^([\\w.]+)\\.addView\\(([\\w.]+)\\)\\s*;?$");
    private static final Pattern P_ADD_VIEW_IDX     = Pattern.compile("^([\\w.]+)\\.addView\\(([\\w.]+),\\s*(.+?)\\)\\s*;?$");
    private static final Pattern P_SET_GRAVITY      = Pattern.compile("^([\\w.]+)\\.setGravity\\(Gravity\\.(\\w+)(?:\\s*\\|\\s*Gravity\\.(\\w+))?\\)\\s*;?$");
    private static final Pattern P_SET_BG_RES       = Pattern.compile("^([\\w.]+)\\.setBackgroundResource\\((.+?)\\)\\s*;?$");
    private static final Pattern P_SET_BG_DRAWABLE  = Pattern.compile("^([\\w.]+)\\.setBackgroundDrawable\\(getResources\\(\\)\\.getDrawable\\(R\\.drawable\\.(\\w+)\\)\\)\\s*;?$");
    private static final Pattern P_SET_TYPEFACE     = Pattern.compile("^([\\w.]+)\\.setTypeface\\(Typeface\\.(\\w+),\\s*(\\d+)\\)\\s*;?$");
    private static final Pattern P_SET_TEXT_SIZE    = Pattern.compile("^([\\w.]+)\\.setTextSize\\((?:\\(int\\))?\\(?(.+?)\\)?\\)\\s*;?$");
    private static final Pattern P_PERFORM_CLICK    = Pattern.compile("^([\\w.]+)\\.performClick\\(\\)\\s*;?$");
    // GradientDrawable variants (DOTALL needed — complex inner class spans lines)
    private static final Pattern P_GRAD_4ARG = Pattern.compile("^([\\w.]+)\\.setBackground\\(new GradientDrawable\\(\\).*?\\.getIns\\(\\(int\\)(.+?),\\s*\\(int\\)(.+?),\\s*(0x[0-9A-Fa-f]+),\\s*(0x[0-9A-Fa-f]+)\\)\\)\\s*;?$", Pattern.DOTALL);
    private static final Pattern P_GRAD_3ARG = Pattern.compile("^([\\w.]+)\\.setBackground\\(new GradientDrawable\\(\\).*?\\.getIns\\(\\(int\\)(.+?),\\s*(0x[0-9A-Fa-f]+),\\s*(0x[0-9A-Fa-f]+)\\)\\)\\s*;?$", Pattern.DOTALL);
    private static final Pattern P_GRAD_2ARG = Pattern.compile("^([\\w.]+)\\.setBackground\\(new GradientDrawable\\(\\).*?\\.getIns\\(\\(int\\)(.+?),\\s*(0x[0-9A-Fa-f]+)\\)\\)\\s*;?$", Pattern.DOTALL);
    private static final Pattern P_GRAD_LINEAR= Pattern.compile("^([\\w.]+)\\.setBackground\\(new GradientDrawable\\(GradientDrawable\\.Orientation\\.\\w+,\\s*new int\\[\\]\\s*\\{(0x[0-9A-Fa-f]+),\\s*(0x[0-9A-Fa-f]+)\\}\\)\\)\\s*;?$");
    private static final Pattern P_COLOR_FILTER = Pattern.compile("^([\\w.]+)\\.getBackground\\(\\)\\.setColorFilter\\((.+?),\\s*PorterDuff\\.Mode\\.(\\w+)\\)\\s*;?$");

    // ── Typed HashMap.put ────────────────────────────────────────────────────
    private static final Pattern P_MAP_PUT_INT  = Pattern.compile("^([\\w.]+)\\.put\\((.+?),\\s*\\(int\\)\\((.+?)\\)\\)\\s*;?$");
    private static final Pattern P_MAP_PUT_DBL  = Pattern.compile("^([\\w.]+)\\.put\\((.+?),\\s*\\(double\\)\\((.+?)\\)\\)\\s*;?$");
    private static final Pattern P_MAP_PUT_BOOL = Pattern.compile("^([\\w.]+)\\.put\\((.+?),\\s*(true|false)\\)\\s*;?$");

    // ── List operations ──────────────────────────────────────────────────────
    private static final Pattern P_LIST_ADD_NUM     = Pattern.compile("^([\\w.]+)\\.add\\(Double\\.valueOf\\((.+?)\\)\\)\\s*;?$");
    private static final Pattern P_LIST_INSERT_INT  = Pattern.compile("^([\\w.]+)\\.add\\(\\(int\\)\\((.+?)\\),\\s*Double\\.valueOf\\((.+?)\\)\\)\\s*;?$");
    private static final Pattern P_LIST_INSERT_STR  = Pattern.compile("^([\\w.]+)\\.add\\(\\(int\\)\\((.+?)\\),\\s*(.+?)\\)\\s*;?$");
    private static final Pattern P_LIST_SET_NUM     = Pattern.compile("^([\\w.]+)\\.set\\(\\(int\\)\\((.+?)\\),\\s*Double\\.valueOf\\((.+?)\\)\\)\\s*;?$");
    private static final Pattern P_LIST_SET_STR     = Pattern.compile("^([\\w.]+)\\.set\\(\\(int\\)\\((.+?)\\),\\s*(.+?)\\)\\s*;?$");
    private static final Pattern P_LIST_ADD_ALL     = Pattern.compile("^([\\w.]+)\\.addAll\\((\\w+)\\)\\s*;?$");
    private static final Pattern P_COLL_SORT        = Pattern.compile("^Collections\\.sort\\((\\w+)\\)\\s*;?$");
    private static final Pattern P_COLL_REVERSE     = Pattern.compile("^Collections\\.reverse\\((\\w+)\\)\\s*;?$");
    private static final Pattern P_COLL_SHUFFLE     = Pattern.compile("^Collections\\.shuffle\\((\\w+)\\)\\s*;?$");
    private static final Pattern P_COLL_SWAP        = Pattern.compile("^Collections\\.swap\\((\\w+),\\s*\\(int\\)\\((.+?)\\),\\s*\\(int\\)\\((.+?)\\)\\)\\s*;?$");
    private static final Pattern P_SORT_LISTMAP     = Pattern.compile("^SketchwareUtil\\.sortListMap\\((\\w+),\\s*(.+?),\\s*(true|false),\\s*(true|false)\\)\\s*;?$");
    private static final Pattern P_LIST_MAP_SET     = Pattern.compile("^([\\w.]+)\\.set\\(\\(int\\)\\((.+?)\\),\\s*(\\w+)\\)\\s*;?$");

    // ── Return blocks (type "f") ──────────────────────────────────────────────
    private static final Pattern P_RETURN_STR  = Pattern.compile("^return\\s*\\(\"(.*)\"\\)\\s*;?$");
    private static final Pattern P_RETURN_BOOL = Pattern.compile("^return\\s*\\((true|false)\\)\\s*;?$");
    private static final Pattern P_RETURN_NUM  = Pattern.compile("^return\\s*\\(([0-9.]+)\\)\\s*;?$");
    private static final Pattern P_RETURN_VAR  = Pattern.compile("^return\\s+(\\w+)\\s*;?$");

    // ── FileUtil ─────────────────────────────────────────────────────────────
    private static final Pattern P_FU_WRITE   = Pattern.compile("^FileUtil\\.writeFile\\((.+?),\\s*(.+?)\\)\\s*;?$");
    private static final Pattern P_FU_COPY    = Pattern.compile("^FileUtil\\.copyFile\\((.+?),\\s*(.+?)\\)\\s*;?$");
    private static final Pattern P_FU_MOVE    = Pattern.compile("^FileUtil\\.moveFile\\((.+?),\\s*(.+?)\\)\\s*;?$");
    private static final Pattern P_FU_DELETE  = Pattern.compile("^FileUtil\\.deleteFile\\((.+?)\\)\\s*;?$");
    private static final Pattern P_FU_MKDIR   = Pattern.compile("^FileUtil\\.makeDir\\((.+?)\\)\\s*;?$");

    // ── Activity / UI ────────────────────────────────────────────────────────
    private static final Pattern P_FINISH_AFFINITY = Pattern.compile("^finishAffinity\\(\\)\\s*;?$");
    private static final Pattern P_SHOW_KEYBOARD   = Pattern.compile("^SketchwareUtil\\.showKeyboard\\(.+\\)\\s*;?$");
    private static final Pattern P_HIDE_KEYBOARD   = Pattern.compile("^SketchwareUtil\\.hideKeyboard\\(.+\\)\\s*;?$");
    private static final Pattern P_LIGHT_STATUS    = Pattern.compile("^getWindow\\(\\)\\.getDecorView\\(\\)\\.setSystemUiVisibility\\(View\\.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR\\)\\s*;?$");

    // ── Intent extras ────────────────────────────────────────────────────────
    private static final Pattern P_INTENT_TYPE        = Pattern.compile("^([\\w.]+)\\.setType\\((.+?)\\)\\s*;?$");
    private static final Pattern P_INTENT_REMOVE_EXTRA= Pattern.compile("^([\\w.]+)\\.removeExtra\\((.+?)\\)\\s*;?$");
    private static final Pattern P_INTENT_FLAGS       = Pattern.compile("^([\\w.]+)\\.setFlags\\(Intent\\.FLAG_ACTIVITY_(\\w+)\\)\\s*;?$");
    private static final Pattern P_START_CHOOSER      = Pattern.compile("^startActivity\\(Intent\\.createChooser\\((\\w+),\\s*(.+?)\\)\\)\\s*;?$");
    private static final Pattern P_LAUNCH_APP         = Pattern.compile("^(\\w+)\\s*=\\s*getPackageManager\\(\\)\\.getLaunchIntentForPackage\\((.+?)\\)\\s*;?$");

    // ── SharedPreferences extra ───────────────────────────────────────────────
    private static final Pattern P_SP_CONTAINS = Pattern.compile("^([\\w.]+)\\.contains\\((.+?)\\)\\.booleanValue\\(\\)\\s*;?$");

    // ── DatePicker / TimePicker ───────────────────────────────────────────────
    private static final Pattern P_TIME_PICKER_SHOW = Pattern.compile("^([\\w.]+)\\.show\\(\\)\\s*;?$");

    /**
     * Tries to match {@code line} against every loaded custom block's code template.
     * Returns a BlockBean if matched, null otherwise.
     *
     * Custom block code uses String.format, so a template like
     *   "%2$s.setText(%1$s);"
     * compiled to regex:
     *   "^(.+?)\.setText\((.+?)\)(?:;)?$"
     * where group 1 → spec param index 1 (textview) and group 2 → spec param index 0 (string).
     *
     * Captured groups are fed through parseExpr() with the type from the spec.
     */
    private BlockBean tryCustomBlockMatch(String line) {
        for (CustomBlockMatcher matcher : getCustomMatchers()) {
            Matcher m = matcher.pattern.matcher(line);
            if (!m.matches()) continue;

            BlockBean bean = new BlockBean(String.valueOf(newId()),
                    matcher.spec, matcher.type, matcher.name);

            // Allocate param slots (one per spec param)
            String[] params = new String[matcher.specParamTypes.length];
            Arrays.fill(params, "");

            // Map each capture group → its spec param slot, then parse with correct type
            for (int g = 0; g < matcher.captureCount; g++) {
                int specIdx = matcher.groupToSpecIdx[g];
                if (specIdx < params.length) {
                    String captured = m.group(g + 1).trim();
                    ExprType type = specIdx < matcher.specParamTypes.length
                                  ? matcher.specParamTypes[specIdx]
                                  : ExprType.UNKNOWN;
                    if (type == ExprType.UNKNOWN) {
                        // View / component name — strip binding. prefix only
                        params[specIdx] = viewName(captured);
                    } else {
                        params[specIdx] = parseExpr(captured, type);
                    }
                }
            }

            for (String p : params) bean.parameters.add(p);
            recognizedCount++;
            return bean;
        }
        return null;
    }

        private BlockBean recognizeLine(String line) {
        Matcher m;

        // ── Toast ──────────────────────────────────────────────────────────────
        m = P_TOAST1.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "toast %s", "doToast",
                    parseExpr(m.group(1).trim(), ExprType.STRING)); }

        m = P_TOAST2.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "toast %s", "doToast",
                    parseExpr(m.group(1).trim(), ExprType.STRING)); }

        // ── Control ────────────────────────────────────────────────────────────
        if (P_FINISH.matcher(line).matches()) { recognizedCount++;
            return new BlockBean(String.valueOf(newId()), "Finish Activity", "f", "finishActivity"); }

        if (P_BREAK.matcher(line).matches()) { recognizedCount++;
            return new BlockBean(String.valueOf(newId()), "break", " ", "break"); }

        // ── Increment / decrement ─────────────────────────────────────────────
        m = P_INC.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "int %m.varInt++", "increaseInt", m.group(1)); }

        m = P_DEC.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "int %m.varInt--", "decreaseInt", m.group(1)); }

        // ── Typed variable declaration ─────────────────────────────────────────
        m = P_ASSIGN_INT.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "set int %m.varInt to %d", "setVarInt",
                    m.group(2), parseExpr(m.group(3).trim(), ExprType.NUMBER)); }

        m = P_ASSIGN_STR.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "set String %m.varStr to %s", "setVarString",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING)); }

        m = P_ASSIGN_BOOL.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "set boolean %m.varBool to %b", "setVarBoolean",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.BOOLEAN)); }

        // ── View operations ────────────────────────────────────────────────────
        m = P_SET_TEXT.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.textview setText %s", "setText",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.STRING)); }

        m = P_SET_VISIBLE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setVisible %m.visible", "setVisible",
                    viewName(m.group(1)), m.group(2)); }

        m = P_SET_ENABLE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setEnable %b", "setEnable",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.BOOLEAN)); }

        m = P_SET_ALPHA.matcher(line);
        if (!m.matches()) m = P_SET_ALPHA2.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setAlpha %d", "setAlpha",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        m = P_SET_ROTATE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setRotate %d", "setRotate",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        m = P_SET_BG_COLOR.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setBgColor %d", "setBgColor",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        m = P_SET_TXT_COLOR.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.textview setTextColor %d", "setTextColor",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        m = P_SET_CHECKED.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.checkBox setChecked %b", "setChecked",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.BOOLEAN)); }

        m = P_SET_CLICKABLE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setClickable %b", "setClickable",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.BOOLEAN)); }

        m = P_REQ_FOCUS.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view requestFocus", "requestFocus", viewName(m.group(1))); }

        m = P_SET_TX.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setTranslationX %d", "setTranslationX",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        m = P_SET_TY.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setTranslationY %d", "setTranslationY",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        m = P_SET_SX.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setScaleX %d", "setScaleX",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        m = P_SET_SY.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setScaleY %d", "setScaleY",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        m = P_SET_TITLE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "setTitle %s", "setTitle",
                    parseExpr(m.group(1).trim(), ExprType.STRING)); }

        // ── Intent ─────────────────────────────────────────────────────────────
        m = P_START_ACT.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "startActivity %m.intent", "startActivity", m.group(1)); }

        m = P_INTENT_EXTRA.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.intent putExtra %s %s", "intentPutExtra",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING),
                    parseExpr(m.group(3).trim(), ExprType.STRING)); }

        m = P_INTENT_SCREEN.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.intent setScreen %m.activity", "intentSetScreen",
                    m.group(1), m.group(2)); }

        m = P_INTENT_ACTION.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.intent setAction %s", "intentSetAction",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING)); }

        m = P_INTENT_DATA.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.intent setData %s", "intentSetData",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING)); }

        // ── Collections ────────────────────────────────────────────────────────
        m = P_MAP_NEW.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.map createNewMap", "mapCreateNew", m.group(1)); }

        m = P_MAP_PUT.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.map put %s %s", "mapPut",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING),
                    parseExpr(m.group(3).trim(), ExprType.STRING)); }

        m = P_MAP_REMOVE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.map remove %s", "mapRemoveKey",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING)); }

        m = P_LIST_REMOVE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "delete index %d in list %m.list", "deleteList",
                    parseExpr(m.group(2).trim(), ExprType.NUMBER), m.group(1)); }

        m = P_LIST_CLEAR.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "clear list %m.list", "clearList", m.group(1)); }

        m = P_LIST_ADD.matcher(line);
        if (m.matches()) {
            String listName = m.group(1);
            String item     = m.group(2).trim();
            boolean isNum   = item.matches("-?\\d+(\\.\\d+)?") || item.contains("doubleValue");
            recognizedCount++;
            if (isNum) {
                return stmt(newId(), "add number %d into list %m.list", "addListInt",
                        parseExpr(item, ExprType.NUMBER), listName);
            } else {
                return stmt(newId(), "add string %s into list %m.list", "addListStr",
                        parseExpr(item, ExprType.STRING), listName);
            }
        }

        // ── SharedPreferences ──────────────────────────────────────────────────
        m = P_FILE_OPEN.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.file setFileName %s", "fileSetFileName",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING)); }

        m = P_FILE_SET.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.file put data %s to key %s", "fileSetData",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING),
                    parseExpr(m.group(3).trim(), ExprType.STRING)); }

        m = P_FILE_REMOVE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.file remove key %s", "fileRemoveData",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING)); }

        // ── Calendar ───────────────────────────────────────────────────────────
        m = P_CAL_NOW.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "get now to %m.calendar", "calendarGetNow", m.group(1)); }

        m = P_CAL_ADD.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.calendar add %m.calendarField %d", "calendarAdd",
                    m.group(1), m.group(2), parseExpr(m.group(3).trim(), ExprType.NUMBER)); }

        m = P_CAL_SET.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.calendar set %m.calendarField %d", "calendarSet",
                    m.group(1), m.group(2), parseExpr(m.group(3).trim(), ExprType.NUMBER)); }

        m = P_CAL_SET_TIME.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.calendar setTime %d", "calendarSetTime",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        // ── Clipboard ──────────────────────────────────────────────────────────
        m = P_CLIPBOARD.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "copy %s to clipboard", "copyToClipboard",
                    parseExpr(m.group(1).trim(), ExprType.STRING)); }

        // ── MediaPlayer ────────────────────────────────────────────────────────
        m = P_MP_SEEK.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.mediaplayer seekTo %d", "mediaplayerSeek",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        m = P_MP_LOOP.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.mediaplayer setLooping %b", "mediaplayerSetLooping",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.BOOLEAN)); }

        m = P_MP_START.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.mediaplayer start", "mediaplayerStart", m.group(1)); }

        m = P_MP_PAUSE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.mediaplayer pause", "mediaplayerPause", m.group(1)); }

        m = P_MP_RESET.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.mediaplayer reset", "mediaplayerReset", m.group(1)); }

        m = P_MP_RELEASE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.mediaplayer release", "mediaplayerRelease", m.group(1)); }

        // ── SeekBar ────────────────────────────────────────────────────────────
        m = P_SB_PROG.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.seekBar setProgress %d", "seekBarSetProgress",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        m = P_SB_MAX.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.seekBar setMax %d", "seekBarSetMax",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        // ── WebView ────────────────────────────────────────────────────────────
        m = P_WV_LOAD.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.webView loadUrl %s", "webViewLoadUrl",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING)); }

        m = P_WV_BACK.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.webView goBack", "webViewGoBack", m.group(1)); }

        m = P_WV_FWD.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.webView goForward", "webViewGoForward", m.group(1)); }

        m = P_WV_STOP.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.webView stopLoading", "webViewStopLoading", m.group(1)); }

        m = P_WV_CLEAR_CACHE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.webView clearCache", "webViewClearCache", m.group(1)); }

        m = P_WV_CLEAR_HIST.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.webView clearHistory", "webViewClearHistory", m.group(1)); }

        // ── Dialog ─────────────────────────────────────────────────────────────
        m = P_DLG_TITLE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.dialog setTitle %s", "dialogSetTitle",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING)); }

        m = P_DLG_MSG.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.dialog setMessage %s", "dialogSetMessage",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING)); }

        m = P_DLG_SHOW.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.dialog show", "dialogShow", m.group(1)); }

        // ── Timer ──────────────────────────────────────────────────────────────
        m = P_TIMER_CANCEL.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "cancel timer %m.timer", "timerCancel", m.group(1)); }

        // ── Spinner ────────────────────────────────────────────────────────────
        m = P_SPN_SEL.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.spinner setSelection %d", "spnSetSelection",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        // ── ListView ───────────────────────────────────────────────────────────
        m = P_LIST_SCROLL.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.list smoothScrollToPosition %d", "listSmoothScrollTo",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        // ── Finish Affinity ───────────────────────────────────────────────────
        if (P_FINISH_AFFINITY.matcher(line).matches()) { recognizedCount++;
            return new BlockBean(String.valueOf(newId()), "Finish Affinity", "f", "finishAffinity"); }

        // ── Return blocks (type "f") ───────────────────────────────────────────
        m = P_RETURN_STR.matcher(line);
        if (m.matches()) { recognizedCount++;
            BlockBean rb = new BlockBean(String.valueOf(newId()), "return %s", "f", "returnString");
            rb.parameters.add(m.group(1)); return rb; }

        m = P_RETURN_BOOL.matcher(line);
        if (m.matches()) { recognizedCount++;
            BlockBean rb = new BlockBean(String.valueOf(newId()), "return %b", "f", "returnBoolean");
            rb.parameters.add(parseExpr(m.group(1), ExprType.BOOLEAN)); return rb; }

        m = P_RETURN_NUM.matcher(line);
        if (m.matches()) { recognizedCount++;
            BlockBean rb = new BlockBean(String.valueOf(newId()), "return %d", "f", "returnNumber");
            rb.parameters.add(m.group(1)); return rb; }

        m = P_RETURN_VAR.matcher(line);
        if (m.matches()) { recognizedCount++;
            // Heuristic: if variable name contains "map" → returnMap, "list" → returnListStr, else asd
            String rv = m.group(1);
            String rvL = rv.toLowerCase();
            if (rvL.contains("listmap") || rvL.contains("maplist")) {
                BlockBean rb = new BlockBean(String.valueOf(newId()), "return %m.listMap", "f", "returnListMap");
                rb.parameters.add(rv); return rb;
            } else if (rvL.contains("list")) {
                BlockBean rb = new BlockBean(String.valueOf(newId()), "return %m.listStr", "f", "returnListStr");
                rb.parameters.add(rv); return rb;
            } else if (rvL.contains("map")) {
                BlockBean rb = new BlockBean(String.valueOf(newId()), "return %m.varMap", "f", "returnMap");
                rb.parameters.add(rv); return rb;
            } else {
                // Could be a view or unknown — use addSourceDirectly to be safe
                fallbackCount++;
                return asd(newId(), "return " + rv + ";");
            }
        }

        // ── Additional view operations ─────────────────────────────────────────
        m = P_SET_ELEVATION.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setElevation %d", "setElevation",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        m = P_REMOVE_VIEW.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view removeView %m.view", "removeView",
                    viewName(m.group(1)), viewName(m.group(2))); }

        m = P_REMOVE_ALL_VIEWS.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view removeAllViews", "removeViews", viewName(m.group(1))); }

        m = P_ADD_VIEW_IDX.matcher(line);  // before P_ADD_VIEW (more specific)
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view addView %m.view index %d", "addViews",
                    viewName(m.group(1)), viewName(m.group(2)),
                    parseExpr(m.group(3).trim(), ExprType.NUMBER)); }

        m = P_ADD_VIEW.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view addView %m.view", "addView",
                    viewName(m.group(1)), viewName(m.group(2))); }

        m = P_SET_GRAVITY.matcher(line);
        if (m.matches()) { recognizedCount++;
            String gv = m.group(2);
            String gh = m.group(3) != null ? m.group(3) : "CENTER_HORIZONTAL";
            return stmt(newId(), "%m.view setGravity %m.gravity_v %m.gravity_h", "setGravity",
                    viewName(m.group(1)), gv, gh); }

        m = P_SET_BG_RES.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setBackgroundResource %m.resource_bg", "setBgResource",
                    viewName(m.group(1)), m.group(2).equals("0") ? "NONE" : m.group(2)); }

        m = P_SET_BG_DRAWABLE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setBackgroundDrawable %m.drawable", "setBgDrawable",
                    viewName(m.group(1)), m.group(2)); }

        m = P_GRAD_4ARG.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setCornerRadius %d stroke %d strokeColor %m.color bgColor %m.color", "setRadiusAndStrokeView",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.NUMBER),
                    parseExpr(m.group(3).trim(), ExprType.NUMBER), m.group(4), m.group(5)); }

        m = P_GRAD_3ARG.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setStroke %d strokeColor %m.color bgColor %m.color", "setStrokeView",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.NUMBER),
                    m.group(3), m.group(4)); }

        m = P_GRAD_2ARG.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setCornerRadius %d color %m.color", "setCornerRadiusView",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.NUMBER), m.group(3)); }

        m = P_GRAD_LINEAR.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setGradientBackground %m.color and %m.color", "setGradientBackground",
                    viewName(m.group(1)), m.group(2), m.group(3)); }

        m = P_COLOR_FILTER.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view setColorFilter %m.color with %m.porterduff", "setColorFilterView",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.NUMBER), m.group(3)); }

        m = P_SET_TYPEFACE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.textview setTypeface %m.font with style %m.typeface", "setTypeface",
                    viewName(m.group(1)), m.group(2), m.group(3)); }

        m = P_SET_TEXT_SIZE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.textview setTextSize %d", "setTextSize",
                    viewName(m.group(1)), parseExpr(m.group(2).trim(), ExprType.NUMBER)); }

        m = P_PERFORM_CLICK.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.view performClick", "performClick", viewName(m.group(1))); }

        // ── Typed HashMap.put ─────────────────────────────────────────────────
        m = P_MAP_PUT_INT.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.varMap put key %s value int %d", "hashmapPutNumber",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING),
                    parseExpr(m.group(3).trim(), ExprType.NUMBER)); }

        m = P_MAP_PUT_DBL.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.varMap put key %s value double %d", "hashmapPutNumber2",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING),
                    parseExpr(m.group(3).trim(), ExprType.NUMBER)); }

        m = P_MAP_PUT_BOOL.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.varMap put key %s value %b", "hashmapPutBoolean",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING),
                    parseExpr(m.group(3).trim(), ExprType.BOOLEAN)); }

        // ── List operations ───────────────────────────────────────────────────
        m = P_LIST_ADD_NUM.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "add %d to %m.listInt", "addListInt",
                    parseExpr(m.group(2).trim(), ExprType.NUMBER), m.group(1)); }

        m = P_LIST_INSERT_INT.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "insert %d at %d to %m.listInt", "insertListInt",
                    parseExpr(m.group(3).trim(), ExprType.NUMBER),
                    parseExpr(m.group(2).trim(), ExprType.NUMBER), m.group(1)); }

        m = P_LIST_INSERT_STR.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "insert %s at %d to %m.listStr", "insertListStr",
                    parseExpr(m.group(3).trim(), ExprType.STRING),
                    parseExpr(m.group(2).trim(), ExprType.NUMBER), m.group(1)); }

        m = P_LIST_SET_NUM.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "set %d at %d of %m.listInt", "setAtPosListnum",
                    parseExpr(m.group(3).trim(), ExprType.NUMBER),
                    parseExpr(m.group(2).trim(), ExprType.NUMBER), m.group(1)); }

        m = P_LIST_SET_STR.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "set %s at %d of %m.listStr", "setAtPosListstr",
                    parseExpr(m.group(3).trim(), ExprType.STRING),
                    parseExpr(m.group(2).trim(), ExprType.NUMBER), m.group(1)); }

        m = P_LIST_MAP_SET.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "set %m.varMap at %d of %m.listMap", "setMapAtPosListmap",
                    m.group(3), parseExpr(m.group(2).trim(), ExprType.NUMBER), m.group(1)); }

        m = P_LIST_ADD_ALL.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.listStr addAll from %m.list", "listAddAll",
                    m.group(1), m.group(2)); }

        m = P_COLL_SORT.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "sort %m.listStr", "sortList", m.group(1)); }

        m = P_COLL_REVERSE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "reverse %m.list", "reverseList", m.group(1)); }

        m = P_COLL_SHUFFLE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "shuffle %m.list", "shuffleList", m.group(1)); }

        m = P_COLL_SWAP.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "swap %m.list position %d with %d", "swapInList",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.NUMBER),
                    parseExpr(m.group(3).trim(), ExprType.NUMBER)); }

        m = P_SORT_LISTMAP.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "sort %m.listMap key %s isNumber %b isAscending %b", "sortListmap",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING),
                    parseExpr(m.group(3).trim(), ExprType.BOOLEAN),
                    parseExpr(m.group(4).trim(), ExprType.BOOLEAN)); }

        // ── FileUtil ──────────────────────────────────────────────────────────
        m = P_FU_WRITE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "write String %s to file path %s", "fileutilwrite",
                    parseExpr(m.group(2).trim(), ExprType.STRING),
                    parseExpr(m.group(1).trim(), ExprType.STRING)); }

        m = P_FU_COPY.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "copy file path %s to path %s", "fileutilcopy",
                    parseExpr(m.group(1).trim(), ExprType.STRING),
                    parseExpr(m.group(2).trim(), ExprType.STRING)); }

        m = P_FU_MOVE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "move file path %s to path %s", "fileutilmove",
                    parseExpr(m.group(1).trim(), ExprType.STRING),
                    parseExpr(m.group(2).trim(), ExprType.STRING)); }

        m = P_FU_DELETE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "delete file path %s", "fileutildelete",
                    parseExpr(m.group(1).trim(), ExprType.STRING)); }

        m = P_FU_MKDIR.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "make directory path %s", "fileutilmakedir",
                    parseExpr(m.group(1).trim(), ExprType.STRING)); }

        // ── Activity / UI ─────────────────────────────────────────────────────
        if (P_SHOW_KEYBOARD.matcher(line).matches()) { recognizedCount++;
            return new BlockBean(String.valueOf(newId()), "Show keyboard", " ", "showKeyboard"); }

        if (P_HIDE_KEYBOARD.matcher(line).matches()) { recognizedCount++;
            return new BlockBean(String.valueOf(newId()), "Hide keyboard", " ", "hideKeyboard"); }

        if (P_LIGHT_STATUS.matcher(line).matches()) { recognizedCount++;
            return new BlockBean(String.valueOf(newId()), "LightStatusBar", " ", "LightStatusBar"); }

        // ── Intent extras ─────────────────────────────────────────────────────
        m = P_LAUNCH_APP.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.intent set app package %s", "launchApp",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING)); }

        m = P_INTENT_TYPE.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.intent setType %s", "intentSetType",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING)); }

        m = P_INTENT_REMOVE_EXTRA.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.intent removeExtra key %s", "intentRemoveExtra",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING)); }

        m = P_INTENT_FLAGS.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "%m.intent setFlags %m.intentFlags", "intentSetFlags",
                    m.group(1), m.group(2)); }

        m = P_START_CHOOSER.matcher(line);
        if (m.matches()) { recognizedCount++;
            return stmt(newId(), "StartActivity %m.intent with Chooser %s", "startActivityWithChooser",
                    m.group(1), parseExpr(m.group(2).trim(), ExprType.STRING)); }

        // ── Generic assignment (after typed declarations failed) ────────────────
        m = P_ASSIGN.matcher(line);
        if (m.matches()) {
            String var = m.group(1);
            String val = m.group(2).trim();
            String opCode, spec;
            ExprType type;
            if (val.startsWith("\"")) {
                opCode = "setVarString"; spec = "set String %m.varStr to %s"; type = ExprType.STRING;
            } else if ("true".equals(val) || "false".equals(val)) {
                opCode = "setVarBoolean"; spec = "set boolean %m.varBool to %b"; type = ExprType.BOOLEAN;
            } else {
                opCode = "setVarInt"; spec = "set int %m.varInt to %d"; type = ExprType.NUMBER;
            }
            recognizedCount++;
            return stmt(newId(), spec, opCode, var, parseExpr(val, type));
        }

        // ── Custom block match (last chance before addSourceDirectly) ───────────
        // Try runtime-loaded custom blocks. If the line was generated by a custom
        // block's String.format template, it will match here and produce the proper
        // BlockBean instead of being wrapped in addSourceDirectly.
        BlockBean customMatch = tryCustomBlockMatch(line);
        if (customMatch != null) return customMatch;

        // ── Fallback: addSourceDirectly ────────────────────────────────────────
        fallbackCount++;
        return asd(newId(), ensureSemicolon(line));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXPRESSION PARSER  –  converts Java expr to param string or @blockId
    // ══════════════════════════════════════════════════════════════════════════

    private String parseExpr(String expr, ExprType hint) {
        if (expr == null || expr.isBlank()) return "";
        expr = expr.trim();

        // Strip outer parentheses  ( expr )
        if (expr.startsWith("(") && matchingClose(expr, 0) == expr.length() - 1)
            return parseExpr(expr.substring(1, expr.length() - 1), hint);

        // ── Boolean constants ──────────────────────────────────────────────────
        if ("true".equals(expr)) { return exprRef(exprB("true", "b", "true")); }
        if ("false".equals(expr)){ return exprRef(exprB("false", "b", "false")); }

        // ── Logical NOT  !expr ─────────────────────────────────────────────────
        if (expr.startsWith("!")) {
            String inner = parseExpr(expr.substring(1).trim(), ExprType.BOOLEAN);
            return exprRef(exprB1("not %b", "b", "not", inner));
        }

        // ── Binary operators (lowest precedence first) ─────────────────────────
        int op;

        op = topLevelOp(expr, "||");
        if (op >= 0) {
            String l = parseExpr(expr.substring(0, op).trim(), ExprType.BOOLEAN);
            String r = parseExpr(expr.substring(op + 2).trim(), ExprType.BOOLEAN);
            return exprRef(exprB2("%b || %b", "b", "||", l, r));
        }

        op = topLevelOp(expr, "&&");
        if (op >= 0) {
            String l = parseExpr(expr.substring(0, op).trim(), ExprType.BOOLEAN);
            String r = parseExpr(expr.substring(op + 2).trim(), ExprType.BOOLEAN);
            return exprRef(exprB2("%b && %b", "b", "&&", l, r));
        }

        op = topLevelOp(expr, "==");
        if (op >= 0) {
            String l = parseExpr(expr.substring(0, op).trim(), ExprType.UNKNOWN);
            String r = parseExpr(expr.substring(op + 2).trim(), ExprType.UNKNOWN);
            return exprRef(exprB2("%d = %d", "b", "=", l, r));
        }

        op = topLevelOp(expr, "!=");
        if (op >= 0) {
            // Sketchware has no != block natively; wrap in not(=(...))
            String l = parseExpr(expr.substring(0, op).trim(), ExprType.UNKNOWN);
            String r = parseExpr(expr.substring(op + 2).trim(), ExprType.UNKNOWN);
            String eqBlock = exprRef(exprB2("%d = %d", "b", "=", l, r));
            return exprRef(exprB1("not %b", "b", "not", eqBlock));
        }

        op = topLevelOp(expr, ">=");
        if (op >= 0) {
            // a >= b  →  not (a < b)
            String l = parseExpr(expr.substring(0, op).trim(), ExprType.NUMBER);
            String r = parseExpr(expr.substring(op + 2).trim(), ExprType.NUMBER);
            String ltBlock = exprRef(exprB2("%d < %d", "b", "<", l, r));
            return exprRef(exprB1("not %b", "b", "not", ltBlock));
        }

        op = topLevelOp(expr, "<=");
        if (op >= 0) {
            // a <= b  →  not (a > b)
            String l = parseExpr(expr.substring(0, op).trim(), ExprType.NUMBER);
            String r = parseExpr(expr.substring(op + 2).trim(), ExprType.NUMBER);
            String gtBlock = exprRef(exprB2("%d > %d", "b", ">", l, r));
            return exprRef(exprB1("not %b", "b", "not", gtBlock));
        }

        op = topLevelOp(expr, ">");
        if (op >= 0) {
            String l = parseExpr(expr.substring(0, op).trim(), ExprType.NUMBER);
            String r = parseExpr(expr.substring(op + 1).trim(), ExprType.NUMBER);
            return exprRef(exprB2("%d > %d", "b", ">", l, r));
        }

        op = topLevelOp(expr, "<");
        if (op >= 0) {
            String l = parseExpr(expr.substring(0, op).trim(), ExprType.NUMBER);
            String r = parseExpr(expr.substring(op + 1).trim(), ExprType.NUMBER);
            return exprRef(exprB2("%d < %d", "b", "<", l, r));
        }

        // Arithmetic +
        op = topLevelOp(expr, "+");
        if (op >= 0) {
            String l = parseExpr(expr.substring(0, op).trim(), hint);
            String r = parseExpr(expr.substring(op + 1).trim(), hint);
            if (hint == ExprType.STRING) {
                // String concat block
                return exprRef(exprB2("%s.concat(%s)", "s", "+", l, r));
            } else {
                return exprRef(exprB2("%d + %d", "d", "+", l, r));
            }
        }

        op = topLevelOp(expr, "-");
        if (op >= 0) {
            String l = parseExpr(expr.substring(0, op).trim(), ExprType.NUMBER);
            String r = parseExpr(expr.substring(op + 1).trim(), ExprType.NUMBER);
            return exprRef(exprB2("%d - %d", "d", "-", l, r));
        }

        op = topLevelOp(expr, "*");
        if (op >= 0) {
            String l = parseExpr(expr.substring(0, op).trim(), ExprType.NUMBER);
            String r = parseExpr(expr.substring(op + 1).trim(), ExprType.NUMBER);
            return exprRef(exprB2("%d * %d", "d", "*", l, r));
        }

        op = topLevelOp(expr, "/");
        if (op >= 0) {
            String l = parseExpr(expr.substring(0, op).trim(), ExprType.NUMBER);
            String r = parseExpr(expr.substring(op + 1).trim(), ExprType.NUMBER);
            return exprRef(exprB2("%d / %d", "d", "/", l, r));
        }

        op = topLevelOp(expr, "%");
        if (op >= 0) {
            String l = parseExpr(expr.substring(0, op).trim(), ExprType.NUMBER);
            String r = parseExpr(expr.substring(op + 1).trim(), ExprType.NUMBER);
            return exprRef(exprB2("%d % %d", "d", "%", l, r));
        }

        // ── String literals ────────────────────────────────────────────────────
        if (expr.startsWith("\"") && expr.endsWith("\"") && expr.length() >= 2) {
            // Strip quotes — Fx re-adds them for %s params
            return expr.substring(1, expr.length() - 1);
        }

        // ── Numeric literals ───────────────────────────────────────────────────
        if (expr.matches("-?\\d+(\\.\\d+)?[fFdDlL]?")) return expr;

        // ── Casts: (int)(x), (float)(x), (long)(x) ────────────────────────────
        Matcher castM = Pattern.compile("^\\((?:int|float|double|long)\\)\\(?(.+?)\\)?$").matcher(expr);
        if (castM.matches()) return parseExpr(castM.group(1).trim(), hint);

        // ── Method call expressions ────────────────────────────────────────────

        // x.length()
        Matcher mLen = Pattern.compile("^(.+)\\.length\\(\\)$").matcher(expr);
        if (mLen.matches())
            return exprRef(exprB1("%s.length()", "d", "stringLength",
                    parseExpr(mLen.group(1).trim(), ExprType.STRING)));

        // x.concat(y)
        Matcher mConcat = Pattern.compile("^(.+)\\.concat\\((.+)\\)$").matcher(expr);
        if (mConcat.matches())
            return exprRef(exprB2("%s.concat(%s)", "s", "stringJoin",
                    parseExpr(mConcat.group(1).trim(), ExprType.STRING),
                    parseExpr(mConcat.group(2).trim(), ExprType.STRING)));

        // x.equals(y)
        Matcher mEq = Pattern.compile("^(.+)\\.equals\\((.+)\\)$").matcher(expr);
        if (mEq.matches())
            return exprRef(exprB2("%s equals %s", "b", "stringEquals",
                    parseExpr(mEq.group(1).trim(), ExprType.STRING),
                    parseExpr(mEq.group(2).trim(), ExprType.STRING)));

        // x.contains(y)
        Matcher mCont = Pattern.compile("^(.+)\\.contains\\((.+)\\)$").matcher(expr);
        if (mCont.matches())
            return exprRef(exprB2("%s contains %s", "b", "stringContains",
                    parseExpr(mCont.group(1).trim(), ExprType.STRING),
                    parseExpr(mCont.group(2).trim(), ExprType.STRING)));

        // x.indexOf(y)
        Matcher mIdx = Pattern.compile("^(.+)\\.indexOf\\((.+)\\)$").matcher(expr);
        if (mIdx.matches())
            return exprRef(exprB2("%s indexOf %s", "d", "stringIndex",
                    parseExpr(mIdx.group(2).trim(), ExprType.STRING),
                    parseExpr(mIdx.group(1).trim(), ExprType.STRING)));

        // x.trim()
        Matcher mTrim = Pattern.compile("^(.+)\\.trim\\(\\)$").matcher(expr);
        if (mTrim.matches())
            return exprRef(exprB1("%s.trim()", "s", "trim",
                    parseExpr(mTrim.group(1).trim(), ExprType.STRING)));

        // x.toUpperCase()
        Matcher mUp = Pattern.compile("^(.+)\\.toUpperCase\\(\\)$").matcher(expr);
        if (mUp.matches())
            return exprRef(exprB1("%s.toUpperCase()", "s", "toUpperCase",
                    parseExpr(mUp.group(1).trim(), ExprType.STRING)));

        // x.toLowerCase()
        Matcher mLo = Pattern.compile("^(.+)\\.toLowerCase\\(\\)$").matcher(expr);
        if (mLo.matches())
            return exprRef(exprB1("%s.toLowerCase()", "s", "toLowerCase",
                    parseExpr(mLo.group(1).trim(), ExprType.STRING)));

        // x.getText().toString()
        Matcher mGetTxt = Pattern.compile("^([\\w.]+)\\.getText\\(\\)\\.toString\\(\\)$").matcher(expr);
        if (mGetTxt.matches())
            return exprRef(exprB1("%m.textview getText", "s", "getText",
                    viewName(mGetTxt.group(1))));

        // x.isEnabled()
        Matcher mIsEn = Pattern.compile("^([\\w.]+)\\.isEnabled\\(\\)$").matcher(expr);
        if (mIsEn.matches())
            return exprRef(exprB1("%m.view isEnable", "b", "getEnable", viewName(mIsEn.group(1))));

        // x.isChecked()
        Matcher mIsCk = Pattern.compile("^([\\w.]+)\\.isChecked\\(\\)$").matcher(expr);
        if (mIsCk.matches())
            return exprRef(exprB1("%m.checkBox isChecked", "b", "getChecked", viewName(mIsCk.group(1))));

        // x.getAlpha()
        Matcher mGA = Pattern.compile("^([\\w.]+)\\.getAlpha\\(\\)$").matcher(expr);
        if (mGA.matches())
            return exprRef(exprB1("%m.view getAlpha", "d", "getAlpha", viewName(mGA.group(1))));

        // x.size()
        Matcher mSz = Pattern.compile("^([\\w.]+)\\.size\\(\\)$").matcher(expr);
        if (mSz.matches())
            return exprRef(exprB1("%m.list.length()", "d", "lengthList", mSz.group(1)));

        // x.isEmpty()
        Matcher mIE = Pattern.compile("^([\\w.]+)\\.isEmpty\\(\\)$").matcher(expr);
        if (mIE.matches())
            return exprRef(exprB1("%m.map.isEmpty()", "b", "mapIsEmpty", mIE.group(1)));

        // Double.parseDouble(x)
        Matcher mPD = Pattern.compile("^Double\\.parseDouble\\((.+)\\)$").matcher(expr);
        if (mPD.matches())
            return exprRef(exprB1("%s to number", "d", "toNumber",
                    parseExpr(mPD.group(1).trim(), ExprType.STRING)));

        // String.valueOf((long)(x))
        Matcher mSVL = Pattern.compile("^String\\.valueOf\\(\\(long\\)\\((.+)\\)\\)$").matcher(expr);
        if (mSVL.matches())
            return exprRef(exprB1("%d to string", "s", "toString",
                    parseExpr(mSVL.group(1).trim(), ExprType.NUMBER)));

        // String.valueOf(x)
        Matcher mSV = Pattern.compile("^String\\.valueOf\\((.+)\\)$").matcher(expr);
        if (mSV.matches())
            return exprRef(exprB1("%d to string with decimal", "s", "toStringWithDecimal",
                    parseExpr(mSV.group(1).trim(), ExprType.NUMBER)));

        // System.currentTimeMillis()
        if ("System.currentTimeMillis()".equals(expr))
            return exprRef(exprB0("current time in ms", "d", "currentTime"));

        // Math.PI / Math.E
        if ("Math.PI".equals(expr)) return exprRef(exprB0("Math.PI", "d", "mathPi"));
        if ("Math.E".equals(expr))  return exprRef(exprB0("Math.E", "d", "mathE"));

        // Math.single-arg: abs, sqrt, round, ceil, floor, sin, cos, tan...
        Matcher mMath1 = Pattern.compile("^Math\\.(abs|sqrt|round|ceil|floor|sin|cos|tan|asin|acos|atan|exp|log|log10)\\((.+)\\)$").matcher(expr);
        if (mMath1.matches()) {
            String fn = mMath1.group(1);
            String opCode = "math" + Character.toUpperCase(fn.charAt(0)) + fn.substring(1);
            String param = parseExpr(mMath1.group(2).trim(), ExprType.NUMBER);
            return exprRef(exprB1("Math." + fn + "(%d)", "d", opCode, param));
        }

        // Math.two-arg: pow, min, max
        Matcher mMath2 = Pattern.compile("^Math\\.(pow|min|max)\\((.+?),\\s*(.+)\\)$").matcher(expr);
        if (mMath2.matches()) {
            String fn = mMath2.group(1);
            String opCode = "math" + Character.toUpperCase(fn.charAt(0)) + fn.substring(1);
            String l = parseExpr(mMath2.group(2).trim(), ExprType.NUMBER);
            String r = parseExpr(mMath2.group(3).trim(), ExprType.NUMBER);
            return exprRef(exprB2("Math." + fn + "(%d, %d)", "d", opCode, l, r));
        }

        // Math.toRadians / Math.toDegrees
        Matcher mMathRD = Pattern.compile("^Math\\.(toRadians|toDegrees)\\((.+)\\)$").matcher(expr);
        if (mMathRD.matches()) {
            String fn = mMathRD.group(1);
            String opCode = "mathTo" + (fn.equals("toRadians") ? "Radian" : "Degree");
            return exprRef(exprB1("Math." + fn + "(%d)", "d", opCode,
                    parseExpr(mMathRD.group(2).trim(), ExprType.NUMBER)));
        }

        // SketchwareUtil.getRandom(a, b)
        Matcher mRand = Pattern.compile("^SketchwareUtil\\.getRandom\\(\\(int\\)\\((.+?)\\),\\s*\\(int\\)\\((.+?)\\)\\)$").matcher(expr);
        if (!mRand.matches())
            mRand = Pattern.compile("^SketchwareUtil\\.getRandom\\((.+?),\\s*(.+)\\)$").matcher(expr);
        if (mRand.matches())
            return exprRef(exprB2("pick random %d to %d", "d", "random",
                    parseExpr(mRand.group(1).trim(), ExprType.NUMBER),
                    parseExpr(mRand.group(2).trim(), ExprType.NUMBER)));

        // SketchwareUtil.getDip(ctx, n)
        Matcher mDip = Pattern.compile("^SketchwareUtil\\.getDip\\([^,]+,\\s*\\(int\\)\\((.+?)\\)\\)$").matcher(expr);
        if (mDip.matches())
            return exprRef(exprB1("getDip %d", "d", "mathGetDip",
                    parseExpr(mDip.group(1).trim(), ExprType.NUMBER)));

        // SketchwareUtil.getDisplayWidthPixels / getDisplayHeightPixels
        if (expr.matches("SketchwareUtil\\.getDisplayWidthPixels\\(.*\\)"))
            return exprRef(exprB0("displayWidth", "d", "mathGetDisplayWidth"));
        if (expr.matches("SketchwareUtil\\.getDisplayHeightPixels\\(.*\\)"))
            return exprRef(exprB0("displayHeight", "d", "mathGetDisplayHeight"));

        // getString(R.string.name)
        Matcher mResStr = Pattern.compile("^getString\\(R\\.string\\.(\\w+)\\)$").matcher(expr);
        if (mResStr.matches()) {
            BlockBean b = new BlockBean(String.valueOf(newId()), mResStr.group(1), "s", "getResStr");
            exprBlocks.add(b);
            return "@" + b.id;
        }

        // getIntent().getStringExtra(key)
        Matcher mIntStr = Pattern.compile("^getIntent\\(\\)\\.getStringExtra\\((.+)\\)$").matcher(expr);
        if (mIntStr.matches())
            return exprRef(exprB1("getStringExtra %s", "s", "intentGetString",
                    parseExpr(mIntStr.group(1).trim(), ExprType.STRING)));

        // SharedPreferences: file.getString(key, default)  →  fileGetData
        Matcher mSpGet = Pattern.compile("^([\\w.]+)\\.getString\\((.+?),\\s*.+\\)$").matcher(expr);
        if (mSpGet.matches())
            return exprRef(exprB2("%m.file getData key %s", "s", "fileGetData",
                    mSpGet.group(1), parseExpr(mSpGet.group(2).trim(), ExprType.STRING)));

        // SharedPreferences: file.contains(key).booleanValue() OR file.contains(key)  →  fileContainsData
        Matcher mSpContains = Pattern.compile("^([\\w.]+)\\.contains\\((.+?)\\)(?:\\.booleanValue\\(\\))?$").matcher(expr);
        if (mSpContains.matches() && mSpContains.group(1).toLowerCase().contains("file"))
            return exprRef(exprB2("%m.file contains %s", "b", "fileContainsData",
                    mSpContains.group(1), parseExpr(mSpContains.group(2).trim(), ExprType.STRING)));

        // getIntent().getIntExtra(key, default)  /  .getBooleanExtra(...)
        Matcher mIntIntExtra = Pattern.compile("^getIntent\\(\\)\\.getIntExtra\\((.+?),\\s*.+\\)$").matcher(expr);
        if (mIntIntExtra.matches())
            return exprRef(exprB1("getIntExtra %s", "d", "intentGetInt",
                    parseExpr(mIntIntExtra.group(1).trim(), ExprType.STRING)));

        Matcher mIntBoolExtra = Pattern.compile("^getIntent\\(\\)\\.getBooleanExtra\\((.+?),\\s*.+\\)$").matcher(expr);
        if (mIntBoolExtra.matches())
            return exprRef(exprB1("getBooleanExtra %s", "b", "intentGetBoolean",
                    parseExpr(mIntBoolExtra.group(1).trim(), ExprType.STRING)));

        // ── Ternary: cond ? a : b  →  represented as nested if-like ASD (no native Sketchware ternary block) ──
        int qIdx = topLevelOp(expr, "?");
        if (qIdx >= 0) {
            int cIdx = topLevelOp(expr.substring(qIdx + 1), ":");
            if (cIdx >= 0) {
                // Can't be split natively — return as typed ASD expression
                String tType = hint == ExprType.STRING ? "s" : hint == ExprType.BOOLEAN ? "b" : "d";
                String tOpCode = hint == ExprType.STRING ? "asdString" : hint == ExprType.BOOLEAN ? "asdBoolean" : "asdNumber";
                return exprRef(exprB1("%s.inputOnly", tType, tOpCode, expr));
            }
        }

        // ── instanceof → boolean ASD (no native block) ─────────────────────────
        if (expr.contains(" instanceof "))
            return exprRef(exprB1("add source directly %s.inputOnly", "b", "asdBoolean", expr));

        // Integer.parseInt(x) / Integer.valueOf(x)
        Matcher mIntParse = Pattern.compile("^Integer\\.(?:parseInt|valueOf)\\((.+)\\)$").matcher(expr);
        if (mIntParse.matches())
            return exprRef(exprB1("%s to number", "d", "toNumber",
                    parseExpr(mIntParse.group(1).trim(), ExprType.STRING)));

        // Float.parseFloat(x) / Long.parseLong(x)
        Matcher mFloatParse = Pattern.compile("^(?:Float\\.parseFloat|Long\\.parseLong)\\((.+)\\)$").matcher(expr);
        if (mFloatParse.matches())
            return exprRef(exprB1("%s to number", "d", "toNumber",
                    parseExpr(mFloatParse.group(1).trim(), ExprType.STRING)));

        // x.get((int)(i))  — list index access (ground truth: getAtListInt, "get at %d of %m.listInt")
        Matcher mListGet = Pattern.compile("^([\\w.]+)\\.get\\(\\(int\\)\\((.+)\\)\\)$").matcher(expr);
        if (mListGet.matches())
            return exprRef(exprB2("get at %d of %m.listInt", "d", "getAtListInt",
                    parseExpr(mListGet.group(2).trim(), ExprType.NUMBER), mListGet.group(1)));

        // map.get(key) — HashMap value access (ground truth: mapGet, "%m.varMap get key %s")
        Matcher mMapGet = Pattern.compile("^([\\w.]+)\\.get\\((.+)\\)$").matcher(expr);
        if (mMapGet.matches())
            return exprRef(exprB2("%m.varMap get key %s", "s", "mapGet",
                    mMapGet.group(1), parseExpr(mMapGet.group(2).trim(), ExprType.STRING)));

        // map.containsKey(key) — ground truth: mapContainKey (no 's'), "%m.varMap contain key %s"
        Matcher mMapHas = Pattern.compile("^([\\w.]+)\\.containsKey\\((.+)\\)$").matcher(expr);
        if (mMapHas.matches())
            return exprRef(exprB2("%m.varMap contain key %s", "b", "mapContainKey",
                    mMapHas.group(1), parseExpr(mMapHas.group(2).trim(), ExprType.STRING)));

        // list.contains(value) — number vs string list distinguished by argument type
        // Ground truth: containListInt "%m.listInt contains %d" / containListStr "%m.listStr contains %s"
        Matcher mListHas = Pattern.compile("^([\\w.]+)\\.contains\\((.+)\\)$").matcher(expr);
        if (mListHas.matches()) {
            String arg = mListHas.group(2).trim();
            boolean isNum = arg.matches("-?\\d+(\\.\\d+)?") || arg.matches("\\(int\\)\\(.+\\)");
            if (isNum) {
                return exprRef(exprB2("%m.listInt contains %d", "b", "containListInt",
                        mListHas.group(1), parseExpr(arg, ExprType.NUMBER)));
            } else {
                return exprRef(exprB2("%m.listStr contains %s", "b", "containListStr",
                        mListHas.group(1), parseExpr(arg, ExprType.STRING)));
            }
        }

        // view.getWidth() / getHeight()
        Matcher mViewDim = Pattern.compile("^([\\w.]+)\\.get(Width|Height)\\(\\)$").matcher(expr);
        if (mViewDim.matches())
            return exprRef(exprB1("%m.view get" + mViewDim.group(2) + "()", "d",
                    "getView" + mViewDim.group(2), viewName(mViewDim.group(1))));

        // view.getX() / getY()
        Matcher mViewPos = Pattern.compile("^([\\w.]+)\\.get([XY])\\(\\)$").matcher(expr);
        if (mViewPos.matches())
            return exprRef(exprB1("%m.view get" + mViewPos.group(2) + "()", "d",
                    "getView" + mViewPos.group(2), viewName(mViewPos.group(1))));

        // Color.parseColor("#xxxxxx")
        Matcher mColorParse = Pattern.compile("^Color\\.parseColor\\(\"(#[0-9A-Fa-f]+)\"\\)$").matcher(expr);
        if (mColorParse.matches())
            return mColorParse.group(1); // colour literal — pass through directly

        // x.hashCode()
        Matcher mHash = Pattern.compile("^(.+)\\.hashCode\\(\\)$").matcher(expr);
        if (mHash.matches())
            return exprRef(exprB1("add source directly %s.inputOnly", "d", "asdNumber", expr));

        // ── Simple identifier → getVar ─────────────────────────────────────────
        if (expr.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            String bType = hint == ExprType.STRING  ? "s"
                         : hint == ExprType.BOOLEAN ? "b" : "d";
            BlockBean b = new BlockBean(String.valueOf(newId()), expr, bType, "getVar");
            exprBlocks.add(b);
            return "@" + b.id;
        }

        // ── Fallback: typed addSource expression block ─────────────────────────
        // Never return raw code as literal - always use a typed ASD expression block.
        // This prevents wrong behaviour where Fx wraps a code expression in quotes.
        switch (hint) {
            case BOOLEAN:
                return exprRef(exprB1("add source directly %s.inputOnly", "b", "asdBoolean", expr));
            case NUMBER:
                return exprRef(exprB1("add source directly %s.inputOnly", "d", "asdNumber", expr));
            case STRING:
                return exprRef(exprB1("add source directly %s.inputOnly", "s", "asdString", expr));
            default:
                // Unknown context: return as raw literal (safe for view names, etc.)
                return expr;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /** Create a statement block (type=" ") with given params. */
    private BlockBean stmt(int id, String spec, String opCode, String... params) {
        BlockBean b = new BlockBean(String.valueOf(id), spec, " ", opCode);
        for (String p : params) b.parameters.add(p);
        return b;
    }

    /** addSourceDirectly fallback. */
    private BlockBean asd(int id, String code) {
        BlockBean b = new BlockBean(String.valueOf(id), "add source directly %s.inputOnly", " ", "addSourceDirectly");
        b.parameters.add(code);
        return b;
    }

    /** Strip 'binding.' prefix from view names for cleaner block display. */
    private String viewName(String raw) {
        return raw.startsWith("binding.") ? raw.substring(8) : raw;
    }

    /** Create expr block with 0 params, add to exprBlocks, return @id. */
    private BlockBean exprB0(String spec, String type, String opCode) {
        BlockBean b = new BlockBean(String.valueOf(newId()), spec, type, opCode);
        exprBlocks.add(b);
        return b;
    }

    /** Create expr block with 1 param. */
    private BlockBean exprB1(String spec, String type, String opCode, String p1) {
        BlockBean b = new BlockBean(String.valueOf(newId()), spec, type, opCode);
        b.parameters.add(p1);
        exprBlocks.add(b);
        return b;
    }

    /** Create expr block with 2 params. */
    private BlockBean exprB2(String spec, String type, String opCode, String p1, String p2) {
        BlockBean b = new BlockBean(String.valueOf(newId()), spec, type, opCode);
        b.parameters.add(p1);
        b.parameters.add(p2);
        exprBlocks.add(b);
        return b;
    }

    /** Shorthand: add bool expr block (already adds to exprBlocks). */
    private BlockBean exprB(String spec, String type, String opCode) {
        return exprB0(spec, type, opCode);
    }

    /** Returns "@id" reference string for a bean (bean is already in exprBlocks). */
    private String exprRef(BlockBean b) { return "@" + b.id; }

    // ── Binary operator scanner ────────────────────────────────────────────────

    /**
     * Finds the LAST occurrence of {@code op} at brace depth 0 in {@code expr},
     * skipping string literals. Returns -1 if not found.
     * "Last" ensures left-associativity (a - b - c splits as (a-b) - c).
     */
    private int topLevelOp(String expr, String op) {
        int depth = 0;
        boolean inStr = false;
        int result = -1;
        for (int i = 0; i <= expr.length() - op.length(); i++) {
            char c = expr.charAt(i);
            if (inStr) {
                if (c == '"' && (i == 0 || expr.charAt(i - 1) != '\\')) inStr = false;
                continue;
            }
            if (c == '"') { inStr = true; continue; }
            if (c == '(' || c == '[') { depth++; continue; }
            if (c == ')' || c == ']') { depth--; continue; }
            if (depth == 0 && expr.startsWith(op, i)) {
                // Make sure it's not part of a larger operator (e.g., > vs >=)
                int after = i + op.length();
                boolean nextIsOp = (after < expr.length()) &&
                        "=><&|".indexOf(expr.charAt(after)) >= 0;
                if (!nextIsOp) result = i;
            }
        }
        return result;
    }

    /**
     * Returns the index of the matching closing ')' for the '(' at {@code openIdx},
     * or -1 on mismatch.
     */
    private int matchingClose(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            if (s.charAt(i) == '(') depth++;
            else if (s.charAt(i) == ')' && --depth == 0) return i;
        }
        return -1;
    }

    // ── Brace skip helpers ────────────────────────────────────────────────────
    private void skipOpen(List<String> lines, int[] pos) {
        if (pos[0] < lines.size() && P_OPEN.matcher(lines.get(pos[0])).matches()) pos[0]++;
    }
    private void skipClose(List<String> lines, int[] pos) {
        if (pos[0] < lines.size() && P_CLOSE.matcher(lines.get(pos[0])).matches()) pos[0]++;
    }
    
    // ── DFS flatten (parent before children) ──────────────────────────────────
    private void flatten(List<Node> nodes, List<BlockBean> result) {
        for (Node n : nodes) {
            result.add(n.bean);
            flatten(n.body1, result);
            flatten(n.body2, result);
        }
    }

    // ── Merge Consecutive ASD Blocks ──────────────────────────────────────────
    private void mergeASDBlocks(List<BlockBean> blocks) {
        for (int i = 0; i < blocks.size() - 1; i++) {
            BlockBean current = blocks.get(i);
            BlockBean next = blocks.get(i + 1);
            if ("addSourceDirectly".equals(current.opCode) && "addSourceDirectly".equals(next.opCode)) {
                String mergedCode = current.parameters.get(0) + "\n" + next.parameters.get(0);
                current.parameters.set(0, mergedCode);
                current.nextBlock = next.nextBlock;
                blocks.remove(i + 1);
                i--; // re-check
            }
        }
    }
    
    private void consumeBlock(List<String> lines, int[] pos) {
    int depth = 1;
    while (pos[0] < lines.size() && depth > 0) {
        String line = lines.get(pos[0]++);
        if (P_OPEN.matcher(line).matches()) {
            depth++;
        } else if (P_CLOSE.matcher(line).matches()) {
            depth--;
        }
    }
}

private String ensureSemicolon(String line) {
    line = line.trim();
    if (line.endsWith(";") || line.endsWith("}")) {
        return line;
    }
    return line + ";";
}
}
