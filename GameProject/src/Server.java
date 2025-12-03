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
	private Vector<UserService> allUsers = new Vector<>();
	
	public Server() {
		try {
			setupConnection(); 
		} catch(IOException e) {
			handleError(e.getMessage());
		}
		
		createRoom("초보만 오세요");
		createRoom("즐겜방입니다");
		createRoom("고수방");
		createRoom("아무나 환영");
		createRoom("잠수 금지");
		
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
		broadcastAll("NEW_ROOM::" + name); 
		return newRoom;
	}
	
	public void broadcastAll(String msg) {
		for (UserService user : allUsers) {
			user.WriteOne(msg);
		}
	}
	
	public String getRoomListString() {
		StringBuilder sb = new StringBuilder();
		for (Room r : RoomVec) {
			sb.append(r.roomName).append(",");
		}
		return sb.toString();
	}
	
	public void checkGameStart(Room room) {
		if (room.roomUsers.size() >= 2 && room.currentKeyword.isEmpty()) { 
			System.out.println("[" + room.roomName + "] 다음 라운드를 시작합니다.");
			room.startGame(); 
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
					allUsers.add(new_user);
					if (new_user.initUser()) { 
						new_user.start(); 
					} else {
						client_socket.close();
						allUsers.remove(new_user);
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
	    UserService host = null; 
	    
	    private String[] keywords = {"기린", "사과", "컴퓨터", "비행기", "자전거", "바나나", "고양이", "피아노", "자동차", "학교", "병원", "경찰"}; 
	    
	    public Room(String name) {
	    	this.roomName = name;
	    }
	    
		public void WriteAll(String str) {
			for (UserService user : roomUsers) {
				user.WriteOne(str); 
			}
		}
		
		public synchronized void startGame() {
			if (roomUsers.size() < 2) {
				WriteAll("CHAT::[시스템] 게임을 시작하려면 최소 2명이 필요합니다.");
				return;
			}
			if (!currentKeyword.isEmpty()) return;
			
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
		
		public void enterUser(UserService user) {
			roomUsers.add(user);
			if (roomUsers.size() == 1) {
				host = user;
				user.WriteOne("ROLE::HOST");
				user.WriteOne("CHAT::[시스템] 당신은 방장입니다. 게임 시작 버튼을 눌러 게임을 시작하세요.");
			} else {
				user.WriteOne("ROLE::GUEST");
			}
			System.out.println(roomName + "(참가자: " + roomUsers.size() + "명)");
		}
		
		public void removeUser(UserService user) {
			roomUsers.removeElement(user);
			WriteAll("CHAT::" + user.UserName + "님이 퇴장했습니다.");
			WriteAll("LOGOUT::" + user.UserName);
			
			if (user == host && !roomUsers.isEmpty()) {
				host = roomUsers.get(0); 
				host.WriteOne("ROLE::HOST");
				WriteAll("CHAT::[시스템] " + host.UserName + "님이 새로운 방장이 되었습니다.");
			}
			
			if (!currentKeyword.isEmpty() && roomUsers.size() < 2) {
				currentKeyword = "";
				WriteAll("CHAT::[시스템] 인원 부족으로 게임이 종료되었습니다.");
				WriteAll("CLEAR::"); 
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
		
		// [추가] 서버에서도 점수 관리
		private int score = 0;
		
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
			this.score = 0; // 방 입장 시 점수 초기화
			targetRoom.enterUser(this);
			broadcastJoin();
		}
		
		public boolean initUser() {
			try {
				String currentRooms = Server.this.getRoomListString();
				if(!currentRooms.isEmpty()) {
					WriteOne("ROOM_LIST::" + currentRooms);
				}
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
			currentRoom.WriteAll("LOGIN::" + this.UserName); // 입/퇴장 동기화
		}
		
		public void WriteOne(String msg) {
			try { out.println(msg); } catch(Exception e) { }
		}
		
		public void run() {
			try {
				String msg;
				while((msg = in.readLine()) != null) {
					msg = msg.trim();
					
					if (msg.startsWith("CREATE_ROOM::")) {
						String roomName = msg.substring("CREATE_ROOM::".length()).trim();
						Server.this.createRoom(roomName);
						continue;
					}
					
					if (msg.startsWith("JOIN_ROOM::")) {
					    if (currentRoom == null) joinRoom(msg.substring("JOIN_ROOM::".length()).trim());
					    continue;
					}
					
					if (currentRoom == null) continue;
					
					if (msg.startsWith("ANSWER::")) {
		                String userAnswer = msg.substring(8).trim();
		                
		                if (!currentRoom.currentKeyword.isEmpty() && userAnswer.equalsIgnoreCase(currentRoom.currentKeyword)) {
		                    if (this.isDrawer) {
		                        this.WriteOne("CHAT::[시스템] 출제자는 정답을 입력할 수 없습니다.");
		                    } else {
		                        String answer = currentRoom.currentKeyword;
		                        currentRoom.currentKeyword = ""; 
		                        
		                        // [중요 수정] 점수 및 승리 로직
		                        this.score++;
		                        
		                        if (this.score >= 10) {
		                        	// 10점 도달 시 게임 종료 알림
		                        	currentRoom.WriteAll("CHAT::" + this.UserName + "님이 정답을 맞혔습니다! (정답: " + answer + ")");
		                        	currentRoom.WriteAll("GAME_OVER::" + this.UserName);
		                        	
		                        	// 모든 유저 점수 초기화 (다음 판을 위해)
		                        	for(UserService u : currentRoom.roomUsers) {
		                        		u.score = 0;
		                        	}
		                        } else {
		                        	// 10점 미만이면 계속 진행
		                        	currentRoom.WriteAll("NOTICE_CORRECT::" + this.UserName + "님이 정답을 맞혔습니다! (정답: " + answer + ")");
			                        try { TimeUnit.SECONDS.sleep(2); } catch(InterruptedException e) {}
			                        Server.this.checkGameStart(currentRoom); 
		                        }
		                    }
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
						if (currentRoom.host == this) {
							currentRoom.startGame();
						} else {
							WriteOne("CHAT::[오류] 방장만 게임을 시작할 수 있습니다.");
						}
					}
				} 
			} catch (IOException e) { 
            } finally {
            	Server.this.allUsers.remove(this);
                if (currentRoom != null) currentRoom.removeUser(this);
                try { if(client_socket != null) client_socket.close(); } catch (Exception e) {} 
            }
		}
	}
	
	public static void main(String[] args) {
		new Server(); 
	}
}