/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.example.osai;
public interface IFaceInference extends android.os.IInterface
{
  /** Default implementation for IFaceInference. */
  public static class Default implements com.example.osai.IFaceInference
  {
    /** Submit one upright JPEG frame and block until the pipeline finishes. */
    @Override public com.example.osai.FrameResult analyzeFrame(com.example.osai.FaceFrame frame) throws android.os.RemoteException
    {
      return null;
    }
    /** Register the last detected face under the given label. */
    @Override public boolean registerCurrent(java.lang.String label) throws android.os.RemoteException
    {
      return false;
    }
    @Override public java.lang.String[] listFaces() throws android.os.RemoteException
    {
      return null;
    }
    @Override public boolean deleteFace(java.lang.String label) throws android.os.RemoteException
    {
      return false;
    }
    @Override public boolean setIdentifyEnabled(boolean enabled) throws android.os.RemoteException
    {
      return false;
    }
    @Override public boolean setLivenessEnabled(boolean enabled) throws android.os.RemoteException
    {
      return false;
    }
    @Override public void setThreadCount(int count) throws android.os.RemoteException
    {
    }
    /** One-line status string. */
    @Override public java.lang.String getStatus() throws android.os.RemoteException
    {
      return null;
    }
    /** JSON snapshot used by the system status page. */
    @Override public java.lang.String getDebugSnapshot() throws android.os.RemoteException
    {
      return null;
    }
    @Override public void resetStats() throws android.os.RemoteException
    {
    }
    /** Kill the inference process on purpose, to test death/reconnect handling. */
    @Override public void crashForTest() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.example.osai.IFaceInference
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.example.osai.IFaceInference interface,
     * generating a proxy if needed.
     */
    public static com.example.osai.IFaceInference asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.example.osai.IFaceInference))) {
        return ((com.example.osai.IFaceInference)iin);
      }
      return new com.example.osai.IFaceInference.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_analyzeFrame:
        {
          com.example.osai.FaceFrame _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.example.osai.FaceFrame.CREATOR);
          com.example.osai.FrameResult _result = this.analyzeFrame(_arg0);
          reply.writeNoException();
          _Parcel.writeTypedObject(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_registerCurrent:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          boolean _result = this.registerCurrent(_arg0);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_listFaces:
        {
          java.lang.String[] _result = this.listFaces();
          reply.writeNoException();
          reply.writeStringArray(_result);
          break;
        }
        case TRANSACTION_deleteFace:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          boolean _result = this.deleteFace(_arg0);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_setIdentifyEnabled:
        {
          boolean _arg0;
          _arg0 = (0!=data.readInt());
          boolean _result = this.setIdentifyEnabled(_arg0);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_setLivenessEnabled:
        {
          boolean _arg0;
          _arg0 = (0!=data.readInt());
          boolean _result = this.setLivenessEnabled(_arg0);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_setThreadCount:
        {
          int _arg0;
          _arg0 = data.readInt();
          this.setThreadCount(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_getStatus:
        {
          java.lang.String _result = this.getStatus();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_getDebugSnapshot:
        {
          java.lang.String _result = this.getDebugSnapshot();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_resetStats:
        {
          this.resetStats();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_crashForTest:
        {
          this.crashForTest();
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.example.osai.IFaceInference
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      /** Submit one upright JPEG frame and block until the pipeline finishes. */
      @Override public com.example.osai.FrameResult analyzeFrame(com.example.osai.FaceFrame frame) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        com.example.osai.FrameResult _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, frame, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_analyzeFrame, _data, _reply, 0);
          _reply.readException();
          _result = _Parcel.readTypedObject(_reply, com.example.osai.FrameResult.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /** Register the last detected face under the given label. */
      @Override public boolean registerCurrent(java.lang.String label) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(label);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerCurrent, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String[] listFaces() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_listFaces, _data, _reply, 0);
          _reply.readException();
          _result = _reply.createStringArray();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean deleteFace(java.lang.String label) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(label);
          boolean _status = mRemote.transact(Stub.TRANSACTION_deleteFace, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean setIdentifyEnabled(boolean enabled) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((enabled)?(1):(0)));
          boolean _status = mRemote.transact(Stub.TRANSACTION_setIdentifyEnabled, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean setLivenessEnabled(boolean enabled) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((enabled)?(1):(0)));
          boolean _status = mRemote.transact(Stub.TRANSACTION_setLivenessEnabled, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void setThreadCount(int count) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(count);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setThreadCount, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** One-line status string. */
      @Override public java.lang.String getStatus() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getStatus, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /** JSON snapshot used by the system status page. */
      @Override public java.lang.String getDebugSnapshot() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getDebugSnapshot, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void resetStats() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_resetStats, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /** Kill the inference process on purpose, to test death/reconnect handling. */
      @Override public void crashForTest() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_crashForTest, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_analyzeFrame = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_registerCurrent = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_listFaces = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_deleteFace = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_setIdentifyEnabled = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_setLivenessEnabled = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_setThreadCount = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    static final int TRANSACTION_getStatus = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    static final int TRANSACTION_getDebugSnapshot = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
    static final int TRANSACTION_resetStats = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
    static final int TRANSACTION_crashForTest = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
  }
  public static final java.lang.String DESCRIPTOR = "com.example.osai.IFaceInference";
  /** Submit one upright JPEG frame and block until the pipeline finishes. */
  public com.example.osai.FrameResult analyzeFrame(com.example.osai.FaceFrame frame) throws android.os.RemoteException;
  /** Register the last detected face under the given label. */
  public boolean registerCurrent(java.lang.String label) throws android.os.RemoteException;
  public java.lang.String[] listFaces() throws android.os.RemoteException;
  public boolean deleteFace(java.lang.String label) throws android.os.RemoteException;
  public boolean setIdentifyEnabled(boolean enabled) throws android.os.RemoteException;
  public boolean setLivenessEnabled(boolean enabled) throws android.os.RemoteException;
  public void setThreadCount(int count) throws android.os.RemoteException;
  /** One-line status string. */
  public java.lang.String getStatus() throws android.os.RemoteException;
  /** JSON snapshot used by the system status page. */
  public java.lang.String getDebugSnapshot() throws android.os.RemoteException;
  public void resetStats() throws android.os.RemoteException;
  /** Kill the inference process on purpose, to test death/reconnect handling. */
  public void crashForTest() throws android.os.RemoteException;
  /** @hide */
  static class _Parcel {
    static private <T> T readTypedObject(
        android.os.Parcel parcel,
        android.os.Parcelable.Creator<T> c) {
      if (parcel.readInt() != 0) {
          return c.createFromParcel(parcel);
      } else {
          return null;
      }
    }
    static private <T extends android.os.Parcelable> void writeTypedObject(
        android.os.Parcel parcel, T value, int parcelableFlags) {
      if (value != null) {
        parcel.writeInt(1);
        value.writeToParcel(parcel, parcelableFlags);
      } else {
        parcel.writeInt(0);
      }
    }
  }
}
