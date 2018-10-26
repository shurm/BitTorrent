//Michael Rollins  NetID mtr96
//Michael Shur  NetID mas868
package rubtclient;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class Message {

	static final int NO_MSG = -2;
	static final int KEEP_ALIVE = -1;
	static final int CHOKE = 0;
	static final int UNCHOKE = 1;
	static final int INTERESTED = 2;
	static final int UNINTERESTED = 3;
	static final int HAVE = 4;
	static final int BITFIELD = 5;
	static final int REQUEST = 6;
	static final int PIECE = 7;
	static final int CANCEL = 8;
	static final int PORT = 9;
	static Object lock = new Object();

	/**
	 * Takes the sockets input, output streams and the socket and creates a message of intrest which is the first thing we must send
	 * @param dis
	 * @param dos
	 * @param socket
	 */
	public static void sendInterest(DataInputStream dis, DataOutputStream dos, Socket socket){
		byte[] peerMessage = new byte[100];
		boolean isUnchoked = true;
		try {
			socket.setSoTimeout(5000);
			while (true) {
				try {
					dos.write(generateMessage(INTERESTED)); 
					dos.flush();		
					dis.read(peerMessage);										
					isUnchoked = true;
					byte[] messageUnchoke = generateMessage(UNCHOKE);
					for (int i = 0; i < 5; i++) {
						if ( !(peerMessage[i] == messageUnchoke[i]) ) {
							isUnchoked = false;	
						}
					}
					if (isUnchoked) {
						return;
					}
				} catch (SocketTimeoutException e) { 
					System.out.println("Socket timeout triggered!");
					break; 
				} catch (IOException e) {
					System.err.println("IOException when sending interest!");
				}
			}
		} catch (SocketException e) {
			System.err.println("Socket Exception!");		
		}
	}
	 
	/**
	 * Requests a single piece of the torrent we want and appends it to our buffer
	 * @param dis
	 * @param dos
	 * @param torrent
	 * @param socket
	 * @return
	 */
	 public static void downloadPiece(DataInputStream dis, DataOutputStream dos, Torrent torrent, Socket socket) {
			int index = 0;
			int begin = 0;		
			int pieceLength = 0;
			int fileLength = torrent.getFileLength();
			int packetLength = 0; //2^14 = 16384 bytes per packet
			String event = null;
			
			int left = torrent.getLeft();
			int hashLength = torrent.getHashesLength();
			
			HttpURLConnection trackerConnection;
			
			//sendInterest(dis, dos, socket);
			
			try {
					socket.setSoTimeout(0);
					packetLength = (int)Math.pow(2.0, 14.0);
					pieceLength = torrent.getPieceLength();
					index = torrent.pickPiece();
					
					
					if(index == -1) { 
						return;
					} else {
						
						System.out.println("[Piece at index] = "+index);
						byte[] piece = null;
						boolean acquiredPiece = false;
						if(index == hashLength - 1) {//gets the small piece we might want to make it more dynamic in the respect of size 
							pieceLength = (fileLength - (index * pieceLength));
						}
						piece = new byte[pieceLength];
						int attempts = 10;
						
						while(true) {		
								if(packetLength > pieceLength){ //for when pieces are smaller then our packet size. 
									packetLength = pieceLength;
								} else if( (pieceLength - begin < packetLength) && (pieceLength - begin > 0) ){ //for small piece when piece size is not a multiple of packet size.
									packetLength = pieceLength - begin;
								}
								byte[] packet = new byte[packetLength];
								if(begin == pieceLength) {
									ByteBuffer[] piece_hash = torrent.getPieceHashes();
									ByteBuffer torrentPieceHash = piece_hash[index];
									byte[] tpHash = torrentPieceHash.array();
									if (Arrays.equals(tpHash, Message.createSHA1Hash(piece))) { //check if piece is not corrupted
										dos.write(generateMessage(4, index, -1, -1, null));
										acquiredPiece = true;
										begin = 0;
										break;
									} else {
										if(attempts == 0){
											begin = 0;
											break;
										} else {
											attempts--;
											begin = 0; //problem with piece so we retry
										}
									}	
								} else {	
									dos.write(generateMessage(REQUEST, index, begin, packetLength, null));
									dos.flush();	
									int msgsize = dis.readInt();  		//size of the message received in bytes			
									int msgtype = dis.readByte(); 		//the type of message received
									int msgpieceindex = dis.readInt();   //the index of the given piece
									int msgpieceoffset = dis.readInt();  //the offset of the piece
									dis.readFully(packet); 				//read the packet
											
									for(int i = 0; i < packet.length; i++) {
											piece[i+begin] = packet[i];
									}
									begin += packetLength;  //packet offset on piece
								}
						}	
						
						if(acquiredPiece == false) {
							System.out.println("Failed to get piece!");
							torrent.addBackToList(index);
						}
						synchronized(torrent.aquireLock()){
							dos.write(generateMessage(Message.INTERESTED));	//send and interest message					
							event = "started";
							torrent.updateLeft(pieceLength);
							torrent.updateDownloaded(pieceLength);
							left = torrent.getLeft();
							torrent.addToBuffer(piece, index);
						}
						if (left == 0) {
							event = "completed";
						}
						torrent.setEvent(event);
						URL newURL = Torrent.createURL(torrent);
						trackerConnection = (HttpURLConnection) newURL.openConnection();
						trackerConnection.setRequestMethod("GET");	
						System.out.println("[Left] = "+ torrent.getLeft());
						System.out.println("[Downloaded] = "+torrent.getDownloaded());
					}
				
				
				
			} catch (FileNotFoundException e) {
				System.err.println("File not found exception!");
			} catch (IOException e) {
				System.err.println("IOException DataInputStream or DataOutputStream error!");
				e.printStackTrace();
			}
			
	} 
	
	/**
	 * Sends a keep_alive message to the peer assocaited with the dos.
	 * @param dos
	 */
	public static void keepAlive(DataOutputStream dos){
		//generate a keep alive message to the peer so they do not kill the connection
		try {
			dos.write(generateMessage(KEEP_ALIVE));
			dos.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}
	
	/**
	 * Creates a SHA1 hash for the given bytes and return it.
	 * @param target
	 * @return
	 */
	public static byte[] createSHA1Hash(byte[] target) {
	    try {	
	      byte[] hash = new byte[target.length];
	      MessageDigest msgdig = MessageDigest.getInstance("SHA-1");
	      hash = msgdig.digest(target);
	      return hash;
	    } catch (NoSuchAlgorithmException e) {
	      System.out.println("No such algorithm");  
	    }
	    return null;
	}

	/**
	 * generates a general message that can be used to communicate with the peer.
	 * @param id
	 * @return
	 */
	public static byte[] generateMessage(int id){
		ByteBuffer message = null;
		switch(id){
			case KEEP_ALIVE:	
				message = ByteBuffer.allocate(4);
				message.putInt(0);
				break;
			case CHOKE:
				message = ByteBuffer.allocate(5);
				message.putInt(1);
				message.put((byte)id);
				break;
			case UNCHOKE:	
				message = ByteBuffer.allocate(5);
				message.putInt(1);
				message.put((byte)id);
				break;
			case INTERESTED:
				message = ByteBuffer.allocate(5);
				message.putInt(1);
				message.put((byte)id);
				break;
			case UNINTERESTED:
				message = ByteBuffer.allocate(5);
				message.putInt(1);
				message.put((byte)id);
				break;
			default:
				break;
		}
		return message.array();
	}

	/**
	 * Generates a specific message that can be used to communicate with the peer 
	 * @param id
	 * @param index
	 * @param begin
	 * @param length
	 * @param block
	 * @return
	 */
	public static byte[] generateMessage(int id, int index, int begin, int length, byte[] block){
		ByteBuffer message = null;
		switch(id){
			case KEEP_ALIVE:
				message = ByteBuffer.allocate(4);
				message.putInt(0);
				break;	
			case CHOKE:
				message = ByteBuffer.allocate(5);
				message.putInt(1);
				message.put((byte)id);
				break;	
			case UNCHOKE:
				message = ByteBuffer.allocate(5);
				message.putInt(1);
				message.put((byte)id);
				break;
			case INTERESTED:
				message = ByteBuffer.allocate(5);
				message.putInt(1);
				message.put((byte)id);
				break;
			case UNINTERESTED:
				message = ByteBuffer.allocate(5);
				message.putInt(1);
				message.put((byte)id);
				break;
			case HAVE:
				message = ByteBuffer.allocate(9);
				message.putInt(5);
				message.put((byte)id);
				message.putInt(index);
				break;		
			case REQUEST:
				message = ByteBuffer.allocate(17);
				message.putInt(13);
				message.put((byte)id);
				message.putInt(index);
				message.putInt(begin);
				message.putInt(length);
				break;
			case PIECE:	
				message = ByteBuffer.allocate(13+length);
				message.putInt(9+length);
				message.put((byte)id);
				message.putInt(index);
				message.putInt(begin);
				message.put(block);
				break;
			/*
			case CANCEL:
				message = ByteBuffer.allocate(17);
				message.putInt(13);
				message.put((byte)id);
				message.putInt(index);
				message.putInt(begin);
				message.putInt(length);
				break;
			case PORT:
				message = ByteBuffer.allocate(17);
				message.putInt(3);
				message.put((byte)id);
				message.putInt(6881);
				break;
			*/
			default:
				break;
		}
		
		return message.array();
	}
	
	/**
	 * Reads a peer message and returns the id of that message response.
	 * @param dis
	 * @return
	 */
	public static int readPeerMessage(DataInputStream dis) {
		try {
			int messageLength = dis.readInt();
			byte messageID = dis.readByte();
			if ( (messageLength == 0) && (messageID == ((byte)0)) ) {
				return KEEP_ALIVE;				
			} else {
				return messageID;
			}
		} catch (IOException e) {
			System.err.println("IOExeption reading from DataInputStream!");
		}
		return NO_MSG;
	}




	




	

}
