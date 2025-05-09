package com.rnzebralinkos;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionBuilder;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.comm.TcpConnection;
import com.zebra.sdk.device.ZebraIllegalArgumentException;
import com.zebra.sdk.printer.PrinterLanguage;
import com.zebra.sdk.printer.PrinterStatus;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;
import com.zebra.sdk.printer.discovery.DiscoveredPrinter;
import com.zebra.sdk.printer.discovery.DiscoveryException;
import com.zebra.sdk.printer.discovery.DiscoveryHandler;
import com.zebra.sdk.printer.discovery.NetworkDiscoverer;

public class ZebraModule extends NativeRNZebraLinkOSSpec {

	public static String NAME = "RNZebraLinkOS";
	private final Map<String, ZebraPrinter> connectedPrinters = new HashMap<>();

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
				Log.d("ZebraModule", "Printer not connected, connecting: " + handle);
				Connection conn = ConnectionBuilder.build(handle);
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
	public void connectNetworkPrinter(String ipAddress, double port, Promise promise) {
		try {
			Connection conn = new TcpConnection(ipAddress, (int)port);
			ZebraPrinter printer = this.retrieveOrConnectPrinter(conn.toString());
			boolean connected = printer.getConnection().isConnected();
			promise.resolve(connected);
		} catch (ConnectionException e) {
			promise.resolve(false);
		}
	}

	@Override
	public void connectBluetoothPrinter(String macAddress, Promise promise) {
		//TODO
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

	@RequiresApi(api = Build.VERSION_CODES.DONUT)
	@Override
	public void findNetworkPrinters(Callback onPrinterFound, Promise promise){
		DiscoveryHandler discoveryHandler = new DiscoveryHandler() {
			public void foundPrinter(DiscoveredPrinter printer) {
				Log.d("ZebraModule", "Found a printer: " + printer.address);

				Map<String, String> discoveryDataMap = printer.getDiscoveryDataMap();
				WritableMap obj = new WritableNativeMap();
				WritableMap discoveryData = new WritableNativeMap();
				for (Map.Entry<String, String> entry : discoveryDataMap.entrySet()) {
					discoveryData.putString(entry.getKey(), entry.getValue());
				}
				obj.putString("handle", printer.getConnection().toString());
				obj.putString("address", printer.address);
				obj.putString("communicationType", "network");
				obj.putMap("discoveryData", discoveryData);
				onPrinterFound.invoke(obj);
			}

			public void discoveryFinished() {
				Log.d("ZebraModule", "Printer discovery finished");
				promise.resolve(null);
			}

			public void discoveryError(String message) {
				Log.d("ZebraModule", "Printer discovery error");
				promise.reject("DiscoveryException", message);
			}
		};
		try {
			Log.d("ZebraModule", "Starting printer discovery.");
			WifiManager wifi = (WifiManager)getReactApplicationContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
			MulticastLock lock = wifi.createMulticastLock("wifi_multicast_lock");
			lock.setReferenceCounted(true);
			lock.acquire();
			NetworkDiscoverer.findPrinters(discoveryHandler);
			lock.release();
		} catch (DiscoveryException e) {
			promise.reject("DiscoveryException", e.getMessage(), e);
		}
	}

	@Override
	public void findBluetoothPrinters(Callback onPrinterFound, Promise promise) {
		//TODO
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
}