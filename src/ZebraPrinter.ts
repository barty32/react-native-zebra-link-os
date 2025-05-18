import { handleToConnectionType } from "./index";
import NativeModule, {
	handleNativeException,
	NativePrinterConnectionData,
	PrinterConnectionType,
	PrinterHandle,
	PrinterLanguage,
	PrinterStatus,
	ProgressCallback
} from "./native/NativeRNZebraLinkOS";


export class ZebraPrinter {

	private readonly handle: PrinterHandle;
	private status: PrinterStatus;
	private controlLanguage: PrinterLanguage;
	private connectionType: PrinterConnectionType;

	constructor(data: NativePrinterConnectionData) {
		this.handle = data.handle;
		this.status = data.status;
		this.controlLanguage = data.controlLanguage as PrinterLanguage;
		this.connectionType = handleToConnectionType(this.handle);
	}

	public getHandle(): PrinterHandle {
		return this.handle;
	}

	public getConnectionType() {
		return this.connectionType;
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
	/**
	 * Sends the appropriate calibrate command to the printer.
	 */
	public async calibrate() {
		console.log("Calibrating printer...", this.handle);
		await handleNativeException(NativeModule.calibratePrinter(this.handle));
	}

	/**
	 * Sends the appropriate restore defaults command to the printer.
	 */
	public async restoreDefaults() {
		await handleNativeException(NativeModule.restorePrinterDefaults(this.handle));
	}

	/**
	 * Sends the appropriate print configuration command to the printer.
	 */
	public async printConfigurationLabel() {
		await handleNativeException(NativeModule.printConfigurationLabel(this.handle));
	}

	/**
	 * Sends a command to the printer and waits for response.
	 * This method can be used to print labels.
	 * @param data command content
	 * @returns the command response, if any.
	 */
	public async send(data: string) {
		console.log("Sending command...", this.handle);
		return await handleNativeException(NativeModule.sendPrinterCommand(this.handle, data));
	}

	/**
	 * Sends the appropriate reset command to the printer.
	 * @remarks You should call disconnect() after this method, as resetting the printer will terminate the connection.
	 */
	public async reset() {
		await handleNativeException(NativeModule.resetPrinter(this.handle));
	}

	/**
	 * Retrieves the names of the files which are stored on the printer.
	 * @param extensions - the extensions to filter on.
	 */
	public async retrieveFileNames(extensions?: string[]) {
		return await handleNativeException(NativeModule.retrieveFileNames(this.handle, extensions));
	}

	/**
	 * Retrieves the properties of the objects which are stored on the printer.
	 * @returns the list of objects with their properties.
	 */
	public async retrieveObjectsProperties() {
		return await handleNativeException(NativeModule.retrieveObjectsProperties(this.handle));
	}
	
	/**
	 * Sends the contents of a file to the printer.
	 * @param filePath the full path of the file to be sent (e.g. "/storage/emulated/0/Documents/sample.lbl").
	 * @param progressCallback callback to update on progress
	 */
	public async sendFileContents(filePath: string, progressCallback?: ProgressCallback) {
		await handleNativeException(NativeModule.sendFileContents(this.handle, filePath, progressCallback));
	}

	//graphics operations

	/**
	 * Prints an image to the connected device as a monochrome image. 
	 * @param image - the image to be printed. If it is a string, it is treated as a full path to the image.
	 * @param x - horizontal starting position in dots.
	 * @param y - vertical starting position in dots.
	 * @param width - desired width of the printed image. Passing a value less than 1 will preserve original width.
	 * @param height - desired height of the printed image. Passing a value less than 1 will preserve original height.
	 * @param insideFormat - boolean value indicating whether this image should be printed by itself (false), or is part of a format being written to the connection (true).
	 */
	public async printImage(image: string | Buffer, x: number, y: number, width = -1, height = -1, insideFormat = false) {
		if(typeof image === "string") {
			await handleNativeException(NativeModule.printImageFromFile(this.handle, image, x, y, width, height, insideFormat));
		}
		else {
			await handleNativeException(NativeModule.printImageFromBuffer(this.handle, Array.from(image), x, y, width, height, insideFormat));
		}
	}

	/**
	 * Stores the specified image to the connected printer as a monochrome image. 
	 * The image will be stored on the printer at printerDriveAndFileName with the extension GRF. 
	 * If a drive letter is not supplied, E will be used as the default (e.g. FILE becomes E:FILE.GRF). 
	 * If an extension is supplied, it is ignored if it is not either BMP or PNG. 
	 * If the extension is ignored, GRF will be used.
	 * @param deviceDriveAndFileName - path on the printer where the image will be stored.
	 * @param image - the image to be stored on the printer.
	 * @param width - desired width of the printed image, in dots. Passing -1 will preserve original width.
	 * @param height - desired height of the printed image, in dots. Passing -1 will preserve original height.
	 */
	public async storeImage(deviceDriveAndFileName: string, image: string | Buffer, width = -1, height = -1) {
		if(typeof image === "string") {
			await handleNativeException(NativeModule.storeImageFromFile(this.handle, deviceDriveAndFileName, image, width, height));
		}
		else {
			await handleNativeException(NativeModule.storeImageFromBuffer(this.handle, deviceDriveAndFileName, Array.from(image), width, height));
		}
	}

	//format util
	/**
	 * Retrieves a format from the printer. 
	 * On a LinkOS/ZPL printer, only .ZPL files are supported. 
	 * On a CPCL printer, only .FMT and .LBL files are supported.
	 * @param formatPathOnPrinter - the location of the file on the printer (e.g. "E:FORMAT.ZPL").
	 * @returns the contents of the format file.
	 */
	public async retrieveFormatFromPrinter(formatPathOnPrinter: string) {
		return await handleNativeException(NativeModule.retrieveFormatFromPrinter(this.handle, formatPathOnPrinter));
	}

	/**
	 * Prints a stored format on the printer, filling in the fields specified by the array. 
	 * On a LinkOS/ZPL printer, only ZPL formats are supported. 
	 * On a CPCL printer, only CPCL formats are supported.
	 * @param formatPathOnPrinter - the name of the format on the printer, including the extension (e.g. "E:FORMAT.ZPL").
	 * @param vars
	 * 	- If it is an array of strings representing the data to fill into the format:
	 * 		For LinkOS/ZPL printer formats, index 0 of the array corresponds to field number 2 (^FN2).
	 * 		For CPCL printer formats, the variables are passed in the order that they are found in the format.
	 * 
	 *  - If it is a map which contains the key/value pairs for the stored format:
	 * 		For LinkOS/ZPL printer formats, the key number should correspond directly to the number of the field in the format.
	 * 		For CPCL printer formats, the values will be passed in ascending numerical order.
	 */
	public async printStoredFormat(formatPathOnPrinter: string, vars: string[] | { [key: number]: string }) {
		if(Array.isArray(vars)) {
			vars = vars.reduce((acc, val, index) => {
				acc[index] = val;
				return acc;
			}, {} as { [key: number]: string });
		}
		await handleNativeException(NativeModule.printStoredFormat(this.handle, formatPathOnPrinter, vars));
	}

	/**
	 * Get the printer's SNMP get community name.
	 */
	public async getCommunityName() {
		return await handleNativeException(NativeModule.getCommunityName(this.handle));
	}

	/**
	 * Returns specific Link-OS™ information.
	 */
	public async getLinkOsVersion() {
		return await handleNativeException(NativeModule.getLinkOsVersion(this.handle));
	}

	/**
	 * Retrieve the TCP port status of the printer and returns a list of TcpPortStatus describing the open ports on the printer. 
	 * The open connection from the SDK will be listed in the return value. 
	 * This method will throw a ConnectionError if it is unable to communicate with the printer.
	 * @remarks Tabletop printers support more than one established connection on the raw port at a time, so the same port may be listed more than once.
	 * @returns List of open ports on the ZebraPrinter. Note: The open connection from the SDK will be listed.
	 */
	public async getPortStatus() {
		return await handleNativeException(NativeModule.getPortStatus(this.handle));
	}

	/**
	 * Retrieves storage information for all of the printer's available drives.
	 * @returns A list of objects detailing information about the printer's available drives.
	 */
	public async getStorageInfo() {
		return await handleNativeException(NativeModule.getStorageInfo(this.handle));
	}

	/**
	 * Stores the file on the printer at the specified location and name using any required file wrappers.
	 * @remarks If the contents of filePath contains any commands which need to be processed by the printer, use sendFileContents() instead. 
	 * These commands include download commands and any immediate commands (~CC, ~CD, ~DB, ~DE, ~DG, ~DY, ~EG, ~HI, ~HU, ~HM, ~HQ, ~HS, ~JA, ~JB, ~JC, ~JD, ~JE, ~JF, ~JG, ~JI, ~JL, ~JN, ~JO, ~JP, ~JQ, ~JR, ~JS, ~JX, ~NC, ~NT, ~PL, ~PP, ~PR, ~PS, ~RO, ~SD, ~TA, ~WC, ~WQ, ^DF)
	 * @param targetPath - the full file path (e.g. "C:\\Users\\%USERNAME%\\Documents\\sample.zpl").
	 * @param fileContents - the full name of the file on the printer (e.g "R:SAMPLE.ZPL").
	 */
	public async storeFileOnPrinter(targetPath: string, fileContents: Buffer) {
		await handleNativeException(NativeModule.storeFileOnPrinter(this.handle, targetPath, Array.from(fileContents)));
	}

	/**
	 * Retrieves a file from the printer's file system and returns the contents of that file as a byte array. 
	 * Will retrieve the following file extensions: ZPL, GRF, DAT, BAS, FMT, PNG, LBL, PCX, BMP, WML, CSV, HTM, TXT.
	 * Files transferred between different printer models may not be compatible.
	 * @param filePath - absolute file path on the printer ("E:SAMPLE.TXT").
	 * @returns The file contents
	 */
	public async getObjectFromPrinter(filePath: string) {
		return Buffer.from(await handleNativeException(NativeModule.getObjectFromPrinter(this.handle, filePath)));
	}

	/**
	 * Deletes the file from the printer. The filePath may also contain wildcards.
	 * @param filePath - the location of the file on the printer. Wildcards are also accepted (e.g. "E:FORMAT.ZPL", "E:*.*")
	 */
	public async deleteFile(filePath: string) {
		await handleNativeException(NativeModule.deleteFile(this.handle, filePath));
	}

	/**
	 * Sends a TrueType® font file to a printer and stores it at the specified path as a TTF.
	 * @param targetPath - Buffer containing the raw font data.
	 * @param fontData - Location to save the font file on the printer.
	 */
	public async uploadTTFFont(targetPath: string, fontData: Buffer) {
		await handleNativeException(NativeModule.uploadTTFFont(this.handle, targetPath, Array.from(fontData)));
	}

	/**
	 * Sends a TrueType® font to a printer and stores it at the specified path as a TrueType® extension (TTE).
	 * @param targetPath - Buffer containing the raw font data.
	 * @param fontData - Location to save the font file on the printer.
	 */
	public async uploadTTEFont(targetPath: string, fontData: Buffer) {
		await handleNativeException(NativeModule.uploadTTEFont(this.handle, targetPath, Array.from(fontData)));
	}

	/**
	 * Send the print directory label command to the printer.
	 */
	public async printDirectoryLabel() {
		await handleNativeException(NativeModule.printDirectoryLabel(this.handle));
	}

	/**
	 * Send the print network configuration command to the printer.
	 */
	public async printNetworkConfigurationLabel() {
		await handleNativeException(NativeModule.printNetworkConfigurationLabel(this.handle));
	}

	/**
	 * Sends the network reset command to the printer.
	 */
	public async resetNetwork() {
		await handleNativeException(NativeModule.resetNetwork(this.handle));
	}

	/**
	 * Send the restore network defaults command to the printer.
	 */
	public async restoreNetworkDefaults() {
		await handleNativeException(NativeModule.restoreNetworkDefaults(this.handle));
	}

	/**
	 * Set the RTC time and date on the printer. Accepted dateTime values include date (e.g. "MM-dd-yyyy"), time (e.g. "HH:mm:ss"), or both (e.g. "MM-dd-yyyy HH:mm:ss").
	 * @param dateTime - date and or time in the proper format (MM-dd-yyyy, HH:mm:ss, or MM-dd-yyyy HH:mm:ss)
	 */
	public async setClock(dateTime: string) {
		// await handleNativeException(NativeModule.setClock(this.handle, dateTime.toLocaleString('en-US', {
		// 	hour12: false,
		// })));
		//TODO: use Date class
		await handleNativeException(NativeModule.setClock(this.handle, dateTime));
	}

	//not implemented link os functions:

	// - settings manipulation
	// - profile management
	// - font download utils
	// - printer alerts
	// - firmware updater

}