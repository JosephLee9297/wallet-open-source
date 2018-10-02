package piuk.blockchain.android.util;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import javax.inject.Inject;

public class OSUtil {

    private Context context;

    @Inject
    public OSUtil(Context context) {
        this.context = context;
    }

    public boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo s : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(s.service.getClassName())) {
                return true;
            }
        }

        return false;
    }

    public boolean hasPackage(String p) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(p, 0);
            return true;
        } catch (NameNotFoundException nnfe) {
            return false;
        }

    }

}
