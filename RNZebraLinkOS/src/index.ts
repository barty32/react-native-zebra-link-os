import { DiscoveredPrinter } from "./DiscoveredPrinter.js";
import { ZebraPrinter } from "./ZebraPrinter.js";
import ZebraNative, { handleNativeException, NativeDiscoveredPrinter } from "./native/NativeRNZebraLinkOS.js";

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

export function findBluetoothPrinters(onPrinterFound: (printer: DiscoveredPrinter) => void): Promise<void> {
	return ZebraNative.findBluetoothPrinters((printer: NativeDiscoveredPrinter) => {
		onPrinterFound(new DiscoveredPrinter(printer));
	});
}

// export function findUsbPrinters(onPrinterFound: (printer: DiscoveredPrinter) => void): Promise<void> {
// 	return RNZebraLinkOS.findUsbPrinters((printer: NativeDiscoveredPrinter) => {
// 		onPrinterFound(new DiscoveredPrinter(printer));
// 	});
// }

export async function connectPrinter(handle: string): Promise<ZebraPrinter> {
	const data = await handleNativeException(ZebraNative.connectPrinter(handle));
	console.log("Connected to printer:", data);
	return new ZebraPrinter(data);
}

export async function connectNetworkPrinter(ipAddress: string, port: number): Promise<ZebraPrinter> {
	const data = await handleNativeException(ZebraNative.connectNetworkPrinter(ipAddress, port));
	console.log("Connected to network printer:", data);
	return new ZebraPrinter(data);
}

export async function connectBluetoothPrinter(macAddress: string): Promise<ZebraPrinter> {
	const data = await handleNativeException(ZebraNative.connectBluetoothPrinter(macAddress));
	console.log("Connected to bluetooth printer:", data);
	return new ZebraPrinter(data);
}

export { DiscoveredPrinter, ZebraPrinter };