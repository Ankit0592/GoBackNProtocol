import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class ARQClient {

	private String hostname;
	private int windowSize;
	private static int segmentSize;
	static DatagramSocket clientsocket;
	static File initialFile;
	static byte[][] sendData;
	static InetAddress IPAddress;
	static int portNo;
	public static String receiveSeq = "1010101010101010";
	static int beg = 0, end = 0;
	static Packet pack[];
	static boolean ack[];
	long delay = 0;

	ARQClient(String host, int port, String f, int window, int MSS) throws UnknownHostException {
		hostname = host;
		IPAddress = InetAddress.getByName(hostname);
		portNo = port;
		windowSize = window;
		segmentSize = MSS;

		try {
			clientsocket = new DatagramSocket();
			initialFile = new File(f);
			if (!initialFile.exists()) {
				System.out.println("Error: 404 File Not Found - the specified file does not exist");
				System.exit(0);
			}
		}

		catch (SocketException s) {
			System.err.println(s);
		}
	}

	public static void main(String args[]) throws Exception {
		if (args.length != 5) {
			System.out.println("Invalid format");
			System.out.println("Parameters required: server-host-name server-port# file-name N MSS");
			System.exit(0);
		}

		ARQClient arq = new ARQClient(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]),
				Integer.parseInt(args[4]));

		// Sharing window size with the server
		String window = args[3];
		byte b[] = window.getBytes();
		DatagramPacket p = new DatagramPacket(b, b.length, ARQClient.IPAddress, ARQClient.portNo);
		clientsocket.send(p);

		System.out.println("Sending Please Wait....");

		// Splitting file to transfer
		int segmentCount = (int) initialFile.length() / segmentSize;
		sendData = new byte[segmentCount + 1][];
		pack = new Packet[segmentCount + 1];
		ack = new boolean[segmentCount + 1];

		int j;
		System.out.println("File Size = " + initialFile.length() + "(bytes)");

		try {
			byte[] bytearray = new byte[(int) initialFile.length()];
			FileInputStream targetstream = new FileInputStream(initialFile);
			BufferedInputStream bin = new BufferedInputStream(targetstream);
			bin.read(bytearray, 0, bytearray.length);

			for (j = 0; j < bytearray.length; j++) {
				if (j % segmentSize == 0) {
					if ((bytearray.length - j) >= segmentSize)
						sendData[j / segmentSize] = new byte[segmentSize];
					else
						sendData[j / segmentSize] = new byte[bytearray.length - j];
				}
				sendData[j / segmentSize][j % segmentSize] = bytearray[j];
			}
			bin.close();
		}

		catch (Exception e) {
			System.out.println(e);
		}

		arq.transfer();

		System.out.println("Total time(ms) = " + arq.delay);
		System.out.println("File transferred successfully.");
	}

	@SuppressWarnings("deprecation")
	void transfer() {
		long startTime = System.currentTimeMillis();

		// Send first packet data to server
		while (((ARQClient.end - ARQClient.beg) < windowSize && sendData.length > ARQClient.end)) {
			pack[end] = new Packet(end, checksum(sendData[end]), sendData[end]);
			ack[end] = false;
			pack[ARQClient.end].start();
			ARQClient.end++;
		}

		// Send remaining packets to server
		while ((ARQClient.beg * segmentSize) < (int) initialFile.length()) {
			while (((ARQClient.end - ARQClient.beg) < windowSize && sendData.length > ARQClient.end)) {
				pack[end] = new Packet(end, checksum(sendData[end]), sendData[end]);
				ack[end] = false;
				pack[ARQClient.end].start();
				ARQClient.end++;
			}

			byte[] receiveData = new byte[segmentSize];
			DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
			try {
				clientsocket.setSoTimeout(1000);
				ARQClient.clientsocket.receive(receive);
				String unpack = new String(receive.getData());
				if (unpack.substring(48, 64).equals(receiveSeq)) {
					String seqtemp = unpack.substring(0, 32);
					int seqn = Integer.parseInt(seqtemp, 2);

					ack[seqn] = true;
					for (int i = beg; beg < sendData.length && ack[i]; i++) {
						beg++;
					}
					pack[seqn].stop();
				}
			} catch (Exception e) {
				System.err.println(e);
				continue;
			}
		}
		long endTime = System.currentTimeMillis();
		delay = endTime - startTime;

		// Sending packet to end the session on server
		try {
			String temp = Integer.toBinaryString(ARQClient.beg + 1);
			for (int i = temp.length(); i < 32; i++)
				temp = "0" + temp;
			byte blank[] = new byte[segmentSize];
			String check = checksum(blank);
			String eof = temp + check + "0000000000000000" + (new String(blank));
			byte send[] = eof.getBytes();
			DatagramPacket p = new DatagramPacket(send, send.length, ARQClient.IPAddress, ARQClient.portNo);
			clientsocket.send(p);
		} catch (Exception e) {
			System.out.print(e);
		}
	}

	// Calculating checksum
	static String checksum(byte[] data) {
		byte s1 = 0;
		byte s2 = 0;
		for (int i = 0; i < data.length; i = i + 2) {
			s1 += data[i];
			if ((i + 1) < data.length)
				s2 += data[i + 1];
		}
		String result1 = Byte.toString(s1);
		String result2 = Byte.toString(s2);
		for (int i = result1.length(); i < 8; i++)
			result1 = "0" + result1;
		for (int i = result2.length(); i < 8; i++)
			result2 = "0" + result2;
		return result1 + result2;
	}
}

// Packet class to send packets
class Packet extends Thread {
	int seqNo;
	String checksum;
	byte[] data;

	Packet(int sequence, String check, byte[] send) {
		seqNo = sequence;
		checksum = check;
		data = send;
	}

	// Fetching sequence number
	static String getSequence(int count) {
		String s = Integer.toBinaryString(count);
		for (int i = s.length(); i < 32; i++)
			s = "0" + s;
		return s;
	}

	public void run() {
		while (this.isAlive()) {
			DatagramPacket packet;
			String seq = getSequence(seqNo);
			String sendSeq = "0101010101010101";
			String header = seq + checksum + sendSeq;
			byte[] senddata;
			senddata = header.getBytes();
			byte[] pack = new byte[data.length + senddata.length];

			// Preparing packet
			for (int i = 0; i < pack.length; i++) {
				if (i < senddata.length)
					pack[i] = senddata[i];
				else {
					if ((i - senddata.length) < data.length)
						pack[i] = data[i - senddata.length];
				}

			}

			packet = new DatagramPacket(pack, pack.length, ARQClient.IPAddress, ARQClient.portNo);

			try {
				ARQClient.clientsocket.send(packet);
				sleep(100);
				System.out.println("Timeout, Sequence Number = " + seqNo);
			} catch (Exception e) {
				System.out.println(e);
			}
		}
	}
}