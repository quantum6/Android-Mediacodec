package net.quantum6.kit;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import net.quantum6.fps.FpsCounter;

import android.hardware.Camera;

public abstract class CameraDataThread implements Runnable, Camera.PreviewCallback
{
    private final static String TAG = CameraDataThread.class.getCanonicalName();
    
    private final static int BUFFER_COUNT_MIN   = 2;
    private final static int BUFFER_COUNT_MAX   = 5;
    
    private final static int DEFAULT_FPS  = 30;
    
    private List<byte[]>  mCameraDataList = Collections.synchronizedList(new LinkedList<byte[]>());
    private List<byte[]>  mEmptyDataList  = Collections.synchronizedList(new LinkedList<byte[]>());

    private FpsCounter mFps = new FpsCounter();
    private boolean threadRunning;
    private Camera mCamera;
    
    private long timeLast;
    private long[] timeArray = new long[DEFAULT_FPS*10];
    private int timeIndex;
    private long timeMax;

    public abstract void onCameraDataArrived(final byte[] data, Camera camera);
    
    public void stop()
    {
        threadRunning = false;
        mCameraDataList.clear();
        mEmptyDataList.clear();
    }
    
    public int getFps()
    {
        return mFps.getFpsAndClear();
    }
    
    public long getMaxTime()
    {
        return timeMax;
    }
    
    @Override
    public void onPreviewFrame(final byte[] data, final Camera camera)
    {
        if (!threadRunning)
        {
            return;
        }
        
        // check max time.
        if (timeLast == 0)
        {
            timeLast = System.currentTimeMillis();
        }
        else
        {
            long currentTime = System.currentTimeMillis();
            timeArray[timeIndex] = (currentTime-timeLast);
            timeIndex ++;
            if (timeIndex == timeArray.length)
            {
                timeIndex = 0;
            }
            timeLast = currentTime;
            
            timeMax = 0;
            for (int i=0; i<timeArray.length; i++)
            {
                if (timeMax < timeArray[i])
                {
                    timeMax = timeArray[i];
                }
            }
        }

        mFps.count();
        if (mCameraDataList.size() >= BUFFER_COUNT_MAX)
        {
            camera.addCallbackBuffer(data);
            return;
        }
        
        mCamera = camera;
        
        byte[] buffer = null;
        if (mEmptyDataList.size() > 0)
        {
            buffer = mEmptyDataList.remove(0);
        }
        if (buffer == null)
        {
            buffer = new byte[data.length];
        }
        if (buffer.length != data.length)
        {
            mEmptyDataList.clear();
            buffer = new byte[data.length];
        }
        System.arraycopy(data, 0, buffer, 0, data.length);
       
        mCameraDataList.add(buffer);

        if (camera != null)
        {
            camera.addCallbackBuffer(data);
        }
    }
    
    @Override
    public void run()
    {
        threadRunning = true;
        long unit = 1000/DEFAULT_FPS;
        //Log.e(TAG, "run()"+threadRunning);
        
        while (threadRunning)
        {
            if (mCameraDataList.size() < BUFFER_COUNT_MIN)
            {
                SystemKit.sleep(unit);
                continue;
            }

            long startTime = System.currentTimeMillis();
            
            byte[] buffer = mCameraDataList.get(0);
            
            onCameraDataArrived(buffer, mCamera);
            mCameraDataList.remove(buffer);
            
            mEmptyDataList.add(buffer);
            SystemKit.sleep(System.currentTimeMillis()-startTime);
        }
        
        mCameraDataList.clear();
        mEmptyDataList.clear();
    }
}
