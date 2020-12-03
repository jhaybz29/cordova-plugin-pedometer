/**
 * License: MIT
 */
package org.apache.cordova.pedometer;

import java.util.List;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.os.Handler;

/**
 * This class listens to the pedometer sensor
 */
public class PedoListener extends CordovaPlugin 
{

    public static int STOPPED 					= 0;
    public static int STARTING 					= 1;
    public static int RUNNING 					= 2;
    public static int ERROR_FAILED_TO_START 	= 3;
    public static int ERROR_NO_SENSOR_FOUND 	= 4;

    private int status;     					// status of listener
    private float startsteps; 					//first value, to be substracted
    private long starttimestamp; 				//time stamp of when the measurement starts

    private SensorManager mSensorManager; 		// Sensor manager
    private Sensor mStepSensor;             	// Pedometer sensor returned by sensor manager

    private CallbackContext callbackContext; 	// Keeps track of the JS callback context.

    private Handler mainHandler					= null;
	
	//Manually creating a listener event
	private SensorEventListener mStepEventListener = new SensorEventListener()
	{
		 /**
		 * Called when the accuracy of the sensor has changed.
		 */
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) 
		{
		  //nothing to do here
		  return;
		}

		/**
		 * Sensor listener event.
		 * @param event
		 */
		@Override
		public void onSensorChanged(SensorEvent event) 
		{
			
			if (status == PedoListener.STOPPED) 
			{
				return;
			}
			
			if(event.sensor == mStepSensor)
			{
				setStatus(PedoListener.RUNNING);
				
				int steps = (int) event.values[0];
				win(getStepsJSON(steps));
			}
		}
	};

    /**
     * Constructor
     */
    public PedoListener() 
	{
        this.starttimestamp = 0;
        this.startsteps = 0;
        this.setStatus(PedoListener.STOPPED);
    }

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova the context of the main Activity.
     * @param webView the associated CordovaWebView.
     */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) 
	{
        super.initialize(cordova, webView);
		mSensorManager = (SensorManager) cordova.getActivity().getSystemService(Context.SENSOR_SERVICE);
        
    }

	/**
     * Called when the activity is becoming visible to the user.
     */
	@Override
    public void onStart() 
	{
		
    }

    /**
     * Executes the request.
     *
     * @param action the action to execute.
     * @param args the exec() arguments.
     * @param callbackContext the callback context used when calling back into JavaScript.
     * @return whether the action was valid.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) 
	{
        this.callbackContext = callbackContext;

        if (action.equals("isStepCountingAvailable")) 
		{
            //Change the sensor checking to getDefaultSensor
            if (mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null) 
			{
                this.win(true);
                return true;
            } 
			else 
			{
                this.setStatus(PedoListener.ERROR_NO_SENSOR_FOUND);
                this.win(false);
                return true;
            }
			
        } 
		else if (action.equals("isDistanceAvailable")) 
		{
            //distance is never available in Android
            this.win(false);
            return true;
        } 
		else if (action.equals("isFloorCountingAvailable")) 
		{
            //floor counting is never available in Android
            this.win(false);
            return true;
        }
        else if (action.equals("startPedometerUpdates")) 
		{
            if (this.status != PedoListener.RUNNING) 
			{
                // If not running, then this is an async call, so don't worry about waiting
                // We drop the callback onto our stack, call start, and let start and the sensor callback fire off the callback down the road
                this.start();
            }
			
            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT, "");
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
			
            return true;
        }
        else if (action.equals("stopPedometerUpdates")) 
		{
            if (this.status == PedoListener.RUNNING) 
			{
                this.stop();
            }
            this.win(null);
            return true;
			
        } 
		else 
		{
            // Unsupported action
            return false;
        }
		
    }

    /**
     * Called by the Broker when listener is to be shut down.
     * Stop listener.
     */
    public void onDestroy() 
	{
        this.stop();
    }


    /**
     * Start listening for pedometers sensor.
     */
    private void start() 
	{
        // If already starting or running, then return
        if ((this.status == PedoListener.RUNNING) || (this.status == PedoListener.STARTING)) 
		{
            return;
        }

        starttimestamp = System.currentTimeMillis();
        this.startsteps = 0;
        this.setStatus(PedoListener.STARTING);

		
		if(mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null)
		{
			mStepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
			boolean IsListening = mSensorManager.registerListener(mStepEventListener, mStepSensor, SensorManager.SENSOR_DELAY_NORMAL);
			if(IsListening == true)
			{
				this.setStatus(PedoListener.STARTING);
			}
			else
			{
				this.setStatus(PedoListener.ERROR_FAILED_TO_START);
				this.fail(PedoListener.ERROR_FAILED_TO_START, "registerListener is returning false");
			}
			
		}
		else
		{
			this.setStatus(PedoListener.ERROR_FAILED_TO_START);
            this.fail(PedoListener.ERROR_FAILED_TO_START, "No sensors found to register step counter listening to.");
            return;
		}
		
    }

    /**
     * Stop listening to sensor.
     */
    private void stop() 
	{
        if (this.status != PedoListener.STOPPED) 
		{
			if(mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null)
			{
				mSensorManager.unregisterListener(mStepEventListener);
			}
        }
		
        this.setStatus(PedoListener.STOPPED);
    }

    /**
     * Called when the view navigates.
     */
    @Override
    public void onReset() 
	{
        if (this.status == PedoListener.RUNNING) 
		{
            this.stop();
        }
    }

    // Sends an error back to JS
    private void fail(int code, String message) 
	{
        // Error object
        JSONObject errorObj = new JSONObject();
        try 
		{
            errorObj.put("code", code);
            errorObj.put("message", message);
        } 
		catch (JSONException e) 
		{
            e.printStackTrace();
        }
		
        PluginResult err = new PluginResult(PluginResult.Status.ERROR, errorObj);
        err.setKeepCallback(true);
        callbackContext.sendPluginResult(err);
    }

    public void win(JSONObject message) 
	{
        // Success return object
        PluginResult result;
        if(message != null)
		{
			result = new PluginResult(PluginResult.Status.OK, message);
		}
        else
		{
			result = new PluginResult(PluginResult.Status.OK);
		}
		
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    public void win(boolean success) 
	{
        // Success return object
        PluginResult result;
        result = new PluginResult(PluginResult.Status.OK, success);

        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    public void setStatus(int status) 
	{
        this.status = status;
    }

    public JSONObject getStepsJSON(int steps) 
	{
        JSONObject r = new JSONObject();
        // pedometerData.startDate; -> ms since 1970
        // pedometerData.endDate; -> ms since 1970
        // pedometerData.numberOfSteps;
        // pedometerData.distance;
        // pedometerData.floorsAscended;
        // pedometerData.floorsDescended;
		
		//Added a comment
        try 
		{
            r.put("startDate", this.starttimestamp);
            r.put("endDate", System.currentTimeMillis());
            r.put("numberOfSteps", steps);
        } 
		catch (JSONException e) 
		{
            e.printStackTrace();
        }
        return r;
    }
}
