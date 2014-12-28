package com.seantsai.bakedbeansrecorder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Display;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

public class MainActivity extends Activity implements SurfaceHolder.Callback {

	public static final String TAG = "MainActivity";
	public static final String ROOT_FOLDER = "Baked Beans Recorder";
	
	public static final File FILE_DIRECTORY_PICTURES = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
	// Preference keys
	private static final String PKEY_SOUNDTYPE = "SoundType";
	private static final String PKEY_DURATION = "Duration";
	
    // Views
    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private TextView mTextPerSecs;
    private Button mBtnTimer;
    private Button mBtnNextBakingStep;
    private Button mBtnNotificationSound;
    private ImageButton mImgBtnAutoFocus;
    private ImageButton mImgBtnOpenFolder;
    private TextView mTextTime;
    private TextView mTextLargerTime;
    private View mViewCaptureEffect;
    private LinearLayout mRightSettingPanel; 
        
    // Others
    private Camera mCamera;
    private static int mCurrentCameraId = CameraInfo.CAMERA_FACING_BACK;

    private Timer mTimer;
    private int mDuration = 60;
    private TextToSpeech mTextToSpeech;
    private boolean mIsTTSInitialized = false;
    private File mSaveFolder = getAPPRootFolder();
	private boolean mIsPreviewStart;
	private boolean mIsTakingPicture;
	private Ringtone mRingtone;
	private SharedPreferences mSharedPreferences;
	private boolean mIsBtnNextBakingStepPressed;
    private static final Map<BakingSteps, Integer> mBakingStepsStringMap;
    private BakingSteps mCurrenyBakingStep = BakingSteps.TEMP_RETURNED;
    static {
    	mBakingStepsStringMap = new HashMap<BakingSteps, Integer>();
    	mBakingStepsStringMap.put(BakingSteps.TEMP_RETURNED, R.string.Temp_Teturned);
    	mBakingStepsStringMap.put(BakingSteps.BLOOM, R.string.Bloom);
    	mBakingStepsStringMap.put(BakingSteps.DEHYDRATE, R.string.Dehydrate);
    	mBakingStepsStringMap.put(BakingSteps.FIRST_POP, R.string.First_Pop);
    	mBakingStepsStringMap.put(BakingSteps.RELEASE, R.string.Release);
    }
    
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(mCurrentCameraId); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }
    
	// Run on UI thread.
    private PictureCallback mPictureCallback = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
        	stopPreview();
        	Display display = getWindowManager().getDefaultDisplay();
        	int rotation = 0;
        	switch (display.getRotation()) {
        	    case Surface.ROTATION_0: // This is display orientation
        	    rotation = mCurrentCameraId == CameraInfo.CAMERA_FACING_BACK ? 90 : 270;
        	    break;
        	case Surface.ROTATION_90:
        	    rotation = mCurrentCameraId == CameraInfo.CAMERA_FACING_BACK ? 0 : 0;
        	    break;
        	case Surface.ROTATION_180:
        	    rotation = mCurrentCameraId == CameraInfo.CAMERA_FACING_BACK ? 270 : 270;
        	    break;
        	case Surface.ROTATION_270:
        	    rotation = mCurrentCameraId == CameraInfo.CAMERA_FACING_BACK ? 180 : 180;
        	        break;
        	}
       	
        	if (mSaveFolder == null)
        		mSaveFolder = getSaveFolder();
			SavePictureAsyncTask savePictureTask = new SavePictureAsyncTask();
			
			String[] strArray = ((String) mTextTime.getText()).split(":");
			
			String fileName = null;
			if (strArray.length == 2) {
				// Make it more readable. EX: 01m:02s -> 1m:02s
				if (strArray[0].charAt(0) == '0')
					strArray[0] = strArray[0].substring(1);
				
				fileName = strArray[0] + "m" + strArray[1] + "s" + 
							(mIsBtnNextBakingStepPressed ? "_" + getString(mBakingStepsStringMap.get(mCurrenyBakingStep)) : "");
			}
			else {
				Log.d(TAG, "Time format is not correct.");
				fileName = mTextTime.getText() + (mIsBtnNextBakingStepPressed ? "_" + getString(mBakingStepsStringMap.get(mCurrenyBakingStep)) : "");
			}
			
			savePictureTask.execute(MainActivity.this, data, rotation, mSaveFolder.getPath(), fileName);
			
            startPreview();
            mIsTakingPicture = false;
            
            if (mIsBtnNextBakingStepPressed) {
	            mIsBtnNextBakingStepPressed = false;
	    		mCurrenyBakingStep = mCurrenyBakingStep.next();
	    		if (mCurrenyBakingStep == BakingSteps.END)
	    			stopTimer();
	    		else
	    			mBtnNextBakingStep.setText(mBakingStepsStringMap.get(mCurrenyBakingStep));
            }
            
            mViewCaptureEffect.setVisibility(View.GONE);
            
        }
    };
    
    public static void setCameraDisplayOrientation(Activity activity,
            int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }
    
	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
 
        // Initialize Ringtone
	    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
	    mRingtone = RingtoneManager.getRingtone(getApplicationContext(), notification);
	    
	    // Initialize Views
	    initViews();
        
        if (!SystemUtility.isAirplaneModeOn(this))
        	popupTipsDialog();
	}  
	
	private void initViews() {
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        
        mViewCaptureEffect = findViewById(R.id.viewCaptureEffect);
        
        mTextPerSecs = (TextView) findViewById(R.id.textPerSecs);        
        mBtnTimer = (Button) findViewById(R.id.btnTimer);
        mBtnNotificationSound = (Button) findViewById(R.id.btnNotificationSound);
        
        mBtnNextBakingStep = (Button) findViewById(R.id.btnNextBakingStep);
        mBtnNextBakingStep.setText(mBakingStepsStringMap.get(mCurrenyBakingStep));
        
        mImgBtnAutoFocus = (ImageButton) findViewById(R.id.imgBtnAutoFocus);
        
        mImgBtnOpenFolder = (ImageButton) findViewById(R.id.imgBtnOpenFolder);
        Drawable drawable = SystemUtility.getESFileExplorerIcon(this);
        if (drawable != null) {
        	mImgBtnOpenFolder.setScaleType(ScaleType.CENTER_CROP);
        	mImgBtnOpenFolder.setImageDrawable(drawable);
        	mImgBtnOpenFolder.setVisibility(View.VISIBLE);
        }
        
        mTextTime = (TextView) findViewById(R.id.textTime);
        mTextLargerTime = (TextView) findViewById(R.id.textTimeLarger);
        
        mRightSettingPanel = (LinearLayout) findViewById(R.id.rightSettingPanel);
        
        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
			
        	Point startPoint = new Point();
        	final int TOLERANCE_X = 40;
        	final int TOLERANCE_Y = 40;
        	final int MODE_NONE = 0;
        	final int MODE_ZOOM = 1;
        	final int MODE_TIMER = 2;
        	
        	int mMode = MODE_NONE;
        	int mTempDuration;
        	int mTempZoom;
        	
        	@Override
			public boolean onTouch(View v, MotionEvent event) {
			        		
				switch(event.getAction()) {
					
					case MotionEvent.ACTION_DOWN:
						startPoint.x = (int)event.getX();
						startPoint.y = (int)event.getY();
						
						mTempDuration = mDuration;
						mTempZoom = mCamera.getParameters().getZoom();
						break;
					case MotionEvent.ACTION_MOVE:
						float offsetX = event.getX()-startPoint.x;
						float offsetY = startPoint.y-event.getY(); // Make scroll up become positive.
						
						//Log.d(TAG, "x:" + event.getX() + ", y:" + event.getY() + ", " + offsetX + ", " + offsetY);
						
						// Do zooming
						if ((mMode == MODE_NONE || mMode == MODE_ZOOM) &&
							Math.abs(offsetY) > Math.abs(offsetX) && Math.abs(offsetY) > TOLERANCE_Y) {
							mMode = MODE_ZOOM;
							
							Parameters parameters = mCamera.getParameters();
							final int MAXZOOM = parameters.getMaxZoom();
							final int OFFSET_PER_ZOOM = 25;
							
							int zoom = (int) ((Math.abs(offsetY)-TOLERANCE_Y)/OFFSET_PER_ZOOM);
							if (offsetY > 0)
								parameters.setZoom(mTempZoom+zoom > MAXZOOM ? MAXZOOM : mTempZoom+zoom);
							else
								parameters.setZoom(mTempZoom-zoom < 0 ? 0 : mTempZoom-zoom);
							//Log.d(TAG, ""+parameters.getZoom());
							mCamera.setParameters(parameters);
							
						}
						// Set timer
						else if (mTimer == null && (mMode == MODE_NONE || mMode == MODE_TIMER) &&
								Math.abs(offsetX) > TOLERANCE_X){
							mMode = MODE_TIMER;
							final int OFFSET_PER_SEC = 15;
							
							int secs = (int) ((Math.abs(offsetX)-TOLERANCE_X)/OFFSET_PER_SEC);
							
							if (offsetX > 0)
								mDuration = mTempDuration+secs > 60 ? 60 : mTempDuration+secs;
							else
								mDuration = mTempDuration-secs < 1 ? 1 : mTempDuration-secs;

							String text = String.format(getResources().getString(R.string.Timer_D_s), mDuration);
							mTextPerSecs.setText(text);						
						}
						break;
					case MotionEvent.ACTION_UP:
						mMode = MODE_NONE;
				}
				
				return true;
			}
		});
        
