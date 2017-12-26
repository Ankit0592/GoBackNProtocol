import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Random;

public class GoBackNServer {
	public static void main(String args[]) throws Exception {
		if (args.length != 3) {
			System.out.println("Invalid format");
			System.out.println("Parameters required: port# file-name probability");
			System.exit(0);
		}

		int portNo = Integer.parseInt(args[0]);
		DatagramSocket serverSocket = new DatagramSocket(portNo);
		System.out.println("Waiting for packets...");
		int packetcount = 0;
		Random rd = new Random();
		float rand;
		boolean recLoop = true;
		String filename = args[1];
		FileOutputStream FOS = new FileOutputStream(filename);
		float p = Float.parseFloat(args[2]);
		int sequencenumber = 0;
		while (recLoop) {
			// Assuming that segment received is <2048 bytes
			byte[] receiveData = new byte[2048];
			DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
			serverSocket.receive(receive);

			// Store data part of packet
			byte[] data = new byte[receive.getLength() - 64];
			System.arraycopy(receiveData, 64, data, 0, data.length);

			// Find sequence number
			sequencenumber = getseqNo(new String(Arrays.copyOfRange(receiveData, 0, 32)));

			// When client transfers file second time
			if (sequencenumber == 0) {
				packetcount = 0;
				FOS = new FileOutputStream(args[1]);
			}

			// Don't do anything if sequence number is out of order
			if (sequencenumber != packetcount) {
				continue;
			}

			// Find checksum and type of packet
			String checksum = new String(Arrays.copyOfRange(receiveData, 32, 48));
			String recSeq = new String(Arrays.copyOfRange(receiveData, 48, 64));

			// Find Random Number between 0 and 1
			rand = rd.nextFloat();
			if (rand <= p) {
				System.out.println("Packet loss, Sequence number = " + sequencenumber);
				packetcount = sequencenumber;
				continue;
			}

			// Find checksum of received packet
			String checksumCal = checksum(data);

			// Find the client IP address and port number
			InetAddress IPAddress = receive.getAddress();
			int port = receive.getPort();
			// If checksum calculated from received packet and header checksum
			// match
			if (checksumCal.equals(checksum)) {
				if (packetcount == sequencenumber) {
					packetcount++;
					// generate acknowledgement and write data to file
					if (recSeq.equals("0101010101010101")) {
						DatagramPacket ack = acknowledge(packetcount, IPAddress, port);
						FOS.write(data);
						serverSocket.send(ack);
					} else {
						recLoop = false;
					}
				}
			}
		}
		FOS.close();
		System.out.println("File received successfully.");
		serverSocket.close();
	}

	// Fetching sequence number converted to integer
	static int getseqNo(String seq) {
		double snum = 0;
		for (int i = 0; i < seq.length(); i++) {
			if (seq.charAt(i) == '1') {
				snum = snum + Math.pow(2, seq.length() - i - 1);
			}
		}
		return (int) snum;
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
