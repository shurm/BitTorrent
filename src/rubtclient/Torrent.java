//Michael Rollins  NetID mtr96
//Michael Shur  NetID mas868
package rubtclient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import GivenTools.BencodingException;
import GivenTools.TorrentInfo;

public class Torrent {

	private String announceURL;
	private byte[] infoHash;
	private String peerID;
	private int port;
	private String event;
	private int uploaded;
	private int downloaded;
	private int left;
	private int fileLength;
	private int pieceLength;
	private ByteBuffer[] pieceHashes;
	private int smallpieceLength;
	private int hashesLength;
	private int minInterval;
	private byte[][] buffer;
	private List<Integer> pieceList = new ArrayList<Integer>();
	private int numberPieces;
	private byte[] fileData;
	
	//private Object leftLock = new Object();
	//private Object downloadedLock = new Object();
	
	private Object tAccessLock = new Object();
	
	
	private TorrentInfo torrentInfo;
	
	
	/**
	 * Creates a Torrent object and initializes it with default values specified by the TorrentInfo object.
	 * @param torrentInfo
	 */
	public Torrent(TorrentInfo torrentInfo) {
		this.torrentInfo = torrentInfo;	
		this.announceURL = torrentInfo.announce_url.toString();
		this.infoHash = torrentInfo.info_hash.array();	
		this.pieceHashes = torrentInfo.piece_hashes;
		this.peerID = "-MY1000-" + genRandString(12);						
		this.port = 6881; 								
		this.event = "started"; 						
		this.uploaded = 0; 
		this.downloaded = 0;
		this.left = torrentInfo.file_length;
		this.fileLength = torrentInfo.file_length;
		this.pieceLength = torrentInfo.piece_length;
		this.smallpieceLength = fileLength - ((fileLength/pieceLength)*pieceLength);
		this.hashesLength = torrentInfo.piece_hashes.length;
		this.minInterval = 110;						
		this.buffer = new byte[hashesLength][];
		this.fileData= new byte[fileLength];
		//this.numberPieces = ((fileLength/pieceLength)+1);
		for(int i = 0; i < hashesLength; i++) {
			pieceList.add(i);
		}
		
	}

	/**
	 * Returns a TorrentIndo object with a specific meta-data file passed
	 * to this method via a string path.
	 * 
	 * @param metaData a String containing the path to a meta-data file
	 * @return a TorrentInfo object with specific meta-data
	 */
	public static TorrentInfo getTorrentInfo(String metaData) {
		TorrentInfo ti = null;
		if(metaData == null || metaData.equals("")) {
			System.out.println("Invalid metadata file!");
			return null;
		} else {
			File file = new File(metaData);
			byte[] fileBytes = new byte[(int)file.length()];
			FileInputStream fin = null;
			try {
				fin = new FileInputStream(file);	
				fin.read(fileBytes);
				ti = new TorrentInfo(fileBytes);
				fin.close();
				
			} catch (FileNotFoundException e) {
				System.err.println("MetaData file not found when retrieving!");
				return null;
			}catch (BencodingException e) {
				System.err.println("Bencoding error when parsing MetaData file bytes!");
				if(fin!=null)
					try {
						fin.close();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				return null;
			}catch (IOException e) {
				System.err.println("IOException reading MetaData file!");
				return null;
			} 
			
		}
		
		return ti;
	}

	/**
	 * Returns a Torrent object with default values initialized using TorrentInfo.
	 * 
	 * @param torrentInfo a TorrentInfo containing the information the torrent file
	 * @return a Torrent object with default values from TorrentInfo 
	 */
	public static Torrent createTorrent(TorrentInfo torrentInfo) {
		if(torrentInfo == null) {
			System.out.println("Null reference on TorrentInfo!");
			return null;
		} else {
			return new Torrent(torrentInfo);
		}
	}

	/**
	 * Generates a random string from (a-b,A-B,0-9) inclusively of size specified by caller.
	 * @param: takes a integer size.
	 * @return: returns a random generated string of length of size.
	 * 
	 */
	private String genRandString(int size) {
		
		String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
		
        StringBuilder build = new StringBuilder();
        Random rand = new Random();
        while (build.length() < size) {
            int index = (int) (rand.nextFloat() * chars.length());
            build.append(chars.charAt(index));
        }
        String newString = build.toString();
        return newString;
	}

	
	public void setEvent(String event) {
		if( (event.equals("started") || event.equals("stopped") || event.equals("completed") || event.equals("empty")) ) {
			this.event = event;
		} else {
			System.out.println("Tried to set invalid event!");
		}
	}
	
	/**
	 * Returns a HttpURLConnection specified by the Torrent object.
	 * 
	 * @param torrent an object with information about the Tracker
	 * @return a HttpURLConnection for a tracker
	 */
	public static HttpURLConnection getTrackerConnection(Torrent torrent) {
		if(torrent == null) {
			System.out.println("Null reference for torrent!");
			return null;
		} else {
			URL url = createURL(torrent);
			int port = 6881;
			while(port < 6890) {
				try {
					HttpURLConnection connection = (HttpURLConnection)url.openConnection();
					return connection;
				} catch (IOException e) {
					System.err.println("IOException when openning connection with tracker!");
				}	
				System.out.println("Tracker could not be reached at port " + port);
				port++;
				torrent.port = port;
				url = createURL(torrent);
			}
			System.out.println("Failed to establish a connection with the tracker!");
			return null;
		}
	}

	/**
	 * Returns a constructed URL object specified by the Torrent object.
	 * 
	 * @param torrent an object with information required to construct a URL
	 * @return a URL for the Torrent object
	 */
	public static URL createURL(Torrent torrent) {
		if(torrent == null) {
			System.out.println("Null reference for torrent!");
			return null;
		} else {
			
			String announceURL = torrent.announceURL;
			String infoHash = bytesToHexString(torrent.infoHash);
			String peerID = torrent.peerID;
			String port = Integer.toString(torrent.port);
			String uploaded = Integer.toString(torrent.uploaded);
			String downloaded = Integer.toString(torrent.downloaded);
			String left = Integer.toString(torrent.fileLength - torrent.downloaded);
			String event = torrent.event;
			String urlString = announceURL + 
					"?info_hash=" + infoHash +
					"&peer_id=" + peerID + 
					"&port=" + port +
					"&uploaded=" + uploaded +
					"&downloaded=" + downloaded +
					"&left=" + left +
					"&event=" + event;
			
			try {
				return new URL(urlString);
			} catch (MalformedURLException e) {
				System.err.println("Malformed URL Exception built from torrent!");
				return null;
			}
			
		}	
	}

	/**
	 * Returns the hex values for the byte array into a string.
	 * 
	 * @param bytes to be converted into hex string
	 * @return a string with hex values for each byte
	 */
	private static String bytesToHexString(byte[] bytes) {
		if(bytes == null) {
			System.out.println("Null reference for byte array!");
			return null;
		} else {
			final StringBuilder builder = new StringBuilder();
			for(int i = 0; i < bytes.length; i++) {
				builder.append("%");
				builder.append(String.format("%02X", bytes[i]));
			}
			return builder.toString();
		}
	}
	
	/**
	 * Gets the responds from the tracker after a connection was made to the server. 
	 * @param connection takes a HttpURLConnection object
	 * @return returns the encoded response in  a byte array
	 */
	public static byte[] getTrackerResponse(HttpURLConnection connection) {
		byte[] encodedBytes = null;
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			StringBuilder stringBuilder = new StringBuilder();
			String line = null;
			while( (line =br.readLine()) != null ) {
				stringBuilder.append(line);
			}
			encodedBytes = stringBuilder.toString().getBytes();
		} catch (IOException e) {
			System.err.println("IOException could not get HttpURLConnection input stream!");
		} finally {
			if(br != null) {
				try {
					br.close();
				} catch (IOException e) {
					System.err.println("IOException could not close BufferedReader!");
				}
			}
		}
		return encodedBytes;
	}
	
