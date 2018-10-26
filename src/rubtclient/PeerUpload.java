//Michael Rollins  NetID mtr96
//Michael Shur  NetID mas868
package rubtclient;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

public class PeerUpload implements Runnable{

	
	private Socket socket;

	private Thread thread;

	private Torrent torrent;
	
	private DataInputStream dis;
	
	private DataOutputStream dos;

	private int RUNNING = 0, PAUSED = 1, RESUME = 2, STOP = 3, DEAD = 4;
	private int state = RUNNING;
	
	public PeerUpload(Socket socket, Torrent torrent){
		this.socket = socket;
		this.torrent = torrent;
		try {
			dis = new DataInputStream(socket.getInputStream());
			dos = new DataOutputStream(socket.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if(socket != null){
				try {
					socket.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
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
		}
	}

	/**
	 * Will make all preform all the operations that are assocaited with the state of the TorrentSession.
	 * Will change states when user input is recieved
	 */
	@Override
	public void run() {
		
		if(socket.isClosed())
			return;
		sendHave(torrent, dos);
		int peerMessage = Message.readPeerMessage(dis);
		if (peerMessage == Message.NO_MSG) {
			System.out.println("Closing not reponse from peer!");
		} else if(peerMessage == Message.INTERESTED) {
			 
				sendUnchoke();
				
				while(state != DEAD){

					peerMessage = Message.readPeerMessage(dis);
					
					if (peerMessage == Message.REQUEST) {
						try {
							int index = dis.readInt();
							int begin = dis.readInt();
							int length = dis.readInt();
							
							
							
							
							System.out.println("Uploading requested piece to " + socket.getInetAddress() + " at index: " + index + ", offset: " + begin);
							if (sendPiece(index, begin, length)){
								
								int uploaded = torrent.getUploaded();
								uploaded += length;
								torrent.setUploaded(uploaded);
								
								System.out.println("Uploaded" + uploaded);
							}
						
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
							if(socket != null){
								try {
									socket.close();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
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
						}
					} else if (peerMessage == Message.UNINTERESTED) {
						System.out.print("Peer is no longer intrested closing." + socket.getInetAddress());
						break;
					} else {
						System.out.println("Peer did not send intrest closing" + socket.getInetAddress());
						break;
					}	
				} 
			
		} else if (peerMessage == Message.UNINTERESTED) {
			System.out.print("Peer is no longer intrested closing." + socket.getInetAddress());
		
		} else {
			System.out.println("Peer did not send intrest closing" + socket.getInetAddress());
		}
		
		if(socket != null){
			try {
				socket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
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
		
		
		
	}

	/**
	 * Write the piece as bytes and sends it to the user.
	 * @param index
	 * @param begin
	 * @param length
	 * @return
	 */
	private boolean sendPiece(int index, int begin, int length) {
		int pieceLength = torrent.getPieceLength();
		byte[] piece = null;
		
		byte [] temp = torrent.getBuffer()[index];
		if ((begin + length) > pieceLength) {
			return false;
		}
		else {
			piece = Arrays.copyOfRange(temp, begin, length+begin);
			try {
				dos.writeInt(9+length);
				dos.writeByte(7);
				dos.writeInt(index);
				dos.writeInt(begin);
				dos.write(piece);
				dos.flush();
				return true;
			} catch (IOException e) {
				System.out.println("Could not send piece message to peer."+e.getMessage());
				return false;
			}
		}
	}

	/**
	 * send the byte buffer to the user to so they know what we have.
	 * @param torrent
	 * @param dos
	 */
	private static void sendHave(Torrent torrent, DataOutputStream dos){
		byte[][] temp = torrent.getBuffer();
		for (int i = 0; i < temp.length; i++) {
			if (temp[i] != null)
				try {
					dos.write(Message.generateMessage(4, i, -1, -1, null));
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		try {
			dos.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Send an unchoke message to the peer assocaited with the dos.
	 */
	private void sendUnchoke() {
		byte[] unchoke = Message.generateMessage(1);
		try {
			dos.write(unchoke); 
			dos.flush();
		}catch (IOException e) { 
			System.out.println("Unchoked failed to send!");
		}
	}

	public void start() {
		thread = new Thread(this);
		thread.start();
	}

	public Thread getThread() {
		return thread;
	}

	public void kill() {
		state = DEAD;
	}
}
