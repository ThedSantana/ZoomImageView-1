package com.kyleduo.zoomimageview;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.kyleduo.zoomimageview.library.ZoomImageView;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

	private ZoomImageView mZoomIV;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mZoomIV = (ZoomImageView) findViewById(R.id.zoom_iv);

		mZoomIV.setBitmap(getImageFromAssetsFile("demo.jpg"));
//		mZoomIV.setBitmap(getImageFromAssetsFile("large.jpg"));
	}

	private Bitmap getImageFromAssetsFile(String fileName) {
		Bitmap image = null;
		AssetManager am = getResources().getAssets();
		try {
			InputStream is = am.open(fileName);
			image = BitmapFactory.decodeStream(is);
			is.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return image;

	}

}
