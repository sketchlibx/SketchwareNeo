package pro.sketchware.analysis.core;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.iC;
import a.a.a.jC;
import a.a.a.kC;

import pro.sketchware.utility.FilePathUtil;

public final class ProjectContext {

    private final String scId;
    private final FilePathUtil filePathUtil = new FilePathUtil();

    private ProjectContext(String scId) {
        this.scId = scId;
    }

    public static ProjectContext of(String scId) {
        return new ProjectContext(scId);
    }
    public String getScId() { return scId; }
    
    public eC getViewManager() { return jC.a(scId); }
    
    public hC getFileManager() { return jC.b(scId); }
    
    public iC getLibraryManager() { return jC.c(scId); }
    
    public kC getResourceManager() { return jC.d(scId); }
    public FilePathUtil paths() { return filePathUtil; }
}
