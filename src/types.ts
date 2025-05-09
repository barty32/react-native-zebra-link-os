

export enum PrinterLanguage {
	CPCL = 'CPCL',
	ZPL = 'ZPL',
	LINE_PRINT = 'LINE_PRINT',
}

export enum PrinterCommunicationType {
	NETWORK = 'NETWORK',
	BLUETOOTH = 'BLUETOOTH',
	USB = 'USB',
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

