package com.seantsai.bakedbeansrecorder;

import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Display;

public class SystemUtility {


	@SuppressLint("NewApi")
	static public boolean isAirplaneModeOn(Context context) {
		
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) 
			return Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
		else 			
			return Settings.System.getInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;

	}
	
	static public Point getDisplaySize(Activity activity) {
		Display display = activity.getWindowManager().getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		return size;
	}
	
	static public Intent getESFileExplorerIntent(Uri uri) {
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		intent.setDataAndType(uri, "*/*");
		//intent.setPackage("com.estrongs.android.pop");
		intent.setClassName("com.estrongs.android.pop", "com.estrongs.android.pop.view.FileExplorerActivity");
		
		return intent;
	}
	
	static public Drawable getESFileExplorerIcon(Context context) {
		try {
			return context.getPackageManager().getActivityIcon(getESFileExplorerIntent(null));
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			return null;
		}
	}
	

	static public boolean isIntentAvailable(Context context, Intent intent) {
	    final PackageManager packageManager = context.getPackageManager();
	    List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
	    if (resolveInfo.size() > 0) {
	    	return true;
	    }
	    return false;
	}
	
	static public boolean startESFileExplorerActivity(Context context, Uri uri) {
		Intent intent = SystemUtility.getESFileExplorerIntent(uri);
		if (SystemUtility.isIntentAvailable(context, intent)) {
			context.startActivity(intent);
			return true;
		}
		
		return false;
	}
	
}
