import { connectPrinter } from "./index.js";
import { NativeDiscoveredPrinter, PrinterHandle } from "./native/NativeRNZebraLinkOS.js";
import { PrinterCommunicationType } from "./types.js";



export class DiscoveredPrinter {

	private readonly handle: PrinterHandle;
	private address: string;
	private communicationType: PrinterCommunicationType;
	private discoveryData: Map<string, string>;

	constructor(printer: NativeDiscoveredPrinter) {
		this.handle = printer.handle;
		this.address = printer.address;

		if(printer.communicationType === 'bluetooth')
			this.communicationType = PrinterCommunicationType.BLUETOOTH;
		else if(printer.communicationType === 'usb')
			this.communicationType = PrinterCommunicationType.USB;
		else
			this.communicationType = PrinterCommunicationType.NETWORK;

		this.discoveryData = new Map<string, string>();
		for(const key in this.discoveryData) {
			this.discoveryData.set(key, printer.discoveryData[key]);
		}
	}

	async connect() {
		return connectPrinter(this.handle);
	}

	getAddress() {
		return this.address;
	}

	getDiscoveryDataMap() {
		return this.discoveryData;
	}

	getCommunicationType() {
		return this.communicationType;
	}

}