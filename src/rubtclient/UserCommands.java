//Michael Rollins  NetID mtr96
//Michael Shur  NetID mas868
package rubtclient;

import java.util.ArrayList;
import java.util.Scanner;

public class UserCommands implements Runnable {

	private ArrayList<TorrentSession> tsList = new ArrayList<TorrentSession>();
	private boolean isRunning = true;
	private Thread thread;
	
	
	public void addSession(TorrentSession ts){
		tsList.add(ts);
	}

	/**
	 * Recieves user inputs on terminal and changes the state of the TorrentSession.
	 */
	@Override
	public void run() {
		Scanner reader = new Scanner(System.in);
		while(isRunning){
			System.out.println("Options: start, stop, pause, resume, terminate, save");
			  
			String operation = reader.nextLine();
			
			switch(operation){
				case "start":
					
					tsList.get(0).start();
					break;
				case "pause":
					tsList.get(0).pause();
					break;
				case "resume":
					tsList.get(0).resume();
					break;
				case "terminate":
					tsList.get(0).terminate();
					isRunning = false;
					break;
				case "stop":
					tsList.get(0).stop();
					break;
				case "save":
					tsList.get(0).save();
					break;
				default:
					System.out.println("Invalid input!");
					break;
			}
			
		}
		reader.close();
	}
	
	public void start(){
		thread = new Thread(this);
		thread.start();
	}
	
	public Thread getThread(){
		return thread;
	}
	
}
