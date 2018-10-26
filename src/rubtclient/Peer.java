//Michael Rollins  NetID mtr96
//Michael Shur  NetID mas868
package rubtclient;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import GivenTools.Bencoder2;
import GivenTools.BencodingException;
import GivenTools.ToolKit;
import GivenTools.TorrentInfo;

public class Peer {

	static final ByteBuffer PEERS = ByteBuffer.wrap(new byte[] {'p','e','e','r','s'});
	static final ByteBuffer IP = ByteBuffer.wrap(new byte[] {'i','p'});
	static final ByteBuffer PEER_ID = ByteBuffer.wrap(new byte[] {'p','e','e','r',' ','i','d'});
	static final ByteBuffer PORT = ByteBuffer.wrap(new byte[] {'p', 'o', 'r', 't'});
	static final ByteBuffer MIN_INTERVAL = ByteBuffer.wrap(new byte[] {'m', 'i', 'n', ' ', 'i', 'n', 't', 'e', 'r','v', 'a', 'l'});
	
	/**
	 * Returns an ArrayList containing the dictoionary of each peer.
	 * @param trackerConnection
	 * @return ArrayList
	 */
	public static ArrayList<HashMap<ByteBuffer, Object>> getPeerList(HttpURLConnection trackerConnection) {
		ArrayList<HashMap<ByteBuffer, Object>> peerList = null;
		BufferedInputStream bis = null;
		ByteArrayOutputStream baos = null;
		try {
			bis = new BufferedInputStream(trackerConnection.getInputStream());
			baos  = new ByteArrayOutputStream();
			byte[] buffer = new byte[trackerConnection.getContentLength()];
			int readNumber;
			while( (readNumber = bis.read(buffer,0,buffer.length)) != -1 ) {
				baos.write(buffer, 0, readNumber); 
			}
			byte[] encodedResponse = baos.toByteArray();
			HashMap<ByteBuffer, Object> decodedResponse = null;	
			decodedResponse = (HashMap<ByteBuffer,Object>)Bencoder2.decode(encodedResponse);
			//ToolKit.print(decodedResponse);
			peerList = new ArrayList<HashMap<ByteBuffer, Object>>();
			ArrayList<Object> list = (ArrayList<Object>)decodedResponse.get(PEERS);
			for(int i = 0; i < list.size(); i++)
			{	
				HashMap<ByteBuffer, Object> eachPeer = (HashMap<ByteBuffer, Object>)list.get(i);
				peerList.add(eachPeer);	
			}
		} catch (IOException e) { 
			System.err.println("IOException getting input stream!");
		} catch (BencodingException e) {
			System.err.println("Bencoder exception ocurred!");
		} finally {
			if(bis != null){
				try {
					bis.close();
				} catch (IOException e) {
					System.err.println("IOException failed to close BufferedInputStream!");
				}
			}
			if(baos != null){
				try {
					baos.close();
				} catch (IOException e) {
					System.err.println("IOException failed to close ByteArrayOutputStream!");
				}
			}
		}
		return peerList;
	}
	
	/**
	 * Returns a socket connection from the given peer.
	 * @param peer Takes the peer object dictionary
	 * @return Socket connection to the peer
	 */
	public static Socket openTCPConnection(HashMap<ByteBuffer,Object> peer) {
		String peerip = objectByteBufferToString(peer.get(IP));
		int port = (int)peer.get(PORT);
		try {
			return new Socket(peerip, port);
		} catch (IOException e) {
			System.err.println("IOException failed to open a socket!");
			return null;
		}
	}
	
	/**
	 * Turns a object which is of type ByteBuffer and turns it into  a string
	 * @param object
	 * @return a string representation of the object/ByteBuffer
	 */
	public static String objectByteBufferToString(Object object) {
		if (object == null) {
			return "";		
		} else {
			String str = "";
			try {
				str = new String((((ByteBuffer)object).array()));
			} catch (ClassCastException e){
				System.err.println("Class cast Exception!");
			}
	        return str;
		}
    }
	
