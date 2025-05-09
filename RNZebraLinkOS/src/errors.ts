

export class ConnectionError extends Error {
	constructor(message: string) {
		super(message);
		this.name = "ConnectionError";
	}
}

export class ZebraPrinterLanguageUnknownError extends Error {
	constructor(message: string) {
		super(message);
		this.name = "ZebraPrinterLanguageUnknownError";
	}
}

export class ZebraPrinterParseError extends Error {
	constructor(message: string) {
		super(message);
		this.name = "ZebraPrinterParseError";
	}
}

export class DiscoveryError extends Error {
	constructor(message: string) {
		super(message);
		this.name = "DiscoveryError";
	}
}


