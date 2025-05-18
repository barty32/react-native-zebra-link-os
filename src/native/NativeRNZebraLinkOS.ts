import { TurboModule, TurboModuleRegistry } from "react-native";
import {
	ConnectionError,
	DiscoveryError,
	IOError,
	NotALinkOsPrinterError,
	ZebraIllegalArgumentError,
	ZebraPrinterLanguageUnknownError,
	ZebraPrinterParseError
} from "../errors";

export type PrinterHandle = string;
export type RawByteArray = Array<number>;
export type ProgressCallback = (bytesWritten: number, totalBytes: number) => void;

export enum ZplPrintMode {
	REWIND = 'Rewind',
	PEEL_OFF = 'Peel-Off',
	TEAR_OFF = 'Tear-Off',
	CUTTER = 'Cutter',
	APPLICATOR = 'Applicator',
	DELAYED_CUT = 'Delayed Cut',
	LINERLESS_PEEL = 'Linerless Peel',
	LINERLESS_REWIND = 'Linerless Rewind',
	PARTIAL_CUTTER = 'Partial Cutter',
	RFID = 'RFID',
	KIOSK = 'Kiosk',
	UNKNOWN = 'Unknown'
}

export enum PrinterLanguage {
	CPCL = 'CPCL',
	ZPL = 'ZPL',
	LINE_PRINT = 'LINE_PRINT',
}

export interface PrinterStatus {
	isHeadCold: boolean;
	isHeadOpen: boolean;
	isHeadTooHot: boolean;
	isPaperOut: boolean;
	isPartialFormatInProgress: boolean;
	isPaused: boolean;
	isReadyToPrint: boolean;
	isReceiveBufferFull: boolean;
	isRibbonOut: boolean;
	labelLengthInDots: number;
	labelsRemainingInBatch: number;
	numberOfFormatsInReceiveBuffer: number;
	printMode: ZplPrintMode;
}

export interface PrinterObjectProperties {
	drivePrefix: string;
	fileName: string;
	extension: string;
	fullName: string;
	CRC32: number;
	fileSize: number;
}

export enum DriveType {
	Flash       ,//= 'FLASH',        // Onboard flash drive.
	RAM         ,//= 'RAM',          // RAM Drive.
	MassStorage ,//= 'MASS_STORAGE', // Removable mass storage drive.
	Unknown     ,//= 'UNKNOWN',      // Unknown drive.
	ReadOnly    ,//= 'READ_ONLY',    // Read only drive.
}

export interface StorageInfo {
	driveLetter: string;
	driveType: DriveType;
	bytesFree: number;
	isPersistent: boolean;
}

export interface TcpPortStatus {
	printerPort: string;
	portName: string;
	remoteIpAddress: string;
	remotePort: string;
	status: string;
}

export enum PrinterConnectionType {
	Network = 'network',
	Bluetooth = 'bluetooth',
	BluetoothLE = 'bluetooth-le',
	BluetoothInsecure = 'bluetooth-insecure',
	USB = 'usb',
}

export interface NativeException extends Error {
	code: string;
	name: string;
	nativeStackAndroid?: {
		class: string;
		file: string;
		lineNumber: number;
		methodName: string;
	}[];
}

export interface NativeDiscoveredPrinter {
	address: string;
	handle: PrinterHandle;
	connectionType: PrinterConnectionType;
	discoveryData: { [key: string]: string };
}

export interface NativePrinterConnectionData {
	handle: PrinterHandle;
	controlLanguage: string;
	status: PrinterStatus;
}

//this interface is used by Codegen to generate native methods
export interface Spec extends TurboModule {

	findNetworkPrinters(onPrinterFound: (printer: NativeDiscoveredPrinter) => void): Promise<void>;
	findBluetoothPrinters(onPrinterFound: (printer: NativeDiscoveredPrinter) => void, useBle: boolean): Promise<void>;
	findUsbPrinters(onPrinterFound: (printer: NativeDiscoveredPrinter) => void): Promise<void>;

	connectPrinter(handle: PrinterHandle): Promise<NativePrinterConnectionData>;
	//connectNetworkPrinter(ipAddress: string, port: number, maxTimeoutForRead: number, timeToWaitForMoreData: number): Promise<NativePrinterConnectionData>;
	//connectBluetoothPrinter(macAddress: string, useBle: boolean, insecure: boolean, maxTimeoutForRead: number, timeToWaitForMoreData: number): Promise<NativePrinterConnectionData>;
	//connectUsbPrinter(maxTimeoutForRead: number, timeToWaitForMoreData: number): Promise<NativePrinterConnectionData>;

