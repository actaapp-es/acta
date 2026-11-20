package moe.shizuku.server;
public interface IShizukuService extends android.os.IInterface {
    int getVersion() throws android.os.RemoteException;
    int getUid() throws android.os.RemoteException;
    int checkPermission(String permission) throws android.os.RemoteException;
    moe.shizuku.server.IRemoteProcess newProcess(String[] cmd, String[] env, String dir) throws android.os.RemoteException;
    String getSELinuxContext() throws android.os.RemoteException;
    String getSystemProperty(String name, String defaultValue) throws android.os.RemoteException;
    void setSystemProperty(String name, String value) throws android.os.RemoteException;
    int addUserService(moe.shizuku.server.IShizukuServiceConnection conn, android.os.Bundle args) throws android.os.RemoteException;
    int removeUserService(moe.shizuku.server.IShizukuServiceConnection conn, android.os.Bundle args) throws android.os.RemoteException;
    void requestPermission(int requestCode) throws android.os.RemoteException;
    boolean checkSelfPermission() throws android.os.RemoteException;
    boolean shouldShowRequestPermissionRationale() throws android.os.RemoteException;
    void attachApplication(moe.shizuku.server.IShizukuApplication application, android.os.Bundle args) throws android.os.RemoteException;
    void exit() throws android.os.RemoteException;
    void attachUserService(android.os.IBinder binder, android.os.Bundle options) throws android.os.RemoteException;
    void dispatchPackageChanged(android.content.Intent intent) throws android.os.RemoteException;
    boolean isHidden(int uid) throws android.os.RemoteException;
    void dispatchPermissionConfirmationResult(int requestUid, int requestPid, int requestCode, android.os.Bundle data) throws android.os.RemoteException;
    int getFlagsForUid(int uid, int mask) throws android.os.RemoteException;
    void updateFlagsForUid(int uid, int mask, int value) throws android.os.RemoteException;
    abstract class Stub extends android.os.Binder implements IShizukuService {
        private static final String DESCRIPTOR = "moe.shizuku.server.IShizukuService";
        static final int TRANSACTION_getVersion_2 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
        static final int TRANSACTION_getUid_3 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
        static final int TRANSACTION_checkPermission_4 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
        static final int TRANSACTION_newProcess_7 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
        static final int TRANSACTION_getSELinuxContext_8 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
        static final int TRANSACTION_getSystemProperty_9 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
        static final int TRANSACTION_setSystemProperty_10 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
        static final int TRANSACTION_addUserService_11 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
        static final int TRANSACTION_removeUserService_12 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
        static final int TRANSACTION_requestPermission_14 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 14);
        static final int TRANSACTION_checkSelfPermission_15 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 15);
        static final int TRANSACTION_shouldShowRequestPermissionRationale_16 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16);
        static final int TRANSACTION_attachApplication_17 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 17);
        static final int TRANSACTION_exit_100 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 100);
        static final int TRANSACTION_attachUserService_101 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 101);
        static final int TRANSACTION_dispatchPackageChanged_102 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 102);
        static final int TRANSACTION_isHidden_103 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 103);
        static final int TRANSACTION_dispatchPermissionConfirmationResult_104 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 104);
        static final int TRANSACTION_getFlagsForUid_105 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 105);
        static final int TRANSACTION_updateFlagsForUid_106 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 106);
        public Stub() { this.attachInterface(this, DESCRIPTOR); }
        public static IShizukuService asInterface(android.os.IBinder obj) {
            if (obj == null) return null;
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && iin instanceof IShizukuService) return (IShizukuService) iin;
            return new Proxy(obj);
        }
        @Override public android.os.IBinder asBinder() { return this; }
        @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
            if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) { data.enforceInterface(DESCRIPTOR); }
            switch (code) {
                case INTERFACE_TRANSACTION: { reply.writeString(DESCRIPTOR); return true; }
                case TRANSACTION_getVersion_2: {
                    int _r = this.getVersion();
                    reply.writeNoException();
                    reply.writeInt(_r);
                    return true;
                }
                case TRANSACTION_getUid_3: {
                    int _r = this.getUid();
                    reply.writeNoException();
                    reply.writeInt(_r);
                    return true;
                }
                case TRANSACTION_checkPermission_4: {
                    String _a0 = data.readString();
                    int _r = this.checkPermission(_a0);
                    reply.writeNoException();
                    reply.writeInt(_r);
                    return true;
                }
                case TRANSACTION_newProcess_7: {
                    String[] _a0 = data.createStringArray();
                    String[] _a1 = data.createStringArray();
                    String _a2 = data.readString();
                    moe.shizuku.server.IRemoteProcess _r = this.newProcess(_a0, _a1, _a2);
                    reply.writeNoException();
                    reply.writeStrongBinder(((((_r))!=null))?((_r).asBinder()):null);
                    return true;
                }
                case TRANSACTION_getSELinuxContext_8: {
                    String _r = this.getSELinuxContext();
                    reply.writeNoException();
                    reply.writeString(_r);
                    return true;
                }
                case TRANSACTION_getSystemProperty_9: {
                    String _a0 = data.readString();
                    String _a1 = data.readString();
                    String _r = this.getSystemProperty(_a0, _a1);
                    reply.writeNoException();
                    reply.writeString(_r);
                    return true;
                }
                case TRANSACTION_setSystemProperty_10: {
                    String _a0 = data.readString();
                    String _a1 = data.readString();
                    this.setSystemProperty(_a0, _a1);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_addUserService_11: {
                    moe.shizuku.server.IShizukuServiceConnection _a0 = moe.shizuku.server.IShizukuServiceConnection.Stub.asInterface(data.readStrongBinder());
                    android.os.Bundle _a1; if (0!=data.readInt()) { _a1 = android.os.Bundle.CREATOR.createFromParcel(data); } else { _a1 = null; }
                    int _r = this.addUserService(_a0, _a1);
                    reply.writeNoException();
                    reply.writeInt(_r);
                    return true;
                }
                case TRANSACTION_removeUserService_12: {
                    moe.shizuku.server.IShizukuServiceConnection _a0 = moe.shizuku.server.IShizukuServiceConnection.Stub.asInterface(data.readStrongBinder());
                    android.os.Bundle _a1; if (0!=data.readInt()) { _a1 = android.os.Bundle.CREATOR.createFromParcel(data); } else { _a1 = null; }
                    int _r = this.removeUserService(_a0, _a1);
                    reply.writeNoException();
                    reply.writeInt(_r);
                    return true;
                }
                case TRANSACTION_requestPermission_14: {
                    int _a0 = data.readInt();
                    this.requestPermission(_a0);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_checkSelfPermission_15: {
                    boolean _r = this.checkSelfPermission();
                    reply.writeNoException();
                    reply.writeInt((((_r))?1:0));
                    return true;
                }
                case TRANSACTION_shouldShowRequestPermissionRationale_16: {
                    boolean _r = this.shouldShowRequestPermissionRationale();
                    reply.writeNoException();
                    reply.writeInt((((_r))?1:0));
                    return true;
                }
                case TRANSACTION_attachApplication_17: {
                    moe.shizuku.server.IShizukuApplication _a0 = moe.shizuku.server.IShizukuApplication.Stub.asInterface(data.readStrongBinder());
                    android.os.Bundle _a1; if (0!=data.readInt()) { _a1 = android.os.Bundle.CREATOR.createFromParcel(data); } else { _a1 = null; }
                    this.attachApplication(_a0, _a1);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_exit_100: {
                    this.exit();
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_attachUserService_101: {
                    android.os.IBinder _a0 = data.readStrongBinder();
                    android.os.Bundle _a1; if (0!=data.readInt()) { _a1 = android.os.Bundle.CREATOR.createFromParcel(data); } else { _a1 = null; }
                    this.attachUserService(_a0, _a1);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_dispatchPackageChanged_102: {
                    android.content.Intent _a0; if (0!=data.readInt()) { _a0 = android.content.Intent.CREATOR.createFromParcel(data); } else { _a0 = null; }
                    this.dispatchPackageChanged(_a0);
                    return true;
                }
                case TRANSACTION_isHidden_103: {
                    int _a0 = data.readInt();
                    boolean _r = this.isHidden(_a0);
                    reply.writeNoException();
                    reply.writeInt((((_r))?1:0));
                    return true;
                }
                case TRANSACTION_dispatchPermissionConfirmationResult_104: {
                    int _a0 = data.readInt();
                    int _a1 = data.readInt();
                    int _a2 = data.readInt();
                    android.os.Bundle _a3; if (0!=data.readInt()) { _a3 = android.os.Bundle.CREATOR.createFromParcel(data); } else { _a3 = null; }
                    this.dispatchPermissionConfirmationResult(_a0, _a1, _a2, _a3);
                    return true;
                }
                case TRANSACTION_getFlagsForUid_105: {
                    int _a0 = data.readInt();
                    int _a1 = data.readInt();
                    int _r = this.getFlagsForUid(_a0, _a1);
                    reply.writeNoException();
                    reply.writeInt(_r);
                    return true;
                }
                case TRANSACTION_updateFlagsForUid_106: {
                    int _a0 = data.readInt();
                    int _a1 = data.readInt();
                    int _a2 = data.readInt();
                    this.updateFlagsForUid(_a0, _a1, _a2);
                    reply.writeNoException();
                    return true;
                }
                default: return super.onTransact(code, data, reply, flags);
            }
        }
        private static class Proxy implements IShizukuService {
            private final android.os.IBinder mRemote;
            Proxy(android.os.IBinder remote) { mRemote = remote; }
            @Override public android.os.IBinder asBinder() { return mRemote; }
            @Override public int getVersion() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_getVersion_2, _data, _reply, 0);
                    _reply.readException();
                    int __ret = _reply.readInt();
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public int getUid() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_getUid_3, _data, _reply, 0);
                    _reply.readException();
                    int __ret = _reply.readInt();
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public int checkPermission(String permission) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(permission);
                    mRemote.transact(TRANSACTION_checkPermission_4, _data, _reply, 0);
                    _reply.readException();
                    int __ret = _reply.readInt();
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public moe.shizuku.server.IRemoteProcess newProcess(String[] cmd, String[] env, String dir) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                moe.shizuku.server.IRemoteProcess _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStringArray(cmd);
                    _data.writeStringArray(env);
                    _data.writeString(dir);
                    mRemote.transact(TRANSACTION_newProcess_7, _data, _reply, 0);
                    _reply.readException();
                    moe.shizuku.server.IRemoteProcess __ret = moe.shizuku.server.IRemoteProcess.Stub.asInterface(_reply.readStrongBinder());
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public String getSELinuxContext() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                String _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_getSELinuxContext_8, _data, _reply, 0);
                    _reply.readException();
                    String __ret = _reply.readString();
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public String getSystemProperty(String name, String defaultValue) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                String _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(name);
                    _data.writeString(defaultValue);
                    mRemote.transact(TRANSACTION_getSystemProperty_9, _data, _reply, 0);
                    _reply.readException();
                    String __ret = _reply.readString();
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public void setSystemProperty(String name, String value) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(name);
                    _data.writeString(value);
                    mRemote.transact(TRANSACTION_setSystemProperty_10, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
            @Override public int addUserService(moe.shizuku.server.IShizukuServiceConnection conn, android.os.Bundle args) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder(((((conn))!=null))?((conn).asBinder()):null);
                    if ((args)!=null) { _data.writeInt(1); (args).writeToParcel(_data, 0); } else { _data.writeInt(0); }
                    mRemote.transact(TRANSACTION_addUserService_11, _data, _reply, 0);
                    _reply.readException();
                    int __ret = _reply.readInt();
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public int removeUserService(moe.shizuku.server.IShizukuServiceConnection conn, android.os.Bundle args) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder(((((conn))!=null))?((conn).asBinder()):null);
                    if ((args)!=null) { _data.writeInt(1); (args).writeToParcel(_data, 0); } else { _data.writeInt(0); }
                    mRemote.transact(TRANSACTION_removeUserService_12, _data, _reply, 0);
                    _reply.readException();
                    int __ret = _reply.readInt();
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public void requestPermission(int requestCode) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt(requestCode);
                    mRemote.transact(TRANSACTION_requestPermission_14, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
            @Override public boolean checkSelfPermission() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                boolean _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_checkSelfPermission_15, _data, _reply, 0);
                    _reply.readException();
                    boolean __ret = (0!=_reply.readInt());
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public boolean shouldShowRequestPermissionRationale() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                boolean _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_shouldShowRequestPermissionRationale_16, _data, _reply, 0);
                    _reply.readException();
                    boolean __ret = (0!=_reply.readInt());
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public void attachApplication(moe.shizuku.server.IShizukuApplication application, android.os.Bundle args) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder(((((application))!=null))?((application).asBinder()):null);
                    if ((args)!=null) { _data.writeInt(1); (args).writeToParcel(_data, 0); } else { _data.writeInt(0); }
                    mRemote.transact(TRANSACTION_attachApplication_17, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
            @Override public void exit() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_exit_100, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
            @Override public void attachUserService(android.os.IBinder binder, android.os.Bundle options) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder(binder);
                    if ((options)!=null) { _data.writeInt(1); (options).writeToParcel(_data, 0); } else { _data.writeInt(0); }
                    mRemote.transact(TRANSACTION_attachUserService_101, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
            @Override public void dispatchPackageChanged(android.content.Intent intent) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    if ((intent)!=null) { _data.writeInt(1); (intent).writeToParcel(_data, 0); } else { _data.writeInt(0); }
                    mRemote.transact(TRANSACTION_dispatchPackageChanged_102, _data, null, android.os.IBinder.FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }
            @Override public boolean isHidden(int uid) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                boolean _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt(uid);
                    mRemote.transact(TRANSACTION_isHidden_103, _data, _reply, 0);
                    _reply.readException();
                    boolean __ret = (0!=_reply.readInt());
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public void dispatchPermissionConfirmationResult(int requestUid, int requestPid, int requestCode, android.os.Bundle data) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt(requestUid);
                    _data.writeInt(requestPid);
                    _data.writeInt(requestCode);
                    if ((data)!=null) { _data.writeInt(1); (data).writeToParcel(_data, 0); } else { _data.writeInt(0); }
                    mRemote.transact(TRANSACTION_dispatchPermissionConfirmationResult_104, _data, null, android.os.IBinder.FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }
            @Override public int getFlagsForUid(int uid, int mask) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt(uid);
                    _data.writeInt(mask);
                    mRemote.transact(TRANSACTION_getFlagsForUid_105, _data, _reply, 0);
                    _reply.readException();
                    int __ret = _reply.readInt();
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public void updateFlagsForUid(int uid, int mask, int value) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt(uid);
                    _data.writeInt(mask);
                    _data.writeInt(value);
                    mRemote.transact(TRANSACTION_updateFlagsForUid_106, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}
