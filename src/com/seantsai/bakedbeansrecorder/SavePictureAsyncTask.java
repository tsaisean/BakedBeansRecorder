package com.seantsai.bakedbeansrecorder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

class SavePictureAsyncTask extends AsyncTask<Object, String, String> {
	private String TAG = "TakePictureAsyncTask";
	private MainActivity mActivity;
	
    @Override
    protected void onPostExecute(String savePath) {
    	Log.d(TAG, "Current thread: " + Thread.currentThread().getId() + " onPostExecute() start.");

    	//Toast.makeText(mActivity, savePath, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected String doInBackground(Object... params) {
    	Log.d(TAG, "Current thread: " + Thread.currentThread().getId() + " doInBackground() start.");
    	
    	mActivity = (MainActivity)(params[0]);
    	byte[] data = (byte[])(params[1]);
    	int rotation = (Integer)(params[2]);
    	String savePath = (String)(params[3]);
    	String fileName = (String)(params[4]);
    	
    	Bitmap bitmap = ImageUtility.toBitmap(data);
    	bitmap = ImageUtility.rotate(bitmap, rotation);

    	File pictureFile = new File(savePath, "IMG_" + fileName + ".jpg");

        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
        	bitmap.compress(CompressFormat.JPEG, 100, fos);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
        
		MediaScannerConnection.scanFile(mActivity, new String[]{pictureFile.getPath()}, null,
                new MediaScannerConnection.OnScanCompletedListener() {
					@Override
					public void onScanCompleted(final String path, final Uri uri) {
						Log.i(TAG, String.format("Scanned path %s -> URI = %s", path, uri.toString()));
					}
				}
		);		
		
        Log.d(TAG, "Current thread: " + Thread.currentThread().getId() + " doInBackground() end.");
        return pictureFile.getPath();
    }

}