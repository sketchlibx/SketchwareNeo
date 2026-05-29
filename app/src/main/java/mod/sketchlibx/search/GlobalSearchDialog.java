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

    // Debouncing tools to prevent lag during fast typing
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public GlobalSearchDialog(String sc_id, DesignActivity activity) {
        this.sc_id = sc_id;
        this.activity = activity;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_MaterialComponents_BottomSheetDialog);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
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
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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

        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SearchAdapter(new ArrayList<>());
        binding.rvSearchResults.setAdapter(adapter);

        searchEngine = new ProjectSearchEngine(sc_id);

        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                
                // Cancel pending search
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                if (query.isEmpty()) {
                    showState(State.INFO);
                    adapter.updateData(new ArrayList<>());
                    return;
                }
                
                // Debounce search by 300ms for performance
                searchRunnable = () -> performSearch(query);
                searchHandler.postDelayed(searchRunnable, 300);
            }
        });

        // Request focus automatically
        binding.editSearch.requestFocus();
    }

    private void performSearch(String query) {
        showState(State.LOADING);
        executorService.execute(() -> {
            List<SearchResult> results = searchEngine.search(query);
            
            // Check if fragment is still attached before updating UI
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    adapter.updateData(results);
                    if (results.isEmpty()) {
                        showState(State.EMPTY);
                    } else {
                        showState(State.RESULTS);
                    }
                });
            }
        });
    }

    private enum State { INFO, LOADING, EMPTY, RESULTS }

    private void showState(State state) {
        binding.layoutInfo.setVisibility(state == State.INFO ? View.VISIBLE : View.GONE);
        binding.layoutEmpty.setVisibility(state == State.EMPTY ? View.VISIBLE : View.GONE);
        binding.rvSearchResults.setVisibility(state == State.RESULTS ? View.VISIBLE : View.GONE);
        // Add a loading spinner layout if you want to implement State.LOADING visually
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
        executorService.shutdown();
        binding = null; // Prevent memory leak
    }

    private class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
        private List<SearchResult> items;

        public SearchAdapter(List<SearchResult> items) {
            this.items = items;
        }

        public void updateData(List<SearchResult> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemGlobalSearchResultBinding itemBinding = ItemGlobalSearchResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SearchResult result = items.get(position);
            
            holder.binding.tvTitle.setText(String.format("[%s] %s", result.category, result.title));
            holder.binding.tvSubtitle.setText(String.format("%s • %s", result.fileName, result.description));

            int iconRes = R.drawable.ic_mtrl_file;
            switch (result.category) {
                case "View": iconRes = R.drawable.ic_mtrl_screen; break;
                case "Logic Block": iconRes = R.drawable.ic_mtrl_puzzle; break;
                case "Variable":
                case "List": iconRes = R.drawable.ic_mtrl_list; break;
                case "Component": iconRes = R.drawable.ic_mtrl_component; break;
            }
            holder.binding.imgIcon.setImageResource(iconRes);

            holder.binding.getRoot().setOnClickListener(v -> {
                dismiss();
                // Safe callback to avoid crashing if Activity is destroying
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (activity != null && !activity.isFinishing()) {
                        activity.handleSearchResult(result);
                    }
                }, 300);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ItemGlobalSearchResultBinding binding;
            public ViewHolder(@NonNull ItemGlobalSearchResultBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
