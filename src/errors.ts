
class BaseError extends Error {
	constructor(message: string) {
		super(message);
		//automatically set the name of the error
		this.name = this.constructor.name;
	}
}

export class ConnectionError extends BaseError {}
export class NotALinkOsPrinterError extends BaseError {}
export class ZebraPrinterLanguageUnknownError extends BaseError {}
export class ZebraIllegalArgumentError extends BaseError {}
export class ZebraPrinterParseError extends BaseError {}
export class DiscoveryError extends BaseError {}
export class IOError extends BaseError {}
