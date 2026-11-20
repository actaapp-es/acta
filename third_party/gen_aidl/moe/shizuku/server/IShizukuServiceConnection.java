package moe.shizuku.server;
public interface IShizukuServiceConnection extends android.os.IInterface {
    void connected(android.os.IBinder service) throws android.os.RemoteException;
    void died() throws android.os.RemoteException;
    abstract class Stub extends android.os.Binder implements IShizukuServiceConnection {
        private static final String DESCRIPTOR = "moe.shizuku.server.IShizukuServiceConnection";
        static final int TRANSACTION_connected_0 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
        static final int TRANSACTION_died_1 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
        public Stub() { this.attachInterface(this, DESCRIPTOR); }
        public static IShizukuServiceConnection asInterface(android.os.IBinder obj) {
            if (obj == null) return null;
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && iin instanceof IShizukuServiceConnection) return (IShizukuServiceConnection) iin;
            return new Proxy(obj);
        }
        @Override public android.os.IBinder asBinder() { return this; }
        @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
            if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) { data.enforceInterface(DESCRIPTOR); }
            switch (code) {
                case INTERFACE_TRANSACTION: { reply.writeString(DESCRIPTOR); return true; }
                case TRANSACTION_connected_0: {
                    android.os.IBinder _a0 = data.readStrongBinder();
                    this.connected(_a0);
                    return true;
                }
                case TRANSACTION_died_1: {
                    this.died();
                    return true;
                }
                default: return super.onTransact(code, data, reply, flags);
            }
        }
        private static class Proxy implements IShizukuServiceConnection {
            private final android.os.IBinder mRemote;
            Proxy(android.os.IBinder remote) { mRemote = remote; }
            @Override public android.os.IBinder asBinder() { return mRemote; }
            @Override public void connected(android.os.IBinder service) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder(service);
                    mRemote.transact(TRANSACTION_connected_0, _data, null, android.os.IBinder.FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }
            @Override public void died() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_died_1, _data, null, android.os.IBinder.FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }
        }
    }
}
