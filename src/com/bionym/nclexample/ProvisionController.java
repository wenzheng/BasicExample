package com.bionym.nclexample;

import java.util.Arrays;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.bionym.ncl.Ncl;
import com.bionym.ncl.NclCallback;
import com.bionym.ncl.NclEvent;
import com.bionym.ncl.NclEventAgreement;
import com.bionym.ncl.NclEventDisconnection;
import com.bionym.ncl.NclEventDiscovery;
import com.bionym.ncl.NclEventError;
import com.bionym.ncl.NclEventProvision;
import com.bionym.ncl.NclEventType;
import com.bionym.ncl.NclProvision;

/**
 * ProvisionController is responsible for the provision the device with a Nymi. It is responsible for initializing NCL, and orchestrate the provision process.
 * To listen on a provision process's progress, one should pass a ProvisionProcessListener to the controller when calling startProvisionProcess() method
 * to start the provision process.
 *
 */
@SuppressLint("UseSparseArrays")
public class ProvisionController {
	protected static final String LOG_TAG = "Nymi NCA ProvisionController";
	protected static final long DISCOVERY_WAIT_TIME_MILLIES = 3000;
    protected static final int RSSI_THRESHOLD = -70;
    public static final boolean USE_SECURE_PROVISION = false;

	protected NclCallback nclCallback;
	
	protected int nymiHandle = Ncl.NYMI_HANDLE_ANY;
    protected int rssi = Integer.MIN_VALUE;
	
	protected Context context;
	protected NclProvision provision;
	protected State state;
	protected ProvisionProcessListener provisionListener;

	protected boolean[][] ledPatterns;

	/**
	 * Constructor
	 * @param context the activity showing the provision UI
	 */
	public ProvisionController(Context context) {
		this.context = context;
	}

    /**
     *
     * @return the connected Nymi handle
     */
	public int getNymiHandle() {
		return nymiHandle;
	}

	/**
	 * Called to clean up resource after the provision process has ended
	 */
	public void finish() {
		stop();
	}
	
	/**
	 * Called to clean up resource after the provision process has ended
	 */
	public void stop() {
		if (nclCallback != null) {
			Ncl.removeBehavior(nclCallback, null, NclEventType.NCL_EVENT_ANY, Ncl.NYMI_HANDLE_ANY);
			nclCallback = null;
		}

        if (state == State.DISCOVERING) {
            state = null;
            Ncl.stopScan();
        }
        
        state = null;
	}
	
	/**
	 * 
	 * @return the current progress listener
	 */
	public ProvisionProcessListener getProvisionListener() {
		return provisionListener;
	}

	/**
	 * Set the progress listener
	 * @param provisionListener the listener
	 */
	public void setProvisionListener(ProvisionProcessListener provisionListener) {
		this.provisionListener = provisionListener;
	}

	/**
	 * 
	 * @return the agreement LED pattern
	 */
	public boolean[] getLedPatterns() {
		return ledPatterns != null? ledPatterns[0]: null;
	}

    public Context getContext() {
        return context;
    }

    /**
	 * Start the provision process
	 * @param listener the process listener
	 * @return true of the provision process is started
	 */
	public boolean startProvision(ProvisionProcessListener listener) {
		return startProvision(listener, DISCOVERY_WAIT_TIME_MILLIES, RSSI_THRESHOLD);
	}
	
	/**
	 * Start the provision process
	 * @param listener the process listener
	 * @param discoveryWaitTime time to wait for device discovery
	 * @return true of the provision process is started
	 */
	public boolean startProvision(ProvisionProcessListener listener,
                                        final long discoveryWaitTime, final int minRssi) {
		if (state != null) {
			return false;
		}

        nymiHandle = -1;
		if (nclCallback == null) {
			nclCallback = new MyNclCallback();
		}
	
        Ncl.addBehavior(nclCallback, null, NclEventType.NCL_EVENT_ANY, nymiHandle);

        provisionListener = listener;

        ThreadUtil.runTask(new Runnable() { // make sure we are not running on the UI thread
            @Override
            public void run() {
                doDiscovery(discoveryWaitTime, minRssi);
            }
        });
		
		return true;
	}

