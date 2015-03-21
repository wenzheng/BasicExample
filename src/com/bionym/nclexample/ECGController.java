package com.bionym.nclexample;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

import com.bionym.ncl.Ncl;
import com.bionym.ncl.NclCallback;
import com.bionym.ncl.NclEvent;
import com.bionym.ncl.NclEventEcg;
import com.bionym.ncl.NclEventType;
import com.bionym.ncl.NclProvision;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class ECGController {
	// Constants
	protected static final String LOG_TAG = "Nymi ECG Controller";
	
	// NCL event callbacks
	protected static NclCallback nclCallback;
	
	// Last RSSI value received
	protected int rssi;

	long startFindingTime = 0L;

	// the current nymi handle
	protected int nymiHandle = Ncl.NYMI_HANDLE_ANY;

	// the current provision that has been made
	protected NclProvision provision;
	
	State state;

	Context context;
	
	private Timer timer;	
	
	private List<Integer> ecgData = new CopyOnWriteArrayList<Integer>();
	
	private AWSSimpleQueueServiceUtil awssqsUtil =   AWSSimpleQueueServiceUtil.getInstance();
	/**
	 * Constructor
	 * @param context the context
	 */
	public ECGController(Context context, int handler) {
		state = State.Stopped;
		if (nclCallback == null) {
			nclCallback = new MyNclCallback();
		}
		Ncl.addBehavior(nclCallback, null, NclEventType.NCL_EVENT_ANY,
				Ncl.NYMI_HANDLE_ANY);
		nymiHandle = handler;
		this.context = context;
		}

	/**
	 * Get the connected Nymi handler
	 * @return
	 */
	public int getNymiHandle() {
		return nymiHandle;
	}
	
	private void startTimer(){
		timer = new Timer();
		final Handler timerHandler = new Handler() {

			@Override
			public void handleMessage(Message msg) {
				super.handleMessage(msg);
				if(msg.what==1){
					StringBuilder sb = new StringBuilder();
					for (Integer test: ecgData){
						sb.append(test);
						sb.append("#");
					}
					Toast.makeText(context, "The size of data is: "+ecgData.size(),
	                        Toast.LENGTH_LONG).show();
					Log.d(LOG_TAG, "The size is "+ ecgData.size() + "and the values are: " + sb.toString());
					awssqsUtil.sendMessageToQueue(sb.toString());
					ecgData.clear();
				}
			}
		};
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				Message msg = new Message();
				msg.what = 1;
				timerHandler.sendMessage(msg);
			}

		}, 5000, 5000);

	}


	/**
	 * 
	 * @return the current state
	 */
	public State getState() {
		return state;
	}


	/**
	 * Callback for NclEventInit
	 *
	 */
	class MyNclCallback implements NclCallback {
		@Override
		public void call(final NclEvent event, final Object userData) {
			Log.d(LOG_TAG, this.toString() + ": " + event.getClass().getName());
			if (event instanceof NclEventEcg) {
				for (int i=0; i<((NclEventEcg) event).samples.length;i++ )
				ecgData.add(((NclEventEcg) event).samples[i]);
			}
		}
	}
	
	public enum State {
		Started,
		Stopped
	}

	public void updateState() {
		switch (state){
		case Stopped: {
			Ncl.startEcgStream(nymiHandle);
			startTimer();
			state = State.Started;
			break;
		}
		case Started: {
			Ncl.stopEcgStream(nymiHandle);
			state = State.Stopped;
			timer.cancel();
			break;
		}
		default: break;
		}
	}
}
