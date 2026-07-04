package pro.sketchware.analysis.modules.manifest;

import java.util.List;

import pro.sketchware.utility.FileResConfig;

public final class DuplicateReceiverCheck extends AbstractDuplicateManifestListCheck {

    @Override public String id() { return "duplicate_receivers"; }
    @Override protected String entryKind() { return "receiver"; }
    @Override protected String issueId() { return "DUP_RECEIVER"; }

    @Override
    protected List<String> readList(FileResConfig frc) {
        return frc.getBroadcastManifestList();
    }
}
