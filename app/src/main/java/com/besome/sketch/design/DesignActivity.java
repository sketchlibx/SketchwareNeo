package com.besome.sketch.design;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.besome.sketch.adapters.JavaFileAdapter;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.common.SrcViewerActivity;
import com.besome.sketch.editor.manage.ManageCollectionActivity;
import com.besome.sketch.editor.manage.ViewSelectorActivity;
import com.besome.sketch.editor.manage.font.ManageFontActivity;
import com.besome.sketch.editor.manage.image.ManageImageActivity;
import com.besome.sketch.editor.manage.library.ManageLibraryActivity;
import com.besome.sketch.editor.manage.sound.ManageSoundActivity;
import com.besome.sketch.editor.manage.view.ManageViewActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.CustomViewPager;
import com.besome.sketch.tools.CompileLogActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.topjohnwu.superuser.Shell;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.eclipse.jgit.api.Git;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.DB;
import a.a.a.GB;
import a.a.a.ProjectBuilder;
import a.a.a.ViewEditorFragment;
import a.a.a.bB;
import a.a.a.bC;
import a.a.a.br;
import a.a.a.cC;
import a.a.a.eC;
import a.a.a.jC;
import a.a.a.kC;
import a.a.a.lC;
import a.a.a.mB;
import a.a.a.Ox;
import a.a.a.rs;
import a.a.a.wq;
import a.a.a.yB;
import a.a.a.yq;
import a.a.a.zy;
import dev.chrisbanes.insetter.Insetter;
import mod.agus.jcoderz.editor.manage.permission.ManagePermissionActivity;
import mod.agus.jcoderz.editor.manage.resource.ManageResourceActivity;
import mod.bobur.FloatingProgressWindow;
import mod.hey.studios.activity.managers.assets.ManageAssetsActivity;
import mod.hey.studios.activity.managers.cpp.ManageCppActivity;
import mod.hey.studios.activity.managers.java.ManageJavaActivity;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.project.ProjectSettings;
import mod.sketchlibx.importer.GradleParser;
import mod.sketchlibx.importer.ParsedGradle;
import mod.hey.studios.compiler.kotlin.KotlinCompilerBridge;
import mod.hey.studios.project.custom_blocks.CustomBlocksDialog;
import mod.hey.studios.project.proguard.ManageProguardActivity;
import mod.hey.studios.project.proguard.ProguardHandler;
import mod.hey.studios.project.stringfog.ManageStringFogFragment;
import mod.hey.studios.project.stringfog.StringfogHandler;
import mod.hey.studios.util.Helper;
import mod.hey.studios.util.SystemLogPrinter;
import mod.hilal.saif.activities.android_manifest.AndroidManifestInjection;
import mod.hilal.saif.activities.tools.ConfigActivity;
import mod.jbk.build.BuildProgressReceiver;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.diagnostic.CompileErrorSaver;
import mod.jbk.diagnostic.MissingFileException;
import mod.jbk.util.LogUtil;
import mod.khaled.logcat.LogReaderActivity;
import mod.sketchlibx.search.GlobalSearchDialog;
import mod.sketchlibx.project.git.GitWorkspace;
import mod.sketchlibx.terminal.TerminalBuildBridge;
import mod.sketchlibx.terminal.TerminalFragment;
import mod.sketchlibx.terminal.TerminalSessionManager;
import mod.hilal.saif.activities.tools.ConfigActivity;
import neo.sketchware.plugin.ui.PluginsTabFragment;
import pro.sketchware.R;
import pro.sketchware.activities.appcompat.ManageAppCompatActivity;
import pro.sketchware.activities.editor.command.ManageXMLCommandActivity;
import pro.sketchware.activities.editor.view.CodeViewerActivity;
import pro.sketchware.activities.editor.view.ViewCodeEditorActivity;
import pro.sketchware.activities.resourceseditor.ResourcesEditorActivity;
import pro.sketchware.dialogs.BuildSettingsBottomSheet;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.activities.editor.gradle.CustomGradleBuildManager;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.apk.ApkSignatures;

public class DesignActivity extends BaseAppCompatActivity implements View.OnClickListener {
    public static String sc_id;
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
    
