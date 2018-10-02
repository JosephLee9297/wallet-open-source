package piuk.blockchain.androidbuysell.datamanagers;

import android.annotation.SuppressLint;
import android.support.annotation.VisibleForTesting;

import info.blockchain.wallet.api.data.Settings;
import info.blockchain.wallet.api.data.WalletOptions;

import org.bitcoinj.core.Sha256Hash;
import org.spongycastle.util.encoders.Hex;

import javax.inject.Inject;

import io.reactivex.Observable;
import piuk.blockchain.androidbuysell.models.ExchangeData;
import piuk.blockchain.androidbuysell.models.WebViewLoginDetails;
import piuk.blockchain.androidbuysell.services.BuyConditions;
import piuk.blockchain.androidbuysell.services.ExchangeService;
import piuk.blockchain.androidcore.data.auth.AuthDataManager;
import piuk.blockchain.androidcore.data.payload.PayloadDataManager;
import piuk.blockchain.androidcore.data.settings.SettingsDataManager;
import piuk.blockchain.androidcore.injection.PresenterScope;

@PresenterScope
public class BuyDataManager {

    private ExchangeService exchangeService;
    private SettingsDataManager settingsDataManager;
    private AuthDataManager authDataManager;
    private PayloadDataManager payloadDataManager;
    private BuyConditions buyConditions;

    @Inject
    public BuyDataManager(SettingsDataManager settingsDataManager,
                          AuthDataManager authDataManager,
                          PayloadDataManager payloadDataManager,
                          BuyConditions buyConditions,
                          ExchangeService exchangeService) {
        this.settingsDataManager = settingsDataManager;
        this.authDataManager = authDataManager;
        this.payloadDataManager = payloadDataManager;
        this.buyConditions = buyConditions;
        this.exchangeService = exchangeService;
    }

    /**
     * ReplaySubjects will re-emit items it observed.
     * It is safe to assumed that walletOptions and
     * the user's country code won't change during an active session.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("CheckResult")
    private void initReplaySubjects() {
        Observable<WalletOptions> walletOptionsStream = authDataManager.getWalletOptions();
        walletOptionsStream.subscribeWith(buyConditions.getWalletOptionsSource());

        Observable<Settings> walletSettingsStream = settingsDataManager.getSettings();
        walletSettingsStream.subscribeWith(buyConditions.getWalletSettingsSource());

        Observable<ExchangeData> exchangeDataStream = exchangeService.getExchangeMetaData();
        exchangeDataStream.subscribeWith(buyConditions.getExchangeDataSource());
    }

    public synchronized Observable<Boolean> getCanBuy() {
        initReplaySubjects();

        return Observable.zip(isBuyRolledOut(), isCoinifyAllowed(), isUnocoinAllowed(), isSfoxAllowed(),
                (isBuyRolledOut, allowCoinify, allowUnocoin, allowSfox) ->
                        isBuyRolledOut && (allowCoinify || allowUnocoin || allowSfox));
    }

    /**
     * Checks if buy is rolled out for user on android based on GUID. (All exchange partners)
     *
     * @return An {@link Observable} wrapping a boolean value
     */
    @VisibleForTesting
    Observable<Boolean> isBuyRolledOut() {
        return buyConditions.getWalletOptionsSource()
                .flatMap(walletOptions -> buyConditions.getWalletSettingsSource()
                        .map(inCoinifyCountry -> isRolloutAllowed(walletOptions.getRolloutPercentage())));
    }

    /**
     * Checks if user has whitelisted sfox account or in valid sfox country
     *
     * @return An {@link Observable} wrapping a boolean value
     */
    public Observable<Boolean> isSfoxAllowed() {
        return Observable.zip(isSfoxEnabled(), buyConditions.getWalletOptionsSource(),
                isInSfoxCountry(), buyConditions.getExchangeDataSource(),
                (sfoxEnabled, walletOptions, sfoxCountry, exchangeData) ->
                        sfoxEnabled &&
                                (sfoxCountry || (exchangeData.getSfox() != null && exchangeData.getSfox().getUser() != null)));
    }

    private Observable<Boolean> isSfoxEnabled() {
        return buyConditions.getWalletOptionsSource()
                .map(options -> options.getAndroidFlags().containsKey("showSfox")
                        && options.getAndroidFlags().get("showSfox"));
    }

