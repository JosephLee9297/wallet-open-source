package piuk.blockchain.android.ui.auth;


import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.support.v7.widget.AppCompatEditText;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import java.util.Locale;

import javax.inject.Inject;

import piuk.blockchain.android.BuildConfig;
import piuk.blockchain.android.R;
import piuk.blockchain.android.data.connectivity.ConnectivityStatus;
import piuk.blockchain.android.databinding.FragmentPinEntryBinding;
import piuk.blockchain.android.injection.Injector;
import piuk.blockchain.android.ui.customviews.PinEntryKeypad;
import piuk.blockchain.android.ui.fingerprint.FingerprintDialog;
import piuk.blockchain.android.ui.fingerprint.FingerprintStage;
import piuk.blockchain.android.ui.launcher.LauncherActivity;
import piuk.blockchain.android.ui.upgrade.UpgradeWalletActivity;
import piuk.blockchain.android.util.DialogButtonCallback;
import piuk.blockchain.androidcore.data.access.AccessState;
import piuk.blockchain.androidcore.data.api.EnvironmentConfig;
import piuk.blockchain.androidcore.utils.PrefsUtil;
import piuk.blockchain.androidcore.utils.annotations.Thunk;
import piuk.blockchain.androidcoreui.ui.base.BaseFragment;
import piuk.blockchain.androidcoreui.ui.customviews.MaterialProgressDialog;
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom;
import piuk.blockchain.androidcoreui.utils.AppUtil;
import piuk.blockchain.androidcoreui.utils.ViewUtils;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

@SuppressWarnings("WeakerAccess")
public class PinEntryFragment extends BaseFragment<PinEntryView, PinEntryPresenter> implements PinEntryView {

    public static final String KEY_VALIDATING_PIN_FOR_RESULT = "validating_pin";
    public static final String KEY_VALIDATED_PIN = "validated_pin";
    public static final int REQUEST_CODE_VALIDATE_PIN = 88;
    private static final int COOL_DOWN_MILLIS = 2 * 1000;
    private static final String KEY_SHOW_SWIPE_HINT = "show_swipe_hint";
    private static final int PIN_LENGTH = 4;
    private static final Handler HANDLER = new Handler();

    @Inject PinEntryPresenter pinEntryPresenter;
    @Inject PrefsUtil prefsUtil;
    @Inject AppUtil appUtil;
    @Inject EnvironmentConfig environmentConfig;

    private ImageView[] pinBoxArray;
    private MaterialProgressDialog materialProgressDialog;
    private FragmentPinEntryBinding binding;
    private FingerprintDialog fingerprintDialog;
    private OnPinEntryFragmentInteractionListener listener;
    private ClearPinNumberRunnable clearPinNumberRunnable = new ClearPinNumberRunnable();
    private boolean isPaused = false;

    private long backPressed;

    {
        Injector.getInstance().getPresenterComponent().inject(this);
    }

    public static PinEntryFragment newInstance(boolean showSwipeHint) {
        Bundle args = new Bundle();
        args.putBoolean(KEY_SHOW_SWIPE_HINT, showSwipeHint);
        PinEntryFragment fragment = new PinEntryFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_pin_entry, container, false);

        // Set title state
        if (getPresenter().isCreatingNewPin()) {
            binding.titleBox.setText(R.string.create_pin);
        } else {
            binding.titleBox.setText(R.string.pin_entry);
            getPresenter().fetchInfoMessage();
        }

        pinBoxArray = new ImageView[PIN_LENGTH];
        pinBoxArray[0] = binding.pinBox0;
        pinBoxArray[1] = binding.pinBox1;
        pinBoxArray[2] = binding.pinBox2;
        pinBoxArray[3] = binding.pinBox3;

        showConnectionDialogIfNeeded();

        binding.swipeHintLayout.setOnClickListener(view -> listener.onSwipePressed());

        getPresenter().onViewReady();
        getPresenter().checkForceUpgradeStatus(BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT);

        if (getArguments() != null) {
            boolean showSwipeHint = getArguments().getBoolean(KEY_SHOW_SWIPE_HINT);
            if (!showSwipeHint) {
                binding.swipeHintLayout.setVisibility(View.INVISIBLE);
            }
        }

