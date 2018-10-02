package piuk.blockchain.androidcoreui.ui.base;

public interface Presenter<VIEW extends View> {

    void onViewDestroyed();

    void onViewPaused();

    void initView(VIEW view);

    VIEW getView();

}