//        mTextLargerTime.setOnTouchListener(new View.OnTouchListener() {
//        	Point startPoint = new Point();
//        	Point viewStartPoint = new Point();
//			@Override
//			public boolean onTouch(View v, MotionEvent event) {
//				switch (event.getAction()) {
//					case MotionEvent.ACTION_DOWN:
//						startPoint.x = (int)event.getX();
//						startPoint.y = (int)event.getY();
//						viewStartPoint.x = (int) mTextLargerTime.getX();
//						viewStartPoint.y = (int) mTextLargerTime.getY();
//						break;
//						
//					case MotionEvent.ACTION_MOVE:
//						mTextLargerTime.setX(viewStartPoint.x + event.getX()-startPoint.x);
//						mTextLargerTime.setY(viewStartPoint.y + event.getY()-startPoint.y);
//						break;
//	
//					default:
//						break;
//				}
//
//				return true;
//			}
//		});
        
        readPreferences();
	}
	
	   private static class MyDragShadowBuilder extends View.DragShadowBuilder {

		    // The drag shadow image, defined as a drawable thing
		    private static Drawable shadow;

		        // Defines the constructor for myDragShadowBuilder
		        public MyDragShadowBuilder(View v) {

		            // Stores the View parameter passed to myDragShadowBuilder.
		            super(v);

		            // Creates a draggable image that will fill the Canvas provided by the system.
		            shadow = new ColorDrawable(Color.LTGRAY);
		        }

		        // Defines a callback that sends the drag shadow dimensions and touch point back to the
		        // system.
		        @Override
		        public void onProvideShadowMetrics (Point size, Point touch) {
		            // Defines local variables
		            int width;
					int height;

		            // Sets the width of the shadow to half the width of the original View
		            width = getView().getWidth() / 2;

		            // Sets the height of the shadow to half the height of the original View
		            height = getView().getHeight() / 2;

		            // The drag shadow is a ColorDrawable. This sets its dimensions to be the same as the
		            // Canvas that the system will provide. As a result, the drag shadow will fill the
		            // Canvas.
		            shadow.setBounds(0, 0, width, height);

		            // Sets the size parameter's width and height values. These get back to the system
		            // through the size parameter.
		            size.set(width, height);

		            // Sets the touch point's position to be in the middle of the drag shadow
		            touch.set(width / 2, height / 2);
		        }

		        // Defines a callback that draws the drag shadow in a Canvas that the system constructs
		        // from the dimensions passed in onProvideShadowMetrics().
		        @Override
		        public void onDrawShadow(Canvas canvas) {

		            // Draws the ColorDrawable in the Canvas passed in from the system.
		            shadow.draw(canvas);
		        }
		    }

	public void popupTipsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.Tips);
        builder.setMessage(R.string.If_you_dont_want_to_be_disturbed_by_phone_calls_or_notifications_you_can_got_to_the_Wireless_and_Network_setting_page_to_turn_on_Airplane_mode);
        builder.setPositiveButton(R.string.Setting, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
		        startActivity(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS));
			}
		});
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
	}
	
	static public File getAPPRootFolder() {
    	File rootFolder = new File(FILE_DIRECTORY_PICTURES, ROOT_FOLDER);
    	if (!rootFolder.exists())
    		rootFolder.mkdir();
    	
    	return rootFolder;
	}
	
	public File getSaveFolder() {

		getAPPRootFolder();
    	
    	// Prepare sub folder
    	String timeStamp = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    	int num = 1;
    	File subFolder = null;
    	while (true) {	
    		subFolder = new File(getAPPRootFolder().getPath(), timeStamp + "_" + num);
    		// Create a new folder with new name.
    		if (subFolder.exists())
    			num++;
    		else {
    			subFolder.mkdir();
    			break;
    		}
    	}
    	return subFolder;
	}
	
	static int mCount = 0; 
	class TakePictureTask extends TimerTask {
		
		@Override
		public void run() {
			mCount++;

			final int time = mCount;		
			
			MainActivity.this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					final int hr = time/3600;
					final int min = (time-hr*3600)/60;
					final int sec = time-hr*3600-min*60;
					
					mTextTime.setText((min < 10 ? "0" : "") + min + ":" +
							  		  (sec < 10 ? "0" : "") + sec);
					
					mTextLargerTime.setText(mTextTime.getText());
				}
			}); 
			
			// We plus one to let user hear the sound earlier.
			if ((time+1) % mDuration == 0)
			{
				final int hr = (time+1)/3600;
				final int min = ((time+1)-hr*3600)/60;
				final int sec = (time+1)-hr*3600-min*60;
				
				playNotificationSound(hr, min, sec);
			}
			
			if (time % mDuration == 0) {
				MainActivity.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						//Toast.makeText(MainActivity.this, "Capture " + mCount, Toast.LENGTH_SHORT).show();
						onCapture(null); // AsyncTask should run on UI thread.
					}
				}); 
			}
		}
	}
	
	private void playNotificationSound(int hr, int min, int sec) {
		String soundType = mBtnNotificationSound.getText().toString();
		
		if (mIsTTSInitialized && soundType.equals(getString(R.string.Human))) {
			String text = String.format(
					(hr > 0 ? hr + getString(R.string.hour) : "") +  
					(min > 0 ? min + getString(R.string.minute) : "") +
					sec + getString(R.string.second)
					);
			mTextToSpeech.speak(text, 2, null);
		}
		else if (soundType.equals(getString(R.string.Ringtone))) {
			mRingtone.play();
		}
	}
	
	@Override
	protected void onResume() {
        mTextToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
			@Override
			public void onInit(int status) {
				 if (status == TextToSpeech.SUCCESS)
				 {
					 mTextToSpeech.setPitch(1.0f);
					 mTextToSpeech.setSpeechRate(1);
					 int result = mTextToSpeech.setLanguage(Locale.getDefault());

		             if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED)		 
		             {
		                 Log.e("TTS", "This Language is not supported");
		             }

					 mIsTTSInitialized = true;
		         }
		         else
		         {
		             Log.e("TTS", "Initilization Failed!");
		         }

			}
		});
        
        mBtnTimer.setText(getString(R.string.Start));
		
		super.onResume();
	}

	@Override
	protected void onPause() {
		
		stopTimer();
		mTextToSpeech.shutdown();
	
		savePreferences();
		
		super.onStop();
	}
	
	private void readPreferences() {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String soundType = mSharedPreferences.getString(PKEY_SOUNDTYPE, null);
        if (soundType != null) mBtnNotificationSound.setText(soundType);
        mDuration = mSharedPreferences.getInt(PKEY_DURATION, mDuration);
        
		String text = String.format(getResources().getString(R.string.Timer_D_s), mDuration);
		mTextPerSecs.setText(text);
	}
	
	private void savePreferences() {
		Editor editor = mSharedPreferences.edit();
		editor.putString(PKEY_SOUNDTYPE, mBtnNotificationSound.getText().toString());
		editor.putInt(PKEY_DURATION, mDuration);
		editor.commit();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

		mHolder = holder;
		mSurfaceWidth = width;
		mSurfaceHeight = height;
		
        if (mHolder.getSurface() == null){
          // preview surface does not exist
          return;
        }

        cameraInit();
        
        startPreview();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
        // Create an instance of Camera
		mHolder = holder;
		mCamera = getCameraInstance();
		
		// The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder);
            startPreview();
        } catch (IOException e) {
            Log.d(TAG , "Error setting camera preview: " + e.getMessage());
        }
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		mCamera.release(); 
		mCamera = null;
	}
	
	public void onCapture(View v) {
		
		if (mCamera != null)
			if (mIsPreviewStart && !mIsTakingPicture) {
				Log.d(TAG, "onCapture - takePicture");
	        	mViewCaptureEffect.setVisibility(View.VISIBLE);
				mCamera.takePicture(null, null, mPictureCallback);
				mIsTakingPicture = true;
			}
			else {
				Log.d(TAG, "onCapture - [Skip] There is an unfinsh capturing.");
			}
	}
	
	public void onToggleAutoFocus(View v) {
		if ((Integer)mImgBtnAutoFocus.getTag() == null || 
			(Integer)mImgBtnAutoFocus.getTag() == R.drawable.ic_autofocus) {
			mImgBtnAutoFocus.setImageResource(R.drawable.ic_autofocus_d);
			mImgBtnAutoFocus.setTag(R.drawable.ic_autofocus_d);
			
	        setFocusMode(Parameters.FOCUS_MODE_INFINITY);

		}
		else {
			mImgBtnAutoFocus.setImageResource(R.drawable.ic_autofocus);
			mImgBtnAutoFocus.setTag(R.drawable.ic_autofocus);
			
			setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
		}
	}
	
	public boolean setFocusMode(String mode) {
        Parameters parameters = mCamera.getParameters();
        List<String> listSupportedFocusModes = parameters.getSupportedFocusModes();
              
        if (listSupportedFocusModes.contains(mode))
        {
        	parameters.setFocusMode(mode);
            mCamera.setParameters(parameters);
            
            return true;
        }
        return false;
	}
	
	
	// This will prompt ES File Explorer 
	public void onOpenFolder(View v) {
		if (mSaveFolder != null) {
			Uri uri = Uri.parse(mSaveFolder.getPath());
			SystemUtility.startESFileExplorerActivity(this, uri);
		}
	}

	public void cameraInit() {
        // stop preview before making changes
        try {
            stopPreview();
        } catch (Exception e){
          // ignore: tried to stop a non-existent preview
        }

        Parameters parameters = mCamera.getParameters();
        List<String> listSupportedFocusModes = parameters.getSupportedFocusModes();
        if (listSupportedFocusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
        {
        	parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        
        List<Size> listSupportedSizes = parameters.getSupportedPictureSizes();
        // Use the mid size
        Size midSize = listSupportedSizes.get(listSupportedSizes.size()/2);
        parameters.setPictureSize(midSize.width, midSize.height);
        
        Camera.Size size = getOptimalPreviewSize(parameters.getSupportedPreviewSizes(), mSurfaceWidth, mSurfaceHeight);
        if (size != null)
        	parameters.setPreviewSize(size.width, size.height);
        
    	mCamera.setParameters(parameters);
        
        MainActivity.setCameraDisplayOrientation(MainActivity.this, mCurrentCameraId, mCamera);

        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(mHolder);
        } catch (Exception e){
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
	}
	
	private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio=(double)h / w;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }
	
	public void onToggleTimerText(View v) {
		if (mTextLargerTime.getVisibility() == View.VISIBLE)
			mTextLargerTime.setVisibility(View.INVISIBLE);
		else {
			mTextLargerTime.setVisibility(View.VISIBLE);
		}
	}
	
	public void onToggleTimer(View v) {
		if (mBtnTimer.getText().equals(getResources().getString(R.string.Start)))
			startTimer();
		else
			stopTimer();
	}
	
	public void startTimer() {
		mSaveFolder = getSaveFolder();
		resetTime();

		if (mTimer == null)
			mTimer = new Timer();
		
		mTimer.schedule(new TakePictureTask(), 1000, 1000);
		mBtnTimer.setText(R.string.Stop);
		
		mTextLargerTime.setVisibility(View.VISIBLE);
		mRightSettingPanel.setVisibility(View.GONE);
		mBtnNextBakingStep.setVisibility(View.VISIBLE);
	}
	
	public void stopTimer() {
		
		if (mTimer != null) {
			mTimer.cancel();
			mTimer = null;
		}
		mBtnTimer.setText(R.string.Start);
		
		mTextLargerTime.setVisibility(View.GONE);
		mRightSettingPanel.setVisibility(View.VISIBLE);
		mBtnNextBakingStep.setVisibility(View.GONE);
	}
	
	public void resetTime() {
		mCount = 0;
		mTextTime.setText("00:00");
		
		mCurrenyBakingStep = BakingSteps.TEMP_RETURNED;
		mBtnNextBakingStep.setText(mBakingStepsStringMap.get(mCurrenyBakingStep));
	}
	
	public void onSelectNotificationSound(View v) {
	    PopupMenu popupMenu = new PopupMenu(this, v);
	    popupMenu.getMenuInflater().inflate(R.menu.voice, popupMenu.getMenu());
	    popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
	 
	     @Override
	     public boolean onMenuItemClick(MenuItem item) {
	    	 	mBtnNotificationSound.setText(item.getTitle());
	    	 return true;
	     }
	      
	    });
	     
	    popupMenu.show();;
	}
	
	public void onSwitchCamera(View v) {
		if (mCurrentCameraId == CameraInfo.CAMERA_FACING_BACK)
			mCurrentCameraId = CameraInfo.CAMERA_FACING_FRONT;
		else
			mCurrentCameraId = CameraInfo.CAMERA_FACING_BACK;
		
		stopPreview();
		mCamera.release();
		mCamera = null;
		
        // Create an instance of Camera
        mCamera = getCameraInstance();
		
        cameraInit();
        
        startPreview();
        
	}
	
	public void onNextBakingStep(View v) {
		mIsBtnNextBakingStepPressed = true;
		onCapture(null);
	}
	
	public void startPreview() {
		if (mCamera != null) {
			mCamera.startPreview();
			mIsPreviewStart = true;
		}
			
	}
	
	public void stopPreview() {
		if (mCamera != null) {
			mCamera.stopPreview();
			mIsPreviewStart = false;
		}
			
	}
}
