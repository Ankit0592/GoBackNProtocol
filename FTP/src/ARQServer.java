import java.io.*;
import java.net.*;
import java.util.*;

public class ARQServer {
	public static void main(String args[]) throws Exception {

		if (args.length != 3) {
			System.out.println("Invalid format");
			System.out.println("Parameters required: port# file-name probability");
			System.exit(0);
		}

		// Create Socket connection on port specified
		DatagramSocket serverSocket = new DatagramSocket(Integer.parseInt(args[0]));
		System.out.println("Waiting for the packets...");
		byte[] r = new byte[5];
		DatagramPacket receiven = new DatagramPacket(r, r.length);
		serverSocket.receive(receiven);
		byte[] d = new byte[receiven.getLength()];
		System.arraycopy(r, 0, d, 0, d.length);
		int n = Integer.parseInt(new String(d));

		Random rd = new Random();
		float rand = 0;
		boolean recLoop = true;

		/// Create new file
		File f = new File(args[1]);
		FileOutputStream fos = new FileOutputStream(f);

		float p = Float.parseFloat(args[2]);
		int seqno = 0, strt = 0;
		byte[][] buffer = new byte[n][1024];

		// Acknowledgement window
		boolean[] ack = new boolean[n];

		// To store the sequence numbers already added to file
		HashSet<Integer> rec = new HashSet<Integer>();

		// Receiving packets from client
		while (recLoop) {
			byte[] receiveData = new byte[2048];
			DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
			serverSocket.receive(receive);

			byte[] data = new byte[receive.getLength() - 64];
			System.arraycopy(receiveData, 64, data, 0, data.length);

			seqno = getseqNo(new String(Arrays.copyOfRange(receiveData, 0, 32)));

			rand = rd.nextFloat();

			if (rand <= p) {
				System.out.println("Packet loss, Sequence number = " + seqno);
				continue;
			}

			String checksum = new String(Arrays.copyOfRange(receiveData, 32, 48));
			String recSeq = new String(Arrays.copyOfRange(receiveData, 48, 64));
			String checksumCal = checksum(data);
			InetAddress IPAddress = receive.getAddress();
			int port = receive.getPort();
			int i = seqno % n;

			// If packet is already added to file but, timeout occurs on client
			if (rec.contains(seqno)) {
				DatagramPacket acknowlegment = acknowledge(seqno, IPAddress, port);
				serverSocket.send(acknowlegment);
				continue;
			}

			if (!ack[i]) {
				buffer[i] = data;
				ack[i] = true;
				rec.add(seqno);
			}

			// If checksum matches the calculated checksum
			if (checksumCal.equals(checksum) && ack[i]) {
				if (recSeq.equals("0101010101010101")) {
					for (; strt < n && ack[strt]; strt = (strt + 1) % n) {
						ack[strt] = false;
						fos.write(buffer[strt]);
					}
					DatagramPacket acknowlegment = acknowledge(seqno, IPAddress, port);
					serverSocket.send(acknowlegment);
				} else {
					recLoop = false;
				}
			}
		}
		System.out.println("File received successfully.");
		fos.close();
		serverSocket.close();
	}

	// Converting sequence number received
	static int getseqNo(String seq) {
		double seqNo = 0;

		for (int i = 0; i < seq.length(); i++) {
			if (seq.charAt(i) == '1') {
				seqNo = seqNo + Math.pow(2, seq.length() - 1 - i);
			}
		}
		return (int) seqNo;
	}

	// Method to calculate checksum
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

	// Creating acknowledgement packet
	static DatagramPacket acknowledge(int s, InetAddress IPAddress, int port) {
		DatagramPacket packet = null;
		String sequence = getSequence(s);
		sequence += "00000000000000001010101010101010";
		byte[] send = sequence.getBytes();
		try {
			packet = new DatagramPacket(send, send.length, IPAddress, port);
		} catch (Exception e) {
			System.out.println(e);
		}
		return packet;
	}

	// Finding sequence number
	static String getSequence(int count) {
		String s = Integer.toBinaryString(count);
		for (int i = s.length(); i < 32; i++)
			s = "0" + s;
		return s;
	}
}