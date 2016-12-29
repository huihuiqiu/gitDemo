package com.hui.zxing.scanner;


import java.io.IOException;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.client.android.AmbientLightManager;
import com.google.zxing.client.android.BeepManager;
import com.google.zxing.client.android.CaptureActivityHandler;
import com.google.zxing.client.android.FinishListener;
import com.google.zxing.client.android.InactivityTimer;
import com.google.zxing.client.android.IntentSource;

import com.google.zxing.client.android.ViewfinderView;
import com.google.zxing.client.android.camera.CameraManager;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.opengl.Visibility;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

public class CaptureActivity extends Activity implements Callback{
	private static final TAG = "CaptureActivity";

	SurfaceView surfaceView;
	ViewfinderView viewfinderView;
	ImageView sImageView;

	AnimationDrawable animationDrawable;

	SurfaceHolder surfaceHolder;

	private CameraManager cameraManager;
	private CaptureActivityHandler handler;

	private boolean hasSurface;
	private InactivityTimer inactivityTimer;
	private BeepManager beepManager;
	private AmbientLightManager ambientLightManager;
	
	private IntentSource source;
	private Collection<BarcodeFormat> decodeFormats;
	  private Map<DecodeHintType,?> decodeHints;
	  private String characterSet;

	public ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	public CameraManager getCameraManager() {
		return cameraManager;
	}
	
	public void drawViewfinder() {
	    viewfinderView.drawViewfinder();
	  }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		setContentView(R.layout.capture_layout);

		initView();
		initData();
	}

	private void initView() {
		surfaceView = (SurfaceView) findViewById(R.id.preview_view);
		viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
		sImageView = (ImageView) findViewById(R.id.s_imageView);
	}

	private void initData() {

		hasSurface = false;
		inactivityTimer = new InactivityTimer(this);
		beepManager = new BeepManager(this);
		ambientLightManager = new AmbientLightManager(this);
		
		surfaceHolder = surfaceView.getHolder();


		//start animation
		sImageView.setBackgroundResource(R.drawable.scan_animation);
		animationDrawable = (AnimationDrawable) sImageView.getBackground();
		sImageView.post(new Runnable() {
			@Override
			public void run()  {
				animationDrawable.start();
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		// CameraManager must be initialized here, not in onCreate(). This is necessary because we don't
	    // want to open the camera driver and measure the screen size if we're going to show the help on
	    // first launch. That led to bugs where the scanning rectangle was the wrong size and partially
	    // off screen.
	    cameraManager = new CameraManager(getApplication());

	    viewfinderView.setCameraManager(cameraManager);

	    handler = null;
	    
	    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
	    
	    beepManager.updatePrefs();
	    ambientLightManager.start(cameraManager);

	    inactivityTimer.onResume();
	    
	    source = IntentSource.NONE;
	    decodeFormats = null;
	    characterSet = null;
	    characterSet = null;
	    
	    if (hasSurface) {
	        // The activity was paused but not stopped, so the surface still exists. Therefore
	        // surfaceCreated() won't be called, so init the camera here.
	        initCamera(surfaceHolder);
	      } else {
	        // Install the callback and wait for surfaceCreated() to init the camera.
	        surfaceHolder.addCallback(this);
	      }

	}

	@Override
	protected void onPause() {
		if (handler != null) {
		      handler.quitSynchronously();
		      handler = null;
		    }
		    inactivityTimer.onPause();
		    ambientLightManager.stop();
		    beepManager.close();
		    cameraManager.closeDriver();
		    
		    if (!hasSurface) {
		      surfaceHolder.removeCallback(this);
		    }
		    super.onPause();

	}

	@Override
	protected void onDestroy() {
		inactivityTimer.shutdown();
		
		super.onDestroy();
		if(animationDrawable != null){
			if(animationDrawable.isRunning()){
				animationDrawable.stop();
			}
			animationDrawable = null;
		}

	}
	
	// 下面的效果如果不要可注释
	@Override
	  public boolean onKeyDown(int keyCode, KeyEvent event) {
	    switch (keyCode) {
	      // Use volume up/down to turn on light
	      case KeyEvent.KEYCODE_VOLUME_DOWN:
	        cameraManager.setTorch(false);
	        return true;
	      case KeyEvent.KEYCODE_VOLUME_UP:
	        cameraManager.setTorch(true);
	        return true;
	    }
	    return super.onKeyDown(keyCode, event);
	  }
	
	// 是否进行重新扫描
	private void restartScan(boolean restart){
		if(restart){
			restartPreviewAfterDelay(2000); //每隔2s重扫
		}else{
			finish();
		}
	}
	
	public void restartPreviewAfterDelay(long delayMS) {
	    if (handler != null) {
	      handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
	    }
	  }

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}
	
	public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
		
	    inactivityTimer.onActivity();
	    
	    beepManager.playBeepSoundAndVibrate();
	    
	    switch (source) {
	      case NONE:
	    	  handleDecodeInternally(rawResult, barcode);
	        break;
	    }
	  }
	
	
	String scanResut;
	
	// Put up our own UI for how to handle the decoded contents.
	  private void handleDecodeInternally(Result rawResult, Bitmap barcode) {

		DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
	    String text = rawResult.getText();
	    
	    long scanTime = rawResult.getTimestamp(); // 扫描时间
	    String textFormat = rawResult.getBarcodeFormat().toString(); // 扫描格式
	    // 如要得到扫描类型，需引入ResultHandler，可参考源码
	    
	    Toast.makeText(this, text, Toast.LENGTH_LONG).show();
	    
	    System.out.println("扫描结果为：" + rawResult.getText());
	    
	    if(TextUtils.isEmpty(scanResut)){
	    	scanResut = rawResult.getText();
	    	restartScan(true);
	    }else {
			if(scanResut.equals(rawResult.getText())){
				System.out.println("已扫描该数据，将重新扫描");
				restartScan(true);
			}else{
				System.out.println("扫描到新数据");
				// 如要退出执行restartScan(false);
				restartScan(true);
			}
		}
	    
	    
	  }

	private void initCamera(SurfaceHolder surfaceHolder) {
	    if (surfaceHolder == null) {
	      throw new IllegalStateException("No SurfaceHolder provided");
	    }
	    if (cameraManager.isOpen()) {
	      return;
	    }
	    try {
	      cameraManager.openDriver(surfaceHolder);
	      // Creating the handler starts the preview, which can also throw a RuntimeException.
	      if (handler == null) {
	        handler = new CaptureActivityHandler(this, decodeFormats, decodeHints, characterSet, cameraManager);
	      }
	    } catch (IOException ioe) {
	      displayFrameworkBugMessageAndExit();
	    } catch (RuntimeException e) {
	      // Barcode Scanner has seen crashes in the wild of this variety:
	      // java.?lang.?RuntimeException: Fail to connect to camera service
	      displayFrameworkBugMessageAndExit();
	    }
	  }
	
	private void displayFrameworkBugMessageAndExit() {
	    AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    builder.setTitle(getString(R.string.app_name));
	    builder.setMessage(getString(R.string.msg_camera_framework_bug));
	    builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
	    builder.setOnCancelListener(new FinishListener(this));
	    builder.show();
	  }

}
