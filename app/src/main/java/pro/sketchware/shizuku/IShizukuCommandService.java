package pro.sketchware.shizuku;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.Keep;

@Keep
public interface IShizukuCommandService extends IInterface {
    String installApk(String apkPath) throws RemoteException;
    String launchApp(String packageName) throws RemoteException;
    void destroy() throws RemoteException;

    @Keep
    abstract class Stub extends Binder implements IShizukuCommandService {
        private static final String DESCRIPTOR = "pro.sketchware.shizuku.IShizukuCommandService";
        private static final int TRANSACTION_INSTALL_APK = IBinder.FIRST_CALL_TRANSACTION;
        private static final int TRANSACTION_LAUNCH_APP = IBinder.FIRST_CALL_TRANSACTION + 1;
        private static final int TRANSACTION_DESTROY = 16777114;

        protected Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IShizukuCommandService asInterface(IBinder binder) {
            if (binder == null) return null;
            IInterface local = binder.queryLocalInterface(DESCRIPTOR);
            if (local instanceof IShizukuCommandService) return (IShizukuCommandService) local;
            return new Proxy(binder);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(DESCRIPTOR);
                return true;
            }
            if (code == TRANSACTION_DESTROY) {
                data.enforceInterface(DESCRIPTOR);
                destroy();
                if (reply != null) reply.writeNoException();
                return true;
            }
            if (code == TRANSACTION_INSTALL_APK) {
                data.enforceInterface(DESCRIPTOR);
                String result = installApk(data.readString());
                if (reply != null) {
                    reply.writeNoException();
                    reply.writeString(result);
                }
                return true;
            }
            if (code == TRANSACTION_LAUNCH_APP) {
                data.enforceInterface(DESCRIPTOR);
                String result = launchApp(data.readString());
                if (reply != null) {
                    reply.writeNoException();
                    reply.writeString(result);
                }
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static final class Proxy implements IShizukuCommandService {
            private final IBinder remote;

            Proxy(IBinder remote) {
                this.remote = remote;
            }

            @Override
            public IBinder asBinder() {
                return remote;
            }

            @Override
            public String installApk(String apkPath) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeString(apkPath);
                    if (!remote.transact(TRANSACTION_INSTALL_APK, data, reply, 0)) {
                        throw new RemoteException("APK install transaction is unavailable");
                    }
                    reply.readException();
                    return reply.readString();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public String launchApp(String packageName) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeString(packageName);
                    if (!remote.transact(TRANSACTION_LAUNCH_APP, data, reply, 0)) {
                        throw new RemoteException("App launch transaction is unavailable");
                    }
                    reply.readException();
                    return reply.readString();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void destroy() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (!remote.transact(TRANSACTION_DESTROY, data, reply, 0)) {
                        throw new RemoteException("Destroy transaction is unavailable");
                    }
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }
    }
}
