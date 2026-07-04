package pro.sketchware.analysis.core;

import pro.sketchware.analysis.model.AnalysisReport;

public interface AnalysisCheck {

    
    String id();

    
    String category();

    
    void run(ProjectContext ctx, SourceIndex index, AnalysisReport.Builder report);
}
