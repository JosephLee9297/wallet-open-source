package piuk.blockchain.androidbuysell.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Created by justin on 5/1/17.
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinifyData implements ExchangeAccount {

    public CoinifyData() {
        // Empty constructor
    }

    public CoinifyData(int user, String token) {
        this.user = user;
        this.token = token;
    }

    @JsonProperty("user")
    private int user = 0;

    @JsonProperty("offline_token")
    private String token = null;

    @JsonProperty("trades")
    private List<TradeData> trades = null;

    public int getUser() {
        return user;
    }

    @Nullable
    @Override
    public String getToken() {
        return token;
    }

    @Nullable
    @Override
    public List<TradeData> getTrades() {
        return trades;
    }

    public void setTrades(List<TradeData> trades) {
        this.trades = trades;
    }
}
