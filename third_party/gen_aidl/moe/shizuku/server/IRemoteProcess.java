package moe.shizuku.server;
public interface IRemoteProcess extends android.os.IInterface {
    android.os.ParcelFileDescriptor getOutputStream() throws android.os.RemoteException;
    android.os.ParcelFileDescriptor getInputStream() throws android.os.RemoteException;
    android.os.ParcelFileDescriptor getErrorStream() throws android.os.RemoteException;
    int waitFor() throws android.os.RemoteException;
    int exitValue() throws android.os.RemoteException;
    void destroy() throws android.os.RemoteException;
    boolean alive() throws android.os.RemoteException;
    boolean waitForTimeout(long timeout, String unit) throws android.os.RemoteException;
    abstract class Stub extends android.os.Binder implements IRemoteProcess {
        private static final String DESCRIPTOR = "moe.shizuku.server.IRemoteProcess";
        static final int TRANSACTION_getOutputStream_0 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
        static final int TRANSACTION_getInputStream_1 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
        static final int TRANSACTION_getErrorStream_2 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
        static final int TRANSACTION_waitFor_3 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
        static final int TRANSACTION_exitValue_4 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
        static final int TRANSACTION_destroy_5 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
        static final int TRANSACTION_alive_6 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
        static final int TRANSACTION_waitForTimeout_7 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
        public Stub() { this.attachInterface(this, DESCRIPTOR); }
        public static IRemoteProcess asInterface(android.os.IBinder obj) {
            if (obj == null) return null;
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && iin instanceof IRemoteProcess) return (IRemoteProcess) iin;
            return new Proxy(obj);
        }
        @Override public android.os.IBinder asBinder() { return this; }
        @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
            if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) { data.enforceInterface(DESCRIPTOR); }
            switch (code) {
                case INTERFACE_TRANSACTION: { reply.writeString(DESCRIPTOR); return true; }
                case TRANSACTION_getOutputStream_0: {
                    android.os.ParcelFileDescriptor _r = this.getOutputStream();
                    reply.writeNoException();
                    if ((_r)!=null) { reply.writeInt(1); (_r).writeToParcel(reply, 0); } else { reply.writeInt(0); }
                    return true;
                }
                case TRANSACTION_getInputStream_1: {
                    android.os.ParcelFileDescriptor _r = this.getInputStream();
                    reply.writeNoException();
                    if ((_r)!=null) { reply.writeInt(1); (_r).writeToParcel(reply, 0); } else { reply.writeInt(0); }
                    return true;
                }
                case TRANSACTION_getErrorStream_2: {
                    android.os.ParcelFileDescriptor _r = this.getErrorStream();
                    reply.writeNoException();
                    if ((_r)!=null) { reply.writeInt(1); (_r).writeToParcel(reply, 0); } else { reply.writeInt(0); }
                    return true;
                }
                case TRANSACTION_waitFor_3: {
                    int _r = this.waitFor();
                    reply.writeNoException();
                    reply.writeInt(_r);
                    return true;
                }
                case TRANSACTION_exitValue_4: {
                    int _r = this.exitValue();
                    reply.writeNoException();
                    reply.writeInt(_r);
                    return true;
                }
                case TRANSACTION_destroy_5: {
                    this.destroy();
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_alive_6: {
                    boolean _r = this.alive();
                    reply.writeNoException();
                    reply.writeInt((((_r))?1:0));
                    return true;
                }
                case TRANSACTION_waitForTimeout_7: {
                    long _a0 = data.readLong();
                    String _a1 = data.readString();
                    boolean _r = this.waitForTimeout(_a0, _a1);
                    reply.writeNoException();
                    reply.writeInt((((_r))?1:0));
                    return true;
                }
                default: return super.onTransact(code, data, reply, flags);
            }
        }
        private static class Proxy implements IRemoteProcess {
            private final android.os.IBinder mRemote;
            Proxy(android.os.IBinder remote) { mRemote = remote; }
            @Override public android.os.IBinder asBinder() { return mRemote; }
            @Override public android.os.ParcelFileDescriptor getOutputStream() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                android.os.ParcelFileDescriptor _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_getOutputStream_0, _data, _reply, 0);
                    _reply.readException();
                    android.os.ParcelFileDescriptor __ret; if (0!=_reply.readInt()) { __ret = android.os.ParcelFileDescriptor.CREATOR.createFromParcel(_reply); } else { __ret = null; }
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public android.os.ParcelFileDescriptor getInputStream() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                android.os.ParcelFileDescriptor _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_getInputStream_1, _data, _reply, 0);
                    _reply.readException();
                    android.os.ParcelFileDescriptor __ret; if (0!=_reply.readInt()) { __ret = android.os.ParcelFileDescriptor.CREATOR.createFromParcel(_reply); } else { __ret = null; }
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public android.os.ParcelFileDescriptor getErrorStream() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                android.os.ParcelFileDescriptor _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_getErrorStream_2, _data, _reply, 0);
                    _reply.readException();
                    android.os.ParcelFileDescriptor __ret; if (0!=_reply.readInt()) { __ret = android.os.ParcelFileDescriptor.CREATOR.createFromParcel(_reply); } else { __ret = null; }
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public int waitFor() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_waitFor_3, _data, _reply, 0);
                    _reply.readException();
                    int __ret = _reply.readInt();
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public int exitValue() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_exitValue_4, _data, _reply, 0);
                    _reply.readException();
                    int __ret = _reply.readInt();
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public void destroy() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_destroy_5, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
            @Override public boolean alive() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                boolean _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_alive_6, _data, _reply, 0);
                    _reply.readException();
                    boolean __ret = (0!=_reply.readInt());
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public boolean waitForTimeout(long timeout, String unit) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                boolean _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeLong(timeout);
                    _data.writeString(unit);
                    mRemote.transact(TRANSACTION_waitForTimeout_7, _data, _reply, 0);
                    _reply.readException();
                    boolean __ret = (0!=_reply.readInt());
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
        }
    }
}
