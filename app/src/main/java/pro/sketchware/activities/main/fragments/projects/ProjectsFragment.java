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
import androidx.core.widget.NestedScrollView;

import com.besome.sketch.adapters.ProjectsAdapter;
import com.besome.sketch.design.DesignActivity;
import com.besome.sketch.editor.manage.library.ProjectComparator;
import com.besome.sketch.projects.MyProjectSettingActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.transition.MaterialFadeThrough;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import a.a.a.DA;
import a.a.a.DB;
import a.a.a.lC;
import dev.chrisbanes.insetter.Insetter;
import mod.hey.studios.project.ProjectTracker;
import mod.hey.studios.project.backup.BackupRestoreManager;
import mod.sketchlibx.importer.ASProjectImporter;
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
    
    private boolean isFabExpanded = false;
    private ExtendedFloatingActionButton fabToggle;
    private ExtendedFloatingActionButton fabActionImport;
    private ExtendedFloatingActionButton fabActionRestore;
    private ExtendedFloatingActionButton fabActionNewProject;
    
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
    private SearchView projectsSearchView;
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
    public void onDestroyView() {
        super.onDestroyView();
        isFabExpanded = false;
        if (fabToggle != null) fabToggle.setOnClickListener(null);
        if (fabActionImport != null) fabActionImport.setOnClickListener(null);
        if (fabActionRestore != null) fabActionRestore.setOnClickListener(null);
        if (fabActionNewProject != null) fabActionNewProject.setOnClickListener(null);
        
        fabToggle = null;
        fabActionImport = null;
        fabActionRestore = null;
        fabActionNewProject = null;
        binding = null; 
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        preference = new DB(requireContext(), "project");

        fabToggle = requireActivity().findViewById(R.id.create_new_project);
        fabActionImport = requireActivity().findViewById(R.id.fab_action_import);
        fabActionRestore = requireActivity().findViewById(R.id.fab_action_restore);
        fabActionNewProject = requireActivity().findViewById(R.id.fab_action_new_project);
        View fabGroup = requireActivity().findViewById(R.id.fab_group);

        if (fabToggle != null) {
            fabToggle.setOnClickListener(v -> toggleFabMenu());
        }
        if (fabActionImport != null) {
            fabActionImport.setOnClickListener(v -> {
                ASProjectImporter.showPicker(getActivity(), this);
                toggleFabMenu();
            });
        }
        if (fabActionRestore != null) {
            fabActionRestore.setOnClickListener(v -> {
                new BackupRestoreManager(getActivity(), this).restore();
                toggleFabMenu();
            });
        }
        if (fabActionNewProject != null) {
            fabActionNewProject.setOnClickListener(v -> {
                toProjectSettingsActivity();
                toggleFabMenu();
            });
        }

        if (fabGroup != null) {
            Insetter.builder().margin(WindowInsetsCompat.Type.navigationBars()).applyToView(fabGroup);
        }

        binding.swipeRefresh.setProgressViewOffset(false, -200, -200);

        binding.swipeRefresh.setOnRefreshListener(() -> {
            if (binding != null && binding.customRefreshIndicator != null) {
                binding.customRefreshIndicator.setVisibility(View.VISIBLE);
            }
            refreshProjectsList();
        });

        projectsAdapter = new ProjectsAdapter(this, projectsList);
        binding.myprojects.setAdapter(projectsAdapter);
        
        if (binding.loadingContainer != null) {
            binding.loadingContainer.setVisibility(View.VISIBLE);
            binding.myprojects.setVisibility(View.GONE);
            binding.emptyContainer.setVisibility(View.GONE);
        }

        refreshProjectsList(); 
        
        if (binding.titleContainer != null) {
            UI.addSystemWindowInsetToPadding(binding.titleContainer, true, false, true, false);
        }

        binding.nestedScroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            int dy = scrollY - oldScrollY;
            if (dy > 8) {
                if (isFabExpanded) {
                    toggleFabMenu();
                } else if (fabToggle != null && fabToggle.isExtended()) {
                    fabToggle.shrink();
                }
            } else if (dy < -8) { // Scrolling Up
                if (!isFabExpanded && fabToggle != null && !fabToggle.isExtended()) {
                    fabToggle.extend();
                }
            }
        });

        if (binding.iconSort != null) {
            binding.iconSort.setOnClickListener(v -> showProjectSortingDialog());
        }

        setupMenu();
    }
    
    private void toggleFabMenu() {
        isFabExpanded = !isFabExpanded;
        if (isFabExpanded) {
            if (fabActionImport != null) fabActionImport.show();
            if (fabActionRestore != null) fabActionRestore.show();
            if (fabActionNewProject != null) fabActionNewProject.show();
            if (fabToggle != null) {
                fabToggle.setIconResource(R.drawable.ic_mtrl_close);
                fabToggle.setText("Close");
                if (!fabToggle.isExtended()) fabToggle.extend();
            }
        } else {
            if (fabActionImport != null) fabActionImport.hide();
            if (fabActionRestore != null) fabActionRestore.hide();
            if (fabActionNewProject != null) fabActionNewProject.hide();
            if (fabToggle != null) {
                fabToggle.setIconResource(R.drawable.ic_mtrl_add);
                fabToggle.setText("Create & Import");
            }
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
        if (!isAdded()) return;

        if (!c()) {
            if (binding.swipeRefresh.isRefreshing()) binding.swipeRefresh.setRefreshing(false);
            if (binding.customRefreshIndicator != null) binding.customRefreshIndicator.setVisibility(View.GONE);
            ((MainActivity) requireActivity()).s(); 
            return;
        }

        executorService.execute(() -> {
            List<HashMap<String, Object>> loadedProjects = lC.a();
            loadedProjects.sort(new ProjectComparator(preference.d("sortBy"), preference.a("pinnedProject", "-1")));

            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                
                if (binding.swipeRefresh.isRefreshing()) binding.swipeRefresh.setRefreshing(false);
                if (binding.customRefreshIndicator != null) binding.customRefreshIndicator.setVisibility(View.GONE);
                
                boolean isEmpty = loadedProjects.isEmpty();
                
                if (binding.loadingContainer != null) binding.loadingContainer.setVisibility(View.GONE);
                
                if (binding.emptyContainer != null) binding.emptyContainer.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                if (binding.myprojects != null) binding.myprojects.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

                projectsList.clear();
                projectsList.addAll(loadedProjects);
                
                projectsAdapter.updateData(projectsList); 
            });
        });
    }

    private void addProject(String sc_id) {
        executorService.execute(() -> {
            HashMap<String, Object> newProject = lC.b(sc_id);
            if (newProject != null) {
                requireActivity().runOnUiThread(() -> {
                    if (binding == null) return;
                    binding.emptyContainer.setVisibility(View.GONE);
                    binding.myprojects.setVisibility(View.VISIBLE);
                    projectsList.add(0, newProject);
                    projectsAdapter.updateData(projectsList);
                    binding.nestedScroll.smoothScrollTo(0, 0); 
                });
            }
        });
    }

    private void updateProject(String sc_id) {
        executorService.execute(() -> {
            HashMap<String, Object> updatedProject = lC.b(sc_id);
            if (updatedProject != null) {
                int index = IntStream.range(0, projectsList.size()).filter(i -> projectsList.get(i).get("sc_id").equals(sc_id)).findFirst().orElse(-1);
                if (index != -1) {
                    projectsList.set(index, updatedProject);
                    requireActivity().runOnUiThread(() -> projectsAdapter.updateData(projectsList));
                }
            }
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
}
