package mod.agus.jcoderz.editor.manage.resource;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.util.List;

import mod.bobur.VectorDrawableLoader;
import neo.sketchware.R;
import neo.sketchware.databinding.ItemResourceTreeBinding;
import neo.sketchware.utility.FileUtil;
import neo.sketchware.utility.ThemeUtils;

public class ResourceTreeAdapter extends ListAdapter<FileNode, ResourceTreeAdapter.TreeViewHolder> {

    public static final Object PAYLOAD_EXPAND = new Object();

    private static final DiffUtil.ItemCallback<FileNode> DIFF =
            new DiffUtil.ItemCallback<FileNode>() {

                @Override
                public boolean areItemsTheSame(
                        @NonNull FileNode oldItem,
                        @NonNull FileNode newItem) {

                    return oldItem.path.equals(newItem.path);
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull FileNode oldItem,
                        @NonNull FileNode newItem) {

                    return oldItem.isExpanded == newItem.isExpanded
                            && oldItem.depth == newItem.depth
                            && oldItem.isLastChild == newItem.isLastChild
                            && oldItem.name.equals(newItem.name);
                }

                @Nullable
                @Override
                public Object getChangePayload(
                        @NonNull FileNode oldItem,
                        @NonNull FileNode newItem) {

                    if (oldItem.isExpanded != newItem.isExpanded
                            && oldItem.depth == newItem.depth
                            && oldItem.name.equals(newItem.name)) {

                        return newItem.isExpanded;
                    }

                    return null;
                }
            };

    private final boolean isTreeView;

    private Listener listener;

    private int cornerRadiusPx;

    public ResourceTreeAdapter(boolean isTreeView) {

        super(DIFF);

        this.isTreeView = isTreeView;
    }

    public interface Listener {

        void onItemClick(FileNode node, int position);

        void onItemLongClick(
                View anchor,
                FileNode node,
                int position
        );

        void onMoreClick(
                View anchor,
                FileNode node,
                int position
        );
    }

    public void setListener(Listener listener) {

        this.listener = listener;
    }

    @NonNull
    @Override
    public TreeViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        ItemResourceTreeBinding binding =
                ItemResourceTreeBinding.inflate(
                        LayoutInflater.from(parent.getContext()),
                        parent,
                        false
                );

        cornerRadiusPx =
                (int) (
                        4 * parent.getContext()
                                .getResources()
                                .getDisplayMetrics()
                                .density
                );

        return new TreeViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(
            @NonNull TreeViewHolder holder,
            int position) {

        FileNode node = getItem(position);

        bindTreeLines(holder, node);

        if (node.isFolder) {

            bindFolder(holder, node);

        } else {

            bindFile(holder, node);
        }

        bindCommon(holder, node);
    }

    @Override
    public void onBindViewHolder(
            @NonNull TreeViewHolder holder,
            int position,
            @NonNull List<Object> payloads) {

        if (!payloads.isEmpty()
                && payloads.get(0) instanceof Boolean) {

            animateChevron(
                    holder,
                    (Boolean) payloads.get(0)
            );

            return;
        }

        onBindViewHolder(holder, position);
    }

    @Override
    public void onViewRecycled(
            @NonNull TreeViewHolder holder) {

        Glide.with(holder.b.fileIcon.getContext())
                .clear(holder.b.fileIcon);

        holder.b.chevronIcon.setRotation(0f);

        super.onViewRecycled(holder);
    }

    private void bindTreeLines(
            @NonNull TreeViewHolder holder,
            FileNode node) {

        holder.b.treeLinesView.setTreeState(
                node.depth,
                new boolean[]{node.ancestorHasLines},
                node.isLastChild
        );
    }

    private void bindFolder(
            @NonNull TreeViewHolder holder,
            FileNode node) {

        Context context =
                holder.b.fileIcon.getContext();

        holder.b.fileIcon.clearColorFilter();

        holder.b.fileIcon.setImageResource(
                R.drawable.ic_mtrl_folder
        );

        holder.b.fileIcon.setColorFilter(
                ThemeUtils.getColor(
                        context,
                        R.attr.colorPrimary
                )
        );

        holder.b.fileName.setTypeface(
                null,
                Typeface.BOLD
        );

        holder.b.fileName.setText(node.name);

        holder.b.chevronIcon.setVisibility(
                View.VISIBLE
        );

        holder.b.chevronIcon.setRotation(
                node.isExpanded ? 90f : 0f
        );

        holder.b.moreButton.setVisibility(
                View.GONE
        );
    }

