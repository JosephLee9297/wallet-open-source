package piuk.blockchain.android.data.datamanagers;

import info.blockchain.wallet.api.Environment;
import info.blockchain.wallet.api.FeeApi;
import info.blockchain.wallet.api.data.FeeLimits;
import info.blockchain.wallet.api.data.FeeOptions;

import org.bitcoinj.core.Coin;
import org.web3j.tx.Transfer;

import javax.inject.Inject;

import io.reactivex.Observable;
import piuk.blockchain.androidcore.data.api.EnvironmentConfig;
import piuk.blockchain.androidcore.data.rxjava.RxBus;
import piuk.blockchain.androidcore.data.rxjava.RxPinning;
import piuk.blockchain.android.data.rxjava.RxUtil;
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsDataManager;
import piuk.blockchain.androidcore.injection.PresenterScope;

@PresenterScope
public class FeeDataManager {

    private final RxPinning rxPinning;
    private FeeApi feeApi;
    private EnvironmentConfig environmentSettings;

    //Bitcoin cash fees are temporarily fetched from wallet-options until an endpoint can be provided
    private WalletOptionsDataManager walletOptionsDataManager;

    @Inject
    FeeDataManager(FeeApi feeApi, WalletOptionsDataManager walletOptionsDataManager, EnvironmentConfig environmentSettings, RxBus rxBus) {
        this.feeApi = feeApi;
        this.walletOptionsDataManager = walletOptionsDataManager;
        this.environmentSettings = environmentSettings;
        rxPinning = new RxPinning(rxBus);
    }

    /**
     * Returns a {@link FeeOptions} object which contains both a "regular" and a "priority" fee
     * option, both listed in Satoshis/byte.
     *
     * @return An {@link Observable} wrapping a {@link FeeOptions} object
     */
    public Observable<FeeOptions> getBtcFeeOptions() {
        if (environmentSettings.getEnvironment().equals(Environment.TESTNET)) {
            return Observable.just(createTestnetFeeOptions());
        } else {
            return rxPinning.call(() -> feeApi.getFeeOptions())
                    .compose(RxUtil.applySchedulersToObservable());
        }
    }

    /**
     * Returns a {@link FeeOptions} object which contains both a "regular" and a "priority" fee
     * option for Ethereum.
     *
     * @return An {@link Observable} wrapping a {@link FeeOptions} object
     */
    public Observable<FeeOptions> getEthFeeOptions() {
        if (environmentSettings.getEnvironment().equals(Environment.TESTNET)) {
            //No Test environment for Eth
            return Observable.just(createTestnetFeeOptions());
        } else {
            return rxPinning.call(() -> feeApi.getEthFeeOptions())
                    .compose(RxUtil.applySchedulersToObservable());
        }
    }

    /**
     * Returns a {@link FeeOptions} object which contains a "regular" fee
     * option, both listed in Satoshis/byte.
     *
     * @return An {@link Observable} wrapping a {@link FeeOptions} object
     */
    public Observable<FeeOptions> getBchFeeOptions() {
        return Observable.just(createBchFeeOptions());
    }

    private FeeOptions createBchFeeOptions() {
        FeeOptions feeOptions = new FeeOptions();
        feeOptions.setRegularFee(walletOptionsDataManager.getBchFee());
        feeOptions.setPriorityFee(walletOptionsDataManager.getBchFee());
        return feeOptions;
    }

    private FeeOptions createTestnetFeeOptions() {
        FeeOptions feeOptions = new FeeOptions();
        feeOptions.setRegularFee(1_000L);
        feeOptions.setPriorityFee(10_000L);
        feeOptions.setLimits(new FeeLimits(23, 23));
        feeOptions.setGasLimit(Transfer.GAS_LIMIT.longValue());
        return feeOptions;
    }
}
