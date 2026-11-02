package pro.sketchware.activities.main.fragments.projects;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuProvider;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.adapters.ProjectsAdapter;
import com.besome.sketch.design.DesignActivity;
import com.besome.sketch.editor.manage.library.ProjectComparator;
import com.besome.sketch.projects.MyProjectSettingActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.MaterialFadeThrough;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import a.a.a.DA;
import a.a.a.DB;
import a.a.a.lC;
import dev.chrisbanes.insetter.Insetter;
import mod.hey.studios.project.ProjectTracker;
import mod.hey.studios.project.backup.BackupRestoreManager;
import pro.sketchware.activities.importer.AndroidStudioImportActivity;
import pro.sketchware.R;
import pro.sketchware.activities.main.activities.MainActivity;
import pro.sketchware.databinding.MyprojectsBinding;
import pro.sketchware.databinding.SortProjectDialogBinding;
import pro.sketchware.utility.UI;

public class ProjectsFragment extends DA {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final List<HashMap<String, Object>> projectsList = new ArrayList<>();
    private MyprojectsBinding binding;
    private ProjectsAdapter projectsAdapter;
    private final AtomicInteger refreshGeneration = new AtomicInteger();
    
    private SearchView projectsSearchView;
    
    public final ActivityResultLauncher<Intent> openProjectSettings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent data = result.getData();
            if (data != null) {
                String sc_id = data.getStringExtra("sc_id");
                if (data.getBooleanExtra("is_new", false)) {
                    toDesignActivity(sc_id);
                    addProject(sc_id);
                } else {
                    updateProject(sc_id);
                }
            }
        }
    });

    private DB preference;
    private MenuProvider menuProvider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialFadeThrough());
        setReturnTransition(new MaterialFadeThrough());
        setExitTransition(new MaterialFadeThrough());
        setReenterTransition(new MaterialFadeThrough());
    }

    @Override
    public void b(int requestCode) { }

    public void toDesignActivity(String sc_id) {
        Intent intent = new Intent(requireContext(), DesignActivity.class);
        ProjectTracker.setScId(sc_id);
        intent.putExtra("sc_id", sc_id);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        requireActivity().startActivity(intent);
    }

    @Override
    public void c(int requestCode) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
        startActivity(intent);
    }

    @Override
    public void d() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).s();
        }
    }

    @Override
    public void e() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).s();
        }
    }

    public void toProjectSettingsActivity() {
        Intent intent = new Intent(getActivity(), MyProjectSettingActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        openProjectSettings.launch(intent);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        binding = MyprojectsBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) refreshProjectsList();
    }

    @Override
    public void onDestroyView() {
        refreshGeneration.incrementAndGet();
        if (projectsAdapter != null) projectsAdapter.release();
        projectsAdapter = null;
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        preference = new DB(requireContext(), "project");

        binding.swipeRefresh.setOnRefreshListener(() -> refreshProjectsList());

        projectsAdapter = new ProjectsAdapter(this, projectsList);
        binding.myprojects.setAdapter(projectsAdapter);
        
        if (binding.loadingContainer != null) {
            binding.loadingContainer.setVisibility(View.VISIBLE);
            binding.myprojects.setVisibility(View.GONE);
            binding.emptyContainer.setVisibility(View.GONE);
        }

        View btnAiAgents = binding.getRoot().findViewById(R.id.btn_home_ai_agents);
        if (btnAiAgents != null) {
            btnAiAgents.setOnClickListener(v -> startActivity(new Intent(requireContext(), neo.sketchware.ai.AiSettingsActivity.class)));
        }
        View btnNewProject = binding.getRoot().findViewById(R.id.btn_home_new_project);
        if (btnNewProject != null) {
            btnNewProject.setOnClickListener(v -> toProjectSettingsActivity());
        }
        View btnImport = binding.getRoot().findViewById(R.id.btn_home_import);
        if (btnImport != null) {
            btnImport.setOnClickListener(v -> startActivityForResult(new Intent(requireContext(), AndroidStudioImportActivity.class), 2407));
        }
        View btnRestore = binding.getRoot().findViewById(R.id.btn_home_restore);
        if (btnRestore != null) {
            btnRestore.setOnClickListener(v -> new BackupRestoreManager(getActivity(), this).restore());
        }

        if (binding.titleContainer != null) {
            UI.addSystemWindowInsetToPadding(binding.titleContainer, true, false, true, false);
        }

        if (binding.iconSort != null) {
            binding.iconSort.setOnClickListener(v -> showProjectSortingDialog());
        }

        setupMenu();
    }
    
    public void openSearch() {
        if (projectsSearchView != null) {
            projectsSearchView.setIconified(false);
            projectsSearchView.requestFocusFromTouch();
        }
    }
    
    private void setupMenu() {
        menuProvider = new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.projects_fragment_menu, menu);
                MenuItem searchItem = menu.findItem(R.id.searchProjects);
                
                if (searchItem != null) {
                    projectsSearchView = (SearchView) searchItem.getActionView();
                    if (projectsSearchView != null) {
                        projectsSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                            @Override
                            public boolean onQueryTextChange(String s) {
                                projectsAdapter.filterData(s);
                                return false;
                            }
                            @Override
                            public boolean onQueryTextSubmit(String s) {
                                projectsSearchView.clearFocus();
                                return false;
                            }
                        });
                    }
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) { return false; }
        };
        requireActivity().addMenuProvider(menuProvider);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (getActivity() == null) return;
        if (hidden) {
            requireActivity().removeMenuProvider(menuProvider);
        } else {
            requireActivity().addMenuProvider(menuProvider);
        }
    }

    public void refreshProjectsList() {
        Activity host = getActivity();
        if (!isAdded() || binding == null || host == null || preference == null) return;

        if (!c()) {
            if (binding.swipeRefresh != null) binding.swipeRefresh.setRefreshing(false);
            if (host instanceof MainActivity) ((MainActivity) host).s();
            return;
        }

        final int generation = refreshGeneration.incrementAndGet();
        executorService.execute(() -> {
            List<HashMap<String, Object>> loadedProjects = new ArrayList<>();
            RuntimeException loadError = null;
            try {
                loadedProjects = lC.a();
                loadedProjects.sort(new ProjectComparator(
                        preference.d("sortBy"), preference.a("pinnedProject", "-1")));
            } catch (RuntimeException error) {
                loadError = error;
            }

            final List<HashMap<String, Object>> result = loadedProjects;
            final RuntimeException error = loadError;
            host.runOnUiThread(() -> {
                if (generation != refreshGeneration.get() || binding == null || !isAdded()) return;
                if (error != null) {
                    android.util.Log.e("ProjectsFragment", "Could not refresh projects", error);
                    if (binding.loadingContainer != null) binding.loadingContainer.setVisibility(View.GONE);
                    if (binding.swipeRefresh != null) binding.swipeRefresh.setRefreshing(false);
                    return;
                }

                projectsList.clear();
                projectsList.addAll(result);
                if (projectsAdapter == null) {
                    if (binding.loadingContainer != null) binding.loadingContainer.setVisibility(View.GONE);
                    if (binding.swipeRefresh != null) binding.swipeRefresh.setRefreshing(false);
                    return;
                }
                projectsAdapter.updateData(projectsList, () -> {
                    if (binding == null || !isAdded() || generation != refreshGeneration.get()) return;
                    boolean isEmpty = result.isEmpty();
                    if (binding.emptyContainer != null) binding.emptyContainer.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                    if (binding.myprojects != null) {
                        binding.myprojects.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                    }
                    if (isEmpty) {
                        if (binding.loadingContainer != null) binding.loadingContainer.setVisibility(View.GONE);
                        if (binding.swipeRefresh != null) binding.swipeRefresh.setRefreshing(false);
                    } else if (binding.myprojects != null) {
                        androidx.recyclerview.widget.RecyclerView projectList = binding.myprojects;
                        projectList.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                if (projectList.getViewTreeObserver().isAlive()) {
                                    projectList.getViewTreeObserver().removeOnPreDrawListener(this);
                                }
                                if (binding == null || !isAdded() || generation != refreshGeneration.get()) return true;
                                if (binding.loadingContainer != null) binding.loadingContainer.setVisibility(View.GONE);
                                if (binding.swipeRefresh != null) binding.swipeRefresh.setRefreshing(false);
                                return true;
                            }
                        });
                    }
                });
            });
        });
    }

    private void addProject(String sc_id) {
        Activity host = getActivity();
        if (host == null || sc_id == null || sc_id.isEmpty()) return;
        try {
            executorService.execute(() -> {
                HashMap<String, Object> newProject = lC.b(sc_id);
                if (newProject == null) return;
                host.runOnUiThread(() -> {
                    if (binding == null || projectsAdapter == null || !isAdded()) return;
                    boolean alreadyExists = projectsList.stream().anyMatch(project ->
                            sc_id.equals(String.valueOf(project.get("sc_id"))));
                    if (alreadyExists) {
                        updateProject(sc_id);
                        return;
                    }
                    binding.emptyContainer.setVisibility(View.GONE);
                    binding.myprojects.setVisibility(View.VISIBLE);
                    projectsList.add(0, newProject);
                    projectsAdapter.updateData(projectsList);
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            refreshProjectsList();
        }
    }

    private void updateProject(String sc_id) {
        Activity host = getActivity();
        if (host == null) return;
        executorService.execute(() -> {
            HashMap<String, Object> updatedProject = lC.b(sc_id);
            if (updatedProject == null) return;
            host.runOnUiThread(() -> {
                if (binding == null || projectsAdapter == null || !isAdded()) return;
                int index = IntStream.range(0, projectsList.size())
                        .filter(i -> sc_id.equals(String.valueOf(projectsList.get(i).get("sc_id"))))
                        .findFirst().orElse(-1);
                if (index != -1) {
                    projectsList.set(index, updatedProject);
                    projectsAdapter.updateData(projectsList);
                } else {
                    refreshProjectsList();
                }
            });
        });
    }

    private void showProjectSortingDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(requireActivity());
        dialog.setTitle("Sort options");

        SortProjectDialogBinding dialogBinding = SortProjectDialogBinding.inflate(LayoutInflater.from(requireActivity()));
        RadioButton sortByName = dialogBinding.sortByName;
        RadioButton sortByID = dialogBinding.sortByID;
        RadioButton sortOrderAsc = dialogBinding.sortOrderAsc;
        RadioButton sortOrderDesc = dialogBinding.sortOrderDesc;

        int storedValue = preference.a("sortBy", ProjectComparator.DEFAULT);
        if ((storedValue & ProjectComparator.SORT_BY_NAME) == ProjectComparator.SORT_BY_NAME) sortByName.setChecked(true);
        else if ((storedValue & ProjectComparator.SORT_BY_ID) == ProjectComparator.SORT_BY_ID) sortByID.setChecked(true);
        
        if ((storedValue & ProjectComparator.SORT_ORDER_ASCENDING) == ProjectComparator.SORT_ORDER_ASCENDING) sortOrderAsc.setChecked(true);
        else if ((storedValue & ProjectComparator.SORT_ORDER_DESCENDING) == ProjectComparator.SORT_ORDER_DESCENDING) sortOrderDesc.setChecked(true);

        dialog.setView(dialogBinding.getRoot());
        dialog.setPositiveButton("Save", (v, which) -> {
            int sortValue = 0;
            if (sortByName.isChecked()) sortValue |= ProjectComparator.SORT_BY_NAME;
            if (sortByID.isChecked()) sortValue |= ProjectComparator.SORT_BY_ID;
            if (sortOrderAsc.isChecked()) sortValue |= ProjectComparator.SORT_ORDER_ASCENDING;
            if (sortOrderDesc.isChecked()) sortValue |= ProjectComparator.SORT_ORDER_DESCENDING;
            
            preference.a("sortBy", sortValue, true);
            v.dismiss();
            refreshProjectsList();
        });
        dialog.setNegativeButton("Cancel", null);
        dialog.show();
    }
    @Override
    public void onDestroy() {
        executorService.shutdownNow();
        super.onDestroy();
    }

}