    private void bindFile(
            @NonNull TreeViewHolder holder,
            FileNode node) {

        Context context =
                holder.b.fileIcon.getContext();

        holder.b.fileName.setTypeface(
                null,
                Typeface.NORMAL
        );

        holder.b.fileName.setText(node.name);

        holder.b.chevronIcon.setVisibility(
                View.INVISIBLE
        );

        holder.b.chevronIcon.setRotation(0f);

        holder.b.moreButton.setVisibility(
                !isTreeView
                        ? View.VISIBLE
                        : View.GONE
        );

        holder.b.fileIcon.clearColorFilter();

        if (FileUtil.isImageFile(node.path)) {

            loadImagePreview(
                    holder,
                    node.path,
                    context
            );

        } else if (
                node.path.endsWith(".xml")
                        && "drawable".equals(
                        ManageResourceActivity.getLastDirectory(
                                node.path
                        )
                )
        ) {

            loadVectorPreview(
                    holder,
                    node.path,
                    context
            );

        } else {

            holder.b.fileIcon.setImageResource(
                    R.drawable.ic_mtrl_file
            );

            holder.b.fileIcon.setColorFilter(
                    ThemeUtils.getColor(
                            context,
                            R.attr.colorOnSurfaceVariant
                    )
            );
        }
    }

    private void bindCommon(
            @NonNull TreeViewHolder holder,
            FileNode node) {

        holder.b.itemRoot.setOnClickListener(v -> {

            if (listener != null) {

                listener.onItemClick(
                        node,
                        holder.getBindingAdapterPosition()
                );
            }
        });

        holder.b.itemRoot.setOnLongClickListener(v -> {

            if (listener != null) {

                listener.onItemLongClick(
                        v,
                        node,
                        holder.getBindingAdapterPosition()
                );
            }

            return true;
        });

        holder.b.moreButton.setOnClickListener(v -> {

            if (listener != null) {

                listener.onMoreClick(
                        v,
                        node,
                        holder.getBindingAdapterPosition()
                );
            }
        });
    }

    private void loadImagePreview(
            @NonNull TreeViewHolder holder,
            String path,
            Context context) {

        Glide.with(context)
                .load(new File(path))
                .apply(
                        new RequestOptions()
                                .override(80, 80)
                                .centerCrop()
                                .transform(
                                        new RoundedCorners(
                                                cornerRadiusPx
                                        )
                                )
                                .error(
                                        R.drawable.ic_mtrl_file
                                )
                )
                .into(holder.b.fileIcon);
    }

    private void loadVectorPreview(
            @NonNull TreeViewHolder holder,
            String path,
            Context context) {

        try {

            new VectorDrawableLoader()
                    .setImageVectorFromFile(
                            holder.b.fileIcon,
                            path
                    );

            holder.b.fileIcon.clearColorFilter();

        } catch (Exception e) {

            holder.b.fileIcon.setImageResource(
                    R.drawable.ic_mtrl_file
            );

            holder.b.fileIcon.setColorFilter(
                    ThemeUtils.getColor(
                            context,
                            R.attr.colorOnSurfaceVariant
                    )
            );
        }
    }

    private void animateChevron(
            @NonNull TreeViewHolder holder,
            boolean expand) {

        float from =
                holder.b.chevronIcon.getRotation();

        float to =
                expand ? 90f : 0f;

        if (Math.abs(from - to) < 1f) {
            return;
        }

        ValueAnimator animator =
                ValueAnimator.ofFloat(from, to);

        animator.setDuration(200);

        animator.addUpdateListener(animation ->

                holder.b.chevronIcon.setRotation(
                        (Float) animation.getAnimatedValue()
                )
        );

        animator.start();
    }

    static class TreeViewHolder
            extends RecyclerView.ViewHolder {

        ItemResourceTreeBinding b;

        public TreeViewHolder(
                @NonNull ItemResourceTreeBinding binding) {

            super(binding.getRoot());

            b = binding;
        }
    }
}