	/**
	 * Adds the piece to the buffer array at the given index.
	 * @param piece a byte array 
	 * @param index a integer at given index
	 */
	public void addToBuffer(byte[] piece, int index) {
		if(piece == null) {
			System.out.println("Null reference for piece!");
		} else {
			if(buffer[index] == null) {
				buffer[index] = piece;
				
				int position=pieceLength * index;
				
				for(int a=0;a<piece.length;a++)
				{
					fileData[position++]=piece[a];
				}	
			} else {
				System.out.println("The piece as already been added to the buffer!");	
			}
		}
	}

	public String getPeerID() {
		return peerID;
	}

	public int getFileLength() {
		return fileLength;
	}

	public Object aquireLock(){
		return tAccessLock;
	}
	
	
	public int getLeft() {
			return left;
	}

	public int getHashesLength() {
		return hashesLength;
	}

	public int getDownloaded() {
			return downloaded;
	}

	public ByteBuffer[] getPieceHashes() {
		return pieceHashes;
	}

	public int getPieceLength() {
		return pieceLength;
	}

	public void setLeft(int value) {
			left = value;
	}

	public void setDownloaded(int value) {
			downloaded = value;
	}

	
	public byte[][] getBuffer() {
		return buffer;
	}
	
	public byte[] getFileData() {
		return fileData;
	}
	
	public void updateLeft(int byteLength) {
			left = left-byteLength;
	}

	public void updateDownloaded(int byteLength) {
			downloaded = downloaded + byteLength;
	}
	
	public synchronized int pickPiece() {
		//synchronized(pieceList){
			int listSize = pieceList.size();	
			if(listSize == 0) { //we got all of the pieces
				return -1;
			} else {
				
				//int randNum = 0 + (int)(Math.random() * (((listSize-1) - 0) + 1));
				int	randNum = ThreadLocalRandom.current().nextInt(0,listSize);
				
				int pieceIndex = pieceList.get(randNum); //gets element at index
				pieceList.remove(randNum); //removes element at index
				return pieceIndex;		// returns element at index
			}
		//}
	}
	
	public synchronized void addBackToList(int piece){
		//synchronized(pieceList){
			pieceList.add(piece);
		//}
	}

	

	public int getMinInterval() {
		return minInterval;
	}

	public TorrentInfo getTorrentInfo(){
		return torrentInfo;
	}
	
	public int getPort(){
		return port;
	}

	public int getUploaded() {
		return uploaded;
	}

	public void setUploaded(int uploaded) {
		this.uploaded = uploaded;
	}
}
