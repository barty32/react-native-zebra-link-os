import { connectPrinter } from "./index";
import { NativeDiscoveredPrinter, PrinterConnectionType, PrinterHandle } from "./native/NativeRNZebraLinkOS";


export class DiscoveredPrinter {

	private readonly handle: PrinterHandle;
	private address: string;
	private connectionType: PrinterConnectionType;
	private discoveryData: Map<string, string>;

	constructor(printer: NativeDiscoveredPrinter) {
		this.handle = printer.handle;
		this.address = printer.address;
		this.connectionType = printer.connectionType;
		this.discoveryData = new Map<string, string>();
		for(const key in printer.discoveryData) {
			this.discoveryData.set(key, printer.discoveryData[key]);
		}
	}

	async connect() {
		return connectPrinter(this.handle);
	}

	getHandle() {
		return this.handle;
	}

	getAddress() {
		return this.address;
	}

	getDiscoveryDataMap() {
		return this.discoveryData;
	}

	getConnectionType() {
		return this.connectionType;
	}
}