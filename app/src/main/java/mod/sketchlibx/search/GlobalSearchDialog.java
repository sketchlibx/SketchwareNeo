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
	
	// High performance UI tools
	private final Handler uiHandler = new Handler(Looper.getMainLooper());
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
		adapter = new SearchAdapter();
		binding.rvSearchResults.setAdapter(adapter);
		
		searchEngine = new ProjectSearchEngine(sc_id);
		
		binding.btnClearSearch.setOnClickListener(v -> binding.editSearch.setText(""));
		
		binding.editSearch.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
			
			@Override
			public void afterTextChanged(Editable s) {
				String query = s.toString().trim();
				
				binding.btnClearSearch.setVisibility(query.length() > 0 ? View.VISIBLE : View.GONE);
				
				if (searchRunnable != null) {
					uiHandler.removeCallbacks(searchRunnable);
				}
				
				if (query.isEmpty()) {
					showState(State.INFO);
					adapter.applyDiff(new ArrayList<>(), null); // Clear immediately
					return;
				}
				
				// Debouncing logic (250ms) to ensure smooth typing
				searchRunnable = () -> performSearch(query);
				uiHandler.postDelayed(searchRunnable, 250);
			}
		});
		
		binding.editSearch.requestFocus();
	}
	
	private void performSearch(String query) {
		showState(State.LOADING);
		executorService.execute(() -> {
			// Background thread search
			List<SearchResult> results = searchEngine.search(query);
			
			// Background DiffUtil Calculation for lag-free list updates
			DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new SearchDiffCallback(adapter.getItems(), results));
			
			if (isAdded() && getActivity() != null) {
				getActivity().runOnUiThread(() -> {
					adapter.applyDiff(results, diffResult);
					if (results.isEmpty()) showState(State.EMPTY);
					else showState(State.RESULTS);
				});
			}
		});
	}
	
	private enum State { INFO, LOADING, EMPTY, RESULTS }
	
	private void showState(State state) {
		binding.layoutInfo.setVisibility(state == State.INFO ? View.VISIBLE : View.GONE);
		binding.layoutEmpty.setVisibility(state == State.EMPTY ? View.VISIBLE : View.GONE);
		binding.layoutLoading.setVisibility(state == State.LOADING ? View.VISIBLE : View.GONE);
		binding.rvSearchResults.setVisibility(state == State.RESULTS ? View.VISIBLE : View.GONE);
	}
	
	@Override
	public void onDestroyView() {
		super.onDestroyView();
		if (searchRunnable != null) uiHandler.removeCallbacks(searchRunnable);
		executorService.shutdown();
		binding = null; 
	}
	
	// High performance adapter leveraging DiffUtil
	private class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
		private final List<SearchResult> items = new ArrayList<>();
		
		public List<SearchResult> getItems() {
			return items;
		}
		
		public void applyDiff(List<SearchResult> newItems, DiffUtil.DiffResult diffResult) {
			items.clear();
			items.addAll(newItems);
			if (diffResult != null) {
				diffResult.dispatchUpdatesTo(this);
			} else {
				notifyDataSetChanged();
			}
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
			
			holder.binding.tvTitle.setText(result.title);
			holder.binding.tvSubtitle.setText(String.format("%s • %s", result.fileName, result.description));
			holder.binding.chipCategory.setText(result.category);
			
			// Icon Mapping matching native Sketchware Neo exactly
			int iconRes = R.drawable.ic_mtrl_file;
			switch (result.category) {
				case "View": iconRes = R.drawable.ic_mtrl_screen; break;
				case "Logic Block": iconRes = R.drawable.ic_mtrl_puzzle; break;
				case "Variable":
				case "List": iconRes = R.drawable.ic_mtrl_list; break;
				case "Component": iconRes = R.drawable.ic_mtrl_component; break;
				case "Resource": iconRes = R.drawable.ic_mtrl_palette; break;
			}
			holder.binding.imgIcon.setImageResource(iconRes);
			
			holder.binding.getRoot().setOnClickListener(v -> {
				dismiss();
				
				new Handler(Looper.getMainLooper()).postDelayed(() -> {
					
					if (activity != null && !activity.isFinishing()) {
						activity.handleSearchResult(result);
					}
				}, 250);
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
	
	// DiffUtil callback for buttery-smooth animations
	private static class SearchDiffCallback extends DiffUtil.Callback {
		private final List<SearchResult> oldList;
		private final List<SearchResult> newList;
		
		public SearchDiffCallback(List<SearchResult> oldList, List<SearchResult> newList) {
			this.oldList = oldList;
			this.newList = newList;
		}
		
		@Override
		public int getOldListSize() { return oldList.size(); }
		
		@Override
		public int getNewListSize() { return newList.size(); }
		
		@Override
		public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
			SearchResult oldItem = oldList.get(oldItemPosition);
			SearchResult newItem = newList.get(newItemPosition);
			// Treat items as the same if they share same title and filename (unique enough for UI)
			return oldItem.title.equals(newItem.title) && oldItem.fileName.equals(newItem.fileName);
		}
		
		@Override
		public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
			SearchResult oldItem = oldList.get(oldItemPosition);
			SearchResult newItem = newList.get(newItemPosition);
			return oldItem.description.equals(newItem.description) && oldItem.category.equals(newItem.category);
		}
	}
}