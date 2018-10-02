package piuk.blockchain.android.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

import java.util.Calendar;

import piuk.blockchain.android.BuildConfig;
import piuk.blockchain.android.R;
import timber.log.Timber;

public class AboutDialog extends AppCompatDialogFragment {

    private static final String STR_MERCHANT_PACKAGE = "info.blockchain.merchant";

    public AboutDialog() {
        // No-op
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_about, null);

        TextView about = view.findViewById(R.id.about);
        TextView licenses = view.findViewById(R.id.licenses);
        TextView rateUs = view.findViewById(R.id.rate_us);
        TextView freeWallet = view.findViewById(R.id.free_wallet);

        about.setText(getString(R.string.about, BuildConfig.VERSION_NAME, String.valueOf(Calendar.getInstance().get(Calendar.YEAR))));

        rateUs.setOnClickListener(v -> {
            try {
                String appPackageName = requireActivity().getPackageName();
                Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName));
                marketIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                startActivity(marketIntent);
            } catch (ActivityNotFoundException e) {
                Timber.e(e, "Google Play Store not found");
            }
        });

        licenses.setOnClickListener(v -> {
            View layout = View.inflate(getActivity(), R.layout.dialog_licenses, null);
            WebView webView = layout.findViewById(R.id.webview);
            webView.loadUrl(("file:///android_asset/licenses.html"));
            new AlertDialog.Builder(getActivity(), R.style.AlertDialogStyle)
                    .setView(layout)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });

        if (hasWallet()) {
            freeWallet.setVisibility(View.GONE);
        } else {
            freeWallet.setOnClickListener(v -> {
                try {
                    Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + STR_MERCHANT_PACKAGE));
                    startActivity(marketIntent);
                } catch (ActivityNotFoundException e) {
                    Timber.e(e, "Google Play Store not found");
                }
            });
        }

        return view;
    }

    private boolean hasWallet() {
        PackageManager pm = getActivity().getPackageManager();
        try {
            pm.getPackageInfo(STR_MERCHANT_PACKAGE, 0);
            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
    }


}
