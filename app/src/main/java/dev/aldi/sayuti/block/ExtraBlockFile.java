package dev.aldi.sayuti.block;

import android.os.Environment;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import mod.hey.studios.util.Helper;
import mod.hilal.saif.blocks.BlocksHandler;
import pro.sketchware.utility.FileUtil;

public class ExtraBlockFile {

    public static final File EXTRA_BLOCKS_DATA_FILE = new File(Environment.getExternalStorageDirectory(),
            ".sketchware/resources/block/My Block/block.json");
    public static final File EXTRA_BLOCKS_PALETTE_FILE = new File(Environment.getExternalStorageDirectory(),
            ".sketchware/resources/block/My Block/palette.json");

    public static final File PLUGIN_BLOCKS_DIR = new File(Environment.getExternalStorageDirectory(),
            ".sketchware/resources/block/plugins/");

    public static ArrayList<HashMap<String, Object>> buildInBlocks = new ArrayList<>();

    public static ArrayList<HashMap<String, Object>> getExtraBlockData() {
        ArrayList<HashMap<String, Object>> extraBlocks = new Gson().fromJson(getExtraBlockFile(), Helper.TYPE_MAP_LIST);

        buildInBlocks.clear();
        BlocksHandler.builtInBlocks(buildInBlocks);
        extraBlocks.addAll(buildInBlocks);

        extraBlocks.addAll(getPluginBlockData());

        return extraBlocks;
    }

    private static ArrayList<HashMap<String, Object>> getPluginBlockData() {
        ArrayList<HashMap<String, Object>> pluginBlocks = new ArrayList<>();

        File[] pluginDirs = PLUGIN_BLOCKS_DIR.listFiles();
        if (pluginDirs == null) return pluginBlocks;

        for (File pluginDir : pluginDirs) {
            File blockFile = new File(pluginDir, "block.json");
            if (!blockFile.exists()) continue;

            try {
                String content = FileUtil.readFile(blockFile.getAbsolutePath());
                if (content.isEmpty()) continue;

                ArrayList<HashMap<String, Object>> parsed = new Gson().fromJson(content, Helper.TYPE_MAP_LIST);
                if (parsed != null) pluginBlocks.addAll(parsed);
            } catch (Exception ignored) {
            }
        }

        return pluginBlocks;
    }

    
    public static String getExtraBlockFile() {
        String fileContent;

        if (EXTRA_BLOCKS_DATA_FILE.exists() && !(fileContent = FileUtil.readFile(EXTRA_BLOCKS_DATA_FILE.getAbsolutePath())).isEmpty()) {
            return fileContent;
        } else {
            return "[]";
        }
    }

    public static String getPaletteBlockFile() {
        String ownPalette = FileUtil.readFile(EXTRA_BLOCKS_PALETTE_FILE.getAbsolutePath());

        org.json.JSONArray combined = new org.json.JSONArray();

        if (!ownPalette.isEmpty()) {
            try {
                org.json.JSONArray ownArray = new org.json.JSONArray(ownPalette);
                for (int i = 0; i < ownArray.length(); i++) combined.put(ownArray.get(i));
            } catch (Exception ignored) {
            }
        }

        File[] pluginDirs = PLUGIN_BLOCKS_DIR.listFiles();
        if (pluginDirs != null) {
            for (File pluginDir : pluginDirs) {
                File paletteFile = new File(pluginDir, "palette.json");
                if (!paletteFile.exists()) continue;

                try {
                    String content = FileUtil.readFile(paletteFile.getAbsolutePath());
                    if (content.isEmpty()) continue;

                    org.json.JSONArray pluginArray = new org.json.JSONArray(content);
                    for (int i = 0; i < pluginArray.length(); i++) combined.put(pluginArray.get(i));
                } catch (Exception ignored) {
                }
            }
        }

        return combined.length() == 0 ? "" : combined.toString();
    }

    public static String getExtraBlockJson() {
        return "[]";
    }
}
