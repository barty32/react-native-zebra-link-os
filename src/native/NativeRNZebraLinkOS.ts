import { TurboModule, TurboModuleRegistry } from "react-native";
import { NativeException } from "../types.js";
import { ConnectionError, DiscoveryError, ZebraPrinterLanguageUnknownError, ZebraPrinterParseError } from "../errors.js";

export type PrinterHandle = string;
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

export interface NativeDiscoveredPrinter {
	address: string;
	handle: PrinterHandle;
	communicationType: 'network' | 'bluetooth' | 'usb';
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
	findBluetoothPrinters(onPrinterFound: (printer: NativeDiscoveredPrinter) => void): Promise<void>;
	//findUsbPrinters(onPrinterFound: (printer: NativeDiscoveredPrinter) => void): Promise<void>;

	connectPrinter(handle: PrinterHandle): Promise<NativePrinterConnectionData>;
	connectNetworkPrinter(ipAddress: string, port: number): Promise<NativePrinterConnectionData>;
	connectBluetoothPrinter(macAddress: string): Promise<NativePrinterConnectionData>;
	//connectUsbPrinter(): Promise<NativePrinterConnectionData>;

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
	sendFileContents( handle: PrinterHandle, filePath: string, progressCallback?: ProgressCallback): Promise<void>;

	//graphics operations
	//printImage(image: any, x: number, y: number, width?: number, height?: number): void;
	//storeImage(path: string, image: any, width: number, height: number): void;
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
			if (nativeException.code === 'ConnectionException')
				throw new ConnectionError(nativeException.message);

			if (nativeException.code === 'ZebraPrinterLanguageUnknownException')
				throw new ZebraPrinterLanguageUnknownError(nativeException.message);

			if(nativeException.code === 'ZebraPrinterParseException')
				throw new ZebraPrinterParseError(nativeException.message);

			if(nativeException.code === 'DiscoveryException')
				throw new DiscoveryError(nativeException.message);
		}
		throw new Error(String(e));
	}
}

export default TurboModuleRegistry.getEnforcing<Spec>("RNZebraLinkOS");
