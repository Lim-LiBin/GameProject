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
		
		//기본 방 5개 생성
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
		
		broadcastAll("NEW_ROOM::" + name); //방이 생기면 모든 접속자에게 알림
		
		return newRoom;
	}
	
	//전제 접속자에게 메시지 전송(로비 갱신용)
	public void broadcastAll(String msg) {
		for (UserService user : allUsers) {
			user.WriteOne(msg);
		}
	}
	
	//현재 방 목록을 문자열로 변환
	public String getRoomListString() {
		StringBuilder sb = new StringBuilder();
		for (Room r : RoomVec) {
			sb.append(r.roomName).append(",");
		}
		return sb.toString();
	}
	
	//정답 후 다음 라운드 자동 시작
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
	    UserService host = null; //방장 변수
	    
	    private String[] keywords = {"기린", "사과", "컴퓨터", "비행기", "자전거", "바나나", "고양이", "피아노"}; 
	    
	    public Room(String name) {
	    	this.roomName = name;
	    }
	    
		public void WriteAll(String str) {
			for (UserService user : roomUsers) {
				user.WriteOne(str); 
			}
		}
		
		//게임 시작 로직
		public synchronized void startGame() {
			//인원이 부족하면 시작하지 않고 메시지 전송
			if (roomUsers.size() < 2) {
				WriteAll("CHAT::[시스템] 게임을 시작하려면 최소 2명이 필요합니다.");
				return;
			}
			
			//이미 게임 중이면 무시
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
			
			// 나간 사람이 방장이고, 방에 아직 사람이 남아있다면
			if (user == host && !roomUsers.isEmpty()) {
				host = roomUsers.get(0); // 다음 사람을 방장으로 지정
				host.WriteOne("ROLE::HOST");
				WriteAll("CHAT::[시스템] " + host.UserName + "님이 새로운 방장이 되었습니다.");
			}
			
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
		                
		                //정답과 일치하는지 확인
		                if (!currentRoom.currentKeyword.isEmpty() && userAnswer.equalsIgnoreCase(currentRoom.currentKeyword)) {
		                    
		                    //입력한 사람이 '출제자'인지 확인
		                    if (this.isDrawer) {
		                        //출제자라면 게임을 끝내지 않고, 본인에게만 경고 메시지 전송
		                        this.WriteOne("CHAT::[시스템] 출제자는 정답을 입력할 수 없습니다.");
		                    } 
		                    //출제자가 아닌 '참여자'가 맞힌 경우 -> 정상적인 정답 처리
		                    else {
		                        String answer = currentRoom.currentKeyword;
		                        
		                        currentRoom.currentKeyword = ""; //키워드 초기화 (게임 종료)
		                        currentRoom.WriteAll("NOTICE_CORRECT::" + this.UserName + "님이 정답을 맞혔습니다! (정답: " + answer + ")");
		                        
		                        try { TimeUnit.SECONDS.sleep(2); } catch(InterruptedException e) {}
		                        
		                        Server.this.checkGameStart(currentRoom); //다음 라운드
		                    }
		                    
		                } else {
		                    //정답이 아니면 일반 채팅으로 전송
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