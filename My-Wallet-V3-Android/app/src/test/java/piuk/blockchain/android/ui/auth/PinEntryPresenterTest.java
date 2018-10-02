package piuk.blockchain.android.ui.auth;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;

import info.blockchain.wallet.api.Environment;
import info.blockchain.wallet.exceptions.AccountLockedException;
import info.blockchain.wallet.exceptions.DecryptionException;
import info.blockchain.wallet.exceptions.HDWalletException;
import info.blockchain.wallet.exceptions.InvalidCredentialsException;
import info.blockchain.wallet.exceptions.PayloadException;
import info.blockchain.wallet.exceptions.ServerConnectionException;
import info.blockchain.wallet.exceptions.UnsupportedVersionException;
import info.blockchain.wallet.payload.data.Account;
import info.blockchain.wallet.payload.data.Wallet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.spongycastle.crypto.InvalidCipherTextException;

import java.net.SocketTimeoutException;
import java.util.Locale;

import io.reactivex.Completable;
import io.reactivex.Observable;
import piuk.blockchain.android.BlockchainTestApplication;
import piuk.blockchain.android.BuildConfig;
import piuk.blockchain.android.ui.launcher.LauncherActivity;
import piuk.blockchain.androidcore.data.access.AccessState;
import piuk.blockchain.androidcore.data.auth.AuthDataManager;
import piuk.blockchain.androidcore.data.api.EnvironmentConfig;
import piuk.blockchain.androidcore.data.payload.PayloadDataManager;
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager;
import piuk.blockchain.androidcore.utils.PrngFixer;
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom;
import piuk.blockchain.android.ui.fingerprint.FingerprintHelper;
import piuk.blockchain.androidcoreui.utils.AppUtil;
import piuk.blockchain.android.util.DialogButtonCallback;
import piuk.blockchain.androidcore.utils.PrefsUtil;
import piuk.blockchain.android.util.StringUtils;

import static io.reactivex.Observable.just;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static piuk.blockchain.android.ui.auth.PinEntryFragment.KEY_VALIDATING_PIN_FOR_RESULT;

@Config(sdk = 23, constants = BuildConfig.class, application = BlockchainTestApplication.class)
@RunWith(RobolectricTestRunner.class)
public class PinEntryPresenterTest {

    private PinEntryPresenter subject;

    @Mock private PinEntryView activity;
    @Mock private AuthDataManager authDataManager;
    @Mock private AppUtil appUtil;
    @Mock private PrefsUtil prefsUtil;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private PayloadDataManager payloadManager;
    @Mock private StringUtils stringUtils;
    @Mock private FingerprintHelper fingerprintHelper;
    @Mock private AccessState accessState;
    @Mock private WalletOptionsDataManager walletOptionsDataManager;
    @Mock private EnvironmentConfig environmentSettings;
    @Mock private PrngFixer prngFixer;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        ImageView mockImageView = mock(ImageView.class);
        when(activity.getPinBoxArray())
                .thenReturn(new ImageView[]{mockImageView, mockImageView, mockImageView, mockImageView});
        when(stringUtils.getString(anyInt())).thenReturn("string resource");
        when(activity.getLocale()).thenReturn(Locale.US);

