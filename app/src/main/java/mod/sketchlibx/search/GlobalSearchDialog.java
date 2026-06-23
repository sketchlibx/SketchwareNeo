package mod.sketchlibx.search;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.design.DesignActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.databinding.DialogGlobalSearchBinding;
import pro.sketchware.databinding.ItemGlobalSearchResultBinding;

public class GlobalSearchDialog extends BottomSheetDialogFragment {
    
    private final String sc_id;
    private final DesignActivity activity;
    private DialogGlobalSearchBinding binding;
    private SearchAdapter adapter;
    private ProjectSearchEngine searchEngine;
    
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Runnable pendingSearchRunnable;
    
    private String activeFilter = "All";

    public GlobalSearchDialog(String sc_id, DesignActivity activity) {
        this.sc_id = sc_id;
        this.activity = activity;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        // FIXED THEME: Inheriting Native Sketchware Theme like GitClientBottomSheet
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            FrameLayout bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogGlobalSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        searchEngine = new ProjectSearchEngine(sc_id);
        setupViews();
        
        // FIXED KEYBOARD: Resizes layout properly when keyboard opens
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        binding.etSearch.requestFocus();
    }

    private void setupViews() {
        binding.rvResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SearchAdapter();
        binding.rvResults.setAdapter(adapter);

        binding.chipGroupFilter.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_all) activeFilter = "All";
            else if (checkedId == R.id.chip_views) activeFilter = "Views";
            else if (checkedId == R.id.chip_logic) activeFilter = "Logic";
            else if (checkedId == R.id.chip_components) activeFilter = "Components";
            
            performSearch(binding.etSearch.getText().toString());
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                performSearch(s.toString());
            }
        });
    }

    private void performSearch(String query) {
        if (pendingSearchRunnable != null) {
            uiHandler.removeCallbacks(pendingSearchRunnable);
        }

        // FIXED EMPTY STATE
        if (query.trim().isEmpty()) {
            binding.layoutIdle.setVisibility(View.VISIBLE);
            binding.layoutEmpty.setVisibility(View.GONE);
            binding.rvResults.setVisibility(View.GONE);
            binding.layoutLoading.setVisibility(View.GONE);
            adapter.updateList(new ArrayList<>());
            return;
        }

        pendingSearchRunnable = () -> {
            binding.layoutLoading.setVisibility(View.VISIBLE);
            binding.layoutIdle.setVisibility(View.GONE);
            binding.layoutEmpty.setVisibility(View.GONE);
            binding.rvResults.setVisibility(View.GONE);

            executorService.execute(() -> {
                List<SearchResult> results = searchEngine.search(query, activeFilter);
                
                uiHandler.post(() -> {
                    binding.layoutLoading.setVisibility(View.GONE);
                    if (results.isEmpty()) {
                        binding.layoutEmpty.setVisibility(View.VISIBLE);
                        binding.rvResults.setVisibility(View.GONE);
                    } else {
                        binding.layoutEmpty.setVisibility(View.GONE);
                        binding.rvResults.setVisibility(View.VISIBLE);
                        adapter.updateList(results);
                    }
                });
            });
        };
        
        uiHandler.postDelayed(pendingSearchRunnable, 250); 
    }

    private class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
        private List<SearchResult> items = new ArrayList<>();

        public void updateList(List<SearchResult> newItems) {
            SearchDiffCallback diffCallback = new SearchDiffCallback(this.items, newItems);
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
            this.items.clear();
            this.items.addAll(newItems);
            diffResult.dispatchUpdatesTo(this);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemGlobalSearchResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SearchResult result = items.get(position);
            holder.binding.tvTitle.setText(result.title);
            holder.binding.tvSubtitle.setText(result.description);
            holder.binding.chipCategory.setText(result.category);

            if (result.category.equals("View")) {
                holder.binding.imgIcon.setImageResource(R.drawable.ic_mtrl_screen);
            } else {
                holder.binding.imgIcon.setImageResource(R.drawable.ic_mtrl_code);
            }

            holder.binding.getRoot().setOnClickListener(v -> {
                dismiss();
                uiHandler.postDelayed(() -> activity.handleSearchResult(result), 350);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ItemGlobalSearchResultBinding binding;
            public ViewHolder(@NonNull ItemGlobalSearchResultBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private static class SearchDiffCallback extends DiffUtil.Callback {
        private final List<SearchResult> oldList;
        private final List<SearchResult> newList;
        
        public SearchDiffCallback(List<SearchResult> oldList, List<SearchResult> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }
        
        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }
        @Override public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            SearchResult oldItem = oldList.get(oldItemPosition);
            SearchResult newItem = newList.get(newItemPosition);
            return oldItem.title.equals(newItem.title) && oldItem.fileName.equals(newItem.fileName);
        }
        @Override public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            SearchResult oldItem = oldList.get(oldItemPosition);
            SearchResult newItem = newList.get(newItemPosition);
            return oldItem.description.equals(newItem.description);
        }
    }
}
