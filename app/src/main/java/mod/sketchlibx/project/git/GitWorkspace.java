package mod.sketchlibx.project.git;

import org.eclipse.jgit.api.Git;

import java.io.File;

import pro.sketchware.utility.FileUtil;

/** Manages Git workspace paths and creation for each project. */
public final class GitWorkspace {

    private GitWorkspace() {
    }

    public static File directoryFor(File storageRoot, String scId) {
        return new File(new File(new File(new File(storageRoot, ".sketchware"), "data"), scId), "git_workspace");
    }

    public static void initialize(File workspace) throws Exception {
        workspace.mkdirs();
        Git.init().setDirectory(workspace).setInitialBranch("main").call().close();

        File gitignore = new File(workspace, ".gitignore");
        FileUtil.writeFile(gitignore.getAbsolutePath(), "bin/\nbuild/\n*.apk\n*.dex\n.gradle/\nlocal.properties\n");
    }
}
