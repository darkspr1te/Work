package com.mct.serial.interphone;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

import com.mct.utils.ServiceHelper;

import android.content.pm.PackageParser.NewPermissionInfo;
import android.os.SystemClock;
import android.util.Log;

public class InterPhoneSerial
{
	
	private boolean mIsSerialOpen = false;
	private static final String TAG = "InterPhoneSerial";
	
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public int open(String port)
	{
		Log.i(TAG, "open serial:"+port);
		mIsSerialOpen = openSerial(port);
		if (mIsSerialOpen)
		{
			mWriteThread = new WriteThread();
			mWriteThread.start();
		}
		return mIsSerialOpen ? 1 : 0;
	}

	public int close()
	{
		Log.i(TAG, "close serial");
		mIsSerialOpen = false;
		return closeSerial() ? 1 : 0;
	}

	public int getLastError()
	{
		return 0;
	}

	public boolean init(int packageLength, int readElapse, int writeElapse, int deviceType)
	{
		Log.i(TAG, "init interphone mcu serial");
		if (packageLength <= 0 || readElapse < 0 || writeElapse < 0)
			return false;

		mPackageLength = packageLength;
		mReadWaitTick = readElapse;
		mWriteWaitTick = writeElapse;
		mDeviceType = deviceType;
		return true;
	}

	public boolean send(ByteBuffer data)
	{
		if (!mIsSerialOpen)
			return false;
		if (data.position() < 2) { return false; }
		try
		{
			synchronized (mWriteThread)
			{
				long nElapse = SystemClock.uptimeMillis() - mWriteWaitElapse;
				if (nElapse < mWriteWaitTick)
				{
					Thread.sleep(mWriteWaitTick - nElapse);
				}
				if(writeData(data.position(),data.array()))
				{
					Log.i(TAG, "write data success,length: "+data.position()+",data: "+ServiceHelper.toHexString(data));
				}
				else
				{
					Log.e(TAG, "write data failed,length: "+data.position()+",data: "+ServiceHelper.toHexString(data));
				}
				mWriteWaitElapse = (int) SystemClock.uptimeMillis();
			}
			onSend(data);
			return true;
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		return false;
	}

	public boolean post(ByteBuffer data)
	{
		if (!mIsSerialOpen)
			return false;
		synchronized (mWriteThread)
		{
			return mWirteBuffers.add(data);
		}
	}

	public void requestListener(Listener ls)
	{
		mListener = ls;
	}

	public void removeListener(Object ls)
	{
		mListener = null;
	}
	
	/*public static void setReceivedAck(boolean receivedAck)
	{
		Log.i(TAG,"setReceivedAck:"+receivedAck);
		mReceivedAck = receivedAck;
	}
	
	public ByteBuffer getLastData()
	{
		return mLastData;
	}*/

	public interface Listener
	{
		public void onReceive(ByteBuffer data);

		public void onSend(ByteBuffer data);
	};

	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public static Object mListener = null;

	public void onReceiveData(int length, byte[] data)
	{
		ByteBuffer buffer = ByteBuffer.allocate(length);
		buffer.put(data);
		Log.i(TAG, "onReceive: "+ServiceHelper.toHexString(buffer));
		try
		{
			if (mListener != null)
				Listener.class.cast(mListener).onReceive(buffer);
			else {
				
				Log.e(TAG, "listener is null");
			}
		} catch (Exception e)
		{
			e.printStackTrace();
			Log.e(TAG, "onReceive  meet exception");
		}
	}

	protected void onSend(ByteBuffer data)
	{
		try
		{
			if (mListener != null)
				Listener.class.cast(mListener).onSend(data);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	// protected ReadThread mReadThread = null;
	protected int mReadThreadState = 0;
	protected int mReadWaitTick = 50;
	protected int mPackageLength = 100;
	protected int mDeviceType = 0x01;

	protected WriteThread mWriteThread = null;
	protected int mWriteThreadState = 0;
	protected int mWriteWaitTick = 50;
	protected int mWriteWaitElapse = 0;
	protected LinkedBlockingQueue<ByteBuffer> mWirteBuffers = new LinkedBlockingQueue<ByteBuffer>();

	protected class WriteThread extends Thread
	{
		public void run()
		{
			synchronized (mWriteThread)
			{
				// 0->1 running
				mWriteThreadState = 1;
				mWriteThread.notifyAll();
			}

			ByteBuffer buffer = null;
			while (!isInterrupted() && mWriteThreadState == 1)
			{
				try
				{
					synchronized (mWriteThread)
					{
						buffer = mWirteBuffers.poll();
					}
					if (buffer != null)
					{
						send(buffer);
						Log.i(TAG,"write buffer left size:"+mWirteBuffers.size());
					}
					Thread.sleep(mWriteWaitTick);
				} catch (Exception e)
				{
					e.printStackTrace();
				}
			}

			synchronized (mWriteThread)
			{
				// 0->2 stopped
				mWriteThreadState = 2;
				mWriteThread.notifyAll();
			}
		}
	}

	private static native boolean openSerial(String portName);

	private static native boolean closeSerial();

	private static native boolean writeData( int len,byte[] data);

	static
	{
		try
		{
			System.loadLibrary("interphone_serial");
		} catch (Exception e)
		{
			e.printStackTrace();
			Log.i(TAG, "load libinterphone_serial.so failed");
		}
	}
}