	isPrinterConnected(handle: PrinterHandle): Promise<boolean>;
	disconnectPrinter(handle: PrinterHandle): Promise<void>;
	getPrinterStatus(handle: PrinterHandle): Promise<PrinterStatus>;

	printConfigurationLabel(handle: PrinterHandle): Promise<void>;
	sendPrinterCommand(handle: PrinterHandle, data: string): Promise<string | null>;
	calibratePrinter(handle: PrinterHandle): Promise<void>;
	resetPrinter(handle: PrinterHandle): Promise<void>;
	restorePrinterDefaults(handle: PrinterHandle): Promise<void>;

	//file operations
	retrieveFileNames(handle: PrinterHandle, extensions?: string[]): Promise<string[]>;
	retrieveObjectsProperties(handle: PrinterHandle): Promise<PrinterObjectProperties[]>;
	sendFileContents(handle: PrinterHandle, filePath: string, progressCallback?: ProgressCallback): Promise<void>;

	//graphics operations
	printImageFromFile(handle: PrinterHandle, imagePath: string, x: number, y: number, width: number, height: number, insideFormat: boolean): Promise<void>;
	printImageFromBuffer(handle: PrinterHandle, imageData: RawByteArray, x: number, y: number, width: number, height: number, insideFormat: boolean): Promise<void>;
	
	storeImageFromFile(handle: PrinterHandle, targetPath: string, imagePath: string, width: number, height: number): Promise<void>;
	storeImageFromBuffer(handle: PrinterHandle, targetPath: string, imageData: RawByteArray, width: number, height: number): Promise<void>;

	//format operations
	retrieveFormatFromPrinter(handle: PrinterHandle, formatPathOnPrinter: string): Promise<string>;
	printStoredFormat(handle: PrinterHandle, formatPathOnPrinter: string, vars: { [key: number]: string }): Promise<void>;
	
	// === LINK OS only functions ===

	getCommunityName(handle: PrinterHandle): Promise<string>;
	getLinkOsVersion(handle: PrinterHandle): Promise<string>;
	getPortStatus(handle: PrinterHandle): Promise<TcpPortStatus[]>;

	//file utils
	getStorageInfo(handle: PrinterHandle): Promise<StorageInfo[]>;
	storeFileOnPrinter(handle: PrinterHandle, targetPath: string, fileContents: RawByteArray): Promise<void>;
	getObjectFromPrinter(handle: PrinterHandle, filePath: string): Promise<RawByteArray>;
	deleteFile(handle: PrinterHandle, filePath: string): Promise<void>;

	//font utils
	uploadTTFFont(handle: PrinterHandle, targetPath: string, fontData: RawByteArray): Promise<void>;
	uploadTTEFont(handle: PrinterHandle, targetPath: string, fontData: RawByteArray): Promise<void>;

	//tools
	printDirectoryLabel(handle: PrinterHandle): Promise<void>;
	printNetworkConfigurationLabel(handle: PrinterHandle): Promise<void>;
	resetNetwork(handle: PrinterHandle): Promise<void>;
	restoreNetworkDefaults(handle: PrinterHandle): Promise<void>;
	setClock(handle: PrinterHandle, dateTime: string): Promise<void>;
}

/**
 * This is used to convert rejected promises from native code into proper JS exceptions.
 * All native methods above should be wrapped in this function.
 */
export async function handleNativeException<T>(fn: Promise<T>): Promise<T> {
	try {
		return await fn;
	} catch (e) {
		if (e instanceof Error) {
			const nativeException = e as NativeException;
			if(nativeException.code === 'ConnectionException')
				throw new ConnectionError(nativeException.message);

			if(nativeException.code === 'NotALinkOsPrinterException')
				throw new NotALinkOsPrinterError(nativeException.message);

			if(nativeException.code === 'ZebraPrinterLanguageUnknownException')
				throw new ZebraPrinterLanguageUnknownError(nativeException.message);

			if(nativeException.code === 'ZebraIllegalArgumentException')
				throw new ZebraIllegalArgumentError(nativeException.message);

			if(nativeException.code === 'ZebraPrinterParseException')
				throw new ZebraPrinterParseError(nativeException.message);

			if(nativeException.code === 'DiscoveryException')
				throw new DiscoveryError(nativeException.message);

			if(nativeException.code === 'IOException')
				throw new IOError(nativeException.message);
		}
		throw new Error(String(e));
	}
}

export default TurboModuleRegistry.getEnforcing<Spec>("RNZebraLinkOS");
