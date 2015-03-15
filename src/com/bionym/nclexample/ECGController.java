package com.bionym.nclexample;

import com.bionym.ncl.Ncl;
import com.bionym.ncl.NclCallback;
import com.bionym.ncl.NclEvent;
import com.bionym.ncl.NclEventEcg;
import com.bionym.ncl.NclEventType;
import com.bionym.ncl.NclProvision;

import android.content.Context;
import android.util.Log;

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
				Log.d(LOG_TAG, "The sample values are"+((NclEventEcg) event).samples);
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
			state = State.Started;
			break;
		}
		case Started: {
			Ncl.stopEcgStream(nymiHandle);
			state = State.Stopped;
			break;
		}
		default: break;
		}
	}
}