        subject = new PinEntryPresenter(authDataManager,
                appUtil,
                prefsUtil,
                payloadManager,
                stringUtils,
                fingerprintHelper,
                accessState,
                walletOptionsDataManager,
                environmentSettings,
                prngFixer);
        subject.initView(activity);
    }

    @Test
    public void onViewReadyValidatingPinForResult() {
        // Arrange
        when(environmentSettings.getEnvironment()).thenReturn(Environment.PRODUCTION);
        Intent intent = new Intent();
        intent.putExtra(KEY_VALIDATING_PIN_FOR_RESULT, true);
        when(activity.getPageIntent()).thenReturn(intent);
        // Act
        subject.onViewReady();
        // Assert
        assertEquals(true, subject.isForValidatingPinForResult());
    }

    @Test
    public void onViewReadyMaxAttemptsExceeded() {
        // Arrange
        when(environmentSettings.getEnvironment()).thenReturn(Environment.PRODUCTION);
        when(activity.getPageIntent()).thenReturn(new Intent());
        when(prefsUtil.getValue(PrefsUtil.KEY_PIN_FAILS, 0)).thenReturn(4);
        when(payloadManager.getWallet()).thenReturn(mock(Wallet.class));
        when(prefsUtil.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, "")).thenReturn("");
        when(fingerprintHelper.getEncryptedData(PrefsUtil.KEY_ENCRYPTED_PIN_CODE)).thenReturn("");
        // Act
        subject.onViewReady();
        // Assert
        assertEquals(true, subject.allowExit());
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
        verify(activity).showMaxAttemptsDialog();
    }

    @Test
    public void checkFingerprintStatusShouldShowDialog() {
        // Arrange
        subject.mValidatingPinForResult = false;
        when(prefsUtil.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, "")).thenReturn("1234");
        when(fingerprintHelper.isFingerprintUnlockEnabled()).thenReturn(true);
        when(fingerprintHelper.getEncryptedData(PrefsUtil.KEY_ENCRYPTED_PIN_CODE)).thenReturn(null);
        when(fingerprintHelper.getEncryptedData(PrefsUtil.KEY_ENCRYPTED_PIN_CODE)).thenReturn("");
        // Act
        subject.checkFingerprintStatus();
        // Assert
        verify(activity).showFingerprintDialog(anyString());
    }

    @Test
    public void checkFingerprintStatusDontShow() {
        // Arrange
        subject.mValidatingPinForResult = true;
        // Act
        subject.checkFingerprintStatus();
        // Assert
        verify(activity).showKeyboard();
    }

    @Test
    public void canShowFingerprintDialog() {
        // Arrange
        subject.mCanShowFingerprintDialog = true;
        // Act
        boolean value = subject.canShowFingerprintDialog();
        // Assert
        assertEquals(true, value);
    }

    @Test
    public void loginWithDecryptedPin() {
        // Arrange
        String pincode = "1234";
        when(authDataManager.validatePin(pincode)).thenReturn(just("password"));
        // Act
        subject.loginWithDecryptedPin(pincode);
        // Assert
        verify(authDataManager).validatePin(pincode);
        verify(activity).getPinBoxArray();
        assertEquals(false, subject.canShowFingerprintDialog());
    }

    @Test
    public void onDeleteClicked() {
        // Arrange
        subject.mUserEnteredPin = "1234";
        // Act
        subject.onDeleteClicked();
        // Assert
        assertEquals("123", subject.mUserEnteredPin);
        verify(activity).getPinBoxArray();
    }

    @Test
    public void padClickedPinAlreadyFourDigits() {
        // Arrange
        subject.mUserEnteredPin = "0000";
        // Act
        subject.onPadClicked("0");
        // Assert
        verifyZeroInteractions(activity);
    }

    @Test
    public void padClickedAllZeros() {
        // Arrange
        subject.mUserEnteredPin = "000";
        // Act
        when(prefsUtil.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, "")).thenReturn("");
        when(fingerprintHelper.getEncryptedData(PrefsUtil.KEY_ENCRYPTED_PIN_CODE)).thenReturn("");
        subject.onPadClicked("0");
        // Assert
        verify(activity).clearPinBoxes();
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
        assertEquals("", subject.mUserEnteredPin);
        assertEquals(null, subject.mUserEnteredConfirmationPin);
    }

    @Test
    public void padClickedShowCommonPinWarning() {
        // Arrange
        subject.mUserEnteredPin = "123";
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("");
        // Act
        subject.onPadClicked("4");
        // Assert
        verify(activity).showCommonPinWarning(any(DialogButtonCallback.class));
    }

    @Test
    public void padClickedShowCommonPinWarningAndClickRetry() {
        // Arrange
        subject.mUserEnteredPin = "123";
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("");
        doAnswer(invocation -> {
            ((DialogButtonCallback) invocation.getArguments()[0]).onPositiveClicked();
            return null;
        }).when(activity).showCommonPinWarning(any(DialogButtonCallback.class));
        // Act
        subject.onPadClicked("4");
        // Assert
        verify(activity).showCommonPinWarning(any(DialogButtonCallback.class));
        verify(activity).clearPinBoxes();
        assertEquals("", subject.mUserEnteredPin);
        assertEquals(null, subject.mUserEnteredConfirmationPin);
    }

    @Test
    public void padClickedShowCommonPinWarningAndClickContinue() {
        // Arrange
        subject.mUserEnteredPin = "123";
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("");
        doAnswer(invocation -> {
            ((DialogButtonCallback) invocation.getArguments()[0]).onNegativeClicked();
            return null;
        }).when(activity).showCommonPinWarning(any(DialogButtonCallback.class));
        // Act
        subject.onPadClicked("4");
        // Assert
        verify(activity).showCommonPinWarning(any(DialogButtonCallback.class));
        assertEquals("", subject.mUserEnteredPin);
        assertEquals("1234", subject.mUserEnteredConfirmationPin);
    }

    @Test
    public void padClickedShowPinReuseWarning() {
        // Arrange
        subject.mUserEnteredPin = "258";
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("");
        when(accessState.getPIN()).thenReturn("2580");
        // Act
        subject.onPadClicked("0");
        // Assert
        verify(activity).dismissProgressDialog();
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), eq(ToastCustom.TYPE_ERROR));
        verify(activity).clearPinBoxes();
    }

    @Test
    public void padClickedVerifyPinValidateCalled() {
        // Arrange
        subject.mUserEnteredPin = "133";
        when(prefsUtil.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, ""))
                .thenReturn("1234567890");
        when(authDataManager.validatePin(anyString())).thenReturn(just(""));
        // Act
        subject.onPadClicked("7");
        // Assert
        verify(activity).setTitleVisibility(View.INVISIBLE);
        verify(activity, times(2)).showProgressDialog(anyInt(), isNull());
        verify(authDataManager).validatePin(anyString());
    }

    @Test
    public void padClickedVerifyPinForResultReturnsValidPassword() {
        // Arrange
        subject.mUserEnteredPin = "133";
        subject.mValidatingPinForResult = true;
        when(prefsUtil.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, ""))
                .thenReturn("1234567890");
        when(authDataManager.validatePin(anyString())).thenReturn(just(""));
        // Act
        subject.onPadClicked("7");
        // Assert
        verify(activity).setTitleVisibility(View.INVISIBLE);
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(activity).dismissProgressDialog();
        verify(authDataManager).validatePin(anyString());
        verify(activity).finishWithResultOk("1337");
    }

    @Test
    public void padClickedVerifyPinValidateCalledReturnsErrorIncrementsFailureCount() {
        // Arrange
        subject.mUserEnteredPin = "133";
        when(prefsUtil.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, ""))
                .thenReturn("1234567890");
        when(authDataManager.validatePin(anyString()))
                .thenReturn(Observable.error(new InvalidCredentialsException()));
        // Act
        subject.onPadClicked("7");
        // Assert
        verify(activity).setTitleVisibility(View.INVISIBLE);
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(authDataManager).validatePin(anyString());
        verify(prefsUtil).setValue(anyString(), anyInt());
        verify(prefsUtil).getValue(anyString(), anyInt());
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
        verify(activity).restartPageAndClearTop();
    }

    @Test
    public void padClickedVerifyPinValidateCalledReturnsServerError() {
        // Arrange
        subject.mUserEnteredPin = "133";
        when(prefsUtil.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, ""))
                .thenReturn("1234567890");
        when(authDataManager.validatePin(anyString()))
                .thenReturn(Observable.error(new ServerConnectionException()));
        // Act
        subject.onPadClicked("7");
        // Assert
        verify(activity).setTitleVisibility(View.INVISIBLE);
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(authDataManager).validatePin(anyString());
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
        verify(activity).restartPageAndClearTop();
    }

    @Test
    public void padClickedVerifyPinValidateCalledReturnsTimeout() {
        // Arrange
        subject.mUserEnteredPin = "133";
        when(prefsUtil.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, ""))
                .thenReturn("1234567890");
        when(authDataManager.validatePin(anyString()))
                .thenReturn(Observable.error(new SocketTimeoutException()));
        // Act
        subject.onPadClicked("7");
        // Assert
        verify(activity).setTitleVisibility(View.INVISIBLE);
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(authDataManager).validatePin(anyString());
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
        verify(activity).restartPageAndClearTop();
    }

    @Test
    public void padClickedVerifyPinValidateCalledReturnsInvalidCipherText() {
        // Arrange
        subject.mUserEnteredPin = "133";
        when(prefsUtil.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, ""))
                .thenReturn("1234567890");
        when(authDataManager.validatePin(anyString())).thenReturn(just(""));
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), anyString()))
                .thenReturn(Completable.error(new InvalidCipherTextException()));
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        // Act
        subject.onPadClicked("7");
        // Assert
        verify(activity).setTitleVisibility(View.INVISIBLE);
        verify(activity, times(2)).showProgressDialog(anyInt(), isNull());
        verify(authDataManager).validatePin(anyString());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), anyString());
        verify(prefsUtil).setValue(anyString(), anyInt());
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
        verify(accessState).setPIN(null);
        verify(appUtil).clearCredentialsAndRestart(LauncherActivity.class);
    }

    @Test
    public void padClickedVerifyPinValidateCalledReturnsGenericException() {
        // Arrange
        subject.mUserEnteredPin = "133";
        when(prefsUtil.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, ""))
                .thenReturn("1234567890");
        when(authDataManager.validatePin(anyString())).thenReturn(just(""));
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), anyString()))
                .thenReturn(Completable.error(new Exception()));
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        // Act
        subject.onPadClicked("7");
        // Assert
        verify(activity).setTitleVisibility(View.INVISIBLE);
        verify(activity, times(2)).showProgressDialog(anyInt(), isNull());
        verify(authDataManager).validatePin(anyString());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), anyString());
        verify(prefsUtil).setValue(anyString(), anyInt());
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
        verify(appUtil).restartApp(LauncherActivity.class);
    }

    @Test
    public void padClickedCreatePinCreateSuccessful() {
        // Arrange
        subject.mUserEnteredPin = "133";
        subject.mUserEnteredConfirmationPin = "1337";
        when(payloadManager.getTempPassword()).thenReturn("temp password");
        when(prefsUtil.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, "")).thenReturn("");
        when(authDataManager.createPin(anyString(), anyString())).thenReturn(Completable.complete());
        when(authDataManager.validatePin(anyString())).thenReturn(just("password"));
        // Act
        subject.onPadClicked("7");
        // Assert
        verify(activity, times(2)).showProgressDialog(anyInt(), isNull());
        verify(authDataManager).createPin(anyString(), anyString());
        verify(fingerprintHelper).clearEncryptedData(PrefsUtil.KEY_ENCRYPTED_PIN_CODE);
        verify(fingerprintHelper).setFingerprintUnlockEnabled(false);
    }

    @Test
    public void padClickedCreatePinCreateFailed() {
        // Arrange
        subject.mUserEnteredPin = "133";
        subject.mUserEnteredConfirmationPin = "1337";
        when(payloadManager.getTempPassword()).thenReturn("temp password");
        when(prefsUtil.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, "")).thenReturn("");
        when(authDataManager.createPin(anyString(), anyString()))
                .thenReturn(Completable.error(new Throwable()));
        // Act
        subject.onPadClicked("7");
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(authDataManager).createPin(anyString(), anyString());
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
        verify(prefsUtil).clear();
        verify(appUtil).restartApp(LauncherActivity.class);
    }

    @Test
    public void padClickedCreatePinWritesNewConfirmationValue() {
        // Arrange
        subject.mUserEnteredPin = "133";
        when(prefsUtil.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, "")).thenReturn("");
        when(authDataManager.createPin(anyString(), anyString())).thenReturn(Completable.complete());
        // Act
        subject.onPadClicked("7");
        // Assert
        assertEquals("1337", subject.mUserEnteredConfirmationPin);
        assertEquals("", subject.mUserEnteredPin);
    }

    @Test
    public void padClickedCreatePinMismatched() {
        // Arrange
        subject.mUserEnteredPin = "133";
        subject.mUserEnteredConfirmationPin = "1234";
        when(prefsUtil.getValue(PrefsUtil.KEY_PIN_IDENTIFIER, "")).thenReturn("");
        when(authDataManager.createPin(anyString(), anyString())).thenReturn(Completable.complete());
        // Act
        subject.onPadClicked("7");
        // Assert
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
        verify(activity).dismissProgressDialog();
    }

    @Test
    public void clearPinBoxes() {
        // Arrange

        // Act
        subject.clearPinBoxes();
        // Assert
        verify(activity).clearPinBoxes();
        assertEquals("", subject.mUserEnteredPin);
    }

    @Test
    public void validatePasswordSuccessful() {
        // Arrange
        String password = "1234567890";
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), eq(password)))

                .thenReturn(Completable.complete());
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        // Act
        subject.validatePassword(password);
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), eq(password));
        verify(activity).dismissProgressDialog();
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
        verify(prefsUtil, times(2)).removeValue(anyString());
        verify(accessState).setPIN(null);
        verify(activity).restartPageAndClearTop();
    }

    @Test
    public void validatePasswordThrowsGenericException() {
        // Arrange
        String password = "1234567890";
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), eq(password)))
                .thenReturn(Completable.error(new Throwable()));
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        // Act
        subject.validatePassword(password);
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), eq(password));
        verify(activity, times(2)).dismissProgressDialog();
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
        verify(activity).showValidationDialog();
    }

    @Test
    public void validatePasswordThrowsServerConnectionException() {
        // Arrange
        String password = "1234567890";
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), eq(password)))
                .thenReturn(Completable.error(new ServerConnectionException()));
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        // Act
        subject.validatePassword(password);
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), eq(password));
        verify(activity).dismissProgressDialog();
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
    }

    @Test
    public void validatePasswordThrowsSocketTimeoutException() {
        // Arrange
        String password = "1234567890";
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), eq(password)))
                .thenReturn(Completable.error(new SocketTimeoutException()));
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        // Act
        subject.validatePassword(password);
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), eq(password));
        verify(activity).dismissProgressDialog();
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
    }

    @Test
    public void validatePasswordThrowsHDWalletExceptionException() {
        // Arrange
        String password = "1234567890";
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), eq(password)))
                .thenReturn(Completable.error(new HDWalletException()));
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        // Act
        subject.validatePassword(password);
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), eq(password));
        verify(activity).dismissProgressDialog();
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
        verify(appUtil).restartApp(LauncherActivity.class);
    }

    @Test
    public void validatePasswordThrowsAccountLockedException() {
        // Arrange
        String password = "1234567890";
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), eq(password)))
                .thenReturn(Completable.error(new AccountLockedException()));
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        // Act
        subject.validatePassword(password);
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), eq(password));
        verify(activity).dismissProgressDialog();
        verify(activity).showAccountLockedDialog();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void updatePayloadInvalidCredentialsException() {
        // Arrange
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), anyString()))
                .thenReturn(Completable.error(new InvalidCredentialsException()));
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        Wallet mockPayload = mock(Wallet.class);
        when(mockPayload.getSharedKey()).thenReturn("1234567890");
        when(payloadManager.getWallet()).thenReturn(mockPayload);
        // Act
        subject.updatePayload("");
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), anyString());
        verify(activity).goToPasswordRequiredActivity();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void updatePayloadServerConnectionException() {
        // Arrange
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), anyString()))
                .thenReturn(Completable.error(new ServerConnectionException()));
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        Wallet mockPayload = mock(Wallet.class);
        when(mockPayload.getSharedKey()).thenReturn("1234567890");
        when(payloadManager.getWallet()).thenReturn(mockPayload);
        // Act
        subject.updatePayload("");
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), anyString());
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void updatePayloadDecryptionException() {
        // Arrange
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), anyString()))
                .thenReturn(Completable.error(new DecryptionException()));
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        Wallet mockPayload = mock(Wallet.class);
        when(mockPayload.getSharedKey()).thenReturn("1234567890");
        when(payloadManager.getWallet()).thenReturn(mockPayload);
        // Act
        subject.updatePayload("");
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), anyString());
        verify(activity).goToPasswordRequiredActivity();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void updatePayloadPayloadExceptionException() {
        // Arrange
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), anyString()))
                .thenReturn(Completable.error(new PayloadException()));
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        Wallet mockPayload = mock(Wallet.class);
        when(mockPayload.getSharedKey()).thenReturn("1234567890");
        when(payloadManager.getWallet()).thenReturn(mockPayload);
        // Act
        subject.updatePayload("");
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), anyString());
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
        verify(appUtil).restartApp(LauncherActivity.class);
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void updatePayloadHDWalletException() {
        // Arrange
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), anyString()))
                .thenReturn(Completable.error(new HDWalletException()));
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        Wallet mockPayload = mock(Wallet.class);
        when(mockPayload.getSharedKey()).thenReturn("1234567890");
        when(payloadManager.getWallet()).thenReturn(mockPayload);
        // Act
        subject.updatePayload("");
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), anyString());
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
        verify(appUtil).restartApp(LauncherActivity.class);
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void updatePayloadVersionNotSupported() {
        // Arrange
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), anyString()))
                .thenReturn(Completable.error(new UnsupportedVersionException()));
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        Wallet mockPayload = mock(Wallet.class);
        when(mockPayload.getSharedKey()).thenReturn("1234567890");
        when(payloadManager.getWallet()).thenReturn(mockPayload);
        // Act
        subject.updatePayload("");
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), anyString());
        verify(activity).showWalletVersionNotSupportedDialog(isNull());
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void updatePayloadAccountLocked() {
        // Arrange
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), anyString()))
                .thenReturn(Completable.error(new AccountLockedException()));
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        Wallet mockPayload = mock(Wallet.class);
        when(mockPayload.getSharedKey()).thenReturn("1234567890");
        when(payloadManager.getWallet()).thenReturn(mockPayload);
        // Act
        subject.updatePayload("");
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), anyString());
        verify(activity).showAccountLockedDialog();
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void updatePayloadSuccessfulSetLabels() {
        // Arrange
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), anyString()))
                .thenReturn(Completable.complete());
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        Account mockAccount = mock(Account.class);
        when(mockAccount.getLabel()).thenReturn(null);
        when(payloadManager.getAccount(0)).thenReturn(mockAccount);
        when(payloadManager.getWallet().getSharedKey()).thenReturn("shared_key");
        when(payloadManager.getWallet().isUpgraded()).thenReturn(true);
        when(accessState.isNewlyCreated()).thenReturn(true);
        // Act
        subject.updatePayload("");
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), anyString());
        verify(appUtil).setSharedKey(anyString());
        verify(payloadManager, atLeastOnce()).getWallet();
        verify(stringUtils).getString(anyInt());
        verify(activity).dismissProgressDialog();
        assertEquals(true, subject.mCanShowFingerprintDialog);
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void updatePayloadSuccessfulUpgradeWallet() {
        // Arrange
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), anyString()))
                .thenReturn(Completable.complete());
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        Account mockAccount = mock(Account.class);
        when(mockAccount.getLabel()).thenReturn("label");
        when(payloadManager.getAccount(0)).thenReturn(mockAccount);
        when(payloadManager.getWallet().getSharedKey()).thenReturn("shared_key");
        when(payloadManager.getWallet().isUpgraded()).thenReturn(false);
        when(accessState.isNewlyCreated()).thenReturn(false);
        // Act
        subject.updatePayload("");
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), anyString());
        verify(appUtil).setSharedKey(anyString());
        verify(activity).goToUpgradeWalletActivity();
        verify(activity).dismissProgressDialog();
        assertEquals(true, subject.mCanShowFingerprintDialog);
    }

    @SuppressLint("VisibleForTests")
    @Test
    public void updatePayloadSuccessfulVerifyPin() {
        // Arrange
        when(payloadManager.initializeAndDecrypt(anyString(), anyString(), anyString()))
                .thenReturn(Completable.complete());
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("prefs string");
        Account mockAccount = mock(Account.class);
        when(mockAccount.getLabel()).thenReturn("label");
        when(payloadManager.getAccount(0)).thenReturn(mockAccount);
        when(payloadManager.getWallet().getSharedKey()).thenReturn("shared_key");
        when(payloadManager.getWallet().isUpgraded()).thenReturn(true);
        when(accessState.isNewlyCreated()).thenReturn(false);
        // Act
        subject.updatePayload("");
        // Assert
        verify(activity).showProgressDialog(anyInt(), isNull());
        verify(payloadManager).initializeAndDecrypt(anyString(), anyString(), anyString());
        verify(appUtil).setSharedKey(anyString());
        verify(appUtil).restartAppWithVerifiedPin(LauncherActivity.class);
        verify(activity).dismissProgressDialog();
        assertEquals(true, subject.mCanShowFingerprintDialog);
    }

    @Test
    public void incrementFailureCount() {
        // Arrange

        // Act
        subject.incrementFailureCountAndRestart();
        // Assert
        verify(prefsUtil).getValue(anyString(), anyInt());
        verify(prefsUtil).setValue(anyString(), anyInt());
        //noinspection WrongConstant
        verify(activity).showToast(anyInt(), anyString());
        verify(activity).restartPageAndClearTop();
    }

    @Test
    public void resetApp() {
        // Arrange

        // Act
        subject.resetApp();
        // Assert
        verify(appUtil).clearCredentialsAndRestart(LauncherActivity.class);
    }

    @Test
    public void allowExit() {
        // Arrange

        // Act
        boolean allowExit = subject.allowExit();
        // Assert
        assertEquals(subject.bAllowExit, allowExit);
    }

    @Test
    public void isCreatingNewPin() {
        // Arrange
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("");
        // Act
        boolean creatingNewPin = subject.isCreatingNewPin();
        // Assert
        assertEquals(true, creatingNewPin);
    }

    @Test
    public void isNotCreatingNewPin() {
        // Arrange
        when(prefsUtil.getValue(anyString(), anyString())).thenReturn("1234567890");
        // Act
        boolean creatingNewPin = subject.isCreatingNewPin();
        // Assert
        assertEquals(false, creatingNewPin);
    }

    @Test
    public void getAppUtil() {
        // Arrange

        // Act
        AppUtil util = subject.getAppUtil();
        // Assert
        assertEquals(util, appUtil);
    }

    @Test
    public void fetchInfoMessage() {
        // Arrange
        when(walletOptionsDataManager.fetchInfoMessage(any(Locale.class)))
                .thenReturn(Observable.just("Some generic message"));

        // Act
        subject.fetchInfoMessage();
        // Assert
        verify(activity).showCustomPrompt(any());
    }

    @Test
    public void fetchInfoMessage_none() {
        // Arrange
        when(walletOptionsDataManager.fetchInfoMessage(any(Locale.class)))
                .thenReturn(Observable.just(""));

        // Act
        subject.fetchInfoMessage();
        // Assert
        verify(activity).getLocale();
        verifyNoMoreInteractions(activity);
    }

    @Test
    public void checkForceUpgradeStatus_false() {
        // Arrange
        int versionCode = 281;
        int sdk = 21;
        when(walletOptionsDataManager.checkForceUpgrade(versionCode, sdk))
                .thenReturn(Observable.just(false));
        // Act
        subject.checkForceUpgradeStatus(versionCode, sdk);
        // Assert
        verify(walletOptionsDataManager).checkForceUpgrade(versionCode, sdk);
        verifyZeroInteractions(activity);
    }

    @Test
    public void checkForceUpgradeStatus_true() {
        // Arrange
        int versionCode = 281;
        int sdk = 21;
        when(walletOptionsDataManager.checkForceUpgrade(versionCode, sdk))
                .thenReturn(Observable.just(true));
        // Act
        subject.checkForceUpgradeStatus(versionCode, sdk);
        // Assert
        verify(walletOptionsDataManager).checkForceUpgrade(versionCode, sdk);
        verify(activity).forceUpgrade();
        verifyNoMoreInteractions(activity);
    }

}