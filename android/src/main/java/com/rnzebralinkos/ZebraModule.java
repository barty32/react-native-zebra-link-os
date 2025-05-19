package com.rnzebralinkos;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.zebra.sdk.btleComm.BluetoothLeConnection;
import com.zebra.sdk.btleComm.BluetoothLeDiscoverer;
import com.zebra.sdk.btleComm.BluetoothLeStatusConnection;
import com.zebra.sdk.btleComm.MultichannelBluetoothLeConnection;
import com.zebra.sdk.comm.BluetoothConnection;
import com.zebra.sdk.comm.BluetoothConnectionInsecure;
import com.zebra.sdk.comm.BluetoothStatusConnection;
import com.zebra.sdk.comm.BluetoothStatusConnectionInsecure;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.comm.MultichannelBluetoothConnection;
import com.zebra.sdk.comm.MultichannelTcpConnection;
import com.zebra.sdk.comm.TcpConnection;
import com.zebra.sdk.comm.TcpStatusConnection;
import com.zebra.sdk.comm.UsbConnection;
import com.zebra.sdk.device.ZebraIllegalArgumentException;
import com.zebra.sdk.graphics.ZebraImageFactory;
import com.zebra.sdk.graphics.ZebraImageI;
import com.zebra.sdk.printer.LinkOsInformation;
import com.zebra.sdk.printer.NotALinkOsPrinterException;
import com.zebra.sdk.printer.PrinterLanguage;
import com.zebra.sdk.printer.PrinterObjectProperties;
import com.zebra.sdk.printer.PrinterStatus;
import com.zebra.sdk.printer.StorageInfo;
import com.zebra.sdk.printer.TcpPortStatus;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;
import com.zebra.sdk.printer.ZebraPrinterLinkOs;
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer;
import com.zebra.sdk.printer.discovery.DiscoveredPrinter;
import com.zebra.sdk.printer.discovery.DiscoveryException;
import com.zebra.sdk.printer.discovery.DiscoveryHandler;
import com.zebra.sdk.printer.discovery.NetworkDiscoverer;
import com.zebra.sdk.printer.discovery.UsbDiscoverer;

public class ZebraModule extends NativeRNZebraLinkOSSpec {

	public static String NAME = "RNZebraLinkOS";
	private static final String CONNECTION_NETWORK = "network";
	private static final String CONNECTION_BLUETOOTH = "bluetooth";
	private static final String CONNECTION_BLUETOOTH_LE = "bluetooth-le";
	private static final String CONNECTION_BLUETOOTH_INSECURE = "bluetooth-insecure";
	private static final String CONNECTION_USB = "usb";

	private final Map<String, ZebraPrinter> connectedPrinters = new HashMap<>();

	private record DiscoveryHandlerImpl(String type, Callback onPrinterFound, Promise promise) implements DiscoveryHandler {
		public void foundPrinter(DiscoveredPrinter printer) {
			Log.d("ZebraModule", "Discovery found a printer [" + type + "]: " + printer.address);

			Map<String, String> discoveryDataMap = printer.getDiscoveryDataMap();
			WritableMap obj = new WritableNativeMap();
			WritableMap discoveryData = new WritableNativeMap();
			for (Map.Entry<String, String> entry : discoveryDataMap.entrySet()) {
				discoveryData.putString(entry.getKey(), entry.getValue());
			}
			obj.putString("handle", createHandleFromConnection(printer.getConnection()));
			obj.putString("address", printer.address);
			obj.putString("connectionType", type);
			obj.putMap("discoveryData", discoveryData);
			onPrinterFound.invoke(obj);
		}

		public void discoveryFinished() {
			Log.d("ZebraModule", "Printer discovery finished [" + type + "]");
			promise.resolve(null);
		}

		public void discoveryError(String message) {
			Log.d("ZebraModule", "Printer discovery error [" + type + "]");
			promise.reject("DiscoveryException", message);
		}
	};

	ZebraModule(ReactApplicationContext context) {
		super(context);
	}

	@Override
	@NonNull
	public String getName() {
		return NAME;
	}

	private ZebraPrinter retrieveOrConnectPrinter(String handle) throws ConnectionException {
		Log.d("ZebraModule", "Connecting to printer " + handle);
		ZebraPrinter printer = connectedPrinters.get(handle);
		if(printer == null){
			try {
				Log.d("ZebraModule", "Printer " + handle + " not connected, connecting now...");
				Connection conn = createConnection(handle);
				conn.open();
				printer = ZebraPrinterFactory.getInstance(conn);
				//TODO: send status to JS
				connectedPrinters.put(handle, printer);
			} catch(ZebraPrinterLanguageUnknownException e) {
				throw new ConnectionException(e);
			}
		}
		return printer;
	}

