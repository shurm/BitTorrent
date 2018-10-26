//Michael Rollins  NetID mtr96
//Michael Shur  NetID mas868
package rubtclient;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

import GivenTools.TorrentInfo;



public class TorrentSession implements Runnable {

	private TorrentInfo torrentInfo;
	private Torrent torrent;
	private Thread thread;
	private String savePath;
	private final Object lock = new Object();

	
	
	int EMBRYO = 0, RUNNING = 1, PAUSED = 2, RESUME = 3, STOPPED = 4, TERMINATE = 5, DEAD = 6;
	int state = EMBRYO;
	
	private ArrayList<HashMap<ByteBuffer, Object>> peerList = new ArrayList<HashMap<ByteBuffer, Object>>();
	
	
	/**
	 * 
	 * @param metaData
	 * @param savePath
	 */
	public TorrentSession(String metaData, String savePath) {
		this.savePath = savePath;
		torrentInfo = Torrent.getTorrentInfo(metaData);
		torrent = Torrent.createTorrent(torrentInfo);
	}
	
	
	public Thread getThread(){
		return thread;
	}
	
	
	public void start(){
		state = RUNNING;
		
		thread = new Thread(this);
		thread.start();
	}

	/**
	 * create a UploadThreadHandler and DownloadThreadHandler starts them and then puts the session into a RUNNING state.
	 */
	@Override
	public void run() {
		
		
		UploadThreadHandler uth = new UploadThreadHandler(torrent);
		
		DownloadThreadHandler dth = new DownloadThreadHandler(torrent);
		
		System.out.println("State == "+ state);
		
		long startTime=System.currentTimeMillis();
		
		torrent.setEvent("started");
		while(state != DEAD){//runs until we decided to kill the program user response...
			
			
			if(state == RUNNING){
				refreshPeerList();
				
				System.out.println("hi");
				
				if(!dth.isFinished()){//if download finished then we dont need to run these anymore
					System.out.println("download time");
					dth.dispatchPeers(peerList,startTime);
				}
				if(!uth.isCurrentlyRunning())
				{
					System.out.println("Restarted!!!!!!!!!!!!!!!!");
					uth.startPeerListener();
				}
				stateWait(torrent.getMinInterval());		
			} else if(state == PAUSED){
				refreshPeerList();
				uth.killPeerListener();
				dth.pause();		
				stateWait(torrent.getMinInterval());	
			} else if(state == STOPPED){
				uth.killPeerListener();
				
				dth.stop();
				stateWait(torrent.getMinInterval());	
			} else if(state == TERMINATE){
				uth.killPeerListener();
				
				state = DEAD;
				dth.stop();
			} else if(state == RESUME){
				state = RUNNING;
				
				//dth.resume();
			} else {
			
				stateWait(10);//else wait 10	
			}
			
			
			
		}
	
		
		
	}

	public void running(){
		state = RUNNING;
		synchronized (lock){
			lock.notify();
		}
	}
	
	public void pause(){
		state = PAUSED;
		synchronized (lock){
			lock.notify();
		}
	}
	
	public void stop(){
		
		state = STOPPED;
		synchronized (lock){
			lock.notify();
		}
	}
	
	public void resume(){
		state = RESUME;
		synchronized (lock){
			lock.notify();
		}	
	}
	
	public void terminate(){
		state = TERMINATE;
		synchronized (lock){
			lock.notify();
		}
	}
	
	/**
	 * Puts this thread to sleep/wait timed
	 * @param seconds
	 */
	private void stateWait(int seconds){
		try {
			synchronized (lock) {
				lock.wait(seconds*1000);//refresh list ever min interval
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return;
	}
	
	/**
	 *Refreshes the peerList with the new aviaible peers from the tracker
	 */
	private void refreshPeerList() {
		peerList.clear();
		ArrayList<HashMap<ByteBuffer, Object>> tempPeerList = Peer.getPeerList(Torrent.getTrackerConnection(torrent));
		for(int i = 0; i < tempPeerList.size(); i++) {
			HashMap<ByteBuffer, Object> newPeer = (HashMap<ByteBuffer, Object>)tempPeerList.get(i);		
			peerList.add(newPeer);
		}
	}


	/**
	 * Save the file to the system.
	 */
	public void save() {
		System.out.println("Saving The torrent.");
		writeFile(torrent, savePath);
		System.out.println("Saving Complete");
	}
	
	/**
	 * Writes the file to output once the session as terminated
	 * @param torrent
	 * @param savePath
	 */
	private static void writeFile(Torrent torrent, String savePath){
		
		byte[] fileData= torrent.getFileData();
		
		try
		{
			FileOutputStream fileWriter = new FileOutputStream(savePath);
			fileWriter.write(fileData);       
			fileWriter.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
