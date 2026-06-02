package mod.agus.jcoderz.editor.manage.resource;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import neo.sketchware.R;
import neo.sketchware.databinding.BottomSheetResourceContextBinding;
import neo.sketchware.utility.ThemeUtils;

/**
 * ResourceContextBottomSheet
 *
 * A Material3 {@link BottomSheetDialogFragment} that replaces the old
 * {@link android.widget.PopupMenu} for file/folder context actions.
 *
 * Usage:
 * <pre>
 *   ResourceContextBottomSheet.show(
 *       getSupportFragmentManager(),
 *       node,
 *       new ResourceContextBottomSheet.Listener() { ... });
 * </pre>
 *
 * Actions displayed per node type:
 *   Folder → Create file inside, Create folder inside, Import here, Rename, Delete
 *   File   → Edit, Edit with…, Rename, Delete
 */
public class ResourceContextBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "ResourceContextSheet";
    private static final String KEY_PATH   = "path";
    private static final String KEY_NAME   = "name";
    private static final String KEY_FOLDER = "isFolder";

    // ── Listener interface ─────────────────────────────────────────────────────

    public interface Listener {
        default void onEdit(String path) {}
        default void onEditWith(String path) {}
        default void onCreateFileInside(String path) {}
        default void onCreateFolderInside(String path) {}
        default void onImportHere(String path) {}
        default void onRename(String path) {}
        default void onDelete(String path) {}
    }

    // ── Factory ────────────────────────────────────────────────────────────────

    /**
     * Creates and immediately shows the sheet.
     *
     * @param fm       {@code getSupportFragmentManager()} from the host activity
     * @param node     the tapped {@link ManageResourceActivity.FileNode}
     * @param listener action callbacks (all have empty default implementations)
     */
    public static void show(
            @NonNull androidx.fragment.app.FragmentManager fm,
            @NonNull ManageResourceActivity.FileNode node,
            @NonNull Listener listener) {

        ResourceContextBottomSheet sheet = new ResourceContextBottomSheet();

        Bundle args = new Bundle();
        args.putString(KEY_PATH, node.path);
        args.putString(KEY_NAME, node.name);
        args.putBoolean(KEY_FOLDER, node.isFolder);
        sheet.setArguments(args);
        sheet.listener = listener;

        sheet.show(fm, TAG);
    }

    // ── State ──────────────────────────────────────────────────────────────────

    private Listener listener;
    private BottomSheetResourceContextBinding b;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                              @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        b = BottomSheetResourceContextBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() == null || listener == null) {
            dismissAllowingStateLoss();
            return;
        }

        String path     = getArguments().getString(KEY_PATH, "");
        String name     = getArguments().getString(KEY_NAME, "");
        boolean isFolder = getArguments().getBoolean(KEY_FOLDER, false);

        setupHeader(name, path, isFolder);
        setupActions(path, isFolder);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    // ── Private setup ──────────────────────────────────────────────────────────

    private void setupHeader(String name, String path, boolean isFolder) {
        b.bsFileName.setText(name);

        // Show the parent directory as a subtle path hint
        int lastSlash = path.lastIndexOf('/');
        String parentPath = lastSlash > 0 ? path.substring(0, lastSlash) : path;
        b.bsFilePath.setText(parentPath);

        // Icon
        b.bsFileIcon.setImageResource(
                isFolder ? R.drawable.ic_mtrl_folder : R.drawable.ic_mtrl_file);
        b.bsFileIcon.setColorFilter(
                ThemeUtils.getColor(requireContext(),
                        isFolder ? R.attr.colorPrimary : R.attr.colorOnSurfaceVariant));
    }

    private void setupActions(String path, boolean isFolder) {
        if (isFolder) {
            // Folder actions
            b.bsActionEdit.setVisibility(View.GONE);
            b.bsActionEditWith.setVisibility(View.GONE);

            b.bsActionCreateFile.setVisibility(View.VISIBLE);
            b.bsActionCreateFolder.setVisibility(View.VISIBLE);
            b.bsActionImport.setVisibility(View.VISIBLE);

            b.bsActionCreateFile.setOnClickListener(v -> {
                dismiss();
                listener.onCreateFileInside(path);
            });
            b.bsActionCreateFolder.setOnClickListener(v -> {
                dismiss();
                listener.onCreateFolderInside(path);
            });
            b.bsActionImport.setOnClickListener(v -> {
                dismiss();
                listener.onImportHere(path);
            });
        } else {
            // File actions
            b.bsActionEdit.setVisibility(View.VISIBLE);
            b.bsActionEditWith.setVisibility(View.VISIBLE);

            b.bsActionCreateFile.setVisibility(View.GONE);
            b.bsActionCreateFolder.setVisibility(View.GONE);
            b.bsActionImport.setVisibility(View.GONE);

            b.bsActionEdit.setOnClickListener(v -> {
                dismiss();
                listener.onEdit(path);
            });
            b.bsActionEditWith.setOnClickListener(v -> {
                dismiss();
                listener.onEditWith(path);
            });
        }

        // Common actions (rename + delete) always visible
        b.bsActionRename.setOnClickListener(v -> {
            dismiss();
            listener.onRename(path);
        });
        b.bsActionDelete.setOnClickListener(v -> {
            dismiss();
            listener.onDelete(path);
        });
    }
}