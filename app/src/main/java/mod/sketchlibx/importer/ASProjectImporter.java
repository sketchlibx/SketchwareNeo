package mod.sketchlibx.importer;

import android.app.Activity;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import a.a.a.lC;
import a.a.a.nB;
import a.a.a.oB;
import a.a.a.wq;
import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import pro.sketchware.activities.main.fragments.projects.ProjectsFragment;
import pro.sketchware.databinding.ProgressMsgBoxBinding;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class ASProjectImporter extends AsyncTask<Void, String, Boolean> {
	
	private static final String TAG = "ASProjectImporter";
	private final WeakReference<Activity> activityRef;
	private final Uri zipUri;
	private final ProjectsFragment fragment;
	private AlertDialog loadingDialog;
	private ProgressMsgBoxBinding binding;
	private String errorMessage = "";
	
	// Project Metadata
	private String newScId;
	private String pkgName = "com.imported.project";
	private String appName = "Imported App";
	private String versionName = "1.0";
	private String versionCode = "1";
	private boolean hasKotlin = false;
	private boolean useMaterial3 = false;
	
	private final String FAB_JSON = "{\"adSize\":\"\",\"adUnitId\":\"\",\"alpha\":1.0,\"checked\":0,\"choiceMode\":0,\"clickable\":1,\"convert\":\"\",\"customView\":\"\",\"dividerHeight\":1,\"enabled\":1,\"firstDayOfWeek\":1,\"id\":\"_fab\",\"image\":{\"rotate\":0,\"scaleType\":\"CENTER\"},\"indeterminate\":\"false\",\"index\":0,\"inject\":\"\",\"layout\":{\"backgroundColor\":16777215,\"borderColor\":-16740915,\"gravity\":0,\"height\":-2,\"layoutGravity\":85,\"marginBottom\":16,\"marginLeft\":16,\"marginRight\":16,\"marginTop\":16,\"orientation\":-1,\"paddingBottom\":0,\"paddingLeft\":0,\"paddingRight\":0,\"paddingTop\":0,\"weight\":0,\"weightSum\":0,\"width\":-2},\"max\":100,\"parentAttributes\":{},\"parentType\":-1,\"preIndex\":0,\"preParentType\":0,\"progress\":0,\"progressStyle\":\"?android:progressBarStyle\",\"scaleX\":1.0,\"scaleY\":1.0,\"spinnerMode\":1,\"text\":{\"hint\":\"\",\"hintColor\":16777215,\"imeOption\":0,\"inputType\":1,\"line\":0,\"singleLine\":0,\"text\":\"\",\"textColor\":16777215,\"textFont\":\"default_font\",\"textSize\":12,\"textType\":0},\"translationX\":0.0,\"translationY\":0.0,\"type\":16}";
	
	public ASProjectImporter(Activity activity, Uri zipUri, ProjectsFragment fragment) {
		this.activityRef = new WeakReference<>(activity);
		this.zipUri = zipUri;
		this.fragment = fragment;
	}
	
	public static void showPicker(Activity activity, ProjectsFragment fragment) {
		FilePickerOptions options = new FilePickerOptions();
		options.setExtensions(new String[]{"zip"});
		options.setTitle("Select AS Project (.zip)");
		
		FilePickerCallback callback = new FilePickerCallback() {
			@Override
			public void onFileSelected(File file) {
				new ASProjectImporter(activity, Uri.fromFile(file), fragment).execute();
			}
		};
		
		new FilePickerDialogFragment(options, callback).show(fragment.getChildFragmentManager(), "filePicker");
	}
	
	@Override
	protected void onPreExecute() {
		Activity act = activityRef.get();
		if (act != null) {
			binding = ProgressMsgBoxBinding.inflate(LayoutInflater.from(act));
			binding.tvProgress.setText("Initializing Importer...");
			loadingDialog = new MaterialAlertDialogBuilder(act)
			.setTitle("Importing Android Studio Project")
			.setCancelable(false)
			.setView(binding.getRoot())
			.create();
			loadingDialog.show();
		}
	}
	
	@Override
	protected void onProgressUpdate(String... values) {
		if (binding != null && values.length > 0) {
			binding.tvProgress.setText(values[0]);
		}
	}
	
	@Override
	protected Boolean doInBackground(Void... voids) {
		Activity act = activityRef.get();
		if (act == null) return false;
		
		String cacheDir = wq.a() + File.separator + "cache" + File.separator + "as_import_tmp";
		FileUtil.deleteFile(cacheDir); 
		FileUtil.makeDir(cacheDir);
		
		try {
			publishProgress("Extracting ZIP...");
			extractZipFromUri(act, zipUri, cacheDir);
			
			File projectRoot = findProjectRoot(new File(cacheDir));
			if (projectRoot == null) {
				errorMessage = "Invalid AS Project: 'src/main' folder hierarchy not found.";
				return false;
			}
			
			File appModule = findAppModule(projectRoot);
			File asSrcMain = new File(appModule, "src/main");
			
			newScId = lC.b(); 
			String dataPath = wq.b(newScId);
			String filesPath = dataPath + File.separator + "files";
			
			publishProgress("Setting up directories...");
			setupSketchwareDirectories(dataPath, filesPath);
			
			publishProgress("Parsing build.gradle...");
			parseGradleFiles(appModule);
			
			publishProgress("Scanning AndroidManifest.xml...");
			String iconResName = parseAndroidManifest(asSrcMain, dataPath);
			
			publishProgress("Scanning Java/Kotlin Files...");
			HashMap<String, String> layoutToSwActivityName = new HashMap<>(); 
			Set<String> customViewLayouts = new HashSet<>();
			ArrayList<File> allCodeFiles = new ArrayList<>();
			scanAndCategorizeCodeFiles(new File(asSrcMain, "java"), layoutToSwActivityName, customViewLayouts, allCodeFiles);
			scanAndCategorizeCodeFiles(new File(asSrcMain, "kotlin"), layoutToSwActivityName, customViewLayouts, allCodeFiles);
			
			publishProgress("Generating Project Settings...");
			generateProjectSettings(dataPath);
			
			publishProgress("Parsing XML Layouts...");
			StringBuilder fileStr = new StringBuilder("@activity\n");
			StringBuilder viewStr = new StringBuilder();
			parseLayouts(new File(asSrcMain, "res/layout"), layoutToSwActivityName, customViewLayouts, fileStr, viewStr);
			
			publishProgress("Importing Source Files...");
			migrateCodeFiles(allCodeFiles, filesPath + File.separator + "java");
			
			publishProgress("Importing Resources & Assets...");
			StringBuilder resourceStr = new StringBuilder("@images\n");
			boolean hasCustomIcon = processResources(new File(asSrcMain, "res"), filesPath, newScId, iconResName, resourceStr);
			processAssets(new File(asSrcMain, "assets"), filesPath);
			
			publishProgress("Importing Firebase & Libraries...");
			String libraryStr = processFirebaseAndLibraries(appModule);
			
			publishProgress("Generating Sketchware Project...");
			createProjectInfo(hasCustomIcon);
			
			oB fileEncryptor = new oB();
			fileEncryptor.a(dataPath + File.separator + "view", fileEncryptor.d(viewStr.toString()));
			fileEncryptor.a(dataPath + File.separator + "file", fileEncryptor.d(fileStr.toString()));
			fileEncryptor.a(dataPath + File.separator + "logic", fileEncryptor.d(""));
			fileEncryptor.a(dataPath + File.separator + "resource", fileEncryptor.d(resourceStr.toString()));
			fileEncryptor.a(dataPath + File.separator + "library", fileEncryptor.d(libraryStr));
			
			publishProgress("Finishing Import...");
			FileUtil.deleteFile(cacheDir);
			
			return true;
			
		} catch (Exception e) {
			errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown Error";
			Log.e(TAG, "Import failed", e);
			return false;
		}
	}
	
	@Override
	protected void onPostExecute(Boolean success) {
		if (loadingDialog != null && loadingDialog.isShowing()) {
			loadingDialog.dismiss();
		}
		
		if (success) {
			SketchwareUtil.toast("AS Project Imported Successfully!");
			if (fragment != null) fragment.refreshProjectsList();
		} else {
			SketchwareUtil.toastError("Import Failed: " + errorMessage, Toast.LENGTH_LONG);
		}
	}
	
	private void setupSketchwareDirectories(String dataPath, String filesPath) {
		FileUtil.makeDir(filesPath + File.separator + "java");
		FileUtil.makeDir(filesPath + File.separator + "resource");
		FileUtil.makeDir(filesPath + File.separator + "assets");
		FileUtil.makeDir(filesPath + File.separator + "app-icon");
		FileUtil.makeDir(dataPath + File.separator + "custom_java");
	}
	
	private void parseGradleFiles(File appModule) {
		File gradleFile = new File(appModule, "build.gradle");
		if (!gradleFile.exists()) gradleFile = new File(appModule, "build.gradle.kts");
		
		if (gradleFile.exists()) {
			String content = FileUtil.readFile(gradleFile.getAbsolutePath());
			
			pkgName = extractRegex(content, "applicationId\\s*=?\\s*[\"']([^\"']+)[\"']", 1, pkgName);
			versionCode = extractRegex(content, "versionCode\\s*=?\\s*(\\d+)", 1, "1");
			versionName = extractRegex(content, "versionName\\s*=?\\s*[\"']([^\"']+)[\"']", 1, "1.0");
			
			if (content.contains("material3") || content.contains("androidx.compose.material3")) {
				useMaterial3 = true;
			}
		}
	}
	
	private String parseAndroidManifest(File asSrcMain, String dataPath) {
		String iconResName = "ic_launcher";
		File manifestFile = new File(asSrcMain, "AndroidManifest.xml");
		if (manifestFile.exists()) {
			String manifestContent = FileUtil.readFile(manifestFile.getAbsolutePath());
			FileUtil.writeFile(dataPath + File.separator + "custom_manifest.xml", manifestContent);
			
			pkgName = extractRegex(manifestContent, "package=\"([^\"]+)\"", 1, pkgName);
			iconResName = extractRegex(manifestContent, "android:icon=\"@(mipmap|drawable)/([^\"]+)\"", 2, iconResName);
			
			File stringsFile = new File(asSrcMain, "res/values/strings.xml");
			if (stringsFile.exists()) {
				String strContent = FileUtil.readFile(stringsFile.getAbsolutePath());
				String labelRes = extractRegex(manifestContent, "android:label=\"@string/([^\"]+)\"", 1, "");
				if (!labelRes.isEmpty()) {
					appName = extractRegex(strContent, "<string name=\"" + labelRes + "\">([^<]+)</string>", 1, appName);
				}
			} else {
				appName = extractRegex(manifestContent, "android:label=\"([^\"]+)\"", 1, appName);
			}
		}
		return iconResName;
	}
	
	private void scanAndCategorizeCodeFiles(File dir, HashMap<String, String> layoutToActivity, Set<String> customViews, ArrayList<File> allCodeFiles) {
		if (dir == null || !dir.exists()) return;
		File[] files = dir.listFiles();
		if (files == null) return;
		
		for (File f : files) {
			if (f.isDirectory()) {
				scanAndCategorizeCodeFiles(f, layoutToActivity, customViews, allCodeFiles);
			} else if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")) {
				allCodeFiles.add(f);
				if (f.getName().endsWith(".kt")) hasKotlin = true;
				
				String content = FileUtil.readFile(f.getAbsolutePath());
				
				// Identify Activities
				boolean isActivity = content.matches(".*class\\s+\\w+\\s*(extends|:)\\s*(Activity|AppCompatActivity|FragmentActivity|ComponentActivity|BaseActivity).*");
				// Identify Custom Views / Fragments / Adapters
				boolean isCustomViewLogic = content.matches(".*class\\s+\\w+\\s*(extends|:)\\s*(Fragment|DialogFragment|BottomSheetDialogFragment|RecyclerView\\.Adapter|BaseAdapter|LinearLayout|RelativeLayout|FrameLayout).*");
				
				Matcher m = Pattern.compile("R\\.layout\\.([a-zA-Z0-9_]+)").matcher(content);
				while (m.find()) {
					String layoutName = m.group(1); 
					if (isActivity) {
						String swActivityName = f.getName().replace(".java", "").replace(".kt", "").toLowerCase(); 
						if (swActivityName.endsWith("activity")) {
							swActivityName = swActivityName.substring(0, swActivityName.length() - 8);
						}
						layoutToActivity.put(layoutName, swActivityName);
					} else if (isCustomViewLogic) {
						customViews.add(layoutName);
					}
				}
			}
		}
	}
	
	private void parseLayouts(File layoutDir, HashMap<String, String> layoutToActivity, Set<String> customViews, StringBuilder fileStr, StringBuilder viewStr) {
		Gson gson = new Gson();
		ArrayList<String> unmappedAsCustom = new ArrayList<>();
		
		if (layoutDir.exists() && layoutDir.isDirectory()) {
			File[] layouts = layoutDir.listFiles();
			if (layouts != null) {
				for (File xml : layouts) {
					if (xml.getName().endsWith(".xml")) {
						String rawName = xml.getName().replace(".xml", "");
						String mappedActName = layoutToActivity.get(rawName);
						boolean isActivity = mappedActName != null;
						
						try {
							ViewBeanParser parser = new ViewBeanParser(xml);
							parser.setSkipRoot(true);
							ArrayList<ViewBean> parsedBeans = parser.parse();
							
							if (isActivity) {
								viewStr.append("@").append(mappedActName).append(".xml\n");
								for (ViewBean bean : parsedBeans) viewStr.append(gson.toJson(bean)).append("\n");
								viewStr.append("@").append(mappedActName).append(".xml_fab\n").append(FAB_JSON).append("\n");
								fileStr.append("{\"fileName\":\"").append(mappedActName).append("\",\"fileType\":0,\"keyboardSetting\":0,\"options\":0,\"orientation\":0,\"theme\":-1}\n");
							} else {
								customViews.add(rawName);
								unmappedAsCustom.add(rawName);
								viewStr.append("@").append(rawName).append(".xml\n");
								for (ViewBean bean : parsedBeans) viewStr.append(gson.toJson(bean)).append("\n");
							}
						} catch (Exception e) {
							Log.e(TAG, "Failed to parse layout: " + xml.getName(), e);
						}
					}
				}
			}
		}
		
		if (!fileStr.toString().contains("\"fileName\":\"main\"")) {
			fileStr.append("{\"fileName\":\"main\",\"fileType\":0,\"keyboardSetting\":0,\"options\":0,\"orientation\":0,\"theme\":-1}\n");
		}
		
		fileStr.append("@customview\n");
		for (String cv : customViews) {
			fileStr.append("{\"fileName\":\"").append(cv).append("\",\"fileType\":1,\"keyboardSetting\":0,\"options\":0,\"orientation\":0,\"theme\":-1}\n");
		}
	}
	
	private void migrateCodeFiles(ArrayList<File> codeFiles, String targetJavaPath) {
		for (File codeFile : codeFiles) {
			String content = FileUtil.readFile(codeFile.getAbsolutePath());
			String pName = extractRegex(content, "package\\s+([a-zA-Z0-9_.]+);?", 1, "");
			
			String destFolder = targetJavaPath;
			if (!pName.isEmpty()) destFolder = targetJavaPath + File.separator + pName.replace(".", File.separator);
			
			FileUtil.makeDir(destFolder);
			FileUtil.copyFile(codeFile.getAbsolutePath(), destFolder + File.separator + codeFile.getName());
		}
	}
	
	private boolean processResources(File resDir, String filesPath, String scId, String iconResName, StringBuilder resourceStr) {
		boolean hasCustomIcon = false;
		if (!resDir.exists()) return false;
		
		String swImagesPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/.sketchware/resources/images/" + scId;
		FileUtil.makeDir(swImagesPath);
		
		for (File dir : resDir.listFiles()) {
			String dName = dir.getName();
			if (dName.startsWith("drawable") || dName.startsWith("mipmap")) {
				File[] resources = dir.listFiles();
				if (resources != null) {
					for (File resFile : resources) {
						String fName = resFile.getName();
						if (fName.endsWith(".png") || fName.endsWith(".jpg") || fName.endsWith(".jpeg") || fName.endsWith(".webp")) {
							if (fName.startsWith(iconResName)) {
								FileUtil.copyFile(resFile.getAbsolutePath(), filesPath + File.separator + "app-icon" + File.separator + "icon.png");
								hasCustomIcon = true;
							} else {
								File targetFile = new File(swImagesPath, fName);
								FileUtil.copyFile(resFile.getAbsolutePath(), targetFile.getAbsolutePath());
								String cleanName = fName.substring(0, fName.lastIndexOf("."));
								resourceStr.append("{\"resFullName\":\"").append(fName).append("\",\"resName\":\"").append(cleanName).append("\",\"resType\":1}\n");
							}
						} else if (fName.endsWith(".xml")) {
							copyResourceToTarget(resFile, filesPath, dName);
						}
					}
				}
			} else if (dName.startsWith("values") || dName.startsWith("font") || dName.startsWith("xml") || dName.startsWith("anim") || dName.startsWith("raw") || dName.startsWith("color") || dName.startsWith("menu")) {
				File[] resources = dir.listFiles();
				if (resources != null) {
					for (File resFile : resources) copyResourceToTarget(resFile, filesPath, dName);
				}
			}
		}
		resourceStr.append("@sounds\n@fonts\n");
		return hasCustomIcon;
	}
	
	private void copyResourceToTarget(File resFile, String filesPath, String dirName) {
		File targetDir = new File(filesPath + File.separator + "resource" + File.separator + dirName);
		FileUtil.makeDir(targetDir.getAbsolutePath());
		FileUtil.copyFile(resFile.getAbsolutePath(), targetDir.getAbsolutePath() + File.separator + resFile.getName());
	}
	
	private void processAssets(File source, String filesPath) {
		File target = new File(filesPath + File.separator + "assets");
		copyFolder(source, target);
		
	}
	
	private void copyFolder(File source, File target) {
		try {
			if (source.isDirectory()) {
				
				if (!target.exists()) {
					target.mkdirs();
				}
				
				File[] files = source.listFiles();
				if (files != null) {
					for (File file : files) {
						copyFolder(
						file,
						new File(target, file.getName())
						);
					}
				}
				
			} else {
				FileUtil.copyFile(
				source.getAbsolutePath(),
				target.getAbsolutePath()
				);
			}
		} catch (Exception e) {
			Log.e(TAG, "copyFolder error", e);
		}
	}
	
	private String processFirebaseAndLibraries(File appModule) {
		String fbDbUrl = "", fbAppId = "", fbApiKey = "", fbProjectId = "", storageBucket = "";
		boolean useFb = false;
		
		File googleServicesFile = new File(appModule, "google-services.json");
		if (googleServicesFile.exists()) {
			useFb = true;
			try {
				JSONObject gsJson = new JSONObject(FileUtil.readFile(googleServicesFile.getAbsolutePath()));
				JSONObject projectInfo = gsJson.getJSONObject("project_info");
				fbProjectId = projectInfo.optString("project_id", "");
				fbDbUrl = projectInfo.optString("firebase_url", "");
				storageBucket = projectInfo.optString("storage_bucket", fbProjectId + ".appspot.com");
				
				JSONArray clients = gsJson.getJSONArray("client");
				if (clients.length() > 0) {
					JSONObject client0 = clients.getJSONObject(0);
					fbAppId = client0.getJSONObject("client_info").optString("mobilesdk_app_id", "");
					JSONArray apiKeys = client0.getJSONArray("api_key");
					if (apiKeys.length() > 0) {
						fbApiKey = apiKeys.getJSONObject(0).optString("current_key", "");
					}
				}
			} catch (Exception e) {
				Log.e(TAG, "Failed parsing google-services.json", e);
			}
		}
		
		String fbData = "{\"adUnits\":[],\"appId\":\"\",\"configurations\":{},\"data\":\"" + fbDbUrl.replace("https://", "") + "\",\"libType\":0,\"reserved1\":\"" + fbAppId + "\",\"reserved2\":\"" + fbApiKey + "\",\"reserved3\":\"" + storageBucket + "\",\"testDevices\":[],\"useYn\":\"" + (useFb ? "Y" : "N") + "\"}";
		
		return "@firebaseDB\n" + fbData + "\n" +
		"@compat\n{\"adUnits\":[],\"appId\":\"\",\"configurations\":{\"material3\":" + useMaterial3 + ",\"dynamic_colors\":true,\"theme\":\"DayNight\"},\"data\":\"\",\"libType\":1,\"reserved1\":\"\",\"reserved2\":\"\",\"reserved3\":\"\",\"testDevices\":[],\"useYn\":\"Y\"}\n" +
		"@admob\n{\"adUnits\":[],\"appId\":\"\",\"configurations\":{},\"data\":\"\",\"libType\":2,\"reserved1\":\"\",\"reserved2\":\"\",\"reserved3\":\"\",\"testDevices\":[],\"useYn\":\"N\"}\n" +
		"@googleMap\n{\"adUnits\":[],\"appId\":\"\",\"configurations\":{},\"data\":\"\",\"libType\":3,\"reserved1\":\"\",\"reserved2\":\"\",\"reserved3\":\"\",\"testDevices\":[],\"useYn\":\"N\"}\n";
	}
	
	private void createProjectInfo(boolean hasCustomIcon) {
		HashMap<String, Object> projMap = new HashMap<>();
		projMap.put("sc_id", newScId);
		projMap.put("my_ws_name", appName.replaceAll("[^a-zA-Z0-9 ]", "").trim());
		projMap.put("my_app_name", appName);
		projMap.put("my_sc_pkg_name", pkgName);
		projMap.put("sc_ver_code", versionCode);
		projMap.put("sc_ver_name", versionName);
		projMap.put("color_primary", -10455380);
		projMap.put("color_primary_dark", -10455380);
		projMap.put("color_accent", -10455380);
		projMap.put("color_control_highlight", -2497793);
		projMap.put("color_control_normal", -10455380);
		projMap.put("my_sc_reg_dt", new nB().a("yyyyMMddHHmmss"));
		projMap.put("sketchware_ver", 158);
		projMap.put("isIconAdaptive", false);
		projMap.put("custom_icon", hasCustomIcon);
		lC.a(newScId, projMap);
	}
	
	private void generateProjectSettings(String dataPath) {
		try {
			JSONObject settings = new JSONObject();
			settings.put("enable_custom_manifest", "true");
			settings.put("enable_viewbinding", "true");
			settings.put("multidex", "true"); // Always safe to enable for AS projects
			if (hasKotlin) settings.put("java_to_kotlin", "true");
			FileUtil.writeFile(dataPath + File.separator + "project_settings", settings.toString());
		} catch (Exception ignored) {}
	}
	
	private String extractRegex(String content, String regex, int group, String fallback) {
		Matcher m = Pattern.compile(regex).matcher(content);
		if (m.find()) return m.group(group);
		return fallback;
	}
	
	private File findProjectRoot(File root) {
		if (new File(root, "settings.gradle").exists() || new File(root, "settings.gradle.kts").exists()) return root;
		File[] children = root.listFiles();
		if (children != null) {
			for (File child : children) {
				if (child.isDirectory()) {
					File found = findProjectRoot(child);
					if (found != null) return found;
				}
			}
		}
		return findAppModule(root) != null ? root : null; 
	}
	
	private File findAppModule(File root) {
		if (new File(root, "src/main").exists()) return root;
		File[] children = root.listFiles();
		if (children != null) {
			for (File child : children) {
				if (child.isDirectory()) {
					File found = findAppModule(child);
					if (found != null) return found;
				}
			}
		}
		return null;
	}
	
	private void extractZipFromUri(Activity context, Uri uri, String destFolder) throws IOException {
		try (InputStream is = context.getContentResolver().openInputStream(uri);
		ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
			ZipEntry ze;
			byte[] buffer = new byte[8192];
			while ((ze = zis.getNextEntry()) != null) {
				File file = new File(destFolder, ze.getName());
				File dir = ze.isDirectory() ? file : file.getParentFile();
				if (!dir.exists() && !dir.mkdirs()) {
					Log.e(TAG, "Failed to create directory: " + dir.getAbsolutePath());
				}
				if (ze.isDirectory()) continue;
				try (FileOutputStream fout = new FileOutputStream(file);
				BufferedOutputStream bout = new BufferedOutputStream(fout, buffer.length)) {
					int count;
					while ((count = zis.read(buffer, 0, buffer.length)) != -1) {
						bout.write(buffer, 0, count);
					}
				}
				zis.closeEntry();
			}
		}
	}
}
