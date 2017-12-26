import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

public class GoBackNClient {
	private int windowSize;
	private static int segmentSize;
	private static int portNo;
	private static InetAddress IPAddress;
	private static File initialFile;
	private static byte[][] sendData;
	public static String receiveSeq = "1010101010101010";
	public static String sendSeq = "0101010101010101";
	boolean[] buffer;
	static DatagramSocket clientSock;

	GoBackNClient(int windowSize, int segmentSize, int portServer, InetAddress IPAddress, String initialFile) {
		this.windowSize = windowSize;
		GoBackNClient.segmentSize = segmentSize;
		GoBackNClient.portNo = portServer;
		GoBackNClient.IPAddress = IPAddress;
		GoBackNClient.initialFile = new File(initialFile);
		if (!GoBackNClient.initialFile.exists()) {
			System.out.println("Error: 404 File Not Found - the specified file does not exist");
			System.exit(0);
		}
		try {
			GoBackNClient.clientSock = new DatagramSocket();
		} catch (SocketException s) {
			System.err.println(s);
		}
	}

	public static void main(String args[]) throws Exception {
		if (args.length != 5) {
			System.out.println("Invalid format");
			System.out.println("Parameters required: server-host-name server-port# file-name N MSS");
			System.exit(0);
		}

		long startTime = System.currentTimeMillis();
		GoBackNClient goback = new GoBackNClient(Integer.parseInt(args[3]), Integer.parseInt(args[4]),
				Integer.parseInt(args[1]), InetAddress.getByName(args[0]), args[2]);

		byte[] receiveData = new byte[GoBackNClient.segmentSize];
		int totalSize = (int) GoBackNClient.initialFile.length();

		FileInputStream targetStream = new FileInputStream(GoBackNClient.initialFile);
		BufferedInputStream bin = new BufferedInputStream(targetStream);
		byte[] byteArray = new byte[totalSize];
		int segmentCount = totalSize / GoBackNClient.segmentSize;
		int packetcount = 0;
		int ack = 0;
		int i, j;
		int waitAck = 0;
		ArrayList<DatagramPacket> sent = new ArrayList<DatagramPacket>();

		// +1 for the last packet which is not MSS size
		GoBackNClient.sendData = new byte[segmentCount + 1][];
		System.out.println("Sending Please Wait....");
		System.out.println("File Size = " + initialFile.length() + "(bytes)");
		bin.read(byteArray, 0, byteArray.length);

		// Splitting file
		for (i = 0; i < byteArray.length; i++) {
			if (i % GoBackNClient.segmentSize == 0) {
				if ((byteArray.length - i) >= GoBackNClient.segmentSize)
					GoBackNClient.sendData[i / GoBackNClient.segmentSize] = new byte[GoBackNClient.segmentSize];
				else
					GoBackNClient.sendData[i / GoBackNClient.segmentSize] = new byte[byteArray.length - i];
			}
			GoBackNClient.sendData[i / GoBackNClient.segmentSize][i % GoBackNClient.segmentSize] = byteArray[i];
		}

		// Send packets according to the algorithm
		while (true) {
			// Send packets equal to windowSize
			while (packetcount - waitAck < goback.windowSize && segmentCount + 1 > packetcount) {
				DatagramPacket sendPacket = rdt_send(packetcount);
				clientSock.send(sendPacket);
				sent.add(sendPacket);
				packetcount++;
			}

			// Receive Acknowledgement
			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

			try {
				clientSock.setSoTimeout(100);
				clientSock.receive(receivePacket);
				String packet = new String(receivePacket.getData());

				// Packet is acknowledged
				if (packet.substring(48, 64).equals(receiveSeq)) {
					String seqtemp = packet.substring(0, 32);
					int seqn = Integer.parseInt(seqtemp, 2);
					ack = seqn;

					if (ack == segmentCount + 1) {
						break;
					}
					// This will slide the window
					waitAck = Math.max(waitAck, ack);
				}
			} catch (SocketTimeoutException sto) {
				System.out.println("Timeout, Sequence number = " + (ack));

				// Resends packets after loss
				for (j = waitAck; j < packetcount; j++) {

					DatagramPacket packet = sent.get(j);
					clientSock.send(packet);
				}
			}
		}
		bin.close();
		long endTime = System.currentTimeMillis();

		// Sending packet to end the session on server
		try {
			String temp = Integer.toBinaryString(packetcount);
			for (int i1 = temp.length(); i1 < 32; i1++)
				temp = "0" + temp;
			byte blank[] = new byte[segmentSize];
			String check = checksum(blank);
			String eof = temp + check + "0000000000000000" + (new String(blank));
			byte send[] = eof.getBytes();
			DatagramPacket p = new DatagramPacket(send, send.length, IPAddress, portNo);
			clientSock.send(p);
		} catch (Exception e) {
			System.out.print(e);
		}
		System.out.println("Total time(ms) = " + (endTime - startTime));
		System.out.println("File transferred successfully.");
		clientSock.close();
	}

	// Compute checksum, sequence number and return packet
	static DatagramPacket rdt_send(int count) {
		String check = checksum(sendData[count]);
		String seqNo = getSequence(count);
		String header = seqNo + check + sendSeq;
		byte[] senddata = header.getBytes();
		byte[] p = new byte[sendData[count].length + senddata.length];

		for (int i = 0; i < p.length; i++) {
			if (i >= senddata.length) {
				if ((i - senddata.length) < sendData[count].length)
					p[i] = sendData[count][i - senddata.length];
			} else {
				p[i] = senddata[i];
			}
		}
		return new DatagramPacket(p, p.length, IPAddress, portNo);
	}

	// Fetching sequence number
	static String getSequence(int count) {
		String s = Integer.toBinaryString(count);
		for (int i = s.length(); i < 32; i++)
			s = "0" + s;
		return s;
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
