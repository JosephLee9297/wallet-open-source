package piuk.blockchain.android;

import android.annotation.SuppressLint;

/**
 * Created by adambennett on 09/08/2016.
 */
@SuppressLint("Registered")
public class BlockchainTestApplication extends BlockchainApplication {

    @Override
    protected void checkSecurityProviderAndPatchIfNeeded() {
        // No-op
    }

}