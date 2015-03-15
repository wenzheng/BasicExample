package com.bionym.nclexample;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.bionym.ncl.Ncl;
import com.bionym.ncl.NclCallback;
import com.bionym.ncl.NclEvent;
import com.bionym.ncl.NclEventInit;
import com.bionym.ncl.NclMode;
import com.bionym.ncl.NclProvision;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity implements ProvisionController.ProvisionProcessListener,
                                                ValidationController.ValidationProcessListener {
	static final String LOG_TAG = "AndroidExample";

    static boolean nclInitialized = false;
    
    EditText nymulatorIp;
    RadioGroup selectLibrary;
    Button startProvision, startValidation, disconnect;

    ProvisionController provisionController;
    ValidationController valiationController;
    boolean connectNymi = true;

    int nymiHandle = Ncl.NYMI_HANDLE_ANY;
    NclProvision provision;

    static Pattern ipPattern = Pattern.compile("^\\s*\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\s*$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }

    @Override
    protected void onStart() {
        super.onStart();

        nymulatorIp = (EditText) findViewById(R.id.nymulatorIp);
        selectLibrary = (RadioGroup) findViewById(R.id.selectLib);
        startProvision = (Button) findViewById(R.id.provision);
        startValidation = (Button) findViewById(R.id.validation);
		disconnect = (Button) findViewById(R.id.disconnect);
		
        selectLibrary.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                 @Override
                 public void onCheckedChanged(RadioGroup group, int checkedId) {
                     if (checkedId == R.id.connectNymy) {
                         connectNymi = true;
                     }
                     else {
                         connectNymi = false;
                     }

                     if (connectNymi) {
                         nymulatorIp.setVisibility(View.GONE);
                     }
                     else {
                         nymulatorIp.setVisibility(View.VISIBLE);
                         String ip = nymulatorIp.getText().toString();
                         Matcher matcher = ipPattern.matcher(ip);
                         if (matcher.matches()) {
                             startProvision.setEnabled(true);
                         }
                         else {
                             startProvision.setEnabled(false);
                         }
                     }
                 }
             }
        );

        nymulatorIp.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!connectNymi) {
                    String ip = nymulatorIp.getText().toString();
                    Matcher matcher = ipPattern.matcher(ip);
                    if (matcher.matches()) {
                        startProvision.setEnabled(true);
                    }
                    else {
                        startProvision.setEnabled(false);
                    }
                }
            }
        });

        startProvision.setOnClickListener(new View.OnClickListener() {
        		long lastClickTime; // for double click protection
            @Override
            public void onClick(View v) {
            		if (System.currentTimeMillis() - lastClickTime >= 1000) { // double click protection
            			lastClickTime = System.currentTimeMillis();
	                initializeNcl();
	                nymiHandle = -1;
	                if (provisionController == null) {
	                		provisionController = new ProvisionController(MainActivity.this);
	                }
	                else {
	                		provisionController.stop();
	                }
	                provisionController.startProvision(MainActivity.this);
            		}
            }
        });

        startValidation.setOnClickListener(new View.OnClickListener() {
        		long lastClickTime;
            @Override
            public void onClick(View v) {
            		if (System.currentTimeMillis() - lastClickTime >= 1000) { // double click protection
            			lastClickTime = System.currentTimeMillis();
            			startProvision.setEnabled(false);
            			if (valiationController == null) {
            				valiationController = new ValidationController(MainActivity.this);
            			}
            			else {
            				valiationController.stop();
            			}
	                valiationController.startValidation(MainActivity.this, provisionController.getProvision());
            		}
            }
        });
        
        disconnect.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (nymiHandle >= 0) {
					disconnect.setEnabled(false);
	                startValidation.setEnabled(true);
	                startProvision.setEnabled(true);
					Ncl.disconnect(nymiHandle);
					nymiHandle = -1;
				}
			}
		});
    }

    @Override
	protected void onPause() {
	    	if (provisionController != null) {
	    		provisionController.stop();
	    	}
	    	
	    	if (valiationController != null) {
	    		valiationController.stop();
	    	}
		super.onPause();
	}

	@Override
    protected void onStop() {
        if (nclInitialized && nymiHandle >= 0) {
            Ncl.disconnect(nymiHandle);
        }

        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    /**
     * Initialize the NCL library
     */
    protected void initializeNcl() {
        if (!nclInitialized) {
            if (connectNymi) {
                initializeNclForNymiBand();
            }
            else {
                initializeNclForNymulator(nymulatorIp.getText().toString().trim());
            }
        }
    }

    /**
     * Process view when NCL library is initialized
     */
    protected void nclInitialized() {
        View selectLibraryContainer = findViewById(R.id.selectLibContainer);
        selectLibraryContainer.setVisibility(View.GONE);
    }

    /**
     * Initialize NCL library for connecting to a Nymi Band
     * @return true if the library is initialized
     */
    protected boolean initializeNclForNymiBand() {
        if (!nclInitialized) {
	    		NclCallback nclCallback = new MyNclCallback();
            boolean result = Ncl.init(nclCallback, null, "NCLExample", NclMode.NCL_MODE_DEFAULT, this);

            if (!result) { // failed to initialize NCL
                Toast.makeText(MainActivity.this, "Failed to initialize NCL library!", Toast.LENGTH_LONG).show();
                return false;
            }
            nclInitialized = true;
            nclInitialized();
        }
        return true;
    }

    /**
     * Initialize NCL library for connecting to a Nymulator
     * @param ip the Nymulator's IP address
     * @return true if the library is initialized
     */
    protected boolean initializeNclForNymulator(String ip) {
        if (!nclInitialized) {
            NclCallback nclCallback = new MyNclCallback();
            Ncl.setIpAndPort(ip, 9089);
            boolean result = Ncl.init(nclCallback, null, "NCLExample", NclMode.NCL_MODE_DEFAULT, this);

            if (!result) { // failed to initialize NCL
                Toast.makeText(MainActivity.this, "Failed to initialize NCL library!", Toast.LENGTH_LONG).show();
                return false;
            }

            nclInitialized = true;
            nclInitialized();
        }

        return true;
    }
	
    @Override
    public void onStartProcess(ProvisionController controller) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Nymi start provision ..",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onAgreement(final ProvisionController controller) {
        nymiHandle = controller.getNymiHandle();
        controller.accept();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Agree on pattern: " + Arrays.toString(controller.getLedPatterns()),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onProvisioned(final ProvisionController controller) {
    		nymiHandle = controller.getNymiHandle();
        provision = controller.getProvision();
		controller.stop();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
	        		startProvision.setEnabled(false);
	        		startValidation.setEnabled(true);
	                Toast.makeText(MainActivity.this, "Nymi provisioned: " + Arrays.toString(provision.id.v),
	                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onFailure(ProvisionController controller) {
		controller.stop();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Nymi provision failed!",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDisconnected(ProvisionController controller) {
		controller.stop();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
	        		startValidation.setEnabled(provision != null);
	        		disconnect.setEnabled(false);
                Toast.makeText(MainActivity.this, "Nymi disconnected: " + provision,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onStartProcess(ValidationController controller) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Nymi start validation for: " + Arrays.toString(provision.id.v),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onFound(ValidationController controller) {
        nymiHandle = controller.getNymiHandle();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Nymi validation found Nymi on: " + Arrays.toString(provision.id.v),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onValidated(ValidationController controller) {
		nymiHandle = controller.getNymiHandle();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            		startValidation.setEnabled(false);
            		disconnect.setEnabled(true);
	            Toast.makeText(MainActivity.this, "Nymi validated!",
	                    Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onFailure(ValidationController controller) {
		controller.stop();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "Nymi validated failed!",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDisconnected(ValidationController controller) {
		controller.stop();
    		runOnUiThread(new Runnable() {
            @Override
            public void run() {
            		disconnect.setEnabled(false);
                startValidation.setEnabled(true);
                startProvision.setEnabled(true);
                Toast.makeText(MainActivity.this, "Nymi disconnected: " + provision,
                        Toast.LENGTH_LONG).show();
            }
        });
    }
    
	/**
	 * Callback for NclEventInit
	 *
	 */
	class MyNclCallback implements NclCallback {
		@Override
		public void call(NclEvent event, Object userData) {
			Log.d(LOG_TAG, this.toString() + ": " + event.getClass().getName());
			if (event instanceof NclEventInit) {
	            if (!((NclEventInit) event).success) {
	                runOnUiThread(new Runnable() {
	                    @Override
	                    public void run() {
	                        Toast.makeText(MainActivity.this, "Failed to initialize NCL library!", Toast.LENGTH_LONG).show();
	                    }
	                });
	            }
	        }
		}
	}
}
