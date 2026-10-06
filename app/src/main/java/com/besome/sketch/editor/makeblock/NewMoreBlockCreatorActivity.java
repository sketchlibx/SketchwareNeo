package com.besome.sketch.editor.makeblock;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import a.a.a.mB;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityNewMoreblockCreatorBinding;

/**
 * Standalone host of {@link NewMoreBlockCreatorView}, used instead of {@link MakeBlockActivity} when
 * {@code ConfigActivity.SETTING_NEW_MOREBLOCK_CREATOR} is enabled.
 * <p>
 * Contract is identical to {@link MakeBlockActivity}: extras {@code sc_id} and {@code project_file} in,
 * {@code block_name} and {@code block_spec} out with {@code RESULT_OK}. Persistence stays with the caller
 * ({@code LogicEditorActivity} through {@code jC}).
 */
public class NewMoreBlockCreatorActivity extends BaseAppCompatActivity {
    private static final String STATE_SC_ID = "sc_id";
    private static final String STATE_PROJECT = "project_file";

    private String scId;
    private ProjectFileBean project;
    private ActivityNewMoreblockCreatorBinding binding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        if (!isStoragePermissionGranted()) {
            finish();
            return;
        }

        if (savedInstanceState == null) {
            scId = getIntent().getStringExtra("sc_id");
            project = getIntent().getParcelableExtra("project_file");
        } else {
            scId = savedInstanceState.getString(STATE_SC_ID);
            project = savedInstanceState.getParcelable(STATE_PROJECT);
        }
        if (scId == null || project == null) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        binding = ActivityNewMoreblockCreatorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.creatorRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                requestClose();
            }
        });

        binding.creatorView.init(scId, project);
        binding.creatorView.setSnackbarAnchor(binding.btnConfirm);
        binding.creatorView.restoreState(savedInstanceState);

        binding.btnClose.setOnClickListener(v -> requestClose());
        binding.btnCancel.setOnClickListener(v -> requestClose());
        binding.btnConfirm.setOnClickListener(v -> onConfirm());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isStoragePermissionGranted()) {
            finish();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_SC_ID, scId);
        outState.putParcelable(STATE_PROJECT, project);
        if (binding != null) binding.creatorView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    private void requestClose() {
        if (mB.a()) return;
        if (binding.creatorView.isEmpty()) {
            hideKeyboard();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logic_editor_more_block_dialog_message_confirm_goback)
                .setIcon(R.drawable.exit_96)
                .setMessage(R.string.logic_editor_more_block_dialog_description_goback)
                .setPositiveButton(R.string.common_word_goback, (dialog, which) -> {
                    dialog.dismiss();
                    hideKeyboard();
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void onConfirm() {
        if (mB.a()) return;
        if (!binding.creatorView.validate()) return;

        Pair<String, String> info = binding.creatorView.getBlockInformation();
        Intent result = new Intent();
        result.putExtra("block_name", info.first);
        result.putExtra("block_spec", info.second);
        // Optional extra, read by the caller only if it knows about it; block_name / block_spec are unchanged.
        result.putExtra("block_defaults", binding.creatorView.getEncodedDefaults());
        hideKeyboard();
        setResult(RESULT_OK, result);
        finish();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null || binding == null) return;
        View focus = getCurrentFocus();
        imm.hideSoftInputFromWindow((focus != null ? focus : binding.getRoot()).getWindowToken(), 0);
    }
}
