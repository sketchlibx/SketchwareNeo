package neo.sketchware.ai;

import android.content.Context;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;

public final class LayoutGenTask {

    private static final String SYSTEM_PROMPT =
            "You are generating an Android layout XML file for a Sketchware Neo project " +
            "(a visual Android app builder that stores layouts as standard Android XML - " +
            "LinearLayout, RelativeLayout, ConstraintLayout, TextView, Button, etc. with " +
            "standard android: attributes). " +
            "You will be given the CURRENT full XML content of the layout, followed by a " +
            "plain-language description of the change the user wants (this may be a small " +
            "tweak to the existing layout, or a request to build a layout from scratch if the " +
            "current XML is empty or minimal). " +
            "Reply with ONLY the complete new XML file content that should replace the current " +
            "one - no markdown code fences, no explanation, no comments outside the XML. " +
            "Preserve every existing view, id, and attribute that the user's request does not " +
            "ask you to change. Every view that should be referenced from code must have a " +
            "unique android:id. Produce well-formed, valid XML only.";

    private LayoutGenTask() {}

    public static void generate(Context context, String currentXml, String userPrompt, AiResponseCallback callback) {
        String userMessage = "Current XML:\n" + currentXml + "\n\nRequested change:\n" + userPrompt;

        AiManager.sendPrompt(context, SYSTEM_PROMPT, userMessage, AiManager.Feature.LAYOUTS, new AiResponseCallback() {
            @Override
            public void onSuccess(String responseText) {
                String xml = stripCodeFences(responseText);
                String issue = validateXml(xml);
                if (issue != null) {
                    callback.onFailure("Generated layout was not applied because its XML is invalid: " + issue + " Please retry the request.");
                    return;
                }
                callback.onSuccess(xml);
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    private static String validateXml(String xml) {
        if (xml == null || xml.trim().isEmpty()) return "the response is empty";
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xml));
            String root = null;
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && root == null) root = parser.getName();
            }
            if (root == null) return "no root element was found";
            return null;
        } catch (Exception e) {
            return e.getMessage() == null ? "XML parsing failed" : e.getMessage();
        }
    }

    private static String stripCodeFences(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) trimmed = trimmed.substring(firstNewline + 1);
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }
}
