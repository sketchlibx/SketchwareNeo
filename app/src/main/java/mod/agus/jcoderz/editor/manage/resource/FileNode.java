package mod.agus.jcoderz.editor.manage.resource;

import android.net.Uri;

import java.util.ArrayList;

import neo.sketchware.utility.FileUtil;

public class FileNode {

    public String path;
    public String name;

    public boolean isFolder;

    // Tree states
    public boolean isExpanded;
    public boolean isLastChild;
    public boolean ancestorHasLines;

    // Depth level
    public int depth;

    // Child nodes
    public ArrayList<FileNode> children;

    public FileNode(String p, int d) {

        path = p;

        try {
            name = Uri.parse(p).getLastPathSegment();
        } catch (Exception e) {
            name = p;
        }

        isFolder = FileUtil.isDirectory(p);

        depth = d;

        // Default states
        isExpanded = false;
        isLastChild = false;
        ancestorHasLines = false;

        children = new ArrayList<>();
    }
}