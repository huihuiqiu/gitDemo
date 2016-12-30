package com.hui.zxing.scanner;

import android.app.Application;
import android.content.Context;

public class WeApplication extends Application{
	
	private static Context mContext;
	
	 public static Context getContext() {
	      return mContext;  
	 }

	@Override
	public void onCreate() {
		super.onCreate();
		mContext = this.getApplicationContext();

	}
	

}
