//Michael Rollins  NetID mtr96
//Michael Shur  NetID mas868
package rubtclient;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.HashMap;



public class PeerDownload implements Runnable {

	private Thread thread;
	private HashMap<ByteBuffer, Object> peer;
	private Torrent torrent;
	private String peerIp;
	private String minInterval;
	private String peerId;
	
	private Socket socket;
	
	
	private int RUNNING = 0, PAUSED = 1, RESUME = 2, STOP = 3, DEAD = 4;
	private int state = RUNNING;
	
	private DataInputStream dis;
	private DataOutputStream dos;
	
	private Object waitLock = new Object();
	
	
	public PeerDownload(HashMap<ByteBuffer, Object> peer, Torrent torrent) {
		this.peer = peer;
		this.torrent = torrent;
		peerIp = Peer.objectByteBufferToString(peer.get(Peer.IP));
		//minInterval = Peer.objectByteBufferToString(peer.get(Peer.MIN_INTERVAL));
		minInterval = "100";
		peerId = Peer.objectByteBufferToString(peer.get(Peer.PEER_ID));
		
		
	}
	/**
	 * Will make all preform all the operations that are assocaited with the state of the TorrentSession.
	 * Will change states when user input is recieved
	 */
	@Override
	public void run() {
		System.out.println(peerId);
		//state = States.RUNNING;
		
		byte[] handShake = Peer.makeHandshake(torrent.getTorrentInfo(), torrent);
		socket = Peer.openTCPConnection(peer);
		
		try {
			socket.setSoTimeout(5*1000);
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(Peer.checkHandshake(handShake, torrent.getTorrentInfo(), socket)) {
			int hashesLength = torrent.getHashesLength();
			byte[] bitfield_message = Peer.getBitfield(socket, hashesLength);
			if(bitfield_message != null){
				System.out.println("Acquired bitmap: " + peerId);
				
				try {
					System.out.println(peerId + ": Started Downloading.");
					dis = new DataInputStream(socket.getInputStream());
					dos = new  DataOutputStream(socket.getOutputStream());
					Message.sendInterest(dis, dos, socket);
				
					while(state != DEAD){
						if(torrent.getLeft() == 0){//if we have downloaded the entire file then we set to dead
							state = DEAD;
							System.out.println("Torrent is finished Terminating: " + peerId);
						}
						
						if(state == RUNNING){
							Message.downloadPiece(dis, dos, torrent, socket);
						} else if(state == PAUSED){
							System.out.println(peerId + ": Paused.");
							Message.keepAlive(dos);						
							synchronized (waitLock) {
								try {
									waitLock.wait(Integer.parseInt(minInterval) * 1000);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
						} else if(state == RESUME){
							state = RUNNING;
							System.out.println(socket.isConnected());
							
							//Message.sendInterest(dis, dos, socket);
						} else if(state == STOP){
						
							state = DEAD;//the kill state
							System.out.println(peerId + ": Terminated.");
						} else {
							//System.out.println("Unknown state reach with: " + peerId);
						}
						
						
					}
					
					
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				System.out.println("No bitmap recieved terminating: " + peerId);
			}	
		} 
		
		if(dis != null){
			try {
				dis.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		if(dos != null){
			try {
				dos.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		if(socket != null){
			try {
				socket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
		
		
	}

	@Override
	public boolean equals(Object obj){
		if(obj == null || !(obj instanceof PeerDownload)){
			return false;
		}	
		PeerDownload peerDownload =(PeerDownload) obj;	
		
		return this.peerId.equals(peerDownload.getPeerId());
	}

	public Thread getThread(){
		return thread;
	}
	
	public void start() {
		thread = new Thread(this);
		thread.start();
	}
	
	public String getPeerId(){
		return peerId;
	}

	public void pause(){
		state = PAUSED;
	}
	
	public void resume(){
		state = RESUME;
		synchronized (waitLock) {//awakens the monitor if it is waiting	
			waitLock.notify();
		}
	}
	
	public void stop(){
		state = STOP;
	}
	
	public boolean isPaused(){
		if(state == PAUSED){
			return true;
		} else {
			return false;
		}
	}
	
}
