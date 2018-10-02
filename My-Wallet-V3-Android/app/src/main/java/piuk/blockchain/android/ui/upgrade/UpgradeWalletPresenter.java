package piuk.blockchain.android.ui.upgrade;

import android.content.Context;
import android.support.annotation.Nullable;

import info.blockchain.wallet.util.PasswordUtil;

import javax.inject.Inject;

import piuk.blockchain.android.R;
import piuk.blockchain.android.ui.launcher.LauncherActivity;
import piuk.blockchain.androidcore.data.access.AccessState;
import piuk.blockchain.androidcoreui.utils.logging.Logging;
import piuk.blockchain.androidcoreui.utils.logging.WalletUpgradeEvent;
import piuk.blockchain.androidcore.data.auth.AuthDataManager;
import piuk.blockchain.androidcore.data.payload.PayloadDataManager;
import piuk.blockchain.android.data.rxjava.RxUtil;
import piuk.blockchain.androidcoreui.ui.base.BasePresenter;
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom;
import piuk.blockchain.androidcoreui.utils.AppUtil;
import piuk.blockchain.androidcore.utils.PrefsUtil;
import piuk.blockchain.android.util.StringUtils;

public class UpgradeWalletPresenter extends BasePresenter<UpgradeWalletView> {

    private PrefsUtil prefs;
    private AppUtil appUtil;
    private AccessState accessState;
    private AuthDataManager authDataManager;
    private PayloadDataManager payloadDataManager;
    private StringUtils stringUtils;

    @Inject
    UpgradeWalletPresenter(PrefsUtil prefs,
                           AppUtil appUtil,
                           AccessState accessState,
                           AuthDataManager authDataManager,
                           PayloadDataManager payloadDataManager,
                           StringUtils stringUtils) {

        this.prefs = prefs;
        this.appUtil = appUtil;
        this.accessState = accessState;
        this.authDataManager = authDataManager;
        this.payloadDataManager = payloadDataManager;
        this.stringUtils = stringUtils;
    }

    @Override
    public void onViewReady() {
        // Check password existence
        String tempPassword = payloadDataManager.getTempPassword();
        if (tempPassword == null) {
            getView().showToast(R.string.upgrade_fail_info, ToastCustom.TYPE_ERROR);
            appUtil.clearCredentialsAndRestart(LauncherActivity.class);
            return;
        }

        // Check password strength
        if (PasswordUtil.ddpw(tempPassword) || PasswordUtil.getStrength(tempPassword) < 50) {
            getView().showChangePasswordDialog();
        }
    }

    void submitPasswords(String firstPassword, String secondPassword) {
        if (firstPassword.length() < 4
                || firstPassword.length() > 255
                || secondPassword.length() < 4
                || secondPassword.length() > 255) {
            getView().showToast(R.string.invalid_password, ToastCustom.TYPE_ERROR);
        } else {
            if (!firstPassword.equals(secondPassword)) {
                getView().showToast(R.string.password_mismatch_error, ToastCustom.TYPE_ERROR);
            } else {
                final String currentPassword = payloadDataManager.getTempPassword();
                payloadDataManager.setTempPassword(secondPassword);

                authDataManager.createPin(currentPassword, accessState.getPIN())
                        .andThen(payloadDataManager.syncPayloadWithServer())
                        .doOnError(ignored -> payloadDataManager.setTempPassword(currentPassword))
                        .doOnSubscribe(ignored -> getView().showProgressDialog(R.string.please_wait))
                        .doAfterTerminate(() -> getView().dismissProgressDialog())
                        .compose(RxUtil.addCompletableToCompositeDisposable(this))
                        .subscribe(
                                () -> getView().showToast(R.string.password_changed, ToastCustom.TYPE_OK),
                                throwable -> {
                                    getView().showToast(R.string.remote_save_ko, ToastCustom.TYPE_ERROR);
                                    getView().showToast(R.string.password_unchanged, ToastCustom.TYPE_ERROR);
                                });
            }
        }
    }

    void onUpgradeRequested(@Nullable String secondPassword) {
        payloadDataManager.upgradeV2toV3(
                secondPassword,
                stringUtils.getString(R.string.default_wallet_name))
                .doOnSubscribe(ignored -> getView().onUpgradeStarted())
                .doOnError(ignored -> accessState.setNewlyCreated(false))
                .doOnComplete(() -> accessState.setNewlyCreated(true))
                .compose(RxUtil.addCompletableToCompositeDisposable(this))
                .subscribe(
                        () -> {
                            Logging.INSTANCE.logCustom(new WalletUpgradeEvent(true));
                            getView().onUpgradeCompleted();
                        },
                        throwable -> {
                            Logging.INSTANCE.logCustom(new WalletUpgradeEvent(false));
                            Logging.INSTANCE.logException(throwable);
                            getView().onUpgradeFailed();
                        });

    }

    void onContinueClicked() {
        prefs.setValue(PrefsUtil.KEY_EMAIL_VERIFIED, true);
        accessState.setIsLoggedIn(true);
        appUtil.restartAppWithVerifiedPin(LauncherActivity.class);
    }

    void onBackButtonPressed(Context context) {
        accessState.logout(context);
        getView().onBackButtonPressed();
    }

}