	/**
	 * Sets up the required pieces for establishing a handshake with a peer. 
	 * @param torrentInfo
	 * @param torrent
	 * @return a byte array containing the required fields for a handshake
	 */
	public static byte[] makeHandshake(TorrentInfo torrentInfo, Torrent torrent) {	
		String protocol = "BitTorrent protocol00000000";
		byte[] protocolLengthBytes = new byte[] { 19 };								
		byte[] protocolBytes = protocol.getBytes(Charset.forName("UTF-8"));			
		byte[] infoHashBytes = torrentInfo.info_hash.array();									
		byte[] peerIDBytes = torrent.getPeerID().getBytes(Charset.forName("UTF-8"));
		ByteBuffer protocolLengthBuffer = ByteBuffer.wrap(protocolLengthBytes);
		ByteBuffer protocolBuffer = ByteBuffer.wrap(protocolBytes);
		ByteBuffer infoHashBuffer = ByteBuffer.wrap(infoHashBytes);
		ByteBuffer peerIDBuffer = ByteBuffer.wrap(peerIDBytes);
		ByteBuffer handshake = ByteBuffer.allocate(68);
		handshake = handshake.put(protocolLengthBuffer);
		handshake = handshake.put(protocolBuffer);
		handshake = handshake.put(infoHashBuffer);
		handshake = handshake.put(peerIDBuffer);
		handshake = handshake.order(ByteOrder.BIG_ENDIAN);
		return handshake.array();
	}
	
	/**
	 * Verifies if the handshake was valid and returns a boolean.
	 * @param handshake
	 * @param torrentInfo
	 * @param socket
	 * @return true if valid handshake, else false
	 */
	public static boolean checkHandshake(byte[] handshake, TorrentInfo torrentInfo, Socket socket) {	
		DataInputStream dis = null;
		DataOutputStream dos = null;
		byte[] responseHash = null;
		try {
			dis = new DataInputStream(socket.getInputStream());
			dos = new DataOutputStream(socket.getOutputStream());
			dos.write(handshake);										
			byte[] response = new byte[68];
			dis.readFully(response);									
			dos.flush();
			responseHash = Arrays.copyOfRange(response, 28, 48);	
			if( !(Arrays.equals(torrentInfo.info_hash.array(), responseHash)) ) {
				System.out.println("Peer handshake response hash mismatch!");
				dis.close();
				dos.close();
				socket.close();
				return false;
			} else {	
				System.out.println("Peer handshake response hash valid!");
				return true;
			}
		} catch (IOException e) {
			System.out.println("IOEception for checkHandshake peer closed connection!");
			/*
			if(dis != null){
				try {
					dis.close();
				} catch (IOException e1) {
					System.out.println("closing socket input stream!");
					e1.printStackTrace();
				}
			}
			if(dos != null){
				try {
					dos.close();
				} catch (IOException e1) {
					System.out.println("closing socket output stream!");
					e1.printStackTrace();
				}
			}
			if(socket != null){
				try {
					socket.close();
				} catch (IOException e1) {
					System.out.println("closing socket!");
					e1.printStackTrace();
				}
			}
			*/
		}
		return false;
	}
	
	/**
	 * returns a byte array which is the bitfield from the given peer
	 * @param socket
	 * @param hashLength
	 * @return
	 */
	public static byte[] getBitfield(Socket socket, int hashLength) {
		byte[] bitfield = null;
		byte messageID;	
		DataInputStream dis;
		try {
			dis = new DataInputStream(socket.getInputStream());
			dis.readInt();
			messageID = dis.readByte();
			if(messageID == ((byte)Message.BITFIELD)){
				bitfield = new byte[hashLength];
			} else {
				System.out.println("Failed to read.");
			}
		} catch (IOException e){
			System.out.println("Failed to read.");
		} 
		return bitfield;
	}
	
	
}
