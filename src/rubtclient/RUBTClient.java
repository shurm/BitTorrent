//Michael Rollins  NetID mtr96
//Michael Shur  NetID mas868
package rubtclient;



public class RUBTClient {

	
	/**
	 * Creates a running instance of UserCommands which will interact with the TorrentSession.
	 * @param metaData
	 * @param savePath
	 */
	public RUBTClient(String metaData, String savePath) {
		UserCommands uc = new UserCommands();
		TorrentSession session = new TorrentSession(metaData, savePath);
		uc.start();
		uc.addSession(session);
		
		
		try {
			uc.getThread().join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		
	}
	
	public static void main(String[] args) {
		
		new RUBTClient("CS352_Exam_Solutions.mp4.torrent","working.mov");	
	}





	
}
