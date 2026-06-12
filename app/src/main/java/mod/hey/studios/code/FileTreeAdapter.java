package mod.hey.studios.code;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;

public class FileTreeAdapter extends RecyclerView.Adapter<FileTreeAdapter.FileViewHolder> {

    private final List<FileNode> nodes = new ArrayList<>();
    private final OnFileClickListener listener;

    public interface OnFileClickListener {
        void onFileClick(File file);
    }

    public static class FileNode {
        public File file;
        public int depth;
        public boolean isExpanded;
        public boolean isDirectory;

        public FileNode(File file, int depth) {
            this.file = file;
            this.depth = depth;
            this.isDirectory = file.isDirectory();
            this.isExpanded = false;
        }
    }

    public FileTreeAdapter(File rootDir, OnFileClickListener listener) {
        this.listener = listener;
        if (rootDir != null && rootDir.exists()) {
            loadDirectory(rootDir, 0);
        }
    }

    private void loadDirectory(File dir, int depth) {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        List<File> directories = new ArrayList<>();
        List<File> plainFiles = new ArrayList<>();
        
        for (File f : files) {
            if (f.isDirectory()) directories.add(f);
            else plainFiles.add(f);
        }
        
        directories.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        plainFiles.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        
        for (File d : directories) nodes.add(new FileNode(d, depth));
        for (File f : plainFiles) nodes.add(new FileNode(f, depth));
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_resource_tree, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileNode node = nodes.get(position);
        holder.fileName.setText(node.file.getName());
        
        // Calculate tree indentation dynamically
        int indentPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, node.depth * 20, holder.itemView.getContext().getResources().getDisplayMetrics());
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.chevronIcon.getLayoutParams();
        params.setMarginStart(indentPx);
        holder.chevronIcon.setLayoutParams(params);

        if (node.isDirectory) {
            holder.chevronIcon.setVisibility(View.VISIBLE);
            holder.chevronIcon.setRotation(node.isExpanded ? 90 : 0);
            holder.fileIcon.setImageResource(R.drawable.ic_mtrl_folder);
            holder.moreButton.setVisibility(View.GONE);
            
            holder.itemView.setOnClickListener(v -> toggleDirectory(position));
        } else {
            holder.chevronIcon.setVisibility(View.INVISIBLE);
            String name = node.file.getName().toLowerCase();
            if (name.endsWith(".java") || name.endsWith(".kt")) holder.fileIcon.setImageResource(R.drawable.ic_class_item);
            else if (name.endsWith(".xml")) holder.fileIcon.setImageResource(R.drawable.ic_code_white_48dp);
            else holder.fileIcon.setImageResource(R.drawable.ic_insert_drive_file_white_48dp);
            
            holder.moreButton.setVisibility(View.VISIBLE);
            holder.itemView.setOnClickListener(v -> listener.onFileClick(node.file));
        }
    }

    private void toggleDirectory(int position) {
        FileNode node = nodes.get(position);
        if (!node.isDirectory) return;

        if (node.isExpanded) {
            int count = 0;
            for (int i = position + 1; i < nodes.size(); i++) {
                if (nodes.get(i).depth > node.depth) count++;
                else break;
            }
            nodes.subList(position + 1, position + 1 + count).clear();
            node.isExpanded = false;
            notifyItemRangeRemoved(position + 1, count);
            notifyItemChanged(position);
        } else {
            File[] children = node.file.listFiles();
            if (children != null && children.length > 0) {
                List<FileNode> newNodes = new ArrayList<>();
                List<File> directories = new ArrayList<>();
                List<File> plainFiles = new ArrayList<>();
                for (File f : children) {
                    if (f.isDirectory()) directories.add(f);
                    else plainFiles.add(f);
                }
                directories.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
                plainFiles.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
                for (File d : directories) newNodes.add(new FileNode(d, node.depth + 1));
                for (File f : plainFiles) newNodes.add(new FileNode(f, node.depth + 1));
                
                nodes.addAll(position + 1, newNodes);
                node.isExpanded = true;
                notifyItemRangeInserted(position + 1, newNodes.size());
                notifyItemChanged(position);
            }
        }
    }

    @Override
    public int getItemCount() { return nodes.size(); }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        ImageView chevronIcon, fileIcon;
        TextView fileName;
        ImageButton moreButton;

        FileViewHolder(View view) {
            super(view);
            chevronIcon = view.findViewById(R.id.chevronIcon);
            fileIcon = view.findViewById(R.id.fileIcon);
            fileName = view.findViewById(R.id.fileName);
            moreButton = view.findViewById(R.id.moreButton);
        }
    }
}
