package mod.sketchlibx.terminal;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.besome.sketch.design.DesignActivity;

public class TerminalFragment extends Fragment {
    private NeoTerminalView terminalView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        terminalView = new NeoTerminalView(requireContext(), DesignActivity.sc_id);
        return terminalView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (terminalView != null) terminalView.destroy();
    }
}
