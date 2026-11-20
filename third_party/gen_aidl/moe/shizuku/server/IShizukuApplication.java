package moe.shizuku.server;
public interface IShizukuApplication extends android.os.IInterface {
    void bindApplication(android.os.Bundle data) throws android.os.RemoteException;
    void dispatchRequestPermissionResult(int requestCode, android.os.Bundle data) throws android.os.RemoteException;
    void showPermissionConfirmation(int requestUid, int requestPid, String requestPackageName, int requestCode) throws android.os.RemoteException;
    abstract class Stub extends android.os.Binder implements IShizukuApplication {
        private static final String DESCRIPTOR = "moe.shizuku.server.IShizukuApplication";
        static final int TRANSACTION_bindApplication_1 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
        static final int TRANSACTION_dispatchRequestPermissionResult_2 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
        static final int TRANSACTION_showPermissionConfirmation_10000 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10000);
        public Stub() { this.attachInterface(this, DESCRIPTOR); }
        public static IShizukuApplication asInterface(android.os.IBinder obj) {
            if (obj == null) return null;
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && iin instanceof IShizukuApplication) return (IShizukuApplication) iin;
            return new Proxy(obj);
        }
        @Override public android.os.IBinder asBinder() { return this; }
        @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
            if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) { data.enforceInterface(DESCRIPTOR); }
            switch (code) {
                case INTERFACE_TRANSACTION: { reply.writeString(DESCRIPTOR); return true; }
                case TRANSACTION_bindApplication_1: {
                    android.os.Bundle _a0; if (0!=data.readInt()) { _a0 = android.os.Bundle.CREATOR.createFromParcel(data); } else { _a0 = null; }
                    this.bindApplication(_a0);
                    return true;
                }
                case TRANSACTION_dispatchRequestPermissionResult_2: {
                    int _a0 = data.readInt();
                    android.os.Bundle _a1; if (0!=data.readInt()) { _a1 = android.os.Bundle.CREATOR.createFromParcel(data); } else { _a1 = null; }
                    this.dispatchRequestPermissionResult(_a0, _a1);
                    return true;
                }
                case TRANSACTION_showPermissionConfirmation_10000: {
                    int _a0 = data.readInt();
                    int _a1 = data.readInt();
                    String _a2 = data.readString();
                    int _a3 = data.readInt();
                    this.showPermissionConfirmation(_a0, _a1, _a2, _a3);
                    reply.writeNoException();
                    return true;
                }
                default: return super.onTransact(code, data, reply, flags);
            }
        }
        private static class Proxy implements IShizukuApplication {
            private final android.os.IBinder mRemote;
            Proxy(android.os.IBinder remote) { mRemote = remote; }
            @Override public android.os.IBinder asBinder() { return mRemote; }
            @Override public void bindApplication(android.os.Bundle data) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    if ((data)!=null) { _data.writeInt(1); (data).writeToParcel(_data, 0); } else { _data.writeInt(0); }
                    mRemote.transact(TRANSACTION_bindApplication_1, _data, null, android.os.IBinder.FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }
            @Override public void dispatchRequestPermissionResult(int requestCode, android.os.Bundle data) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt(requestCode);
                    if ((data)!=null) { _data.writeInt(1); (data).writeToParcel(_data, 0); } else { _data.writeInt(0); }
                    mRemote.transact(TRANSACTION_dispatchRequestPermissionResult_2, _data, null, android.os.IBinder.FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }
            @Override public void showPermissionConfirmation(int requestUid, int requestPid, String requestPackageName, int requestCode) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt(requestUid);
                    _data.writeInt(requestPid);
                    _data.writeString(requestPackageName);
                    _data.writeInt(requestCode);
                    mRemote.transact(TRANSACTION_showPermissionConfirmation_10000, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}