    public final ActivityResultLauncher<Intent> changeOpenFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            assert result.getData() != null;
            projectFile = result.getData().getParcelableExtra("project_file");
            refresh();
        }
    });
    
    private ImageView xmlLayoutOrientation;
    private boolean B;
    private int currentTabNumber;
    private CustomViewPager viewPager;
    private CoordinatorLayout coordinatorLayout;
    private DrawerLayout drawer;
    private yq q;
    private DB r;
    private DB t;
    private Menu bottomMenu;
    private final androidx.activity.result.ActivityResultLauncher<Intent> versionHistoryLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    String restoredScId = DesignActivity.sc_id;
                    finish();
                    Intent intent = new Intent(DesignActivity.this, DesignActivity.class);
                    intent.putExtra("sc_id", restoredScId);
                    startActivity(intent);
                }
            });
    private PopupMenu bottomPopupMenu;
    private MaterialButton btnRun;
    private MaterialButton btnOptions;
    private ProjectFileBean projectFile;
    private TextView fileName;
    private String currentJavaFileName;
    private ViewEditorFragment viewTabAdapter;
    private FloatingProgressWindow fProgress;
    
    private final ActivityResultLauncher<Intent> openCollectionManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            if (viewTabAdapter != null) {
                viewTabAdapter.j();
            }
        }
    });
    
    private final ActivityResultLauncher<Intent> openResourcesManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            if (viewTabAdapter != null && viewPager.getCurrentItem() == 0) {
                viewTabAdapter.i();
                refreshViewTabAdapter();
            }
        }
    });
    
    private final ActivityResultLauncher<Intent> openViewCodeEditor = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            if (viewTabAdapter != null) {
                viewTabAdapter.i();
            }
            refresh();
        }
    });
    
    private rs eventTabAdapter;
    private br componentTabAdapter;
    private PluginsTabFragment pluginsTabAdapter;
    private boolean pluginsTabEnabled;
    private Toolbar toolbar;
    
    private final ActivityResultLauncher<Intent> openImageManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            refresh();
        }
    });
    
    private final ActivityResultLauncher<Intent> openLibraryManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            refresh();
            if (viewTabAdapter != null) {
                viewTabAdapter.n();
            }
        }
    });
    
    private final ActivityResultLauncher<Intent> openViewManager = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            refresh();
        }
    });
    
    private BuildTask currentBuildTask;
    private final BroadcastReceiver buildCancelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BuildTask.ACTION_CANCEL_BUILD.equals(intent.getAction()) && currentBuildTask != null) {
                String token = intent.getStringExtra("build_token");
                if (token != null && token.equals(currentBuildTask.buildToken)) currentBuildTask.cancelBuild();
            }
        }
    };

    private final TerminalBuildBridge.Host terminalBuildHost = new TerminalBuildBridge.Host() {
        @Override
        public boolean isBuildRunning() {
            return currentBuildTask != null && currentBuildTask.running;
        }

        @Override
        public boolean startBuild(TerminalBuildBridge.Listener listener) {
            if (Looper.myLooper() != Looper.getMainLooper() || isFinishing() || isDestroyed() || isBuildRunning()) return false;
            BuildTask buildTask = new BuildTask(DesignActivity.this, listener);
            currentBuildTask = buildTask;
            buildTask.execute();
            return true;
        }

        @Override
        public void cancelBuild() {
            BuildTask buildTask = currentBuildTask;
            if (buildTask != null && buildTask.running && !buildTask.canceled) buildTask.cancelBuild();
        }
    };

    private void saveVersionCodeInformationToProject() {
        HashMap<String, Object> projectMetadata = lC.b(sc_id);
        if (projectMetadata != null) {
            projectMetadata.put("sketchware_ver", GB.d(getApplicationContext()));
            lC.b(sc_id, projectMetadata);
            
            String projectFilePath = wq.e() + File.separator + sc_id + File.separator + "project";
            File projectFileActual = new File(projectFilePath);
            if (projectFileActual.exists()) {
                projectFileActual.setLastModified(System.currentTimeMillis());
            }
        }
    }

    private void loadProject(boolean haveSavedState) {
        projectFile = getDefaultProjectFile();
        jC.a(sc_id, haveSavedState);
        jC.b(sc_id, haveSavedState);
        kC var2 = jC.d(sc_id, haveSavedState);
        jC.c(sc_id, haveSavedState);
        cC.c(sc_id);
        bC.d(sc_id);
        if (!haveSavedState) {
            var2.f();
            var2.g();
            var2.e();
        }
    }

    private ProjectFileBean getDefaultProjectFile() {
        return jC.b(sc_id).b(ProjectFileBean.DEFAULT_XML_NAME);
    }

    private void refreshFileSelector() {
        if (projectFile == null) {
            projectFile = getDefaultProjectFile();
        }

        String javaFileName = projectFile.getJavaName();
        String xmlFileName = projectFile.getXmlName();

        if (!javaFileName.isEmpty()) {
            currentJavaFileName = javaFileName;
        }

        if (viewPager.getCurrentItem() == 0) {
            if (!ProjectFileBean.DEFAULT_XML_NAME.equals(xmlFileName) && jC.b(sc_id).b(xmlFileName) == null) {
                projectFile = getDefaultProjectFile();
                xmlFileName = ProjectFileBean.DEFAULT_XML_NAME;
            }
            fileName.setText(xmlFileName);
        } else {
            if (!ProjectFileBean.DEFAULT_JAVA_NAME.equals(currentJavaFileName) && jC.b(sc_id).a(currentJavaFileName) == null) {
                projectFile = getDefaultProjectFile();
                currentJavaFileName = ProjectFileBean.DEFAULT_JAVA_NAME;
            }
            fileName.setText(currentJavaFileName);
        }
    }

    private void refreshViewTabAdapter() {
        if (viewTabAdapter != null && projectFile != null) {
            int orientation = projectFile.orientation;
            if (orientation == ProjectFileBean.ORIENTATION_PORTRAIT) {
                xmlLayoutOrientation.setImageResource(R.drawable.ic_screen_portrait_grey600_24dp);
            } else if (orientation == ProjectFileBean.ORIENTATION_LANDSCAPE) {
                xmlLayoutOrientation.setImageResource(R.drawable.ic_screen_landscape_grey600_24dp);
            } else {
                xmlLayoutOrientation.setImageResource(R.drawable.ic_screen_rotation_grey600_24dp);
            }
            viewTabAdapter.initialize(projectFile);
        }
    }

    private void refreshEventTabAdapter() {
        if (eventTabAdapter != null && projectFile != null) {
            eventTabAdapter.setCurrentActivity(projectFile);
            eventTabAdapter.refreshEvents();
        }
    }

    private void refreshComponentTabAdapter() {
        if (componentTabAdapter != null && projectFile != null) {
            componentTabAdapter.setProjectFile(projectFile);
            componentTabAdapter.refreshData();
        }
    }

    private void refresh() {
        refreshFileSelector();
        if (viewPager.getCurrentItem() == 0) {
            refreshViewTabAdapter();
        } else {
            refreshEventTabAdapter();
            refreshComponentTabAdapter();
        }
    }

    public void setTouchEventEnabled(boolean touchEventEnabled) {
        if (touchEventEnabled) {
            viewPager.enableTouchEvent();
        } else {
            viewPager.disableTouchEvent();
        }
    }

    private void indicateCompileErrorOccurred(String projectId, String error) {
        new CompileErrorSaver(projectId).writeLogsToFile(error);
        if (isFinishing() || isDestroyed() || !projectId.equals(sc_id)) return;
        Snackbar snackbar = Snackbar.make(coordinatorLayout, "Show compile log", Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(Helper.getResString(R.string.common_word_show), v -> {
            if (!mB.a() && !isFinishing() && !isDestroyed() && projectId.equals(sc_id)) {
                snackbar.dismiss();
                Intent intent = new Intent(getApplicationContext(), CompileLogActivity.class);
                intent.putExtra("error", error);
                intent.putExtra("sc_id", projectId);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
        });
        snackbar.show();
    }

    @Override
    public void finish() {
        jC.a();
        cC.a();
        bC.a();
        setResult(RESULT_CANCELED, getIntent());
        super.finish();
    }

    private void checkForUnsavedProjectData() {
        if (jC.c(sc_id).g() || jC.b(sc_id).g() || jC.d(sc_id).q() || jC.a(sc_id).d() || jC.a(sc_id).c()) {
            askIfToRestoreOldUnsavedProjectData();
        }
    }

    private void installBuiltApk() {
        if (!ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_ROOT_AUTO_INSTALL_PROJECTS)) {
            requestPackageInstallerInstall();
        } else {
            File apkUri = new File(q.finalToInstallApkPath);
            long length = apkUri.length();
            Shell.getShell(shell -> {
                if (shell.isRoot()) {
                    List<String> stdout = new LinkedList<>();
                    List<String> stderr = new LinkedList<>();

                    Shell.cmd("cat " + apkUri + " | pm install -S " + length).to(stdout, stderr).submit(result -> {
                        if (result.isSuccess()) {
                            SketchwareUtil.toast("Package installed successfully!");
                            if (ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_ROOT_AUTO_OPEN_AFTER_INSTALLING)) {
                                Intent launcher = getPackageManager().getLaunchIntentForPackage(q.packageName);
                                if (launcher != null) {
                                    startActivity(launcher);
                                } else {
                                    SketchwareUtil.toastError("Couldn't launch project, either not installed or not with launcher activity.");
                                }
                            }
                        } else {
                            String sharedErrorMessage = "Failed to install package, result code: " + result.getCode() + ". ";
                            SketchwareUtil.toastError(sharedErrorMessage + "Logs are available in /Internal storage/.sketchware/debug.txt", Toast.LENGTH_LONG);
                            LogUtil.e("DesignActivity", sharedErrorMessage + "stdout: " + stdout + ", stderr: " + stderr);
                        }
                    });
                } else {
                    SketchwareUtil.toastError("No root access granted. Continuing using default package install prompt.");
                    requestPackageInstallerInstall();
                }
            });
        }
    }

    private void requestPackageInstallerInstall() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri = FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName() + ".provider", new File(q.finalToInstallApkPath));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END);
        } else if (viewTabAdapter != null && viewTabAdapter.isPropertyViewVisible()) {
            hideViewPropertyView();
        } else {
            if (currentTabNumber > 0) {
                currentTabNumber--;
                viewPager.setCurrentItem(currentTabNumber);
            } else if (t.c("P12I2")) {
                k();
                saveChangesAndCloseProject();
            } else {
                showSaveBeforeQuittingDialog();
            }
        }
    }

    public void hideViewPropertyView() {
        if (viewTabAdapter != null) viewTabAdapter.a(false);
    }

    private void saveChangesAndCloseProject() {
        k();
        SaveChangesProjectCloser saveChangesProjectCloser = new SaveChangesProjectCloser(this);
        saveChangesProjectCloser.execute();
    }

    private void saveProject() {
        k();
        ProjectSaver projectSaver = new ProjectSaver(this);
        projectSaver.execute();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.design);
        if (!isStoragePermissionGranted()) {
            finish();
        }

        if (savedInstanceState == null) {
            sc_id = getIntent().getStringExtra("sc_id");
        } else {
            sc_id = savedInstanceState.getString("sc_id");
        }

        r = new DB(getApplicationContext(), "P1");
        t = new DB(getApplicationContext(), "P12");
        
        TerminalSessionManager.releaseAllExcept(this, sc_id);
        TerminalBuildBridge.register(sc_id, terminalBuildHost);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle(sc_id);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        drawer = findViewById(R.id.drawer_layout);
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        Insetter.builder().margin(WindowInsetsCompat.Type.navigationBars()).applyToView(findViewById(R.id.container));

        coordinatorLayout = findViewById(R.id.layout_coordinator);
        fileName = findViewById(R.id.file_name);

        findViewById(R.id.file_name_container).setOnClickListener(this);

        btnRun = findViewById(R.id.btn_run);
        btnRun.setOnClickListener(v -> {
            if (currentBuildTask != null && currentBuildTask.running) {
                if (!currentBuildTask.canceled && !currentBuildTask.isBuildFinished) {
                    currentBuildTask.cancelBuild();
                } else {
                    Snackbar.make(btnRun, "The previous build is finishing. Run again when it has stopped.", Snackbar.LENGTH_LONG).show();
                }
                return;
            }

            BuildTask buildTask = new BuildTask(this);
            currentBuildTask = buildTask;
            buildTask.execute();
        });

        btnOptions = findViewById(R.id.btn_options);
        btnOptions.setOnClickListener(v -> bottomPopupMenu.show());

        bottomPopupMenu = new PopupMenu(this, btnOptions);
        bottomMenu = bottomPopupMenu.getMenu();
        
        bottomMenu.add(Menu.NONE, 1, Menu.NONE, "Build Settings").setOnMenuItemClickListener(item -> {
            BuildSettingsBottomSheet sheet = BuildSettingsBottomSheet.newInstance(sc_id);
            sheet.show(getSupportFragmentManager(), BuildSettingsBottomSheet.TAG);
            return true;
        });

        bottomMenu.add(Menu.NONE, 99, Menu.NONE, "Advanced Settings").setOnMenuItemClickListener(item -> {
            mod.sketchlibx.settings.AdvancedSettingsBottomSheet advSheet = new mod.sketchlibx.settings.AdvancedSettingsBottomSheet(sc_id, currentJavaFileName, this);
            advSheet.show(getSupportFragmentManager(), "AdvancedSettings");
            return true;
        });

        bottomMenu.add(Menu.NONE, 2, Menu.NONE, "Clean temporary files").setVisible(false).setOnMenuItemClickListener(item -> {
            new Thread(() -> {
                FileUtil.deleteFile(q.projectMyscPath);
                updateBottomMenu();
                runOnUiThread(() -> SketchwareUtil.toast("Done cleaning temporary files!"));
            }).start();
            return true;
        });

        bottomMenu.add(Menu.NONE, 3, Menu.NONE, "Show last compile error").setOnMenuItemClickListener(item -> {
            new CompileErrorSaver(sc_id).showLastErrors(this);
            return true;
        });

        bottomMenu.add(Menu.NONE, 5, Menu.NONE, "Show source code").setOnMenuItemClickListener(item -> {
            showCurrentActivitySrcCode();
            return true;
        });
        
        bottomMenu.add(Menu.NONE, 9, Menu.NONE, "Edit Custom Java")
            .setVisible(false)
            .setOnMenuItemClickListener(item -> {
                toCustomJavaEditor();
                return true;
        });

        bottomMenu.add(Menu.NONE, 4, Menu.NONE, "Install last built APK").setVisible(false).setOnMenuItemClickListener(item -> {
            if (FileUtil.isExistFile(q.finalToInstallApkPath)) {
                installBuiltApk();
            } else SketchwareUtil.toast("APK doesn't exist anymore");
            return true;
        });

        bottomMenu.add(Menu.NONE, 6, Menu.NONE, "Show Apk signatures").setVisible(false).setOnMenuItemClickListener(item -> {
            ApkSignatures apkSignatures = new ApkSignatures(this, q.finalToInstallApkPath);
            apkSignatures.showSignaturesDialog();
            return true;
        });

        bottomMenu.add(Menu.NONE, 7, Menu.NONE, "Direct XML editor").setOnMenuItemClickListener(item -> {
            toViewCodeEditor();
            return true;
        });
        
        boolean isVersionHistoryEnabled = new ProjectSettings(sc_id).getValue("enable_version_history", "false").equals("true");
        bottomMenu.add(Menu.NONE, 8, Menu.NONE, "Version History")
            .setVisible(isVersionHistoryEnabled)
            .setOnMenuItemClickListener(item -> {
                Intent intent = new Intent(DesignActivity.this, mod.sketchlibx.project.history.LocalHistoryActivity.class);
        intent.putExtra("sc_id", sc_id);
        versionHistoryLauncher.launch(intent);
        return true;
        });
        
        bottomMenu.add(Menu.NONE, 10, Menu.NONE, "Git Client").setOnMenuItemClickListener(item -> {
            checkAndInitGit();
            return true;
        });
        
        bottomPopupMenu.setOnDismissListener(menu -> btnOptions.setChecked(false));

        xmlLayoutOrientation = findViewById(R.id.img_orientation);
        viewPager = findViewById(R.id.viewpager);
        viewPager.setAdapter(new ViewPagerAdapter(getSupportFragmentManager()));
        viewPager.setOffscreenPageLimit(3);
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {  
                if (currentTabNumber == 1) {
                    if (eventTabAdapter != null) eventTabAdapter.c();
                } else if (currentTabNumber == 2 && componentTabAdapter != null) {
                    componentTabAdapter.unselectAll();
                }
                
                boolean isCustomJavaEnabled = new ProjectSettings(sc_id).getValue(ProjectSettings.SETTING_ENABLE_CUSTOM_JAVA, "false").equals("true");

                if (position == 0) {
                    bottomMenu.findItem(7).setVisible(true); 
                    bottomMenu.findItem(9).setVisible(false); 
                    if (viewTabAdapter != null) {
                        viewTabAdapter.showHidePropertyView(true);
                        xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_screen);
                    }
                } else if (position == 1) {
                    bottomMenu.findItem(7).setVisible(false); 
                    bottomMenu.findItem(9).setVisible(isCustomJavaEnabled); 
                    if (viewTabAdapter != null) {
                        xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_code);
                        viewTabAdapter.showHidePropertyView(false);
                    }
                } else if (position == 2) {
                    bottomMenu.findItem(7).setVisible(false);
                    bottomMenu.findItem(9).setVisible(isCustomJavaEnabled); 
                    if (viewTabAdapter != null) {
                        xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_code);
                        viewTabAdapter.showHidePropertyView(false);
                    }
                } else {
                    bottomMenu.findItem(7).setVisible(false);
                    bottomMenu.findItem(9).setVisible(false);
                    if (viewTabAdapter != null) {
                        xmlLayoutOrientation.setImageResource(R.drawable.ic_mtrl_code);
                        viewTabAdapter.showHidePropertyView(false);
                    }
                }
                refresh();
                currentTabNumber = position;
                invalidateOptionsMenu();
            }
        });
        viewPager.getAdapter().notifyDataSetChanged();
        ((TabLayout) findViewById(R.id.tab_layout)).setupWithViewPager(viewPager);

        IntentFilter filter = new IntentFilter(BuildTask.ACTION_CANCEL_BUILD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(buildCancelReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(buildCancelReceiver, filter);
        }

        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(i);
            SketchwareUtil.toast("Please allow overlay permission");
        }
        fProgress = new FloatingProgressWindow();
    }
    
