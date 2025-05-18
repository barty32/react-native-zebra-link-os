import { DiscoveredPrinter } from "./DiscoveredPrinter";
import { ZebraPrinter } from "./ZebraPrinter";
import ZebraNative, {
	handleNativeException,
	NativeDiscoveredPrinter,
	PrinterConnectionType,
	PrinterHandle,
	ZplPrintMode,
	PrinterStatus,
	PrinterObjectProperties,
	DriveType,
	StorageInfo,
	TcpPortStatus
} from "./native/NativeRNZebraLinkOS";

/**
 * This function will search the network using a combination of discovery methods to find printers on the network. 
 * When the discovery is finished, the promise returned will resolve.
 * @param onPrinterFound Callback that will be invoked for each printer found during discovery.
 */
export function findNetworkPrinters(onPrinterFound: (printer: DiscoveredPrinter) => void): Promise<void> {
	return ZebraNative.findNetworkPrinters((printer: NativeDiscoveredPrinter) => {
		onPrinterFound(new DiscoveredPrinter(printer));
	});
}

export function findBluetoothPrinters(onPrinterFound: (printer: DiscoveredPrinter) => void, useBle: boolean): Promise<void> {
	return ZebraNative.findBluetoothPrinters((printer: NativeDiscoveredPrinter) => {
		onPrinterFound(new DiscoveredPrinter(printer));
	}, useBle);
}

export function findUsbPrinters(onPrinterFound: (printer: DiscoveredPrinter) => void): Promise<void> {
	return ZebraNative.findUsbPrinters((printer: NativeDiscoveredPrinter) => {
		onPrinterFound(new DiscoveredPrinter(printer));
	});
}

export async function connectPrinter(handle: string): Promise<ZebraPrinter> {
	const data = await handleNativeException(ZebraNative.connectPrinter(handle));
	console.log("Connected to printer:", data);
	return new ZebraPrinter(data);
}

export async function connectNetworkPrinter(ipAddress: string, port = -1, statusPort = -1): Promise<ZebraPrinter> {
	let handle = '';
	if(port === -1 && statusPort === -1) {
		handle = `TCP:${ipAddress}:9100`;
	}
	else if(port === -1) {
		handle = `TCP_STATUS:${ipAddress}:${statusPort}`;
	}
	else if(statusPort === -1) {
		handle = `TCP:${ipAddress}:${port}`;
	}
	else {
		handle = `TCP_MULTI:${ipAddress}:${port}:${statusPort}`;
	}
	console.log("Connecting to network printer:", handle);
	return await connectPrinter(handle);
}

export async function connectBluetoothPrinter(macAddress: string, useBle: boolean, insecure = false): Promise<ZebraPrinter> {
	let handle = '';
	if(useBle) handle = `BTLE:${macAddress}`;
	else if(insecure) handle = `BT_INSECURE:${macAddress}`;
	else handle = `BT:${macAddress}`;
	console.log("Connecting to bluetooth printer:", handle);
	return await connectPrinter(handle);
}

// export async function connectUsbPrinter(): Promise<ZebraPrinter> {
// 	const handle = 'USB:vid:pid';
// 	console.log("Connecting to USB printer:", handle);
// 	return await connectPrinter(handle);
// }

export function handleToConnectionType(handle: PrinterHandle) {
	if(handle.startsWith('TCP')) return PrinterConnectionType.Network;
	if(handle.startsWith('BTLE')) return PrinterConnectionType.BluetoothLE;
	if(handle.startsWith('BT')) {
		if(handle.includes('INSECURE')) return PrinterConnectionType.BluetoothInsecure;
		else return PrinterConnectionType.Bluetooth;
	}
	if(handle.startsWith('USB')) return PrinterConnectionType.USB;
	throw new Error(`Invalid handle: ${handle}`);
}

//export everything from native module
export {
	DiscoveredPrinter,
	ZebraPrinter,
	PrinterHandle,
	ZplPrintMode,
	PrinterStatus,
	PrinterObjectProperties,
	DriveType,
	StorageInfo,
	TcpPortStatus,
	PrinterConnectionType
};