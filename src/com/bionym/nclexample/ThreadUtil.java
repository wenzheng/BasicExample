package com.bionym.nclexample;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.os.Handler;
import android.os.Looper;

public class ThreadUtil {
	protected static ScheduledThreadPoolExecutor executor;
	
	/**
	 * 
	 * @return true if the calling code is running under the mail looper 
	 */
	public static boolean isRunByMainLopper() {
		Looper looper = Looper.getMainLooper();
		
		if (looper != null) {
			return looper.getThread() == Thread.currentThread();
		}
		
		return false;
	}
	
	/**
	 * Run the runnable on the main looper thread
	 * @param runnable the runnable
	 */
	public static void runOnMainLooper(Runnable runnable) {
		if (isRunByMainLopper()) {
			runnable.run();
		}
		else {
			Handler handler = new Handler(Looper.getMainLooper());
			handler.post(runnable);
		}
	}
	
	/**
	 * Run the runnable on the main looper thread
	 * @param runnable the runnable
	 * @param delay the milliseconds to delay
	 */
	public static void runOnMainLooper(Runnable runnable, long delay) {
		Handler handler = new Handler(Looper.getMainLooper());
		handler.postDelayed(runnable, delay);
	}

    /**
     * Run the runnable on a non-UI thread
     * @param runnable the runnable
     */
    public static void runTask(Runnable runnable) {
        runTaskAfterMillies(runnable, 0);
    }

    /**
     * Run a task on a non-UI thread after the delay
     * @param runnable the runnable
     * @param delay the delay to run the task, it is in milliseconds
     * @return a ScheduledFuture to managing the task
     */
    public static ScheduledFuture<?> runTaskAfterMillies(Runnable runnable, long delay) {
        if (executor == null) {
            synchronized (ProvisionController.class) {
                if (executor == null) {
                    executor = new ScheduledThreadPoolExecutor(2);
                }
            }
        }

        if (delay > 0) {
            return executor.schedule(runnable, delay, TimeUnit.MILLISECONDS);
        }
        else {
            executor.execute(runnable);

            return null;
        }
    }
}
