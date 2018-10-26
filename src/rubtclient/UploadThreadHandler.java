//Michael Rollins  NetID mtr96
//Michael Shur  NetID mas868
package rubtclient;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class UploadThreadHandler{

	private Torrent torrent;
	
	private PeerListener peerListener;
	
	private ArrayList<PeerUpload> activeUploads = new ArrayList<PeerUpload>();
	
	//private static int count = 0;
	
	public UploadThreadHandler(Torrent torrent) {
		this.torrent = torrent;
	}
	
	/**
	 * Listens for incoming connections and dispatched a socket to handle requests.
	 * @author Michael Shur
	 * @author Michael Rollins
	 *
	 */
	private class PeerListener implements Runnable{

		//private Thread thread;
		private boolean running = true;
		
		/*
		public void start(){
			if(thread == null){
				thread = new Thread(this);
				thread.start();
			}
		}
		*/
		
		@Override
		public void run(){
			
			try {
				ServerSocket ss = new ServerSocket(torrent.getPort());
			
				System.out.println("PORT IS "+torrent.getPort());
				
				while(running){
					
					Socket socket = ss.accept();
					
					byte[] handshake = Peer.makeHandshake(torrent.getTorrentInfo(), torrent);
					
					if(!Peer.checkHandshake(handshake, torrent.getTorrentInfo(), socket)){
						System.out.println("Invalid handshake closing connection at:"+socket.getInetAddress());
						updateActiveUploadList();
						socket.close();
					} else {
						System.out.println("Succesful handshake at:" + socket.getInetAddress());
						
						PeerUpload pu = new PeerUpload(socket,torrent);
						activeUploads.add(pu);
						pu.start();
						updateActiveUploadList();
					}
					
					
				}
				ss.close();
				System.out.println("SERVER SOCKET IS NOW CLOSED");
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		public void kill(){
			running = false;
		}
		
	}
	
	/**
	 * clears any dead threads/sockets if any from the list
	 */
	private void updateActiveUploadList(){
		for(int i = 0; i < activeUploads.size(); i++){
			if(!activeUploads.get(i).getThread().isAlive()){//If the thread has died remove it from the activeUploads
				activeUploads.remove(i);
			}
			
		}
	}
	
	public boolean isCurrentlyRunning()
	{
		if(peerListener != null && peerListener.running==true)
			return true;
		return false;
	}
	/**
	 * starts the PeerListener
	 */
	public void startPeerListener(){		
		if(peerListener != null){
			peerListener.kill();
		}
		peerListener = new PeerListener();
		peerListener.run();
	}
	
	/**
	 * Kills the PeerListener and puts all PeerUploads into a DEAD state
	 */
	public void killPeerListener(){
		if(peerListener != null){
			peerListener.kill();
			for(PeerUpload pu : activeUploads){
				pu.kill();
			}
		}
	}
	
	
	
	/*
	public void stop(){
		if(peerListener != null){
			peerListener.kill();
		}
		for(int i = 0; i < activeUploads.size(); i++){
			activeUploads.get(i).stop();
		}
	}
	 */
}
