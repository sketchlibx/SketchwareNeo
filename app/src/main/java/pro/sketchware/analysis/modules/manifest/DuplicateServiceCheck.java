package pro.sketchware.analysis.modules.manifest;

import java.util.List;

import pro.sketchware.utility.FileResConfig;

public final class DuplicateServiceCheck extends AbstractDuplicateManifestListCheck {

    @Override public String id() { return "duplicate_services"; }
    @Override protected String entryKind() { return "service"; }
    @Override protected String issueId() { return "DUP_SERVICE"; }

    @Override
    protected List<String> readList(FileResConfig frc) {
        return frc.getServiceManifestList();
    }
}