	private ZebraPrinterLinkOs getLinkOsPrinter(String handle) throws ConnectionException, NotALinkOsPrinterException {
		ZebraPrinter basicPrinter = this.retrieveOrConnectPrinter(handle);
		ZebraPrinterLinkOs linkOsPrinter = ZebraPrinterFactory.createLinkOsPrinter(basicPrinter);
		if(linkOsPrinter == null) {
			throw new NotALinkOsPrinterException();
		}
		return linkOsPrinter;
	}

	@Override
	public void connectPrinter(String handle, Promise promise) {
		Log.d("ZebraModule", "Connecting to printer " + handle);
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			PrinterLanguage printerLanguage = printer.getPrinterControlLanguage();
			PrinterStatus printerStatus = printer.getCurrentStatus();
			WritableMap connData = new WritableNativeMap();
			connData.putString("handle", handle);
			connData.putString("controlLanguage", printerLanguage.toString());
			connData.putMap("status", convertPrinterStatus(printerStatus));
			promise.resolve(connData);
			Log.d("ZebraModule", "Connected to printer" + handle);
		} catch(ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		}
	}

	@Override
	public void isPrinterConnected(String handle, Promise promise) {
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			boolean connected = printer.getConnection().isConnected();
			promise.resolve(connected);
		} catch (ConnectionException e) {
			promise.resolve(false);
		}
	}

	@Override
	public void disconnectPrinter(String handle, Promise promise) {
		Log.d("ZebraModule", "Disconnecting printer " + handle);
		ZebraPrinter printer = connectedPrinters.get(handle);
		if(printer != null){
			try {
				Connection conn = printer.getConnection();
				conn.close();
			} catch(ConnectionException ignored) {}
		}
		connectedPrinters.remove(handle);
		promise.resolve(null);
	}

	@Override
	public void getPrinterStatus(String handle, Promise promise) {
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			PrinterStatus status = printer.getCurrentStatus();
			ReadableMap statusMap = convertPrinterStatus(status);
			promise.resolve(statusMap);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.KITKAT)
	@Override
	public void sendPrinterCommand(String handle, String data, Promise promise) {
		Log.d("ZebraPrinter", "Sending command to printer: " + handle);
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			//printer.sendCommand(data);
			byte[] responseBytes = printer.getConnection().sendAndWaitForResponse(data.getBytes(StandardCharsets.UTF_8), 500, 100, null);
			promise.resolve(responseBytes != null ? new String(responseBytes) : null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		}
	}

	@Override
	public void calibratePrinter(String handle, Promise promise) {
		Log.d("ZebraPrinter", "Calibrating printer: " + handle);
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			printer.calibrate();
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		}
	}

	@Override
	public void printConfigurationLabel(String handle, Promise promise) {
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			printer.printConfigurationLabel();
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		}
	}

	@Override
	public void resetPrinter(String handle, Promise promise) {
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			printer.reset();
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		}
	}

	@Override
	public void restorePrinterDefaults(String handle, Promise promise) {
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			printer.restoreDefaults();
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		}
	}

	@Override
	public void retrieveFileNames(String handle, @Nullable ReadableArray extensions, Promise promise) {
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			if(extensions != null) {
				String[] ext = extensions.toArrayList().toArray(new String[0]);
				//String[] ext = (String[])extensions.toArrayList().toArray();
				printer.retrieveFileNames(ext);
			}
			else {
				printer.retrieveFileNames();
			}
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (ZebraIllegalArgumentException e) {
			promise.reject("ZebraPrinterParseException", e.getMessage(), e);
		}
	}

	@Override
	public void retrieveObjectsProperties(String handle, Promise promise) {
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			List<PrinterObjectProperties> objects = printer.retrieveObjectsProperties();

			WritableArray jsArray = new WritableNativeArray();
			for (PrinterObjectProperties obj : objects) {
				WritableMap jsObject = new WritableNativeMap();
				jsObject.putString("drivePrefix", obj.getDrivePrefix());
				jsObject.putString("fileName", obj.getFileName());
				jsObject.putString("extension", obj.getExtension());
				jsObject.putString("fullName", obj.getFullName());
				jsObject.putLong("CRC32", obj.getCRC32());
				jsObject.putLong("fileSize", obj.getFileSize());
				jsArray.pushMap(jsObject);
			}
			promise.resolve(jsArray);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (ZebraIllegalArgumentException e) {
			promise.reject("ZebraPrinterParseException", e.getMessage(), e);
		}
	}

	@Override
	public void sendFileContents(String handle, String filePath, @Nullable Callback progressCallback, Promise promise) {
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			if(progressCallback != null)
				printer.sendFileContents(filePath, progressCallback::invoke);
			else
				printer.sendFileContents(filePath);

			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		}
	}

	@Override
	public void printImageFromFile(String handle, String imagePath, double x, double y, double width, double height, boolean insideFormat, Promise promise) {
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			ZebraImageI image = ZebraImageFactory.getImage(imagePath);
			printer.printImage(image, (int)x, (int)y, (int)width, (int)height, insideFormat);
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (IOException e) {
			promise.reject("IOException", e.getMessage(), e);
		}
	}

	@Override
	public void printImageFromBuffer(String handle, ReadableArray imageData, double x, double y, double width, double height, boolean insideFormat, Promise promise) {
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			ZebraImageI image = ZebraImageFactory.getImage(reactArrayToInputStream(imageData));
			printer.printImage(image, (int)x, (int)y, (int)width, (int)height, insideFormat);
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (IOException e) {
			promise.reject("IOException", e.getMessage(), e);
		}
	}

	@Override
	public void storeImageFromFile(String handle, String targetPath, String imagePath, double width, double height, Promise promise) {
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			ZebraImageI image = ZebraImageFactory.getImage(imagePath);
			printer.storeImage(targetPath, image, (int)width, (int)height);
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (ZebraIllegalArgumentException e) {
			promise.reject("ZebraIllegalArgumentException", e.getMessage(), e);
		} catch (IOException e) {
			promise.reject("IOException", e.getMessage(), e);
		}
	}

	@Override
	public void storeImageFromBuffer(String handle, String targetPath, ReadableArray imageData, double width, double height, Promise promise) {
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			ZebraImageI image = ZebraImageFactory.getImage(reactArrayToInputStream(imageData));
			printer.storeImage(targetPath, image, (int)width, (int)height);
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (ZebraIllegalArgumentException e) {
			promise.reject("ZebraIllegalArgumentException", e.getMessage(), e);
		} catch (IOException e) {
			promise.reject("IOException", e.getMessage(), e);
		}
	}

	@Override
	public void retrieveFormatFromPrinter(String handle, String formatPathOnPrinter, Promise promise) {
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			byte[] formatRaw = printer.retrieveFormatFromPrinter(formatPathOnPrinter);
			promise.resolve(new String(formatRaw));
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		}
	}

	@Override
	public void printStoredFormat(String handle, String formatPathOnPrinter, ReadableMap vars, Promise promise) {
		try {
			ZebraPrinter printer = this.retrieveOrConnectPrinter(handle);
			Map<Integer, String> map = new HashMap<>();
			for(Map.Entry<String, Object> entry : vars.toHashMap().entrySet()){
				try {
					map.put(Integer.parseInt(entry.getKey()), entry.getValue().toString());
				} catch (NumberFormatException ignored) {
					Log.w("ZebraModule", "Invalid number passed to format map.");
				}
			}
			printer.printStoredFormat(formatPathOnPrinter, map);
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		}
	}

	@Override
	public void getCommunityName(String handle, Promise promise) {
		try {
			ZebraPrinterLinkOs printer = this.getLinkOsPrinter(handle);
			promise.resolve(printer.getGetCommunityName());
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (NotALinkOsPrinterException e) {
			promise.reject("NotALinkOsPrinterException", e.getMessage(), e);
		}
	}

	@Override
	public void getLinkOsVersion(String handle, Promise promise) {
		try {
			ZebraPrinterLinkOs printer = this.getLinkOsPrinter(handle);
			LinkOsInformation info = printer.getLinkOsInformation();
			promise.resolve(info.getMajor() + "." + info.getMinor() + "." + info.getMicro());
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (NotALinkOsPrinterException e) {
			promise.reject("NotALinkOsPrinterException", e.getMessage(), e);
		}
	}

	@Override
	public void getPortStatus(String handle, Promise promise) {
		try {
			ZebraPrinterLinkOs printer = this.getLinkOsPrinter(handle);

			WritableArray jsArray = new WritableNativeArray();
			for (TcpPortStatus obj : printer.getPortStatus()) {
				WritableMap jsObject = new WritableNativeMap();
				jsObject.putString("printerPort", obj.getPrinterPort());
				jsObject.putString("portName", obj.getPortName());
				jsObject.putString("remoteIpAddress", obj.getRemoteIpAddress());
				jsObject.putString("remotePort", obj.getRemotePort());
				jsObject.putString("status", obj.getStatus());
				jsArray.pushMap(jsObject);
			}
			promise.resolve(jsArray);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (NotALinkOsPrinterException e) {
			promise.reject("NotALinkOsPrinterException", e.getMessage(), e);
		}
	}

	@Override
	public void getStorageInfo(String handle, Promise promise) {
		try {
			ZebraPrinterLinkOs printer = this.getLinkOsPrinter(handle);

			WritableArray jsArray = new WritableNativeArray();
			for (StorageInfo obj : printer.getStorageInfo()) {
				WritableMap jsObject = new WritableNativeMap();
				jsObject.putString("driveLetter", String.valueOf(obj.driveLetter));
				jsObject.putInt("driveType", obj.driveType.ordinal());
				jsObject.putLong("bytesFree", obj.bytesFree);
				jsObject.putBoolean("isPersistent", obj.isPersistent);
				jsArray.pushMap(jsObject);
			}
			promise.resolve(jsArray);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (NotALinkOsPrinterException e) {
			promise.reject("NotALinkOsPrinterException", e.getMessage(), e);
		}
	}

	@Override
	public void storeFileOnPrinter(String handle, String targetPath, ReadableArray fileContents, Promise promise) {
		try {
			ZebraPrinterLinkOs printer = this.getLinkOsPrinter(handle);
			printer.storeFileOnPrinter(reactArrayToByteArray(fileContents), targetPath);
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (ZebraIllegalArgumentException e) {
			promise.reject("ZebraIllegalArgumentException", e.getMessage(), e);
		} catch (NotALinkOsPrinterException e) {
			promise.reject("NotALinkOsPrinterException", e.getMessage(), e);
		}
	}

	@Override
	public void getObjectFromPrinter(String handle, String filePath, Promise promise) {
		try {
			ZebraPrinterLinkOs printer = this.getLinkOsPrinter(handle);
			byte[] buffer = printer.getObjectFromPrinter(filePath);
			promise.resolve(byteArrayToReactArray(buffer));
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (ZebraIllegalArgumentException e) {
			promise.reject("ZebraIllegalArgumentException", e.getMessage(), e);
		} catch (NotALinkOsPrinterException e) {
			promise.reject("NotALinkOsPrinterException", e.getMessage(), e);
		}
	}

	@Override
	public void deleteFile(String handle, String filePath, Promise promise) {
		try {
			ZebraPrinterLinkOs printer = this.getLinkOsPrinter(handle);
			printer.deleteFile(filePath);
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (NotALinkOsPrinterException e) {
			promise.reject("NotALinkOsPrinterException", e.getMessage(), e);
		}
	}

	@Override
	public void uploadTTFFont(String handle, String targetPath, ReadableArray fontData, Promise promise) {
		try {
			ZebraPrinterLinkOs printer = this.getLinkOsPrinter(handle);
			InputStream stream = reactArrayToInputStream(fontData);
			//for some reason they call this method "download", even if it sends the file to the printer
			printer.downloadTtfFont(stream, targetPath);
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (NotALinkOsPrinterException e) {
			promise.reject("NotALinkOsPrinterException", e.getMessage(), e);
		}
	}

	@Override
	public void uploadTTEFont(String handle, String targetPath, ReadableArray fontData, Promise promise) {
		try {
			ZebraPrinterLinkOs printer = this.getLinkOsPrinter(handle);
			InputStream stream = reactArrayToInputStream(fontData);
			//for some reason they call this method "download", even if it sends the file to the printer
			printer.downloadTteFont(stream, targetPath);
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (NotALinkOsPrinterException e) {
			promise.reject("NotALinkOsPrinterException", e.getMessage(), e);
		}
	}

	@Override
	public void printDirectoryLabel(String handle, Promise promise) {
		try {
			ZebraPrinterLinkOs printer = this.getLinkOsPrinter(handle);
			printer.printDirectoryLabel();
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (NotALinkOsPrinterException e) {
			promise.reject("NotALinkOsPrinterException", e.getMessage(), e);
		}
	}

	@Override
	public void printNetworkConfigurationLabel(String handle, Promise promise) {
		try {
			ZebraPrinterLinkOs printer = this.getLinkOsPrinter(handle);
			printer.printNetworkConfigurationLabel();
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (NotALinkOsPrinterException e) {
			promise.reject("NotALinkOsPrinterException", e.getMessage(), e);
		}
	}

	@Override
	public void resetNetwork(String handle, Promise promise) {
		try {
			ZebraPrinterLinkOs printer = this.getLinkOsPrinter(handle);
			printer.resetNetwork();
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (NotALinkOsPrinterException e) {
			promise.reject("NotALinkOsPrinterException", e.getMessage(), e);
		}
	}

	@Override
	public void restoreNetworkDefaults(String handle, Promise promise) {
		try {
			ZebraPrinterLinkOs printer = this.getLinkOsPrinter(handle);
			printer.restoreNetworkDefaults();
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (NotALinkOsPrinterException e) {
			promise.reject("NotALinkOsPrinterException", e.getMessage(), e);
		}
	}

	@Override
	public void setClock(String handle, String dateTime, Promise promise) {
		try {
			ZebraPrinterLinkOs printer = this.getLinkOsPrinter(handle);
			printer.setClock(dateTime);
			promise.resolve(null);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		} catch (ZebraIllegalArgumentException e) {
			promise.reject("ZebraIllegalArgumentException", e.getMessage(), e);
		} catch (NotALinkOsPrinterException e) {
			promise.reject("NotALinkOsPrinterException", e.getMessage(), e);
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.DONUT)
	@Override
	public void findNetworkPrinters(Callback onPrinterFound, Promise promise){
		try {
			Log.d("ZebraModule", "Starting network printer discovery.");
			DiscoveryHandler discoveryHandler = new DiscoveryHandlerImpl(CONNECTION_NETWORK, onPrinterFound, promise);

			Context context = getReactApplicationContext().getApplicationContext();
			MulticastLock lock = null;
			//create multicast lock only if permission is given in AndroidManifest
			if(context.checkCallingOrSelfPermission(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE) == PackageManager.PERMISSION_GRANTED) {
				WifiManager wifi = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
				lock = wifi.createMulticastLock("wifi_multicast_lock");
				lock.setReferenceCounted(true);
				lock.acquire();
			}
			NetworkDiscoverer.findPrinters(discoveryHandler);
			if(lock != null){
				lock.release();
			}
		} catch (DiscoveryException e) {
			promise.reject("DiscoveryException", e.getMessage(), e);
		}
	}

	@Override
	public void findBluetoothPrinters(Callback onPrinterFound, boolean useBle, Promise promise) {
		try {
			Log.d("ZebraModule", "Starting bluetooth printer discovery.");
			Context context = getReactApplicationContext().getApplicationContext();
			//TODO: check permissions ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION

			if(Build.VERSION.SDK_INT < Build.VERSION_CODES.ECLAIR ||
				(!useBle && !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) ||
				(useBle && !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
			){
				promise.reject("ConnectionException", "Bluetooth is not supported on this device");
				return;
			}
			if(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
					ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
					ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
			) {
				promise.reject("ConnectionException", "Permission to Bluetooth was not granted");
				return;
			}
			//TODO: this doesn't work, Android says BLUETOOTH_SCAN permission is missing
			DiscoveryHandler discoveryHandler = new DiscoveryHandlerImpl(useBle ? CONNECTION_BLUETOOTH_LE : CONNECTION_BLUETOOTH, onPrinterFound, promise);
			if(useBle) BluetoothDiscoverer.findPrinters(context, discoveryHandler);
			else BluetoothLeDiscoverer.findPrinters(context, discoveryHandler);
		} catch (ConnectionException e) {
			promise.reject("ConnectionException", e.getMessage(), e);
		}
	}

	@Override
	public void findUsbPrinters(Callback onPrinterFound, Promise promise) {
		Log.d("ZebraModule", "Starting USB printer discovery.");
		DiscoveryHandler discoveryHandler = new DiscoveryHandlerImpl(CONNECTION_USB, onPrinterFound, promise);

		Context context = getReactApplicationContext().getApplicationContext();
		//TODO: check permissions ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION
		//if(context.checkCallingOrSelfPermission(Manifest.permission.CHANGE_WIFI_MULTICAST_STATE) == PackageManager.PERMISSION_GRANTED) {}
		UsbDiscoverer.findPrinters(context, discoveryHandler);

//		UsbManager manager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
//
//		if (manager != null) {
//			Map<String, UsbDevice> usbDevices = manager.getDeviceList();
//			for(UsbDevice device : usbDevices.values()) {
////				if (UsbDiscoverer.isZebraUsbDevice(device)) {
////					var1.foundPrinter(new DiscoveredPrinterUsb(var4.getDeviceName(), var0, var4));
////				}
//				Log.d("ZebraModule", "FOUND USB DEVICE:");
//				Log.d("ZebraModule", "device class:" + device.getDeviceClass());
//				Log.d("ZebraModule", "device id:" + device.getDeviceId());
//				Log.d("ZebraModule", "device name:" + device.getDeviceName());
//				Log.d("ZebraModule", "device protocol:" + device.getDeviceProtocol());
//				Log.d("ZebraModule", "device subclass:" + device.getDeviceSubclass());
//				Log.d("ZebraModule", "device manufacturer name:" + device.getManufacturerName());
//				Log.d("ZebraModule", "device product id:" + device.getProductId());
//				//Log.d("ZebraModule", "device serial number:" + device.getSerialNumber());
//				Log.d("ZebraModule", "device vendor id:" + device.getVendorId());
//				Log.d("ZebraModule", "device version:" + device.getVersion());
//			}
//		}
		//UsbDevice device = manager.getDeviceList().values()[0];
	}

	@NonNull
	private static ReadableMap convertPrinterStatus(@NonNull PrinterStatus status) {
		WritableMap map = new WritableNativeMap();
		map.putString("printMode", status.printMode.toString());
		map.putInt("labelLengthInDots", status.labelLengthInDots);
		map.putInt("numberOfFormatsInReceiveBuffer", status.numberOfFormatsInReceiveBuffer);
		map.putInt("labelsRemainingInBatch", status.labelsRemainingInBatch);
		map.putBoolean("isPartialFormatInProgress", status.isPartialFormatInProgress);
		map.putBoolean("isHeadCold", status.isHeadCold);
		map.putBoolean("isHeadOpen", status.isHeadOpen);
		map.putBoolean("isHeadTooHot", status.isHeadTooHot);
		map.putBoolean("isPaperOut", status.isPaperOut);
		map.putBoolean("isRibbonOut", status.isRibbonOut);
		map.putBoolean("isReceiveBufferFull", status.isReceiveBufferFull);
		map.putBoolean("isPaused", status.isPaused);
		map.putBoolean("isReadyToPrint", status.isReadyToPrint);
		return map;
	}

	@NonNull
	private static byte[] reactArrayToByteArray(@NonNull ReadableArray array) {
		byte[] buffer = new byte[array.size()];
		for(int i = 0; i < array.size(); i++){
			buffer[i] = (byte)array.getInt(i);
		}
		return buffer;
	}
	@NonNull
	private static InputStream reactArrayToInputStream(@NonNull ReadableArray array) {
		return new ByteArrayInputStream(reactArrayToByteArray(array));
	}

	@NonNull
	private static WritableArray byteArrayToReactArray(@NonNull byte[] array) {
		WritableArray jsArray = new WritableNativeArray();
		for(byte b : array) {
			jsArray.pushInt(b);
		}
		return jsArray;
	}

	private static String createHandleFromConnection(@NonNull Connection conn) {
		if(conn instanceof UsbConnection){
			UsbManager manager = ((UsbConnection)conn).getManager();
			if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
				Map<String, UsbDevice> usbDevices = manager.getDeviceList();
				for (UsbDevice device : usbDevices.values()) {
					if (device.getDeviceName().equals(((UsbConnection)conn).getDeviceName())) {
						return "USB:" + Integer.toString(device.getVendorId(), 16) + ":" + Integer.toString(device.getProductId(), 16);
					}
				}
			}
		}
		if(conn instanceof BluetoothLeStatusConnection){
			return "BTLE_STATUS:" + ((BluetoothLeStatusConnection)conn).getMACAddress();
		}
		if(conn instanceof MultichannelBluetoothLeConnection){
			return "BTLE_MULTI:" + ((BluetoothLeConnection)((MultichannelBluetoothLeConnection)conn).getPrintingChannel()).getMACAddress();
		}
		if(conn instanceof BluetoothLeConnection){
			return "BTLE:" + ((BluetoothLeConnection)conn).getMACAddress();
		}
		if(conn instanceof BluetoothStatusConnectionInsecure){
			return "BT_STATUS_INSECURE:" + ((BluetoothStatusConnectionInsecure)conn).getMACAddress();
		}
		if(conn instanceof BluetoothStatusConnection){
			return "BT_STATUS:" + ((BluetoothStatusConnection)conn).getMACAddress();
		}
		if(conn instanceof BluetoothConnectionInsecure){
			return "BT_INSECURE:" + ((BluetoothConnectionInsecure)conn).getMACAddress();
		}
		if(conn instanceof MultichannelBluetoothConnection){
			return "BT_MULTI:" + ((BluetoothConnection)((MultichannelBluetoothConnection)conn).getPrintingChannel()).getMACAddress();
		}
		if(conn instanceof BluetoothConnection){
			return "BT:" + ((BluetoothConnection)conn).getMACAddress();
		}
		//On TCP connections this is ok
		return conn.toString();
	}

	private Connection createConnection(@NonNull String handle, int mtr, int ttw) throws ConnectionException {
		Context context = getReactApplicationContext().getApplicationContext();
		if(handle.startsWith("TCP_MULTI")){
			//MultichannelTcpConnection - TCP_MULTI:ip:printing_port:status_port
			String[] parts = handle.split(":");
			if(parts.length != 4){
				throw new ConnectionException("Invalid connection handle");
			}
			try{
				String ip = parts[1];
				int port = Integer.parseInt(parts[2]);
				int statusPort = Integer.parseInt(parts[3]);
				return new MultichannelTcpConnection(ip, port, statusPort, mtr, ttw);
			} catch(NumberFormatException e) {
				throw new ConnectionException("Invalid connection handle");
			}
		}
		else if(handle.startsWith("TCP")){
			//TcpConnection - TCP:ip:port
			//TcpStatusConnection - TCP_STATUS:ip:port
			String[] parts = handle.split(":");
			if(parts.length != 3){
				throw new ConnectionException("Invalid connection handle");
			}
			try{
				String ip = parts[1];
				int port = Integer.parseInt(parts[2]);
				if(handle.startsWith("TCP_STATUS")) return new TcpStatusConnection(ip, port, mtr, ttw);
				else return new TcpConnection(ip, port, mtr, ttw);
			} catch(NumberFormatException e) {
				throw new ConnectionException("Invalid connection handle");
			}
		}
		else if(handle.startsWith("BT")){
			//BluetoothConnection - BT:mac
			//BluetoothStatusConnection - BT_STATUS:mac
			//BluetoothConnectionInsecure - BT_INSECURE:mac
			//BluetoothStatusConnectionInsecure - BT_STATUS_INSECURE:mac
			//MultichannelBluetoothConnection - BT_MULTI:mac

			//BluetoothLeConnection - BTLE:mac
			//BluetoothLeStatusConnection - BTLE_STATUS:mac
			//MultichannelBluetoothLeConnection - BTLE_MULTI:mac
			String mac = handle.substring(handle.indexOf(':') + 1);
			if(mac.split(":").length != 6){
				throw new ConnectionException("Invalid connection handle");
			}
			if(Build.VERSION.SDK_INT < Build.VERSION_CODES.ECLAIR ||
					(!handle.startsWith("BTLE") && !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) ||
					(handle.startsWith("BTLE") && !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
			){
				throw new ConnectionException("Bluetooth is not supported on this device");
			}
			if(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
					ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
					ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
			) {
				throw new ConnectionException("Permission to Bluetooth was not granted");
			}
			if(handle.startsWith("BTLE_STATUS")) return new BluetoothLeStatusConnection(mac, mtr, ttw, context);
			else if(handle.startsWith("BTLE_MULTI")) return new MultichannelBluetoothLeConnection(mac, mtr, ttw, context);
			else if(handle.startsWith("BTLE")) return new BluetoothLeConnection(mac, mtr, ttw, context);
			else if(handle.startsWith("BT_STATUS")) return new BluetoothStatusConnection(mac, mtr, ttw);
			else if(handle.startsWith("BT_INSECURE")) return new BluetoothConnectionInsecure(mac, mtr, ttw);
			else if(handle.startsWith("BT_STATUS_INSECURE")) return new BluetoothStatusConnectionInsecure(mac, mtr, ttw);
			else if(handle.startsWith("BT_MULTI")) return new MultichannelBluetoothConnection(mac, mtr, ttw);
			else return new BluetoothConnection(mac, mtr, ttw);
		}
		else if(handle.startsWith("USB")){
			//UsbConnection - USB:vid:pid
			String[] parts = handle.split(":");
			if(parts.length != 3){
				throw new ConnectionException("Invalid connection handle");
			}
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
				throw new ConnectionException("Unsupported Android SDK version");
			}
			try {
				int vid = Integer.parseInt(parts[1], 16);
				int pid = Integer.parseInt(parts[2], 16);

				UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
				if (manager != null) {
					Map<String, UsbDevice> usbDevices = manager.getDeviceList();
					for (UsbDevice device : usbDevices.values()) {
						if (device.getVendorId() == vid && device.getProductId() == pid) {
							if(!this.requestUsbPermissions(context, manager, device)){
								throw new ConnectionException("Permission to USB device was denied");
							}
							return new UsbConnection(manager, device, mtr, ttw);
						}
					}
				}
				throw new ConnectionException("USB device is not connected");
			} catch(NumberFormatException e) {
				//bottom ConnectionException will be thrown
			}
		}
		throw new ConnectionException("Invalid connection handle");
	}

	private Connection createConnection(@NonNull String handle) throws ConnectionException {
		return createConnection(handle, 5000, 500);
	}


	@RequiresApi(api = Build.VERSION_CODES.HONEYCOMB_MR1)
	private boolean requestUsbPermissions(Context context, UsbManager manager, UsbDevice device) {
		if (manager.hasPermission(device)) {
			return true;
		}
		Log.d("ZebraModule", "USB permission was not granted, requesting it now...");

		final String ACTION_USB_PERMISSION = "com.android.rnzebralinkos.USB_PERMISSION";
		PendingIntent intent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);

		// Wait synchronously for permission to be granted
		final Object permissionLock = new Object();
		final boolean[] permissionGranted = { false };

		BroadcastReceiver receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
					synchronized (permissionLock) {
						UsbDevice dev = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
						if (dev != null && dev.getDeviceId() == device.getDeviceId()) {
							permissionGranted[0] = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
							Log.d("ZebraModule", "USB permission is " + (permissionGranted[0] ? "granted" : "denied"));
							permissionLock.notify();
						}
					}
					Log.d("ZebraModule", "Unregistering USB permission broadcast receiver");
					context.unregisterReceiver(this);
				}
			}
		};

		Log.d("ZebraModule", "Registering USB permission broadcast receiver");
		ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

		manager.requestPermission(device, intent);

		synchronized (permissionLock) {
			try {
				//if the user does not confirm the dialog in 30 seconds, the permission is denied
				permissionLock.wait(30000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		Log.d("ZebraModule", "Finished requesting USB permission, status: " + (permissionGranted[0] ? "granted" : "denied"));
		return permissionGranted[0];
	}

//	@Override
//	public void connectDiscoveredPrinter(String printerAddress, Promise promise){
//		DiscoveredPrinter printer = discoveredPrinters.get(printerAddress);
//		if(printer == null){
//			promise.reject("PrinterNotFoundError", "Invalid printer handle");
//			return;
//		}
//		this.retrieveOrConnectPrinter(printer.getConnection(), promise);
//	}

//	private void connectPrinter(Connection conn, Promise promise) {
//		Log.d("ZebraModule", "Connecting to printer " + conn);
//		try {
//			conn.open();
//			ZebraPrinter zebraPrinter = ZebraPrinterFactory.getInstance(conn);
//			String handle = conn.toString();
//			connectedPrinters.put(handle, zebraPrinter);
//			PrinterLanguage printerLanguage = zebraPrinter.getPrinterControlLanguage();
//			PrinterStatus printerStatus = zebraPrinter.getCurrentStatus();
//			WritableMap connData = new WritableNativeMap();
//			connData.putString("handle", handle);
//			connData.putString("controlLanguage", printerLanguage.toString());
//			connData.putMap("status", convertPrinterStatus(printerStatus));
//			promise.resolve(connData);
//			Log.d("ZebraModule", "Connected to printer" + handle);
//		} catch(ConnectionException e) {
//			Log.e("ZebraModule", "Printer connection failed");
//			promise.reject("ConnectionException", e.getMessage(), e);
//		} catch (ZebraPrinterLanguageUnknownException e) {
//			Log.e("ZebraModule", "Printer language is unknown");
//			promise.reject("ZebraPrinterLanguageUnknownException", e.getMessage(), e);
//		}
//	}


//	@Override
//	public void sendPrinterCommand(String handle, String data, Promise promise) {
//		Log.d("ZebraPrinter", "Sending command printer... ;handle:" + handle);
//		ZebraPrinter printer = connectedPrinters.get(handle);
//		if(printer == null){
//			promise.reject("PrinterNotFoundError", "Invalid printer handle");
//			return;
//		}
//		try {
//			printer.sendCommand(data);
//			promise.resolve(null);
//		} catch (ConnectionException e) {
//			promise.reject("ConnectionException", e.getMessage(), e);
//		}
//	}

//	@Override
//	public void connectNetworkPrinter(String ipAddress, double port, double maxTimeoutForRead, double timeToWaitForMoreData, Promise promise) {
//		try {
//			Connection conn = new TcpConnection(ipAddress, (int)port);
//			//It is a bit inefficient to create a new connection,
//			//convert it to handle, and then create a new connection from the handle
//			ZebraPrinter printer = this.retrieveOrConnectPrinter(conn.toString());
//			boolean connected = printer.getConnection().isConnected();
//			promise.resolve(connected);
//		} catch (ConnectionException e) {
//			promise.resolve(false);
//		}
//	}
//
//	@Override
//	public void connectBluetoothPrinter(String macAddress, boolean useBle, boolean insecure, double maxTimeoutForRead, double timeToWaitForMoreData, Promise promise) {
//		try {
//			Context context = getReactApplicationContext().getApplicationContext();
//			Connection conn;
//			if(useBle) conn = new BluetoothLeConnection(macAddress, (int)maxTimeoutForRead, (int)timeToWaitForMoreData, context);
//			else if(insecure) conn = new BluetoothConnectionInsecure(macAddress, (int)maxTimeoutForRead, (int)timeToWaitForMoreData);
//			else conn = new BluetoothConnection(macAddress, (int)maxTimeoutForRead, (int)timeToWaitForMoreData);
//
//			ZebraPrinter printer = this.retrieveOrConnectPrinter(createHandleFromConnection(conn));
//			boolean connected = printer.getConnection().isConnected();
//			promise.resolve(connected);
//		} catch (ConnectionException e) {
//			promise.resolve(false);
//		}
//	}
//
//	@RequiresApi(api = Build.VERSION_CODES.HONEYCOMB_MR1)
//	@Override
//	public void connectUsbPrinter(double maxTimeoutForRead, double timeToWaitForMoreData, Promise promise) {
//		try {
//			Context context = getReactApplicationContext().getApplicationContext();
//			UsbManager manager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
//
//			Connection conn = null;
//			if (manager != null) {
//				Map<String, UsbDevice> usbDevices = manager.getDeviceList();
//
//				for(UsbDevice device : usbDevices.values()) {
//					if (UsbDiscoverer.isZebraUsbDevice(device)) {
//						conn = new UsbConnection(manager, device, (int)maxTimeoutForRead, (int)timeToWaitForMoreData);
//					}
//				}
//			}
//			ZebraPrinter printer = this.retrieveOrConnectPrinter(conn.toString());
//			boolean connected = printer.getConnection().isConnected();
//			promise.resolve(connected);
//		} catch (ConnectionException e) {
//			promise.resolve(false);
//		}
//	}
}