        binding.keyboard.setPadClickedListener(new PinEntryKeypad.OnPinEntryPadClickedListener() {
            @Override
            public void onNumberClicked(String number) {
                getPresenter().onPadClicked(number);
            }

            @Override
            public void onDeleteClicked() {
                getPresenter().onDeleteClicked();
            }
        });

        if (environmentConfig.shouldShowDebugMenu()) {
            ToastCustom.makeText(
                    getActivity(),
                    "Current environment: "
                            + environmentConfig.getEnvironment().getName(),
                    ToastCustom.LENGTH_SHORT,
                    ToastCustom.TYPE_GENERAL);

            binding.buttonSettings.setVisibility(View.VISIBLE);
            binding.buttonSettings.setOnClickListener(view -> {
                        if (getActivity() != null) {
                            new EnvironmentSwitcher(getActivity(), prefsUtil, appUtil).showDebugMenu();
                        }
                    }
            );
        }

        return binding.getRoot();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnPinEntryFragmentInteractionListener) {
            listener = (OnPinEntryFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context + " must implement OnPinEntryFragmentInteractionListener");
        }
    }

    @Override
    public void showFingerprintDialog(String pincode) {
        // Show icon for relaunching dialog
        binding.fingerprintLogo.setVisibility(View.VISIBLE);
        binding.fingerprintLogo.setOnClickListener(v -> getPresenter().checkFingerprintStatus());
        // Show dialog itself if not already showing
        if (fingerprintDialog == null && getPresenter().canShowFingerprintDialog()) {
            fingerprintDialog = FingerprintDialog.Companion.newInstance(pincode, FingerprintStage.AUTHENTICATE);
            fingerprintDialog.setAuthCallback(new FingerprintDialog.FingerprintAuthCallback() {
                @Override
                public void onAuthenticated(String data) {
                    dismissFingerprintDialog();
                    getPresenter().loginWithDecryptedPin(data);
                }

                @Override
                public void onCanceled() {
                    dismissFingerprintDialog();
                    showKeyboard();
                }
            });

            HANDLER.postDelayed(() -> {
                if (getActivity() != null && !getActivity().isFinishing() && !isPaused) {
                    getActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .add(fingerprintDialog, FingerprintDialog.TAG)
                            .commitAllowingStateLoss();
                } else {
                    fingerprintDialog = null;
                }
            }, 200);

            hideKeyboard();
        }
    }

    @Override
    public void showKeyboard() {
        if (getActivity() != null && binding.keyboard.getVisibility() == View.INVISIBLE) {
            Animation bottomUp = AnimationUtils.loadAnimation(getActivity(), R.anim.bottom_up);
            binding.keyboard.startAnimation(bottomUp);
            binding.keyboard.setVisibility(View.VISIBLE);
        }
    }

    private void hideKeyboard() {
        if (getActivity() != null && binding.keyboard.getVisibility() == View.VISIBLE) {
            Animation bottomUp = AnimationUtils.loadAnimation(getActivity(), R.anim.top_down);
            binding.keyboard.startAnimation(bottomUp);
            binding.keyboard.setVisibility(View.INVISIBLE);
        }
    }

    private void showConnectionDialogIfNeeded() {
        if (getContext() != null) {
            if (!ConnectivityStatus.hasConnectivity(getContext())) {
                new AlertDialog.Builder(getContext(), R.style.AlertDialogStyle)
                        .setMessage(getString(R.string.check_connectivity_exit))
                        .setCancelable(false)
                        .setPositiveButton(R.string.dialog_continue, (dialog, id) -> restartPageAndClearTop())
                        .create()
                        .show();
            }
        }
    }

    @Override
    public void showMaxAttemptsDialog() {
        if (getContext() != null) {
            new AlertDialog.Builder(getContext(), R.style.AlertDialogStyle)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.password_or_wipe)
                    .setCancelable(false)
                    .setPositiveButton(R.string.use_password, (dialog, whichButton) -> showValidationDialog())
                    .setNegativeButton(R.string.wipe_wallet, (dialog, whichButton) -> getPresenter().resetApp())
                    .show();
        }
    }

    public void onBackPressed() {
        if (getPresenter().isForValidatingPinForResult()) {
            finishWithResultCanceled();

        } else if (getPresenter().allowExit()) {
            if (backPressed + COOL_DOWN_MILLIS > System.currentTimeMillis()) {
                getPresenter().logout(getContext());
                return;
            } else {
                showToast(R.string.exit_confirm, ToastCustom.TYPE_GENERAL);
            }

            backPressed = System.currentTimeMillis();
        }
    }

    @Override
    public void showWalletVersionNotSupportedDialog(String walletVersion) {
        if (getContext() != null) {
            new AlertDialog.Builder(getContext(), R.style.AlertDialogStyle)
                    .setTitle(R.string.warning)
                    .setMessage(String.format(getString(R.string.unsupported_encryption_version), walletVersion))
                    .setCancelable(false)
                    .setPositiveButton(R.string.exit, (dialog, whichButton) -> getPresenter().logout(getContext()))
                    .setNegativeButton(R.string.logout, (dialog, which) -> {
                        getPresenter().logout(getContext());
                        getPresenter().getAppUtil().restartApp(LauncherActivity.class);
                    })
                    .show();
        }
    }

    @Override
    public void clearPinBoxes() {
        HANDLER.postDelayed(clearPinNumberRunnable, 200);
    }

    @Override
    public void goToPasswordRequiredActivity() {
        Intent intent = new Intent(getContext(), PasswordRequiredActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void goToUpgradeWalletActivity() {
        Intent intent = new Intent(getContext(), UpgradeWalletActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void setTitleString(@StringRes int title) {
        HANDLER.postDelayed(() -> binding.titleBox.setText(title), 200);
    }

    @Override
    public void setTitleVisibility(@ViewUtils.Visibility int visibility) {
        binding.titleBox.setVisibility(visibility);
    }

    public void resetPinEntry() {
        if (getActivity() != null && !getActivity().isFinishing() && getPresenter() != null) {
            getPresenter().clearPinBoxes();
        }
    }

    public boolean allowExit() {
        return getPresenter().allowExit();
    }

    public boolean isValidatingPinForResult() {
        return getPresenter().isForValidatingPinForResult();
    }

    @Override
    public ImageView[] getPinBoxArray() {
        return pinBoxArray;
    }

    @Override
    public void restartPageAndClearTop() {
        Intent intent = new Intent(getContext(), PinEntryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void showCommonPinWarning(DialogButtonCallback callback) {
        if (getContext() != null) {
            new AlertDialog.Builder(getContext(), R.style.AlertDialogStyle)
                    .setTitle(R.string.common_pin_dialog_title)
                    .setMessage(R.string.common_pin_dialog_message)
                    .setPositiveButton(R.string.common_pin_dialog_try_again, (dialogInterface, i) -> callback.onPositiveClicked())
                    .setNegativeButton(R.string.common_pin_dialog_continue, (dialogInterface, i) -> callback.onNegativeClicked())
                    .setCancelable(false)
                    .create()
                    .show();
        }
    }

    @Override
    public void showValidationDialog() {
        if (getContext() != null) {
            final AppCompatEditText password = new AppCompatEditText(getContext());
            password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            password.setHint(R.string.password);

            new AlertDialog.Builder(getContext(), R.style.AlertDialogStyle)
                    .setTitle(R.string.app_name)
                    .setMessage(getString(R.string.password_entry))
                    .setView(ViewUtils.getAlertDialogPaddedView(getContext(), password))
                    .setCancelable(false)
                    .setNegativeButton(android.R.string.cancel, (dialog, whichButton) ->
                            getPresenter().getAppUtil().restartApp(LauncherActivity.class))
                    .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
                        final String pw = password.getText().toString();

                        if (!pw.isEmpty()) {
                            getPresenter().validatePassword(pw);
                        } else {
                            getPresenter().incrementFailureCountAndRestart();
                        }
                    }).show();
        }
    }

    @Override
    public void showAccountLockedDialog() {
        if (getContext() != null) {
            new AlertDialog.Builder(getContext(), R.style.AlertDialogStyle)
                    .setTitle(R.string.account_locked_title)
                    .setMessage(R.string.account_locked_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.exit, (dialogInterface, i) -> getActivity().finish())
                    .create()
                    .show();
        }
    }

    @Override
    public void showToast(@StringRes int message, @ToastCustom.ToastType String toastType) {
        ToastCustom.makeText(getContext(), getString(message), ToastCustom.LENGTH_SHORT, toastType);
    }

    @Override
    public void showProgressDialog(@StringRes int messageId, @Nullable String suffix) {
        dismissProgressDialog();
        materialProgressDialog = new MaterialProgressDialog(getContext());
        materialProgressDialog.setCancelable(false);
        if (suffix != null) {
            materialProgressDialog.setMessage(getString(messageId) + suffix);
        } else {
            materialProgressDialog.setMessage(getString(messageId));
        }

        if (getActivity() != null && !getActivity().isFinishing()) materialProgressDialog.show();
    }

    @Override
    public void dismissProgressDialog() {
        if (materialProgressDialog != null && materialProgressDialog.isShowing()) {
            materialProgressDialog.dismiss();
            materialProgressDialog = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        isPaused = false;
        getPresenter().clearPinBoxes();
        getPresenter().checkFingerprintStatus();
    }

    @Override
    public void finishWithResultOk(String pin) {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_VALIDATED_PIN, pin);
        Intent intent = new Intent();
        intent.putExtras(bundle);
        getActivity().setResult(RESULT_OK, intent);
        getActivity().finish();
    }

    private void finishWithResultCanceled() {
        Intent intent = new Intent();
        getActivity().setResult(RESULT_CANCELED, intent);
        getActivity().finish();
    }

    @Override
    public void forceUpgrade() {
        if (getContext() != null) {
            AlertDialog alertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogStyle)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.force_upgrade_message)
                    .setPositiveButton(R.string.update, null)
                    .setNegativeButton(R.string.exit, null)
                    .setCancelable(false)
                    .create();

            alertDialog.show();
            // Buttons are done this way to avoid dismissing the dialog
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String appPackageName = getContext().getPackageName();
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                } catch (ActivityNotFoundException e) {
                    // Device doesn't have the Play Store installed, direct them to the official
                    // store web page anyway
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                }
            });
            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v ->
                    AccessState.getInstance().logout(getContext()));
        }
    }

    @Override
    public Intent getPageIntent() {
        return getActivity().getIntent();
    }

    @Override
    public void onPause() {
        super.onPause();
        isPaused = true;
        dismissFingerprintDialog();
    }

    @Override
    public void onDestroy() {
        dismissProgressDialog();
        super.onDestroy();
    }

    @Thunk
    void dismissFingerprintDialog() {
        if (fingerprintDialog != null && fingerprintDialog.isVisible()) {
            fingerprintDialog.dismissAllowingStateLoss();
            fingerprintDialog = null;
        }

        // Hide if fingerprint unlock has become unavailable
        if (!getPresenter().getIfShouldShowFingerprintLogin()) {
            binding.fingerprintLogo.setVisibility(View.GONE);
        }
    }

    private class ClearPinNumberRunnable implements Runnable {
        ClearPinNumberRunnable() {
            // Empty constructor
        }

        @Override
        public void run() {
            for (ImageView pinBox : getPinBoxArray()) {
                // Reset PIN buttons to blank
                pinBox.setImageResource(R.drawable.rounded_view_blue_white_border);
            }
        }
    }

    @Override
    public void showCustomPrompt(AppCompatDialogFragment alertFragments) {
        if (getActivity() != null && !getActivity().isFinishing()) {
            alertFragments.show(getFragmentManager(), alertFragments.getTag());
        }
    }

    @Override
    public void showTestnetWarning() {
        if (getActivity() != null && !getActivity().isFinishing()) {
            Snackbar snack = Snackbar.make(
                    getActivity().findViewById(android.R.id.content),
                    R.string.testnet_warning,
                    Snackbar.LENGTH_LONG
            );
            View view = snack.getView();
            view.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.product_red_medium));
            snack.show();
        }
    }

    @Override
    public Locale getLocale() {
        return Locale.getDefault();
    }

    @Override
    protected PinEntryPresenter createPresenter() {
        return pinEntryPresenter;
    }

    @Override
    protected PinEntryView getMvpView() {
        return this;
    }

    interface OnPinEntryFragmentInteractionListener {

        void onSwipePressed();

    }
}