	/**
	 * Call this method to agree on the agreement pattern received from Nymi
	 * @return true if the operation is successful
	 */
	public boolean accept() {
		if (state != State.AGREED) {
			return false;
		}

		state = State.PROVISIONING;
		ThreadUtil.runTask(new Runnable() {
            @Override
            public void run() {
                Ncl.provision(nymiHandle, USE_SECURE_PROVISION);
            }
        });
		return true;
	}

	/**
	 * 
	 * @return the current provision process state
	 */
	public State getState() {
		return state;
	}

	/**
	 * 
	 * @return the agreement pattern
	 */
	public boolean[][] getAgreementPattern() {
		return ledPatterns;
	}

	/**
	 * 
	 * @return the provision that was made
	 */
	public NclProvision getProvision() {
		return provision;
	}

    /**
     * Perform Nymi discovery
     * @param discoveryWaitTime the time to wait
     * @param minRssi the minimal RSSI value to accept
     * @return true if the discovery can start
     */
	protected boolean doDiscovery(final long discoveryWaitTime, final int minRssi) {
		if (provisionListener != null) {
			provisionListener.onStartProcess(ProvisionController.this);
		}

        if (Ncl.startDiscovery() == false) {
            state = State.FAILED;
            Log.d(LOG_TAG, "Start discovery failed");
            if (provisionListener != null) {
                provisionListener.onFailure(ProvisionController.this);
            }

            return false;
        }

        state = State.DISCOVERING;

        // now wait for the device discovering to complete
        final long startTime = System.currentTimeMillis();
        ThreadUtil.runTaskAfterMillies(new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "end waiting");
                long remains = discoveryWaitTime + startTime - System.currentTimeMillis();
                if (remains <= 0 || (nymiHandle >= 0 && rssi >= minRssi)) {
                    if (Ncl.stopScan() == false) { // Cannot stop scan, raise failure
                        Log.d(LOG_TAG, "Stop scan failed!");
                        state = State.FAILED;
                        if (provisionListener != null) {
                            provisionListener.onFailure(ProvisionController.this);
                        }

                        Ncl.removeBehavior(nclCallback, null, NclEventType.NCL_EVENT_ANY, Ncl.NYMI_HANDLE_ANY);
                    }

                    if (nymiHandle < 0) {
                    		Log.d(LOG_TAG, "No Nymi!");
	                    ThreadUtil.runOnMainLooper(new Runnable() {
	                        @Override
	                        public void run() {
	            	                Toast.makeText(context, "Failed to find Nymi!",
	            	                        Toast.LENGTH_LONG).show();
	                        }
	                    });
                    }
                    else { // we have found a Nymi, let's get the agreement pattern
                    		Log.d(LOG_TAG, "Agree with: " + nymiHandle);
                    		if (!Ncl.agree(nymiHandle)) { // cannot get agreement pattern? raise failure
	                        Log.d(LOG_TAG, "Agreement failed!");
	                        if (provisionListener != null) {
	                            provisionListener.onFailure(ProvisionController.this);
	                        }
                    		}
                    		else { // advance to the agreement stage
	                        state = State.AGREEING;
                    		}
                    }
                }
                else {
                    ThreadUtil.runTaskAfterMillies(this, Math.min(500, remains));
                }
            }
        }, 1500);

        return true;
	}

	/**
	 * Interface for listening on the provision process
	 *
	 */
	public interface ProvisionProcessListener {
        /**
         * Called when the provision process is started
         * @param controller the ProvisionController performing the provision
         */
        public void onStartProcess(ProvisionController controller);

        /**
		 * Called when the agreement pattern is available
		 * @param controller the ProvisionController performing the provision
		 */
		public void onAgreement(ProvisionController controller);
		
		/**
		 * Called when the provision process is completed
		 * @param controller the ProvisionController performing the provision
		 */
		public void onProvisioned(ProvisionController controller);
		
		/**
		 * Called when the provision process failed
		 * @param controller the ProvisionController performing the provision
		 */
		public void onFailure(ProvisionController controller);
		
		/**
		 * Called when the connected Nymi during the provision process is disconnected 
		 * @param controller the ProvisionController performing the provision
		 */
		public void onDisconnected(ProvisionController controller);
	}
	
	/**
	 * Callback for NclEventInit
	 *
	 */
	class MyNclCallback implements NclCallback {
		@Override
		public void call(NclEvent event, Object userData) {
			Log.d(LOG_TAG, this.toString() + ": " + event.getClass().getName());
			if (event instanceof NclEventDiscovery) {
				Log.d(LOG_TAG, "Device discovered: " + 
										((NclEventDiscovery) event).nymiHandle + " rssi: " +
										((NclEventDiscovery) event).rssi);
				if (state == State.DISCOVERING) { // we are still in discovery mode
	                if (((NclEventDiscovery) event).rssi > rssi) {
	                    rssi = ((NclEventDiscovery) event).rssi;
	                    nymiHandle = ((NclEventDiscovery) event).nymiHandle;
					}
				}
			}
			else if (event instanceof NclEventAgreement) {
				if (((NclEventAgreement) event).nymiHandle == nymiHandle && state == State.AGREEING) {
					state = State.AGREED;
					nymiHandle = ((NclEventAgreement) event).nymiHandle;
					ledPatterns = ((NclEventAgreement) event).leds;
	                Log.d(LOG_TAG, "Agreement pattern: " + Arrays.toString(ledPatterns[0]));
					if (provisionListener != null) {
						provisionListener.onAgreement(ProvisionController.this);
					}
				}
			}
			else if (event instanceof NclEventProvision) {
				if (((NclEventProvision) event).nymiHandle == nymiHandle && state == State.PROVISIONING) {
					Log.d(LOG_TAG, "Provision is successful!");
					provision = ((NclEventProvision) event).provision;
	                state = State.SUCCEEDED;
				}
			}
			else if (event instanceof NclEventDisconnection) {
				if (((NclEventDisconnection) event).nymiHandle == nymiHandle) { // connected Nymi was disconnected
					if (state == State.SUCCEEDED) {
						if (provisionListener != null) {
							provisionListener.onProvisioned(ProvisionController.this);
						}
					}
					else {
	                    if (provisionListener != null) {
	                        provisionListener.onFailure(ProvisionController.this);
	                    }
	                    state = State.FAILED;
	                }
	                state = null;
	                Ncl.removeBehavior(nclCallback, null, NclEventType.NCL_EVENT_ANY, Ncl.NYMI_HANDLE_ANY);
				}
			}
			else if (event instanceof NclEventError) {
				state = State.FAILED;
                if (provisionListener != null) {
                    provisionListener.onFailure(ProvisionController.this);
                }
                Ncl.removeBehavior(nclCallback, null, NclEventType.NCL_EVENT_ANY, Ncl.NYMI_HANDLE_ANY);
			}
		}
	}
	
	public enum State {
		DISCOVERING, ///< \brief discovery started
		AGREEING, ///< \brief agreement in progress, but hasn't finished yet. \warning Stopping provision operation during this state will cause desynchronization between Nymi state and NCL state
		AGREED, ///< \brief agreement completed User should call \ref accept or \ref reject based on the \ref leds result
		PROVISIONING, ///< \brief provisioning in progress but hasn't finished yet. 
		SUCCEEDED, ///< \brief provision has successfully provisioned a Nymi. User may call \ref provision to obtain the provision data, which is used for starting a \ref Session
		NO_DEVICE, ///< \brief provision has failed due to no active devices in the area. Make sure the Nymi is nearby and is in provisioning mode
		FAILED, ///< \brief NCL initialization has failed, you may attempt to retry \ref init, but you should check if the ble connector is working first
		NO_BLE, ///< \brief the device has no BLE
		BLE_DISABLED, ///< \brief BLE is disabled
		AIRPLANE_MODE ///< \brief The device is in airplane mode
	}
}
