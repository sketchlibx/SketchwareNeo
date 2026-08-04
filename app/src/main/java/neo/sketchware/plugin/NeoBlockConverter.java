package neo.sketchware.plugin;

import mod.sketchlibx.project.editor.BlocksConverter;

public interface NeoBlockConverter {

    String getConverterName();

    BlocksConverter.ConversionResult convertJavaToBlocks(String javaCode);
}