private void checkAndInitGit() {
        String gitWorkspacePath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/git_workspace";
        File gitWorkspace = new File(gitWorkspacePath);
        File gitDir = new File(gitWorkspace, ".git");

        if (!gitDir.exists() || !gitDir.isDirectory()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Initialize Git Repository")
                    .setMessage("This project does not have a Git repository yet. It will be created safely in a permanent workspace away from junk files:\n" + gitWorkspacePath)
                    .setPositiveButton("Initialize", (dialog, which) -> {
                        try {
                            GitWorkspace.initialize(gitWorkspace);

                            SketchwareUtil.toast("Git Repository Initialized Safely!");
                            openGitClient();
                        } catch (Exception e) {
                            SketchwareUtil.toastError("Failed to init Git: " + e.getMessage());
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            openGitClient();
        }
    }

    private void openGitClient() {
        mod.sketchlibx.project.git.GitClientBottomSheet sheet = new mod.sketchlibx.project.git.GitClientBottomSheet(sc_id);
        sheet.show(getSupportFragmentManager(), "GitClient");
    }

    private boolean isDebugApkExists() {
        if (q != null) return FileUtil.isExistFile(q.finalToInstallApkPath);
        return false;
    }

    private void updateBottomMenu() {
        if (bottomMenu != null) {
            handler.post(() -> {
                bottomMenu.findItem(2).setVisible(q != null && FileUtil.isExistFile(q.projectMyscPath));
                var isDebugApkExists = isDebugApkExists();
                bottomMenu.findItem(4).setVisible(isDebugApkExists);
                bottomMenu.findItem(6).setVisible(isDebugApkExists);
            });
        }
    }

    
    public void refreshAdvancedSettingsUI() {
        if (bottomMenu == null) return;
        boolean isCustomJavaEnabled = new ProjectSettings(sc_id)
                .getValue(ProjectSettings.SETTING_ENABLE_CUSTOM_JAVA, "false")
                .equals("true");
        handler.post(() -> {
            boolean shouldShow = (currentTabNumber == 1 || currentTabNumber == 2)
                    && isCustomJavaEnabled;
            bottomMenu.findItem(9).setVisible(shouldShow);
        });
        invalidateOptionsMenu();
    }

    @Override
    public void onDestroy() {
        TerminalBuildBridge.unregister(sc_id, terminalBuildHost);
        super.onDestroy();
        if (isFinishing()) TerminalSessionManager.releaseProject(this, sc_id);
        unregisterReceiver(buildCancelReceiver);
        fProgress.exit();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (currentBuildTask != null && !currentBuildTask.canceled && !currentBuildTask.isBuildFinished) {
           fProgress.start(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.design_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.design_actionbar_titleopen_drawer) {
            if (!drawer.isDrawerOpen(GravityCompat.END)) {
                drawer.openDrawer(GravityCompat.END);
            }
        } else if (itemId == R.id.design_option_menu_title_save_project) {
            saveProject();
        } else if (itemId == R.id.design_option_menu_search) {
            handleSearchIconClick();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void handleSearchIconClick() {
        if (currentTabNumber == 1) {
            showEventSearchPopup();
        } else {
            openGlobalSearch();
        }
    }


    private void openGlobalSearch() {
        GlobalSearchDialog dialog = new GlobalSearchDialog(sc_id, this);
        dialog.show(getSupportFragmentManager(), "GlobalSearch");
    }

    private void showEventSearchPopup() {
        View anchor = toolbar.findViewById(R.id.design_option_menu_search);
        if (anchor == null) anchor = toolbar;

        PopupMenu searchPopupMenu = new PopupMenu(this, anchor);
        searchPopupMenu.getMenu().add(Menu.NONE, 1, Menu.NONE, "Global Search");
        searchPopupMenu.getMenu().add(Menu.NONE, 2, Menu.NONE, Helper.getResString(R.string.logic_search_events));
        searchPopupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                openGlobalSearch();
            } else if (item.getItemId() == 2) {
                if (eventTabAdapter != null) eventTabAdapter.toggleSearchBar();
            }
            return true;
        });
        searchPopupMenu.show();
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        k();

        HashMap<String, Object> projectInfo = lC.b(sc_id);
        getSupportActionBar().setTitle(yB.c(projectInfo, "my_ws_name"));
        q = new yq(getApplicationContext(), wq.d(sc_id), projectInfo);

        try {
            ProjectLoader projectLoader = new ProjectLoader(this, savedInstanceState);
            projectLoader.execute();
        } catch (Exception e) {
            crashlytics.log("ProjectLoader failed");
            crashlytics.recordException(e);
        } finally {
            SystemLogPrinter.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isStoragePermissionGranted()) finish();
        long freeMegabytes = GB.c();
        if (freeMegabytes < 100L && freeMegabytes > 0L) warnAboutInsufficientStorageSpace();
        if (fProgress != null) fProgress.stop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
        if (!isStoragePermissionGranted()) finish();
        if (!B) {
            UnsavedChangesSaver unsavedChangesSaver = new UnsavedChangesSaver(this);
            unsavedChangesSaver.execute();
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.file_name_container) {
            if (viewPager.getCurrentItem() == 0) showAvailableViews();
            else showAvailableJavaFiles();
        }
    }

    private void showSaveBeforeQuittingDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.design_quit_title_exit_projet));
        dialog.setIcon(R.drawable.ic_mtrl_exit);
        dialog.setMessage(Helper.getResString(R.string.design_quit_message_confirm_save));
        dialog.setPositiveButton(Helper.getResString(R.string.design_quit_button_save_and_exit), (v, which) -> {
            if (!mB.a()) {
                v.dismiss();
                try {
                    saveChangesAndCloseProject();
                } catch (Exception e) {
                    crashlytics.recordException(e);
                    h();
                }
            }
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_exit), (v, which) -> {
            if (!mB.a()) {
                v.dismiss();
                try {
                    k();
                    DiscardChangesProjectCloser discardChangesProjectCloser = new DiscardChangesProjectCloser(this);
                    discardChangesProjectCloser.execute();
                } catch (Exception e) {
                    crashlytics.recordException(e);
                    h();
                }
            }
        });
        dialog.setNeutralButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    private void warnAboutInsufficientStorageSpace() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.common_word_warning));
        dialog.setIcon(R.drawable.break_warning_96_red);
        dialog.setMessage(Helper.getResString(R.string.common_message_insufficient_storage_space));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_ok), null);
        dialog.show();
    }

    private void askIfToRestoreOldUnsavedProjectData() {
        B = true;
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.ic_mtrl_history);
        dialog.setTitle(Helper.getResString(R.string.design_restore_data_title));
        dialog.setMessage(Helper.getResString(R.string.design_restore_data_message_confirm));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_restore), (v, which) -> {
            if (!mB.a()) {
                boolean g = jC.c(sc_id).g();
                boolean g2 = jC.b(sc_id).g();
                boolean q = jC.d(sc_id).q();
                boolean d = jC.a(sc_id).d();
                boolean c = jC.a(sc_id).c();
                if (g) jC.c(sc_id).h();
                if (g2) jC.b(sc_id).h();
                if (q) jC.d(sc_id).r();
                if (d) jC.a(sc_id).h();
                if (c) jC.a(sc_id).f();
                if (g) {
                    jC.b(sc_id).a(jC.c(sc_id));
                    jC.a(sc_id).a(jC.c(sc_id).d());
                }
                if (g2 || g) jC.a(sc_id).a(jC.b(sc_id));
                if (q) {
                    jC.a(sc_id).c(jC.d(sc_id));
                    jC.a(sc_id).a(jC.d(sc_id));
                }
                refresh();
                B = false;
                v.dismiss();
            }
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_no), (v, which) -> {
            B = false;
            v.dismiss();
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showCurrentActivitySrcCode() {
        if (projectFile == null) return;
        k();
        new Thread(() -> {
            var filename = Helper.getText(fileName);
            var code = new yq(getApplicationContext(), sc_id).getFileSrc(filename, jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
            runOnUiThread(() -> {
                if (isFinishing()) return;
                h();
                if (code.isEmpty()) {
                    SketchwareUtil.toast("Failed to generate source.");
                    return;
                }
                var scheme = filename.endsWith(".xml") ? CodeViewerActivity.SCHEME_XML : CodeViewerActivity.SCHEME_JAVA;
                launchActivity(CodeViewerActivity.class, null, new Pair<>("code", code), new Pair<>("sc_id", sc_id), new Pair<>("scheme", scheme));
            });
        }).start();
    }

    private void toCustomJavaEditor() {
        if (projectFile == null) return;
        
        boolean isCustomJavaEnabled = new ProjectSettings(sc_id).getValue(ProjectSettings.SETTING_ENABLE_CUSTOM_JAVA, "false").equals("true");
        if (!isCustomJavaEnabled) {
            SketchwareUtil.toastError("Custom Java is disabled. Enable it in Advanced Settings first.");
            return;
        }
        
        k();
        new Thread(() -> {
            String tempJavaFileName = projectFile.getJavaName();
            if (tempJavaFileName == null || tempJavaFileName.isEmpty()) {
                tempJavaFileName = currentJavaFileName;
            }
            
            if (tempJavaFileName == null || tempJavaFileName.isEmpty()) {
                runOnUiThread(() -> { h(); SketchwareUtil.toastError("No Java file selected"); });
                return;
            }

            final String javaFileName = tempJavaFileName;

            String customJavaDir = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/custom_java/";
            FileUtil.makeDir(customJavaDir);
            String customJavaPath = customJavaDir + javaFileName;

            if (!FileUtil.isExistFile(customJavaPath)) {
                String source = new yq(getApplicationContext(), sc_id).getFileSrc(javaFileName, jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
                FileUtil.writeFile(customJavaPath, source);
            }

            runOnUiThread(() -> {
                if (isFinishing()) return;
                h();
                Intent intent = new Intent(DesignActivity.this, SrcCodeEditor.class);
                intent.putExtra("content", customJavaPath);
                intent.putExtra("title", "Custom " + javaFileName);
                intent.putExtra("sc_id", sc_id);
                startActivity(intent);
            });
        }).start();
    }

    private void showAvailableJavaFiles() {
        var dialog = new MaterialAlertDialogBuilder(this).create();
        dialog.setTitle(R.string.design_file_selector_title_java);
        dialog.setIcon(R.drawable.ic_mtrl_java);
        View customView = a.a.a.wB.a(this, R.layout.file_selector_popup_select_java);
        RecyclerView recyclerView = customView.findViewById(R.id.file_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext(), RecyclerView.VERTICAL, false));
        var adapter = new JavaFileAdapter(sc_id);
        adapter.setOnItemClickListener(projectFileBean -> {
            projectFile = projectFileBean;
            refreshFileSelector();
            refreshEventTabAdapter();
            refreshComponentTabAdapter();
            dialog.dismiss();
        });
        recyclerView.setAdapter(adapter);
        dialog.setView(customView);
        dialog.show();
    }

    private void showAvailableViews() {
        Intent intent = new Intent(getApplicationContext(), ViewSelectorActivity.class);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("current_xml", projectFile.getXmlName());
        intent.putExtra("is_custom_view", projectFile.fileType == 1 || projectFile.fileType == 2);
        changeOpenFile.launch(intent);
    }

    void toViewCodeEditor() {
        if (projectFile == null) return;
        k();
        new Thread(() -> {
            String filename = Helper.getText(fileName);
            var xmlGenerator = new Ox(q.N, projectFile);
            var projectDataManager = jC.a(sc_id);
            var viewBeans = projectDataManager.d(filename);
            var viewFab = projectDataManager.h(filename);
            xmlGenerator.setExcludeAppCompat(true);
            xmlGenerator.a(eC.a(viewBeans), viewFab);
            String content = xmlGenerator.b();
            runOnUiThread(() -> {
                if (isFinishing()) return;
                h();
                launchActivity(ViewCodeEditorActivity.class, openViewCodeEditor, new Pair<>("title", filename), new Pair<>("content", content));
            });
        }).start();
    }

    void toLogReader() {
        Intent intent = new Intent(getApplicationContext(), LogReaderActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        startActivity(intent);
    }

    void toCollectionManager() { launchActivity(ManageCollectionActivity.class, openCollectionManager); }
    
    void toAndroidManifestManager() {
        if (projectFile == null) return;
        launchActivity(AndroidManifestInjection.class, null, new Pair<>("file_name", currentJavaFileName));
    }
    
    void toAppCompatInjectionManager() { if (projectFile != null) launchActivity(ManageAppCompatActivity.class, null, new Pair<>("file_name", projectFile.getXmlName())); }
    void toAssetManager() { launchActivity(ManageAssetsActivity.class, null); }
    void toCppManager()   { launchActivity(ManageCppActivity.class,   null, new Pair<>("pkgName", q.packageName)); }
    void toCustomBlocksViewer() { new CustomBlocksDialog().show(this, sc_id); }
    void toJavaManager() { launchActivity(ManageJavaActivity.class, null, new Pair<>("pkgName", q.packageName)); }
    void toPermissionManager() { launchActivity(ManagePermissionActivity.class, null); }
    void toProguardManager() { launchActivity(ManageProguardActivity.class, null); }
    void toResourceManager() { launchActivity(ManageResourceActivity.class, openResourcesManager); }
    void toResourceEditor() { launchActivity(ResourcesEditorActivity.class, openResourcesManager); }
    void toStringFogManager() {
        var fragmentManager = getSupportFragmentManager();
        if (fragmentManager.findFragmentByTag("stringFogFragment") == null) {
            var bottomSheet = new ManageStringFogFragment();
            bottomSheet.show(fragmentManager, "stringFogFragment");
        }
    }
    void toFontManager() { launchActivity(ManageFontActivity.class, null); }
    void toImageManager() { launchActivity(ManageImageActivity.class, openImageManager); }
    void toLibraryManager() { launchActivity(ManageLibraryActivity.class, openLibraryManager); }
    void toViewManager() { launchActivity(ManageViewActivity.class, openViewManager); }
    void toSoundManager() { launchActivity(ManageSoundActivity.class, null); }
    void toSourceCodeViewer() { launchActivity(SrcViewerActivity.class, null, new Pair<>("current", Helper.getText(fileName))); }
    void toXMLCommandManager() { launchActivity(ManageXMLCommandActivity.class, null); }

    @SafeVarargs
    private void launchActivity(Class<? extends Activity> toLaunch, ActivityResultLauncher<Intent> optionalLauncher, Pair<String, String>... extras) {
        Intent intent = new Intent(getApplicationContext(), toLaunch);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", sc_id);
        for (Pair<String, String> extra : extras) {
            intent.putExtra(extra.first, extra.second);
        }

        if (optionalLauncher == null) startActivity(intent);
        else optionalLauncher.launch(intent);
    }

    private abstract static class BaseTask {
        protected final WeakReference<DesignActivity> activityRef;
        protected BaseTask(DesignActivity activity) { activityRef = new WeakReference<>(activity); }
        protected DesignActivity getActivity() { return activityRef.get(); }
    }

    private static class BuildTask extends BaseTask implements BuildProgressReceiver {
        public static final String ACTION_CANCEL_BUILD = "com.besome.sketch.design.ACTION_CANCEL_BUILD";
        private static final String CHANNEL_ID = "build_notification_channel";
        private static final java.util.concurrent.Semaphore BUILD_LOCK = new java.util.concurrent.Semaphore(1, true);
        private static final java.util.concurrent.atomic.AtomicInteger NEXT_NOTIFICATION_ID = new java.util.concurrent.atomic.AtomicInteger(1000);
        private final Object terminalStateLock = new Object();
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();
        private final NotificationManager notificationManager;
        private final int notificationId = NEXT_NOTIFICATION_ID.incrementAndGet();
        private final String buildToken = Integer.toString(notificationId) + "-" + Long.toString(System.nanoTime());
        private final String buildScId;
        private final MaterialButton btnRun;
        private final MaterialButton btnOptions;
        private final LinearLayout progressContainer;
        private final TextView progressText;
        private final LinearProgressIndicator progressBar;
        public volatile boolean canceled;
        private volatile boolean isBuildFinished;
        private boolean isShowingNotification = false;
        private FloatingProgressWindow fProgress;
        private final TerminalBuildBridge.Listener terminalListener;
        private volatile boolean terminalResultReported;
        volatile boolean running;

        public BuildTask(DesignActivity activity) {
            this(activity, null);
        }

        public BuildTask(DesignActivity activity, TerminalBuildBridge.Listener terminalListener) {
            super(activity);
            buildScId = DesignActivity.sc_id;
            notificationManager = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
            btnRun = activity.btnRun;
            btnOptions = activity.btnOptions;
            progressContainer = activity.findViewById(R.id.progress_container);
            progressText = activity.findViewById(R.id.progress_text);
            progressBar = activity.findViewById(R.id.progress);
            fProgress = activity.fProgress;
            this.terminalListener = terminalListener;
        }

        private void reportTerminalSuccess(String apkPath) {
            if (terminalListener == null || terminalResultReported) return;
            terminalResultReported = true;
            terminalListener.onSuccess(apkPath);
        }

        private void reportTerminalFailure(String message) {
            if (terminalListener == null || terminalResultReported) return;
            terminalResultReported = true;
            terminalListener.onFailure(message);
        }

        private void reportTerminalCanceled() {
            if (terminalListener == null || terminalResultReported) return;
            terminalResultReported = true;
            terminalListener.onCanceled();
        }

        private boolean markFailureUnlessCanceled() {
            synchronized (terminalStateLock) {
                if (canceled || isBuildFinished) return false;
                isBuildFinished = true;
                return true;
            }
        }

        private boolean markSuccessUnlessCanceled() {
            synchronized (terminalStateLock) {
                if (canceled || isBuildFinished) return false;
                isBuildFinished = true;
                return true;
            }
        }

        public void execute() {
            running = true;
            onPreExecute();
            executorService.execute(this::doInBackground);
        }

        private void onPreExecute() {
            DesignActivity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                updateRunButton(true);
                activity.r.a("P1I10", true);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                maybeShowNotification();
            });
        }
        
                private void doInBackground() {
            DesignActivity activity = getActivity();
            boolean buildLockAcquired = false;
            try {
                if (activity == null) {
                    canceled = true;
                    return;
                }
                onProgress("Waiting for another build to finish...", 1);
                while (!canceled && !buildLockAcquired) {
                    buildLockAcquired = BUILD_LOCK.tryAcquire(250, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
                if (canceled) return;
                new CompileErrorSaver(buildScId).deleteSavedLogs();
                mod.sketchlibx.project.history.TimeMachineManager.takeSnapshot(buildScId);
                var sc_id = buildScId;
                CustomGradleBuildManager.prepareBuild(sc_id);
                if (canceled) return;
                var q = new yq(activity.getApplicationContext(), sc_id);
                activity.q = q;
                onProgress("Deleting temporary files...", 1);
                FileUtil.deleteFile(q.projectMyscPath);
                if (canceled) return;
                q.c(activity.getApplicationContext());
                if (canceled) return;
                q.a();
                q.a(activity.getApplicationContext(), wq.e("600"));
                if (yB.a(lC.b(sc_id), "custom_icon")) {
                    q.aa(wq.e() + File.separator + sc_id + File.separator + "mipmaps");
                    if (yB.a(lC.b(sc_id), "isIconAdaptive", false)) {
                        q.createLauncherIconXml("""
                                <?xml version="1.0" encoding="utf-8"?>
                                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android" >
                                <background android:drawable="@mipmap/ic_launcher_background"/>
                                <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
                                <monochrome android:drawable="@mipmap/ic_launcher_monochrome"/>
                                </adaptive-icon>""");
                    } else {
                        q.a(wq.e() + File.separator + sc_id + File.separator + "icon.png");
                    }
                }
                if (canceled) return;
                onProgress("Generating source code...", 2);
                kC kC = jC.d(sc_id);
                kC.b(q.resDirectoryPath + File.separator + "drawable-xhdpi");
                kC = jC.d(sc_id);
                kC.c(q.resDirectoryPath + File.separator + "raw");
                kC = jC.d(sc_id);
                kC.a(q.assetsPath + File.separator + "fonts");
                ProjectBuilder builder = new ProjectBuilder(this, activity.getApplicationContext(), q);
                var fileManager = jC.b(sc_id);
                var dataManager = jC.a(sc_id);
                var libraryManager = jC.c(sc_id);
                q.a(libraryManager, fileManager, dataManager);
                builder.buildBuiltInLibraryInformation();
                q.b(fileManager, dataManager, libraryManager, builder.getBuiltInLibraryManager());
                q.f();
                q.e();
                builder.maybeExtractAapt2();
                if (canceled) return;
                onProgress("Extracting built-in libraries...", 3);
                BuiltInLibraries.extractCompileAssets(this);
                if (canceled) return;
                onProgress("AAPT2 is running...", 8);
                builder.compileResources();
                if (canceled) return;
                onProgress("Generating view binding...", 11);
                builder.generateViewBinding();
                if (canceled) return;
                onProgress("Java is compiling...", 13);
                builder.compileJavaCode();
                if (canceled) return;
                onProgress("Compiling C/C++ Native Code...", 14);
                builder.compileNativeCode();
                if (canceled) return;
                StringfogHandler stringfogHandler = new StringfogHandler(sc_id);
                stringfogHandler.start(this, builder);
                if (canceled) return;
                ProguardHandler proguardHandler = new ProguardHandler(sc_id);
                proguardHandler.start(this, builder);
                if (canceled) return;
                onProgress(builder.getDxRunningText(), 17);
                boolean isMultiDexEnabled = new ProjectSettings(sc_id).getValue("multidex", "false").equals("true");
                if (isMultiDexEnabled) builder.setMultiDexEnabled(true);
                builder.createDexFilesFromClasses();
                if (canceled) return;
                onProgress("Merging DEX files...", 18);
                builder.getDexFilesReady();
                if (canceled) return;
                onProgress("Building APK...", 19);
                builder.buildApk();
                if (canceled) return;
                onProgress("Signing APK...", 20);
                builder.signDebugApk();
                if (canceled) return;
                if (!markSuccessUnlessCanceled()) return;
                if (terminalListener == null) {
                    try {
                        activity.installBuiltApk();
                    } catch (RuntimeException installError) {
                        activity.runOnUiThread(() -> SketchwareUtil.toastError("Build succeeded, but the APK installer could not be opened: "
                                + (installError.getMessage() == null ? installError.getClass().getSimpleName() : installError.getMessage())));
                    }
                }
                try {
                    neo.sketchware.plugin.PluginManager.notifyBuildSuccess(sc_id);
                } catch (RuntimeException notificationError) {
                    Log.w("DesignActivity$BuildTask", "Build succeeded, but a plugin could not be notified.", notificationError);
                }
                reportTerminalSuccess(q.finalToInstallApkPath);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                synchronized (terminalStateLock) { canceled = true; }
            } catch (MissingFileException e) {
                if (!markFailureUnlessCanceled()) return;
                String message = (e.isMissingDirectory() ? "A required directory is missing: " : "A required file is missing: ")
                        + e.getMissingFile().getAbsolutePath();
                reportTerminalFailure(message);
                DesignActivity currentActivity = getActivity();
                if (currentActivity != null) currentActivity.runOnUiThread(() -> {
                    boolean isMissingDirectory = e.isMissingDirectory();
                    MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(currentActivity);
                    if (isMissingDirectory) {
                        dialog.setTitle("Missing directory detected");
                        dialog.setMessage("A directory important for building is missing. "
                                + "Sketchware Neo can try creating " + e.getMissingFile().getAbsolutePath()
                                + " if you'd like to.");
                        dialog.setNeutralButton("Create", (v, which) -> {
                            v.dismiss();
                            if (!e.getMissingFile().mkdirs()) SketchwareUtil.toastError("Failed to create directory / directories!");
                        });
                    } else {
                        dialog.setTitle("Missing file detected");
                        dialog.setMessage("A file needed for building is missing. Put the correct file back to "
                                + e.getMissingFile().getAbsolutePath() + " and try building again.");
                    }
                    dialog.setPositiveButton("Dismiss", null);
                    dialog.show();
                });
            } catch (zy e) {
                if (!markFailureUnlessCanceled()) return;
                reportTerminalFailure(e.getMessage());
                DesignActivity currentActivity = getActivity();
                if (currentActivity != null) {
                    currentActivity.indicateCompileErrorOccurred(buildScId, e.getMessage());
                    neo.sketchware.plugin.PluginManager.notifyBuildError(buildScId, e.getMessage());
                }
            } catch (Throwable tr) {
                if (!markFailureUnlessCanceled()) return;
                LogUtil.e("DesignActivity$BuildTask", "Failed to build project", tr);
                String error = Log.getStackTraceString(tr);
                reportTerminalFailure(tr.getClass().getSimpleName() + ": " + tr.getMessage());
                DesignActivity currentActivity = getActivity();
                if (currentActivity != null) {
                    currentActivity.indicateCompileErrorOccurred(buildScId, error);
                    neo.sketchware.plugin.PluginManager.notifyBuildError(buildScId, error);
                }
            } finally {
                if (buildLockAcquired) BUILD_LOCK.release();
                boolean canceledAtEnd;
                boolean unfinishedAtEnd = false;
                synchronized (terminalStateLock) {
                    canceledAtEnd = canceled;
                    if (canceledAtEnd) isBuildFinished = true;
                    else if (!isBuildFinished) {
                        isBuildFinished = true;
                        unfinishedAtEnd = true;
                    }
                }
                if (canceledAtEnd) reportTerminalCanceled();
                else if (unfinishedAtEnd) reportTerminalFailure("The build stopped before it finished.");
                executorService.shutdown();
                DesignActivity currentActivity = getActivity();
                if (currentActivity != null) currentActivity.runOnUiThread(this::onPostExecute);
                else {
                    running = false;
                    if (notificationManager != null) notificationManager.cancel(notificationId);
                }
            }
        }
        
        @Override
        public void onProgress(String progress, int step) {
            int totalSteps = 20;
            DesignActivity activity = getActivity();
            if (activity == null) return;
            if (terminalListener != null && step >= 0 && !canceled) terminalListener.onProgress(progress);
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed()) return;
                if (canceled) {
                    progressBar.setIndeterminate(true);
                    progressText.setText("Cancellation requested. Finishing the active build step…");
                    return;
                }
                progressBar.setIndeterminate(step == -1);
                progressText.setText(progress);
                int progressInt = (step * 100) / totalSteps;
                progressBar.setProgress(progressInt, true);
                Log.d("DesignActivity$BuildTask", step + " / " + totalSteps);
                updateNotification(progress + " (" + step + " / " + totalSteps + ")");
            });
            if (!canceled && fProgress != null) fProgress.updateProgress(step);
        }

        private void onPostExecute() {
            if (fProgress != null) fProgress.stop();
            if (isShowingNotification && notificationManager != null) {
                notificationManager.cancel(notificationId);
                isShowingNotification = false;
            }
            DesignActivity activity = getActivity();
            if (activity == null) {
                running = false;
                return;
            }
            Runnable cleanup = () -> {
                try {
                    if (!activity.isDestroyed()) {
                        updateRunButton(false);
                        activity.updateBottomMenu();
                        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                } finally {
                    running = false;
                }
            };
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) cleanup.run();
            else activity.runOnUiThread(cleanup);
        }

        public void cancelBuild() {
            synchronized (terminalStateLock) {
                if (!running || canceled || isBuildFinished) return;
                canceled = true;
            }
            updateNotification("Cancellation requested; finishing the active build step…");
            DesignActivity activity = getActivity();
            if (activity != null) activity.runOnUiThread(() -> {
                if (!activity.isDestroyed()) {
                    btnRun.setEnabled(false);
                    btnRun.setText("Stopping…");
                    progressBar.setIndeterminate(true);
                    progressText.setText("Cancellation requested. Waiting for the active compiler step to finish…");
                }
            });
        }

        private void maybeShowNotification() {
            DesignActivity activity = getActivity();
            if (activity == null) return;
            if (!isShowingNotification) {
                createNotificationChannelIfNeeded();
                NotificationCompat.Builder builder = new NotificationCompat.Builder(activity, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_mtrl_code)
                        .setContentTitle("Building project")
                        .setContentText("Starting build...")
                        .setOngoing(true)
                        .setProgress(0, 0, true);
                if (!canceled) builder.addAction(R.drawable.ic_cancel_white_96dp, "Cancel build", getCancelPendingIntent());
                notificationManager.notify(notificationId, builder.build());
                isShowingNotification = true;
            }
        }

        private void updateNotification(String progress) {
            DesignActivity activity = getActivity();
            if (activity == null) return;
            NotificationCompat.Builder builder = new NotificationCompat.Builder(activity, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_mtrl_code)
                    .setContentTitle("Building project")
                    .setContentText(progress)
                    .setOngoing(true)
                    .setProgress(0, 0, true);
            if (!canceled) builder.addAction(R.drawable.ic_cancel_white_96dp, "Cancel build", getCancelPendingIntent());
            notificationManager.notify(notificationId, builder.build());
        }

        private PendingIntent getCancelPendingIntent() {
            DesignActivity activity = getActivity();
            if (activity == null) return null;
            Intent cancelIntent = new Intent(BuildTask.ACTION_CANCEL_BUILD);
            cancelIntent.putExtra("build_token", buildToken);
            return PendingIntent.getBroadcast(activity, notificationId, cancelIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        private void createNotificationChannelIfNeeded() {
            DesignActivity activity = getActivity();
            if (activity == null) return;
            CharSequence name = "Build Notifications";
            String description = "Notifications for build progress";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }

        private void updateRunButton(boolean isRunning) {
            var context = getActivity();
            if (context == null || context.isDestroyed()) return;
            btnRun.setEnabled(true);
            btnRun.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.getColor(context, isRunning ? R.attr.colorErrorContainer : R.attr.colorPrimary)));
            btnRun.setIcon(ContextCompat.getDrawable(context, isRunning ? R.drawable.ic_mtrl_stop : R.drawable.ic_mtrl_run));
            btnRun.setIconTint(ColorStateList.valueOf(ThemeUtils.getColor(context, isRunning ? R.attr.colorOnErrorContainer : R.attr.colorSurfaceContainerLowest)));
            btnRun.setTextColor(ColorStateList.valueOf(ThemeUtils.getColor(context, isRunning ? R.attr.colorOnErrorContainer : R.attr.colorSurfaceContainerLowest)));
            btnRun.setText(isRunning ? "Stop" : "Run");
            btnOptions.setEnabled(!isRunning);
            progressContainer.setVisibility(isRunning ? View.VISIBLE : View.GONE);
        }
    }

    private static class ProjectLoader extends BaseTask {
        private final Bundle savedInstanceState;
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public ProjectLoader(DesignActivity activity, Bundle savedInstanceState) {
            super(activity);
            this.savedInstanceState = savedInstanceState;
        }

        public void execute() {
            getActivity().k();
            executorService.execute(this::doInBackground);
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity != null) {
                activity.loadProject(savedInstanceState != null);
                activity.runOnUiThread(() -> {
                    activity.updateBottomMenu();
                    activity.refresh();
                    activity.h();
                    if (savedInstanceState == null) {
                        activity.checkForUnsavedProjectData();
                    }
                });
            }
        }
    }

    private static class DiscardChangesProjectCloser extends BaseTask {
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public DiscardChangesProjectCloser(DesignActivity activity) {
            super(activity);
        }

        public void execute() {
            getActivity().k();
            executorService.execute(this::doInBackground);
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity != null) {
                var sc_id = DesignActivity.sc_id;
                jC.d(sc_id).v();
                jC.d(sc_id).w();
                jC.d(sc_id).u();
                activity.runOnUiThread(() -> {
                    activity.h();
                    activity.finish();
                });
            }
        }
    }

    private static class ProjectSaver extends BaseTask {
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public ProjectSaver(DesignActivity activity) {
            super(activity);
        }

        public void execute() {
            getActivity().k();
            executorService.execute(this::doInBackground);
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity != null) {
                var sc_id = DesignActivity.sc_id;
                jC.d(sc_id).a();
                jC.b(sc_id).m();
                jC.a(sc_id).j();
                jC.d(sc_id).x();
                jC.c(sc_id).l();
                
                mod.sketchlibx.project.history.TimeMachineManager.takeSnapshot(sc_id);
                
                activity.runOnUiThread(() -> {
                    bB.a(activity.getApplicationContext(), Helper.getResString(R.string.common_message_complete_save), bB.TOAST_NORMAL).show();
                    activity.saveVersionCodeInformationToProject();
                    activity.h();
                    jC.d(sc_id).f();
                    jC.d(sc_id).g();
                    jC.d(sc_id).e();
                });
            }
        }
    }

    private static class SaveChangesProjectCloser extends BaseTask {
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public SaveChangesProjectCloser(DesignActivity activity) {
            super(activity);
        }

        public void execute() {
            getActivity().k();
            executorService.execute(this::doInBackground);
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity != null) {
                var sc_id = DesignActivity.sc_id;
                jC.d(sc_id).a();
                jC.b(sc_id).m();
                jC.a(sc_id).j();
                jC.d(sc_id).x();
                jC.c(sc_id).l();
                jC.d(sc_id).h();
                
                mod.sketchlibx.project.history.TimeMachineManager.takeSnapshot(sc_id);
                
                activity.runOnUiThread(() -> {
                    bB.a(activity.getApplicationContext(), Helper.getResString(R.string.common_message_complete_save), bB.TOAST_NORMAL).show();
                    activity.saveVersionCodeInformationToProject();
                    activity.h();
                    activity.finish();
                });
            }
        }
    }

    private static class UnsavedChangesSaver extends BaseTask {
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        public UnsavedChangesSaver(DesignActivity activity) {
            super(activity);
        }

        public void execute() {
            executorService.execute(this::doInBackground);
        }

        private void doInBackground() {
            DesignActivity activity = getActivity();
            if (activity != null) {
                eC ecInstance = jC.a(sc_id);
                synchronized (ecInstance) {
                    ecInstance.k();
                }
            }
        }
    }

    private class ViewPagerAdapter extends FragmentPagerAdapter {
        private final String[] labels;

        public ViewPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
            labels = new String[]{
                    Helper.getResString(R.string.design_tab_title_view),
                    Helper.getResString(R.string.design_tab_title_event),
                    Helper.getResString(R.string.design_tab_title_component),
                    "Terminal"};
        }

        @Override public int getCount() { return labels.length; }
        @Override public CharSequence getPageTitle(int position) { return labels[position]; }

        @Override
        @NonNull
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            if (position == 0) viewTabAdapter = (ViewEditorFragment) fragment;
            else if (position == 1) eventTabAdapter = (rs) fragment;
            else if (position == 2) componentTabAdapter = (br) fragment;
            return fragment;
        }

        @Override
        @NonNull
        public Fragment getItem(int position) {
            if (position == 0) return new ViewEditorFragment();
            else if (position == 1) return new rs();
            else if (position == 2) return new br();
            else return new TerminalFragment();
        }
    }
    
    public void jumpToFileAndTab(String searchFileName, int tabIndex) {
        ProjectFileBean fileBean = jC.b(sc_id).b(searchFileName);
        if (fileBean == null) {
            fileBean = jC.b(sc_id).a(searchFileName);
        }
        
        if (fileBean != null) {
            this.projectFile = fileBean;
            refreshFileSelector();
            viewPager.setCurrentItem(tabIndex);
            refresh();
            SketchwareUtil.toast("Jumped to " + searchFileName);
        } else {
            SketchwareUtil.toastError("Could not find file: " + searchFileName);
        }
    }
    
    public void handleSearchResult(mod.sketchlibx.search.SearchResult result) {
        ProjectFileBean fileBean = jC.b(sc_id).b(result.fileName);
        if (fileBean == null) {
            fileBean = jC.b(sc_id).a(result.fileName);
        }
        
        if (fileBean != null) {
            k(); 
            
            boolean isDifferentFile = (this.projectFile == null) || 
                (!this.projectFile.getJavaName().equals(fileBean.getJavaName()));

            this.projectFile = fileBean;
            refreshFileSelector();
            viewPager.setCurrentItem(result.tabIndex, false);
            refresh();
            
            int delayTime = isDifferentFile ? 850 : 350;

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    if (result.category.equals("View") && result.targetId != null) {
                        
                        if (result.title.contains("(Drawer")) {
                            if (!drawer.isDrawerOpen(androidx.core.view.GravityCompat.END)) {
                                drawer.openDrawer(androidx.core.view.GravityCompat.END);
                            }
                        }
                        
                        if (viewTabAdapter != null) {
                            viewTabAdapter.showHidePropertyView(true);
                        }
                        
                        com.besome.sketch.editor.view.ViewProperty viewProperty = findViewById(R.id.view_property);
                        if (viewProperty != null) {
                            viewProperty.setVisibility(View.VISIBLE);
                            viewProperty.a(result.targetId); 
                        }
                        
                    } else if (result.category.equals("Logic Block") && result.targetId != null && result.eventName != null) {
                        Intent intent = new Intent(DesignActivity.this, com.besome.sketch.editor.LogicEditorActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        intent.putExtra("sc_id", sc_id);
                        intent.putExtra("id", result.targetId);
                        intent.putExtra("event", result.eventName);
                        intent.putExtra("project_file", projectFile);
                        intent.putExtra("event_text", result.eventName);
                        startActivity(intent);
                    } else if (result.category.equals("Component") || result.category.equals("Variable") || result.category.equals("List")) {
                        pro.sketchware.utility.SketchwareUtil.toast("Switched to " + result.category + " tab");
                    }
                } catch (Exception e) {
                    Log.e("DeepLink", "Failed to resolve deep link", e);
                } finally {
                    h(); 
                }
            }, delayTime);

        } else {
            pro.sketchware.utility.SketchwareUtil.toastError("Could not find file: " + result.fileName);
        }
    }
    
    public void reloadProjectAfterTimeTravel() {
        k();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                jC.a(); 
                cC.a(); 
                bC.a(); 
                
                Intent intent = getIntent();
                finish(); 
                startActivity(intent); 
                SketchwareUtil.toast("Reverted successfully");
            } catch (Exception e) {
                h();
                SketchwareUtil.toastError("Failed to restart design interface: " + e.getMessage());
            }
        }, 1000); 
    }
}
