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
	//방 및 유저 관리 컬렉션
	private Vector<Room> RoomVec = new Vector<>(); //방 목록을 순서대로 보여주기 위한 리스트(순서 보장)
	private ConcurrentHashMap<String, Room> RoomMap = new ConcurrentHashMap<>(); //방 이름으로 방 객체를 빠르게 검색하기 위한 맵
	private Vector<UserService> allUsers = new Vector<>(); //전체 접속자 리스트
	
	public Server() {
		try {
			setupConnection(); 
		} catch(IOException e) {
			handleError(e.getMessage());
		}
		
		//유저들이 들어갈 수 있는 기본 방 5개 생성
		createRoom("초보만 오세요");
		createRoom("즐겜방입니다");
		createRoom("고수방");
		createRoom("아무나 환영");
		createRoom("잠수 금지");
		
		//클라이언트 접속을 받는 스레드 시작
		AcceptServer accept_server = new AcceptServer();
		accept_server.start();
	}
	
	//9999 포트로 서버 소켓 생성
	public void setupConnection() throws IOException {
		socket = new ServerSocket(9999); 
	}
	
	//오류 처리 메서드
	private static void handleError(String string) {
		System.out.println("[오류] " + string);
		System.exit(1);
	}
	
	//방 생성 및 검색 로직
	//이미 존재하는 방 이름이면 해당 객체를 반환
	//없는 방이면 새로 생성 후 리스트와 맵에 등록
	public Room createRoom(String name) {
		name = name.trim();
		
		//이미 존재하는 방이면 해당 방 객체 반환
		if (RoomMap.containsKey(name)) {
			return RoomMap.get(name);
		}
		
		//새 방 생성 및 리스트에 추가
		Room newRoom = new Room(name);
		RoomVec.add(newRoom);
		RoomMap.put(name, newRoom);
		System.out.println("[방 생성] " + name);
		
		//로비에 있는 유저들의 방 목록 갱신을 위해 브로드캐스트
		broadcastAll("NEW_ROOM::" + name);
		
		return newRoom;
	}
	
	//로비에 있는 전체 유저에게 메시지 전송
	public void broadcastAll(String msg) {
		for (UserService user : allUsers) {
			user.WriteOne(msg);
		}
	}
	
	//현재 방 목록을 문자열로 반환
	//클라이언트가 최초 접속 시 현재 개설된 방 리스트를 보여주기 위함
	public String getRoomListString() {
		StringBuilder sb = new StringBuilder();
		for (Room r : RoomVec) {
			sb.append(r.roomName).append(",");
		}
		return sb.toString();
	}
	
	//한 라운드가 끝난 후 다음 게임 자동 시작 체크
	public void checkGameStart(Room room) {
		//2명 이상이고, 현재 진행 중인 단어가 없을 때 다음 라운드 시작
		if (room.roomUsers.size() >= 2 && room.currentKeyword.isEmpty()) { 
			System.out.println("[" + room.roomName + "] 다음 라운드를 시작합니다.");
			room.startGame(); 
		} 
	}
	
	//클라이언트의 연결 요청을 지속적으로 받아들이는 내부 클래스
	private class AcceptServer extends Thread {
		@Override
		public void run() {
			while(true) {
				try {
					System.out.println("[서버] 접속 대기 중...");
					Socket client_socket = socket.accept(); //연결 요청이 올 때까지 스레드 대기
					
					//접속된 클라이언트마다 1:1로 통신할 전담 스레드 생성
					UserService new_user = new UserService(client_socket);
					allUsers.add(new_user);
					
					//초기화 성공 시 스레드 시작
					if (new_user.initUser()) { 
						new_user.start(); 
					} else { //실패 시 연결 해제
						client_socket.close();
						allUsers.remove(new_user);
					}
				} catch(IOException e) {
					System.out.println("[오류] AcceptServer: " + e.getMessage());
				}
			}
		}
	}
	
	//개별 방의 상태와 게임 로직을 관리하는 클래스
	class Room {
		String roomName;
	    Vector<UserService> roomUsers = new Vector<>(); 
	    String currentKeyword = ""; //현재 라운드의 정답
	    UserService host = null; //방장 (게임 시작 제어권 보유)
	    int nextDrawerIndex = 0; //라운드마다 출제자를 균등하게 배분하기 위한 인덱스
	    
	    //제시어 목록
	    private String[] keywords = {
	    	    "기린", "고양이", "호랑이", "토끼", "거북이", "공룡", "닭", "펭귄", "오징어", 
	    	    "사과", "바나나", "수박", "포도", "아이스크림", "햄버거", "피자", "계란후라이", "핫도그",
	    	    "컴퓨터", "피아노", "자전거", "비행기", "자동차", "시계", "안경", "우산", "가위", "칫솔", "망치",
	    	    "나무", "해바라기", "눈사람", "무지개", "학교", "병원", "아파트",
	    	    "축구", "야구", "농구", "수영", "낚시"
	    	};
	    
	    public Room(String name) {
	    	this.roomName = name;
	    }
	    
	    //방에 참여 중인 모든 유저에게 메시지 전송
		public void WriteAll(String str) {
			for (UserService user : roomUsers) {
				user.WriteOne(str); 
			}
		}
		
		//게임 시작 및 라운드 진행 로직
		//자동 시작 로직이 겹칠 경우 발생할 수 있는 데이터 경쟁 방지
		public synchronized void startGame() {
			//최소 인원 체크
			if (roomUsers.size() < 2) {
				WriteAll("CHAT::[시스템] 게임을 시작하려면 최소 2명이 필요합니다.");
				return;
			}
			
			//이미 게임 중이면 중복 시작 방지
			if (!currentKeyword.isEmpty()) return;
			
			//출제자 선정 로직
			//인덱스를 인원수로 나눈 나머지를 사용하여 순환 처리
			int drawerIndex = nextDrawerIndex % roomUsers.size();
			UserService drawer = roomUsers.get(drawerIndex);
			nextDrawerIndex++;
			
			//랜덤 제시어 선택
			String keyword = keywords[(int)(Math.random() * keywords.length)]; 
			this.currentKeyword = keyword; 
			
			//역할에 따른 프로토콜 처리
			for (UserService user : roomUsers) {
				if (user == drawer) {
					//출제자에게는 정답 단어를 전송
					user.WriteOne("START::drawer," + keyword);
					user.isDrawer = true; 
				} else {
					//맞히는 사람에게는 단어의 글자 수를 전송
					user.WriteOne("START::guesser," + keyword.length());
					user.isDrawer = false; 
				}
			}
			System.out.println("[" + roomName + "] 새 라운드 시작! (출제자: " + drawer.UserName + ", 단어: " + keyword + ")");
		}
		
		//유저 입장 및 방장 권한 처리
		public void enterUser(UserService user) {
			roomUsers.add(user);
			
			//방이 비어있다가 첫 유저가 들어오면 방장 권한 부여
			if (roomUsers.size() == 1) {
				host = user;
				user.WriteOne("ROLE::HOST");
				user.WriteOne("CHAT::[시스템] 당신은 방장입니다. 게임 시작 버튼을 눌러 게임을 시작하세요.");
			} else {
				user.WriteOne("ROLE::GUEST");
			}
			System.out.println(roomName + "(참가자: " + roomUsers.size() + "명)");
		}
		
		//유저 퇴장 및 예외 상황 처리
		//유저 이탈 시 리스트에서 제거하고, 방장이 나갔을 경우 권한 다음 사람에게 주는 로직 수행
		public void removeUser(UserService user) {
			roomUsers.removeElement(user);
			WriteAll("CHAT::" + user.UserName + "님이 퇴장했습니다.");
			WriteAll("LOGOUT::" + user.UserName); //클라이언트 UI 갱신용
			
			//방장이 나갔을 경우, 아직 방에 사람이 남아있다면
			if (user == host && !roomUsers.isEmpty()) {
				host = roomUsers.get(0); // 리스트의 첫 번째 사람에게 방장으로 지정
				host.WriteOne("ROLE::HOST");
				WriteAll("CHAT::[시스템] " + host.UserName + "님이 새로운 방장이 되었습니다.");
			}
			
			//게임 도중 유저가 나가서 게임 불가능 상태 (2명 미만)가 되면 게임 강제 종료 
			if (!currentKeyword.isEmpty() && roomUsers.size() < 2) {
				currentKeyword = "";
				WriteAll("CHAT::[시스템] 인원 부족으로 게임이 종료되었습니다.");
				WriteAll("CLEAR::");
			}
		}
	}

	//클라이언트 1명과 서버 간의 1:1 통신 담당
	class UserService extends Thread {
		private BufferedReader in;
		private PrintWriter out;
		private Socket client_socket; 
		private Room currentRoom = null; 
		private String UserName = ""; 
		private boolean isDrawer = false; //현재 라운드에서 출제자인지 여부
		private int userScore = 0;
		
		// [추가] 서버에서도 점수 관리
		private int score = 0;
		
		public UserService(Socket client_socket) {
			this.client_socket = client_socket;
			try {
				in = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));
				out = new PrintWriter(client_socket.getOutputStream(), true);
			} catch(Exception e) { }
		}
		
		//방 입장 처리
		//방 객체에 자신을 등록하고 브로드캐스트
		public void joinRoom(String roomName) {
			//방이 없으면 생성하고, 있으면 가져옴
			Room targetRoom = Server.this.createRoom(roomName);
			this.currentRoom = targetRoom;
			this.score = 0; // 방 입장 시 점수 초기화
			targetRoom.enterUser(this);
			broadcastJoin();
		}
		
		//초기 접속 프로토콜
		public boolean initUser() {
			try {
				String currentRooms = Server.this.getRoomListString();
				//방 목록 전송
				if(!currentRooms.isEmpty()) {
					WriteOne("ROOM_LIST::" + currentRooms);
				}
				
				//닉네임 수신
				String line1 = in.readLine();
				if (line1 == null || !line1.startsWith("LOGIN::")) return false;
				this.UserName = line1.substring("LOGIN::".length()).trim();
				return true;
			} catch(Exception e) { return false; }
		}
		
		//방 입장 사실을 알리고 기존 유저 목록을 받아옴
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
		
		//클라이언트로부터 오는 모든 메시지를 분석하여 처리
		public void run() {
			try {
				String msg;
				while((msg = in.readLine()) != null) {
					msg = msg.trim();
					
					//로비에서 이루어지는 채팅
					if (msg.startsWith("LOBBY_CHAT::")) {
						String chatMsg = msg.substring("LOBBY_CHAT::".length());
						Server.this.broadcastAll("LOBBY_CHAT::" + this.UserName + ": " + chatMsg);
						continue;
					}
					
					//방 생성 요청
					if (msg.startsWith("CREATE_ROOM::")) {
						String roomName = msg.substring("CREATE_ROOM::".length()).trim();
						Server.this.createRoom(roomName);
						continue;
					}
					//방 입장 요청
					if (msg.startsWith("JOIN_ROOM::")) {
					    if (currentRoom == null) joinRoom(msg.substring("JOIN_ROOM::".length()).trim());
					    continue;
					}
					
					//아래 로직은 방에 입장한 상태여야 함
					if (currentRoom == null) continue;
					
					//정답 제출 처리
					if (msg.startsWith("ANSWER::")) {
		                String userAnswer = msg.substring(8).trim();
		                
		                //정답 판별
		                if (!currentRoom.currentKeyword.isEmpty() && userAnswer.equalsIgnoreCase(currentRoom.currentKeyword)) {
		                    
		                    //출제자가 정답을 입력하는 경우 방지
		                    if (this.isDrawer) {
		                        this.WriteOne("CHAT::[시스템] 출제자는 정답을 입력할 수 없습니다.");
		                    } 
		                    //참여자가 정답을 맞힌 경우
		                    else {
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
		                    //정답이 아니면 일반 채팅으로 간주하여 방 전체에 전송
		                    currentRoom.WriteAll("CHAT::" + this.UserName + ": " + userAnswer);
		                }
		            }
					//그림 데이터 중계
					else if (msg.startsWith("DRAW::") || msg.startsWith("CLEAR::") || msg.startsWith("RGB::")) {
						//출제자 그림 권한 검사
						//출제자가 아닌 사람이 보내면 무시
						if (isDrawer) currentRoom.WriteAll(msg);
					}
					//게임방 내부 채팅
					else if (msg.startsWith("CHAT::")) {
						currentRoom.WriteAll(msg); 
					}
					//게임 시작 요청
					else if (msg.startsWith("GAME_START::")) {
						//방장 권한 확인
						if (currentRoom.host == this) {
							currentRoom.startGame();
						} else {
							WriteOne("CHAT::[오류] 방장만 게임을 시작할 수 있습니다.");
						}
					}
				} 
			} catch (IOException e) { 
				//클라이언트가 강제로 종료하거나 네트워크 오류 발생 시
            } finally {
            	//전체 유저 목록에서 제거
            	Server.this.allUsers.remove(this);
            	
            	//참여 중이던 방에서 퇴장 처리
                if (currentRoom != null) currentRoom.removeUser(this);
                
                //소켓 자원 반환
                try { if(client_socket != null) client_socket.close(); } catch (Exception e) {} 
            }
		}
	}
	
	public static void main(String[] args) {
		new Server(); 
	}
}