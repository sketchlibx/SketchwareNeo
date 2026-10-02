package neo.sketchware.ai;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pro.sketchware.R;

/** Add/edit an AI Agent while preserving the original AiModelConfig persistence contract. */
public class AiModelEditActivity extends BaseAppCompatActivity {
    public static final String EXTRA_CONFIG = "config";

    private TextInputLayout layoutProvider, layoutDisplayName, layoutModelName, layoutApiKey,
            layoutCustomEndpoint, layoutMaxTokens;
    private MaterialAutoCompleteTextView dropdownProvider;
    private TextInputEditText editDisplayName, editModelName, editApiKey, editCustomEndpoint,
            editMaxTokens, editSystemPrompt;
    private TextView textTemperatureValue, textThreadsValue, textEndpointHint;
    private Slider sliderTemperature, sliderThreads;
    private View layoutThreads;
    private CheckBox checkChat, checkBlocks, checkLogic, checkLayouts, checkCustomBlocks, checkErrorFix;
    private MaterialButton buttonTestConnection, buttonSave;
    private AiModelConfig existingConfig;
    private List<String> providerIds;
    private Map<String, AiProvider> providers;
    private int cpuCores;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_model_edit);
        existingConfig = getIntent().getSerializableExtra(EXTRA_CONFIG) instanceof AiModelConfig
                ? (AiModelConfig) getIntent().getSerializableExtra(EXTRA_CONFIG) : null;
        bindViews();

        boolean edit = existingConfig != null;
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setTitle(edit ? "Edit AI Agent" : "Add AI Agent");
        toolbar.setNavigationOnClickListener(v -> finish());

        cpuCores = Math.max(1, Runtime.getRuntime().availableProcessors());
        sliderThreads.setValueTo(cpuCores);
        providers = AiProviderRegistry.getAll();
        providerIds = new ArrayList<>(providers.keySet());
        List<String> names = new ArrayList<>();
        for (String id : providerIds) names.add(providers.get(id).getProviderName());
        dropdownProvider.setSimpleItems(names.toArray(new String[0]));
        dropdownProvider.setOnItemClickListener((p, v, position, id) -> applyProviderCapabilities(providerIds.get(position), true));

        sliderTemperature.addOnChangeListener((s, value, fromUser) ->
                textTemperatureValue.setText(String.format(Locale.getDefault(), "%.1f", value)));
        sliderThreads.addOnChangeListener((s, value, fromUser) -> textThreadsValue.setText(String.valueOf((int) value)));

        int initial = 0;
        if (edit) {
            editDisplayName.setText(existingConfig.displayName);
            editModelName.setText(existingConfig.modelName);
            editApiKey.setText(existingConfig.apiKey);
            editCustomEndpoint.setText(existingConfig.customEndpoint);
            editMaxTokens.setText(String.valueOf(existingConfig.maxTokens));
            editSystemPrompt.setText(existingConfig.systemPrompt);
            checkChat.setChecked(existingConfig.enableChat);
            checkBlocks.setChecked(existingConfig.enableBlocks);
            checkLogic.setChecked(existingConfig.enableLogic);
            checkLayouts.setChecked(existingConfig.enableLayouts);
            checkCustomBlocks.setChecked(existingConfig.enableCustomBlocks);
            checkErrorFix.setChecked(existingConfig.enableErrorFix);
            int found = providerIds.indexOf(existingConfig.providerId);
            if (found >= 0) initial = found;
        } else {
            editMaxTokens.setText("4096");
        }

        if (!providerIds.isEmpty()) {
            dropdownProvider.setText(names.get(initial), false);
            applyProviderCapabilities(providerIds.get(initial), false);
        }
        sliderTemperature.setValue(clamp((float) (edit ? existingConfig.temperature : 0.7), 0f, 2f));
        textTemperatureValue.setText(String.format(Locale.getDefault(), "%.1f", sliderTemperature.getValue()));
        int initialThreads = edit && existingConfig.threads > 0 ? existingConfig.threads : Math.min(4, cpuCores);
        sliderThreads.setValue(clamp(initialThreads, 1, cpuCores));
        textThreadsValue.setText(String.valueOf((int) sliderThreads.getValue()));
        buttonSave.setOnClickListener(v -> save());
        buttonTestConnection.setOnClickListener(v -> testConnection());
        MaterialButton modeLocal=findViewById(R.id.buttonModeLocal); MaterialButton modeCloud=findViewById(R.id.buttonModeCloud);
        modeLocal.setOnClickListener(v->selectProviderType(true));
        modeCloud.setOnClickListener(v->selectProviderType(false));
    }

    private void bindViews() {
        layoutProvider=findViewById(R.id.layoutProvider); dropdownProvider=findViewById(R.id.dropdownProvider);
        layoutDisplayName=findViewById(R.id.layoutDisplayName); layoutModelName=findViewById(R.id.layoutModelName);
        layoutApiKey=findViewById(R.id.layoutApiKey); layoutCustomEndpoint=findViewById(R.id.layoutCustomEndpoint);
        layoutMaxTokens=findViewById(R.id.layoutMaxTokens); textEndpointHint=findViewById(R.id.textEndpointHint);
        editDisplayName=findViewById(R.id.editDisplayName); editModelName=findViewById(R.id.editModelName);
        editApiKey=findViewById(R.id.editApiKey); editCustomEndpoint=findViewById(R.id.editCustomEndpoint);
        editMaxTokens=findViewById(R.id.editMaxTokens); editSystemPrompt=findViewById(R.id.editSystemPrompt);
        sliderTemperature=findViewById(R.id.sliderTemperature); textTemperatureValue=findViewById(R.id.textTemperatureValue);
        layoutThreads=findViewById(R.id.layoutThreads); sliderThreads=findViewById(R.id.sliderThreads); textThreadsValue=findViewById(R.id.textThreadsValue);
        checkChat=findViewById(R.id.checkChat); checkBlocks=findViewById(R.id.checkBlocks); checkLogic=findViewById(R.id.checkLogic);
        checkLayouts=findViewById(R.id.checkLayouts); checkCustomBlocks=findViewById(R.id.checkCustomBlocks); checkErrorFix=findViewById(R.id.checkErrorFix);
        buttonTestConnection=findViewById(R.id.buttonTestConnection); buttonSave=findViewById(R.id.buttonSave);
    }

    private void applyProviderCapabilities(String providerId, boolean userSelected) {
        AiProvider provider=providers.get(providerId); if(provider==null)return;
        boolean endpoint=provider.requiresCustomEndpoint();
        layoutCustomEndpoint.setVisibility(endpoint?View.VISIBLE:View.GONE);
        textEndpointHint.setVisibility(provider.isLocal()?View.VISIBLE:View.GONE);
        layoutApiKey.setVisibility(provider.requiresApiKey()?View.VISIBLE:View.GONE);
        sliderTemperature.setEnabled(provider.supportsTemperature());
        layoutThreads.setVisibility(provider.supportsThreads()?View.VISIBLE:View.GONE);
        if(endpoint && TextUtils.isEmpty(String.valueOf(editCustomEndpoint.getText()))) {
            String def=provider.getDefaultEndpoint(); if(!TextUtils.isEmpty(def)){editCustomEndpoint.setText(def);editCustomEndpoint.setSelection(def.length());}
        }
        if(provider.isLocal() && userSelected && TextUtils.isEmpty(String.valueOf(editDisplayName.getText()))) editDisplayName.setText("Local AI");
    }

    private void selectProviderType(boolean local){
        for(int i=0;i<providerIds.size();i++){AiProvider p=providers.get(providerIds.get(i));if(p!=null&&p.isLocal()==local){dropdownProvider.setText(p.getProviderName(),false);applyProviderCapabilities(providerIds.get(i),true);return;}}
        Toast.makeText(this, local ? "Local provider is unavailable" : "Cloud providers are unavailable", Toast.LENGTH_SHORT).show();
    }

    private boolean validateFields(boolean allowBlankName) {
        layoutProvider.setError(null); layoutDisplayName.setError(null); layoutModelName.setError(null);
        layoutApiKey.setError(null); layoutCustomEndpoint.setError(null); layoutMaxTokens.setError(null);
        String providerName=String.valueOf(dropdownProvider.getText()).trim();
        int index=-1; for(int i=0;i<providerIds.size();i++){AiProvider p=providers.get(providerIds.get(i));if(p!=null&&p.getProviderName().equals(providerName)){index=i;break;}}
        if(index<0){layoutProvider.setError("Choose a provider");return false;}
        String name=String.valueOf(editDisplayName.getText()).trim(); String model=String.valueOf(editModelName.getText()).trim();
        String key=String.valueOf(editApiKey.getText()).trim(); String endpoint=String.valueOf(editCustomEndpoint.getText()).trim();
        boolean ok=true;
        if(TextUtils.isEmpty(name)&&!allowBlankName){layoutDisplayName.setError("Required");ok=false;}
        if(TextUtils.isEmpty(model)){layoutModelName.setError("Required");ok=false;}
        AiProvider p=providers.get(providerIds.get(index));
        if(p!=null&&p.requiresApiKey()&&TextUtils.isEmpty(key)){layoutApiKey.setError("Required");ok=false;}
        if(p!=null&&p.requiresCustomEndpoint()&&TextUtils.isEmpty(endpoint)){layoutCustomEndpoint.setError("Required");ok=false;}
        try{int mt=Integer.parseInt(String.valueOf(editMaxTokens.getText()).trim());if(mt<128||mt>32768){layoutMaxTokens.setError("Use 128–32768");ok=false;}}catch(Exception e){layoutMaxTokens.setError("Enter a valid number");ok=false;}
        return ok;
    }

    private void save(){
        if(!validateFields(false))return;
        String providerName=String.valueOf(dropdownProvider.getText()).trim(); String providerId=null;
        for(String id:providerIds){AiProvider p=providers.get(id);if(p!=null&&p.getProviderName().equals(providerName)){providerId=id;break;}}
        int maxTokens=Integer.parseInt(String.valueOf(editMaxTokens.getText()).trim());
        String endpoint=String.valueOf(editCustomEndpoint.getText()).trim();
        if(existingConfig==null) existingConfig=new AiModelConfig(providerId,String.valueOf(editDisplayName.getText()).trim(),String.valueOf(editApiKey.getText()).trim(),String.valueOf(editModelName.getText()).trim(),endpoint);
        else {existingConfig.providerId=providerId;existingConfig.displayName=String.valueOf(editDisplayName.getText()).trim();existingConfig.modelName=String.valueOf(editModelName.getText()).trim();existingConfig.apiKey=String.valueOf(editApiKey.getText()).trim();existingConfig.customEndpoint=endpoint;}
        AiProvider p=providers.get(providerId); existingConfig.temperature=sliderTemperature.getValue();existingConfig.threads=p!=null&&p.supportsThreads()?(int)sliderThreads.getValue():0;existingConfig.maxTokens=maxTokens;existingConfig.topP=0.9;
        existingConfig.systemPrompt=String.valueOf(editSystemPrompt.getText()).trim(); existingConfig.enableChat=checkChat.isChecked();existingConfig.enableBlocks=checkBlocks.isChecked();existingConfig.enableLogic=checkLogic.isChecked();existingConfig.enableLayouts=checkLayouts.isChecked();existingConfig.enableCustomBlocks=checkCustomBlocks.isChecked();existingConfig.enableErrorFix=checkErrorFix.isChecked();
        if(AiManager.getConfigs(this).stream().anyMatch(c->c.id.equals(existingConfig.id))) AiManager.updateConfig(this,existingConfig); else AiManager.addConfig(this,existingConfig);
        setResult(RESULT_OK,new Intent());finish();
    }

    private void testConnection(){
        if(!validateFields(true))return;
        String providerName=String.valueOf(dropdownProvider.getText()).trim();String id=null;for(String p:providerIds){AiProvider x=providers.get(p);if(x!=null&&x.getProviderName().equals(providerName)){id=p;break;}}
        if(id==null)return; AiModelConfig c=new AiModelConfig(id,"Connection test",String.valueOf(editApiKey.getText()).trim(),String.valueOf(editModelName.getText()).trim(),String.valueOf(editCustomEndpoint.getText()).trim());c.temperature=sliderTemperature.getValue();c.maxTokens=128;
        buttonTestConnection.setEnabled(false);buttonSave.setEnabled(false);buttonTestConnection.setText("Testing…");
        AiManager.testConfig(this,c,new AiResponseCallback(){public void onSuccess(String s){buttonTestConnection.setEnabled(true);buttonSave.setEnabled(true);buttonTestConnection.setText("Test connection");Toast.makeText(AiModelEditActivity.this,"Connection successful",Toast.LENGTH_SHORT).show();}public void onFailure(String e){buttonTestConnection.setEnabled(true);buttonSave.setEnabled(true);buttonTestConnection.setText("Test connection");new MaterialAlertDialogBuilder(AiModelEditActivity.this).setTitle("Connection failed").setMessage(e).setPositiveButton("OK",null).show();}});
    }
    private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
}
