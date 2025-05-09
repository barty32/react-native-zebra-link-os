import NativeModule, { handleNativeException, NativePrinterConnectionData, PrinterHandle, PrinterStatus, ProgressCallback } from "./native/NativeRNZebraLinkOS.js";
import { PrinterLanguage } from "./types.js";


export class ZebraPrinter {

	private readonly handle: PrinterHandle;
	private status: PrinterStatus;
	private controlLanguage: PrinterLanguage;

	constructor(data: NativePrinterConnectionData) {
		this.handle = data.handle;
		this.status = data.status;
		this.controlLanguage = data.controlLanguage as PrinterLanguage;
	}

	public getStatus() {
		return this.status;
	}

	/**
	 * Returns the printer control language (e.g. ZPL or CPCL) of the printer.
	 */
	public getControlLanguage() {
		return this.controlLanguage;
	}

	/**
	 * Checks if the connection to the printer is open.
	 * @returns true if the connection is open.
	 */
	public async isConnected() {
		return await handleNativeException(NativeModule.isPrinterConnected(this.handle));
	}

	public async connect() {
		const data = await handleNativeException(NativeModule.connectPrinter(this.handle));
		this.status = data.status;
		this.controlLanguage = data.controlLanguage as PrinterLanguage;
	}

	public async disconnect() {
		await handleNativeException(NativeModule.disconnectPrinter(this.handle));
	}

	/**
	 * Queries the printer for its status.
	 * Use getStatus() to retrieve it.
	 */
	public async queryPrinterStatus() {
		this.status = await handleNativeException(NativeModule.getPrinterStatus(this.handle))
	}

	//tools
	public async calibrate() {
		console.log("Calibrating printer...", this.handle);
		await handleNativeException(NativeModule.calibratePrinter(this.handle));
	}

	public async restoreDefaults() {
		await handleNativeException(NativeModule.restorePrinterDefaults(this.handle));
	}

	public async printConfigurationLabel() {
		await handleNativeException(NativeModule.printConfigurationLabel(this.handle));
	}

	public async send(data: string) {
		console.log("Sending command...", this.handle);
		return await handleNativeException(NativeModule.sendPrinterCommand(this.handle, data));
	}

	public async reset() {
		await handleNativeException(NativeModule.resetPrinter(this.handle));
	}

	//file operations
	public async retrieveFileNames(extensions?: string[]) {
		return await handleNativeException(NativeModule.retrieveFileNames(this.handle, extensions));
	}

	public async sendFileContents(filePath: string, progressCallback?: ProgressCallback) {
		await handleNativeException(NativeModule.sendFileContents(this.handle, filePath, progressCallback));
	}

	//graphics operations
	//printImage(image: any, x: number, y: number, width?: number, height?: number): void;
	//storeImage(path: string, image: any, width: number, height: number): void;

	//format util

	//link os functions

}