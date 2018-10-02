package piuk.blockchain.android.ui.recover;

import info.blockchain.wallet.bip44.HDWalletFactory;

import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import piuk.blockchain.android.R;
import piuk.blockchain.androidcoreui.ui.base.BasePresenter;
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom;
import timber.log.Timber;

public class RecoverFundsPresenter extends BasePresenter<RecoverFundsView> {

    @Inject
    RecoverFundsPresenter() {
        // Constructor intentionally empty
    }

    @Override
    public void onViewReady() {
        // No-op
    }

    void onContinueClicked() {
        String recoveryPhrase = getView().getRecoveryPhrase();
        if (recoveryPhrase == null || recoveryPhrase.isEmpty()) {
            getView().showToast(R.string.invalid_recovery_phrase, ToastCustom.TYPE_ERROR);
            return;
        }

        try {
            if (isValidMnemonic(recoveryPhrase)) {
                getView().gotoCredentialsActivity(recoveryPhrase);
            } else {
                getView().showToast(R.string.invalid_recovery_phrase, ToastCustom.TYPE_ERROR);
            }
        } catch (Exception e) {
            // This should never happen
            Timber.wtf(e);
            getView().showToast(R.string.restore_failed, ToastCustom.TYPE_ERROR);
        }
    }

    /**
     * We only support US english mnemonics atm
     */
    private boolean isValidMnemonic(String recoveryPhrase) throws MnemonicException.MnemonicWordException, IOException {
        List<String> words = Arrays.asList(recoveryPhrase.trim().split("\\s+"));

        InputStream wis = HDWalletFactory.class.getClassLoader()
                .getResourceAsStream("wordlist/" + new Locale("en", "US") + ".txt");

        if (wis == null) {
            throw new MnemonicException.MnemonicWordException("cannot read BIP39 word list");
        }

        MnemonicCode mc = new MnemonicCode(wis, null);

        try {
            mc.check(words);
            return true;
        } catch (MnemonicException e) {
            return false;
        }
    }
}
