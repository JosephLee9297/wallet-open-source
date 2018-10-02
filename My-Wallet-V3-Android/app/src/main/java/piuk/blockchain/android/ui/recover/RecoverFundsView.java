package piuk.blockchain.android.ui.recover;

import android.support.annotation.StringRes;

import piuk.blockchain.androidcoreui.ui.base.View;
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom;

interface RecoverFundsView extends View {

    String getRecoveryPhrase();

    void showToast(@StringRes int message, @ToastCustom.ToastType String toastType);

    void showProgressDialog(@StringRes int messageId);

    void dismissProgressDialog();

    void gotoCredentialsActivity(String recoveryPhrase);

}
