//Michael Rollins  NetID mtr96
//Michael Shur  NetID mas868
package rubtclient;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

public class DownloadThreadHandler{

	
	private Torrent torrent;
	private ArrayList<PeerDownload> activeDownloads = new ArrayList<PeerDownload>();
	private boolean isFinished;
	
	
	public DownloadThreadHandler(Torrent torrent) 
	{
		this.isFinished = false;
		this.torrent = torrent;
	}

	/**
	 * Takes a peerList and added and starts PeerDownload objects, removes dead threads if any inside activedownloads
	 * @param peerList
	 */
	public void dispatchPeers(ArrayList<HashMap<ByteBuffer, Object>> peerList,long startTime) {
		
		if(torrent.getLeft() != 0){
			for(int i = 0; i < activeDownloads.size(); i++){
				if(!activeDownloads.get(i).getThread().isAlive()){//If the thread has died remove it from the activeDownloads
					activeDownloads.remove(i);
				}
				if(activeDownloads.get(i).isPaused()){
					activeDownloads.get(i).resume();
				}
			}
			for(int i = 0; i < peerList.size(); i++){
				PeerDownload peerDownload = new PeerDownload(peerList.get(i),torrent);
				if(!activeDownloads.contains(peerDownload)){//Adds the new peerDownload to the activeDownloads, and start the thread
					
						activeDownloads.add(peerDownload);
						peerDownload.start();
					
					
				}
				
			}
		} else {
			if(isFinished==false)
			{
				System.out.println("Total Download Time: "+(System.currentTimeMillis()-startTime)/1000.0+" seconds or "+(System.currentTimeMillis()-startTime)/60000.0+" minutes");
			}
				
			
			isFinished = true;
		}
		
	}
	
	/**
	 * Puts all PeerDownloads inside the list to the pause state.
	 */
	public void pause(){
		for(int i = 0; i < activeDownloads.size(); i ++){
			activeDownloads.get(i).pause();//sends a pause signal to the active PeerDownload threads.
		}
	}
	
	/**
	 * Puts all PeerDownload inside the list to the stop state.
	 */
	public void stop(){
		for(int i = 0; i < activeDownloads.size(); i++){
			activeDownloads.get(i).stop();
		}
	}
	
	/**
	 * Returns the status of the DownloadThreadHeandler 
	 * @return boolean true on isFinsihed, else false.
	 */
	public Boolean isFinished(){
		return isFinished;
	}
	
}
