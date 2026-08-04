package neo.sketchware.plugin;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NeoBuildErrorInfo {

    private static final Pattern FILE_LINE_PATTERN = Pattern.compile("([\\w./\\\\-]+\\.(?:java|kt|xml)):(\\d+)");

    private final String filePath;
    private final Integer lineNumber;
    private final String errorType;
    private final String rawMessage;

    private NeoBuildErrorInfo(String filePath, Integer lineNumber, String errorType, String rawMessage) {
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.errorType = errorType;
        this.rawMessage = rawMessage;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public String getErrorType() {
        return errorType;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public static NeoBuildErrorInfo parse(String rawMessage) {
        if (rawMessage == null) rawMessage = "";

        String filePath = null;
        Integer lineNumber = null;

        Matcher matcher = FILE_LINE_PATTERN.matcher(rawMessage);
        if (matcher.find()) {
            filePath = matcher.group(1);
            try {
                lineNumber = Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException ignored) {
            }
        }

        String errorType;
        if (rawMessage.contains("OutOfMemoryError")) {
            errorType = "OutOfMemoryError";
        } else if (rawMessage.contains("cannot find symbol")) {
            errorType = "CannotFindSymbol";
        } else if (rawMessage.toLowerCase().contains("syntax")) {
            errorType = "SyntaxError";
        } else {
            errorType = "Unknown";
        }

        return new NeoBuildErrorInfo(filePath, lineNumber, errorType, rawMessage);
    }
}
