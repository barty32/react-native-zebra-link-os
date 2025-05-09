import { useState } from 'react';
import { DiscoveredPrinter, findNetworkPrinters, ZebraPrinter } from 'react-native-zebra-link-os';
import { ActivityIndicator, Button, Pressable, SafeAreaView, ScrollView, Text, View } from 'react-native';

export default function App() {
	const [printer, setPrinter] = useState<ZebraPrinter | null>(null);
	const [discoveredPrinters, setDiscoveredPrinters] = useState<{ [key: string]: DiscoveredPrinter }>({});
	const [loading, setLoading] = useState(false);
	
	const runDiscovery = async () => {
		setLoading(true);
		await findNetworkPrinters((printer) => {
			console.log('Printer found:', printer.getDiscoveryDataMap().entries());
			setDiscoveredPrinters((prev) => ({
				...prev,
				[printer.getAddress()]: printer
			}));
		});
		setLoading(false);
	}

	const print = (async () => {
		if(!printer) return;
		// await printer.send(`
		// 	^XA
		// 		^FO120,80^A0,20^FDABCD1234^FS 
		// 		^FO10,10^GB245,120,2^FS
		// 		^FO20,20^BQN,2,4^FDQA,12345678^FS
		// 	^XZ
		// `)
		await printer.send(`
			^XA
				^HH
			^XZ
		`)
		//
		// ^FO50,20^A0,40^FDTest label^FS 
		// ^FO30,60^BY2^BCN,40,,,,A^FD123ABC^FS 
		// ^FO10,10^GB245,120,2^FS
		// ^FO120,40^A0,20^FDDevice^FS 
		//
	})

	const calibrate = (async () => {
		if(!printer) return;
		await printer.calibrate();
	})

	return (
		<SafeAreaView style={styles.container}>
			<ScrollView style={styles.container}>
				<Text style={styles.header}>Zebra printer Example</Text>
				<Group name="Discovery">
					{Object.values(discoveredPrinters).map(p => (
						<Pressable
							style={{
								height: 50,
							}}
							android_ripple={{ color: '#ccc' }}
							key={p.getAddress()}
							onPress={async () => {
								try {
									const printer = await p.connect();
									setPrinter(printer);
								} catch(e) {
									console.error("Error connecting to the printer:", e);
								}
							}}
						>
							<Text>{p.getAddress() + '\n' + p.getCommunicationType()}</Text>
						</Pressable>
					))}
					<ActivityIndicator
						size="large"
						animating={loading}
					/>
					<Button
						title="Run discovery"
						onPress={runDiscovery}
					/>
				</Group>
				<Group name="Printer operations">
					{printer === null ? <Text>Connect to a printer first</Text> : <>
						<PrinterAction
							title="Print"
							action={print}
						/>
						<PrinterAction
							title="Calibrate"
							action={calibrate}
						/>
						{/* <PrinterAction 
							title="Print configuration label"
							action={printer.printConfigurationLabel}
						/>
						<PrinterAction
							title="Reset"
							action={printer.reset}
						/> */}
						<PrinterAction
							title="Retrieve file names"
							action={(async () => {
								const fileNames = await printer.retrieveFileNames();
								alert("File names: \n" + fileNames.join("\n"));
							})}
						/>
						<PrinterAction
							title="Send file"
							action={(async () => {
								await printer.sendFileContents("/storage/emulated/0/Download/test.txt");
							})}
						/>
					</>}
				</Group>
			</ScrollView>
		</SafeAreaView>
	);
}

function Group(props: { name: string; children: React.ReactNode }) {
	return (
		<View style={styles.group}>
			<Text style={styles.groupHeader}>{props.name}</Text>
			{props.children}
		</View>
	);
}

function PrinterAction({ title, action }: { title: string; action: () => Promise<any> }) {
	const [commandPending, setCommandPending] = useState(false);
	return (
		<View style={{flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'}}>
			<Button
				title={title}
				disabled={commandPending}
				onPress={async () => {
					setCommandPending(true);
					try {
						await action();
					}
					catch(e) {
						console.error("Error when executing action:", e);
					}
					finally {
						setCommandPending(false);
					}
				}}
			/>
			<ActivityIndicator
				size="small"
				animating={commandPending}
			/>
		</View>
	)
}

const styles = {
	header: {
		fontSize: 30,
		margin: 20,
	},
	groupHeader: {
		fontSize: 20,
		marginBottom: 20,
	},
	group: {
		margin: 20,
		backgroundColor: '#fff',
		borderRadius: 10,
		padding: 20,
	},
	container: {
		flex: 1,
		backgroundColor: '#eee',
	},
	view: {
		flex: 1,
		height: 200,
	},
};
