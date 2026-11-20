package com.acta.recorder;
public interface IRecorderService extends android.os.IInterface {
    int start(android.os.ParcelFileDescriptor pfd, int wantSource) throws android.os.RemoteException;
    long stop() throws android.os.RemoteException;
    boolean isRecording() throws android.os.RemoteException;
    int lastSource() throws android.os.RemoteException;
    void destroy() throws android.os.RemoteException;
    abstract class Stub extends android.os.Binder implements IRecorderService {
        private static final String DESCRIPTOR = "com.acta.recorder.IRecorderService";
        static final int TRANSACTION_start_0 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
        static final int TRANSACTION_stop_1 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
        static final int TRANSACTION_isRecording_2 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
        static final int TRANSACTION_lastSource_3 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
        static final int TRANSACTION_destroy_4 = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
        public Stub() { this.attachInterface(this, DESCRIPTOR); }
        public static IRecorderService asInterface(android.os.IBinder obj) {
            if (obj == null) return null;
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && iin instanceof IRecorderService) return (IRecorderService) iin;
            return new Proxy(obj);
        }
        @Override public android.os.IBinder asBinder() { return this; }
        @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
            if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) { data.enforceInterface(DESCRIPTOR); }
            switch (code) {
                case INTERFACE_TRANSACTION: { reply.writeString(DESCRIPTOR); return true; }
                case TRANSACTION_start_0: {
                    android.os.ParcelFileDescriptor _a0; if (0!=data.readInt()) { _a0 = android.os.ParcelFileDescriptor.CREATOR.createFromParcel(data); } else { _a0 = null; }
                    int _a1 = data.readInt();
                    int _r = this.start(_a0, _a1);
                    reply.writeNoException();
                    reply.writeInt(_r);
                    return true;
                }
                case TRANSACTION_stop_1: {
                    long _r = this.stop();
                    reply.writeNoException();
                    reply.writeLong(_r);
                    return true;
                }
                case TRANSACTION_isRecording_2: {
                    boolean _r = this.isRecording();
                    reply.writeNoException();
                    reply.writeInt((((_r))?1:0));
                    return true;
                }
                case TRANSACTION_lastSource_3: {
                    int _r = this.lastSource();
                    reply.writeNoException();
                    reply.writeInt(_r);
                    return true;
                }
                case TRANSACTION_destroy_4: {
                    this.destroy();
                    reply.writeNoException();
                    return true;
                }
                default: return super.onTransact(code, data, reply, flags);
            }
        }
        private static class Proxy implements IRecorderService {
            private final android.os.IBinder mRemote;
            Proxy(android.os.IBinder remote) { mRemote = remote; }
            @Override public android.os.IBinder asBinder() { return mRemote; }
            @Override public int start(android.os.ParcelFileDescriptor pfd, int wantSource) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    if ((pfd)!=null) { _data.writeInt(1); (pfd).writeToParcel(_data, 0); } else { _data.writeInt(0); }
                    _data.writeInt(wantSource);
                    mRemote.transact(TRANSACTION_start_0, _data, _reply, 0);
                    _reply.readException();
                    int __ret = _reply.readInt();
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public long stop() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                long _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_stop_1, _data, _reply, 0);
                    _reply.readException();
                    long __ret = _reply.readLong();
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public boolean isRecording() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                boolean _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_isRecording_2, _data, _reply, 0);
                    _reply.readException();
                    boolean __ret = (0!=_reply.readInt());
                    _result = __ret;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
                return _result;
            }
            @Override public int lastSource() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                android.os.Parcel _reply = android.os.Parcel.obtain();
                int _result;
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_lastSource_3, _data, _reply, 0);
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
                    mRemote.transact(TRANSACTION_destroy_4, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}
