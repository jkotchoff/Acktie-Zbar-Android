package com.acktie.mobile.android.camera;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.proxy.TiViewProxy;

import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;

import com.acktie.mobile.android.InputArgs;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.aztec.AztecReader;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.pdf417.PDF417Reader;
import com.google.zxing.qrcode.QRCodeReader;

public class CameraCallback implements PreviewCallback {
	private static final String LCAT = "Acktiemobile:CameraCallback";
	private CameraManager cameraManager = null;
	private TiViewProxy viewProxy = null;
	private InputArgs args = null;
	private long lastScanDetected = System.currentTimeMillis();
	private boolean pictureTaken = false;

	private Reader[] readers;

	public CameraCallback(TiViewProxy viewProxy, CameraManager cameraManager, InputArgs args) {
		this.cameraManager = cameraManager;
		this.viewProxy = viewProxy;
		this.args = args;
    // TODO: make this configurable
		this.readers = new Reader[]{ new QRCodeReader() };
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		// These could have been || (or'ed) together but I wanted to make it
		// easier to understand why the image was not processed
		if (hasEnoughTimeElapsedToScanNextImage()) {
			return;
		} else if (args.isScanFromImageCapture() && !pictureTaken) {
			return;
		}

		scanImageUsingXZing(data, camera, pictureTaken);

		pictureTaken = false;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void scanImageUsingXZing(byte[] data, Camera camera,
			boolean fromPictureTaken) {
		if (cameraManager.isStopped()) {
			return;
		}

		Camera.Parameters parameters = cameraManager.getCameraParameters();
		// If null, likely called after camera has been released.
		if (parameters == null) {
			return;
		}

		Size size = parameters.getPreviewSize();

		Result result = null;
		PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(data,
				size.width, size.height, 0, 0, size.width, size.height, false);

		if (source != null) {
			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

			try {
				result = decodeInternal(bitmap);
			} catch (NotFoundException e) {
				return;
			} catch (NullPointerException npe) {
				npe.printStackTrace();
				return;
			}
		}

		if (result != null) {
			String resultData = result.getText();
			String type = result.getBarcodeFormat().name();
			if (viewProxy != null && resultData != null) {
				System.out.println(resultData);
				if (!args.isContinuous()) {
					cameraManager.stop();
				}

				HashMap results = new HashMap();
				results.put("data", resultData);
				results.put("type", type);

				callSuccessInViewProxy(results);
				lastScanDetected = getOneSecondFromNow();
			}
		} else if (fromPictureTaken) {
			callErrorInViewProxy();
		}
	}

	private boolean hasEnoughTimeElapsedToScanNextImage() {
		return lastScanDetected > System.currentTimeMillis();
	}

	private long getOneSecondFromNow() {
		return System.currentTimeMillis() + 1000;
	}

	private Result decodeInternal(BinaryBitmap image) throws NotFoundException {
		if (readers != null) {
			for (Reader reader : readers) {
				try {
					return reader.decode(image);
				} catch (ReaderException re) {
					// continue
				}
			}
		}
		throw NotFoundException.getNotFoundInstance();
	}

	public void reset() {
		if (readers != null) {
			for (Reader reader : readers) {
				reader.reset();
			}
		}
	}

	public void setPictureTaken(boolean pictureTaken) {
		this.pictureTaken = pictureTaken;
	}

	private void callErrorInViewProxy() {
		Log.d(LCAT, "Calling callErrorInViewProxy");
		try {
			Method successMethod = viewProxy.getClass().getMethod(
					"errorCallback");
			if (successMethod != null) {
				successMethod.invoke(viewProxy);
			}
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void callSuccessInViewProxy(
			@SuppressWarnings("rawtypes") HashMap results) {
		Log.d(LCAT, "Calling callSuccessInViewProxy");
		try {
			Method successMethod = viewProxy.getClass().getMethod(
					"successCallback", HashMap.class);
			if (successMethod != null) {
				successMethod.invoke(viewProxy, results);
			}
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
