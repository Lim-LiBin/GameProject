import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Server {
	private ServerSocket socket = null; 
	private Vector<Room> RoomVec = new Vector<>(); 
	private ConcurrentHashMap<String, Room> RoomMap = new ConcurrentHashMap<>();
	
	public Server() {
		try {
			setupConnection(); 
		} catch(IOException e) {
			handleError(e.getMessage());
		}
		AcceptServer accept_server = new AcceptServer();
		accept_server.start();
	}
	
	public void setupConnection() throws IOException {
		socket = new ServerSocket(9999); 
	}
	
	private static void handleError(String string) {
		System.out.println("[오류] " + string);
		System.exit(1);
	}
	
	public Room createRoom(String name) {
		name = name.trim();
		if (RoomMap.containsKey(name)) {
			return RoomMap.get(name);
		}
		Room newRoom = new Room(name);
		RoomVec.add(newRoom);
		RoomMap.put(name, newRoom);
		System.out.println("[방 생성] " + name);
		return newRoom;
	}
	
	public void checkGameStart(Room room) {
		// 방의 인원이 2명 이상이고, 현재 진행 중인 단어가 없을 때만 시작
		if (room.roomUsers.size() >= 2 && room.currentKeyword.isEmpty()) { 
			System.out.println("[" + room.roomName + "] 게임 시작 조건을 만족하여 시작합니다.");
			room.startGame(); 
		} else {
			System.out.println("[" + room.roomName + "] 대기 중. (인원: " + room.roomUsers.size() + "/2, 진행중: " + !room.currentKeyword.isEmpty() + ")");
		}
	}
	
	private class AcceptServer extends Thread {
		@Override
		public void run() {
			while(true) {
				try {
					System.out.println("[서버] 접속 대기 중...");
					Socket client_socket = socket.accept(); 
					UserService new_user = new UserService(client_socket);
					if (new_user.initUser()) { 
						new_user.start(); 
					} else {
						client_socket.close();
					}
				} catch(IOException e) {
					System.out.println("[오류] AcceptServer: " + e.getMessage());
				}
			}
		}
	}
	
	class Room {
		String roomName;
	    Vector<UserService> roomUsers = new Vector<>(); 
	    String currentKeyword = ""; 
	    
	    private String[] keywords = {"기린", "사과", "컴퓨터", "비행기", "자전거", "바나나", "고양이", "피아노"}; 
	    
	    public Room(String name) {
	    	this.roomName = name;
	    }
	    
		public void WriteAll(String str) {
			for (UserService user : roomUsers) {
				user.WriteOne(str); 
			}
		}
		
		// [중요 수정] 동기화 처리로 안전하게 게임 시작
		public synchronized void startGame() {
			if (roomUsers.size() < 2 || !currentKeyword.isEmpty()) return; 
			
			int drawerIndex = (int)(Math.random() * roomUsers.size());
			UserService drawer = roomUsers.get(drawerIndex);
			String keyword = keywords[(int)(Math.random() * keywords.length)]; 
			
			this.currentKeyword = keyword; 
			
			for (UserService user : roomUsers) {
				if (user == drawer) {
					user.WriteOne("START::drawer," + keyword);
					user.isDrawer = true; 
				} else {
					user.WriteOne("START::guesser," + keyword.length());
					user.isDrawer = false; 
				}
			}
			System.out.println("[" + roomName + "] 새 라운드 시작! (출제자: " + drawer.UserName + ", 단어: " + keyword + ")");
		}
		
		public void removeUser(UserService user) {
			roomUsers.removeElement(user);
			WriteAll("CHAT::" + user.UserName + "님이 퇴장했습니다.");
			WriteAll("LOGOUT::" + user.UserName);
			
			if (!currentKeyword.isEmpty() && roomUsers.size() < 2) {
				currentKeyword = "";
				WriteAll("CHAT::[시스템] 인원 부족으로 게임이 종료되었습니다.");
				WriteAll("CLEAR::"); // 화면 지우기
			}
		}
	}

	class UserService extends Thread {
		private BufferedReader in;
		private PrintWriter out;
		private Socket client_socket; 
		private Room currentRoom = null; 
		private String UserName = ""; 
		private boolean isDrawer = false; 
		
		public UserService(Socket client_socket) {
			this.client_socket = client_socket;
			try {
				in = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));
				out = new PrintWriter(client_socket.getOutputStream(), true);
			} catch(Exception e) { }
		}
		
		public void joinRoom(String roomName) {
			Room targetRoom = Server.this.createRoom(roomName);
			this.currentRoom = targetRoom;
			targetRoom.roomUsers.add(this);
			
			broadcastJoin();
			Server.this.checkGameStart(targetRoom);
		}
		
		public boolean initUser() {
			try {
				String line1 = in.readLine(); 
				if (line1 == null || !line1.startsWith("LOGIN::")) return false;
				this.UserName = line1.substring("LOGIN::".length()).trim();
				return true;
			} catch(Exception e) { return false; }
		}
		
		public void broadcastJoin() {
			if (currentRoom == null) return;
			for (UserService user : currentRoom.roomUsers) {
				if (user != this) this.WriteOne("LOGIN::" + user.UserName);
			}
			currentRoom.WriteAll("CHAT::" + this.UserName + "님이 입장했습니다.");
			this.WriteOne("CHAT::방 이름: " + currentRoom.roomName);
			this.WriteOne("LOGIN::" + this.UserName); // 나 자신도 리스트에 추가
		}
		
		public void WriteOne(String msg) {
			try { out.println(msg); } catch(Exception e) { }
		}
		
		public void run() {
			try {
				String msg;
				while((msg = in.readLine()) != null) {
					msg = msg.trim();
					
					if (msg.startsWith("JOIN_ROOM::")) {
					    if (currentRoom == null) joinRoom(msg.substring("JOIN_ROOM::".length()).trim());
					    continue;
					}
					
					if (currentRoom == null) continue;
					
					if (msg.startsWith("ANSWER::")) {
						String userAnswer = msg.substring(8).trim();
						
						// 정답 판별 로직
						if (!currentRoom.currentKeyword.isEmpty() && userAnswer.equalsIgnoreCase(currentRoom.currentKeyword)) {
							String answer = currentRoom.currentKeyword;
							
							// [핵심] 정답 맞춤 처리
							currentRoom.currentKeyword = ""; // 즉시 키워드 초기화 (재시작 준비)
							currentRoom.WriteAll("NOTICE_CORRECT::" + this.UserName + "님이 정답을 맞혔습니다! (정답: " + answer + ")");
							currentRoom.WriteAll("CHAT::[정답] " + this.UserName + " (정답: " + answer + ")");
							
							// 2초 딜레이 후 게임 재시작
							try { TimeUnit.SECONDS.sleep(2); } catch(InterruptedException e) {}
							
							// 재시작 요청
							Server.this.checkGameStart(currentRoom);
							
						} else {
							currentRoom.WriteAll("CHAT::" + this.UserName + ": " + userAnswer);
						}
					} 
					else if (msg.startsWith("DRAW::") || msg.startsWith("CLEAR::") || msg.startsWith("RGB::")) {
						if (isDrawer) currentRoom.WriteAll(msg);
					}
					else if (msg.startsWith("CHAT::")) {
						currentRoom.WriteAll(msg); 
					}
					else if (msg.startsWith("GAME_START::")) {
						currentRoom.startGame();
					}
					
				} 
			} catch (IOException e) { 
            } finally {
                if (currentRoom != null) currentRoom.removeUser(this);
                try { if(client_socket != null) client_socket.close(); } catch (Exception e) {} 
            }
		}
	}
	
	public static void main(String[] args) {
		new Server(); 
	}
}