    /**
     * Checks whether or not a user is accessing their wallet from a Sfox country/state.
     *
     * @return An {@link Observable} wrapping a boolean value
     */
    private Observable<Boolean> isInSfoxCountry() {
        return buyConditions.getWalletOptionsSource()
                .flatMap(walletOptions -> buyConditions.getWalletSettingsSource()
                        .map(settings ->
                                walletOptions.getPartners().getSfox().getCountries().contains(settings.getCountryCode())
                                        && walletOptions.getPartners().getSfox().getStates().contains(settings.getState())));
    }

    /**
     * Checks if user has whitelisted coinify account or in valid coinify country
     *
     * @return An {@link Observable} wrapping a boolean value
     */
    public Observable<Boolean> isCoinifyAllowed() {
        return Observable.zip(isInCoinifyCountry(), buyConditions.getExchangeDataSource(),
                (coinifyCountry, exchangeData) -> coinifyCountry
                        || (exchangeData.getCoinify() != null && exchangeData.getCoinify().getUser() != 0));
    }

    /**
     * Checks whether or not a user is accessing their wallet from a SEPA country.
     *
     * @return An {@link Observable} wrapping a boolean value
     */
    private Observable<Boolean> isInCoinifyCountry() {
        return buyConditions.getWalletOptionsSource()
                .flatMap(walletOptions -> buyConditions.getWalletSettingsSource()
                        .map(settings -> walletOptions.getPartners().getCoinify().getCountries().contains(settings.getCountryCode())));
    }

    /**
     * Checks whether or not buy/sell is allowed to be rolled out based on percentage check on
     * user's GUID.
     *
     * @return An {@link Observable} wrapping a boolean value
     */
    private boolean isRolloutAllowed(double rolloutPercentage) {
        String plainGuid = payloadDataManager.getWallet().getGuid().replace("-", "");

        byte[] guidHashBytes = Sha256Hash.hash(Hex.encode(plainGuid.getBytes()));
        int unsignedByte = guidHashBytes[0] & 0xff;

        return ((unsignedByte + 1.0) / 256.0) <= rolloutPercentage;
    }

    /**
     * Checks whether or not a user is accessing their wallet from India.
     *
     * @return An {@link Observable} wrapping a boolean value
     */
    private Observable<Boolean> isInUnocoinCountry() {
        return buyConditions.getWalletOptionsSource()
                .flatMap(walletOptions -> buyConditions.getWalletSettingsSource()
                        .map(settings -> walletOptions.getPartners().getUnocoin().getCountries().contains(settings.getCountryCode())));
    }

    @VisibleForTesting
    Observable<Boolean> isUnocoinAllowed() {
        return Observable.zip(isInUnocoinCountry(), isUnocoinWhitelisted(), isUnocoinEnabledOnAndroid(),
                (unocoinCountry, whiteListed, androidEnabled) -> (unocoinCountry || whiteListed) && androidEnabled);
    }

    private Observable<Boolean> isUnocoinWhitelisted() {
        return settingsDataManager.getSettings()
                .map(settings -> settings.getInvited().containsKey("unocoin")
                        && settings.getInvited().get("unocoin"));
    }

    private Observable<Boolean> isUnocoinEnabledOnAndroid() {
        return buyConditions.getWalletOptionsSource()
                .map(options -> options.getAndroidFlags().containsKey("showUnocoin")
                        && options.getAndroidFlags().get("showUnocoin"));
    }

    public Observable<WebViewLoginDetails> getWebViewLoginDetails() {
        return exchangeService.getWebViewLoginDetails();
    }

    public Observable<String> watchPendingTrades() {
        return exchangeService.watchPendingTrades();
    }

    public void reloadExchangeData() {
        exchangeService.reloadExchangeData();
    }

    public void wipe() {
        exchangeService.wipe();
    }

    /**
     * Returns user's country code based on calculated value from wallet settings.
     *
     * @return An {@link Observable} wrapping a String value
     */
    public Observable<String> getCountryCode() {
        return buyConditions.getWalletSettingsSource()
                .map(Settings::getCountryCode);
    }

    /**
     * Checks whether or not supplied country code is available for Coinify trade.
     *
     * @param countryCode ISO3 country code as defined here https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
     * @return An {@link Observable} wrapping a boolean value
     */
    public Observable<Boolean> isInCoinifyCountry(String countryCode) {
        return buyConditions.getWalletOptionsSource()
                .map(walletOptions -> walletOptions.getPartners().getCoinify().getCountries().contains(countryCode));
    }
}
