import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;

public class Server {
	private ServerSocket socket = null; 
	private Socket client_socket = null;
	private Vector<UserService> UserVec = new Vector<>(); 
	
	// [추가] 임시 단어장
	private String[] keywords = {"기린", "사과", "컴퓨터", "비행기", "자전거"};
	
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
	
	// [새 메서드] 게임 시작 로직
	public void checkGameStart() {
		// [수정] 2명 이상이고, 현재 게임 중이 아닐 때 (지금은 항상 시작)
		if (UserVec.size() >= 2) { 
			System.out.println("[게임 시작] 2명 이상 접속하여 게임을 시작합니다.");
			
			// [TODO] 실제로는 랜덤하게 뽑아야 함
			UserService drawer = UserVec.get(0); 
			String keyword = keywords[(int)(Math.random() * keywords.length)]; // 랜덤 단어
			
			for (UserService user : UserVec) {
				if (user == drawer) {
					System.out.println("출제자(" + user.UserName + "): " + keyword);
					user.WriteOne("START::drawer," + keyword);
				} else {
					System.out.println("정답자(" + user.UserName + "): " + keyword.length() + "글자");
					user.WriteOne("START::guesser," + keyword.length());
				}
			}
		} else {
			System.out.println("[게임 대기] 1명만 접속 중입니다.");
		}
	}
	
	// [수정] 클라이언트 접속 담당하는 전용 스레드
	private class AcceptServer extends Thread {
		@Override
		public void run() {
			while(true) {
				try {
					System.out.println("[서버] 클라이언트 접속 대기 중...");
					client_socket = socket.accept(); 
					
					System.out.println("새로운 참가자 from " + client_socket);
					
					// [수정] UserService 생성
					UserService new_user = new UserService(client_socket);

					// [수정] 유저 이름 먼저 받고, 리스트에 추가 (타이밍 문제 해결)
					if (new_user.initUser()) { // initUser()가 이름을 성공적으로 읽으면 true 반환
						UserVec.add(new_user); // [!!] 리스트에 먼저 추가
						new_user.start(); // 스레드 시작
						
						// [!!] 환영 메시지 및 유저 목록 전송 (initUser에서 이동)
						new_user.broadcastJoin(); 
						
						System.out.println("[접속] 사용자 입장. (현재 참가자 수 " + UserVec.size() + "명)");
						
						// [추가] 게임 시작 로직 호출
						checkGameStart();

					} else {
						// 이름 읽기 실패 (연결 끊김 등)
						System.out.println("[접속 실패] 이름 읽기 실패");
						client_socket.close();
					}

				} catch(IOException e) {
					System.out.println("[오류] AcceptServer 에러 발생");
				}
			}
		}
	}
	
	class UserService extends Thread {
		private BufferedReader in;
		private PrintWriter out;
		
		private Socket client_socket; 
		private Vector<UserService> user_vc;
		private String UserName = ""; 
		
		// [수정] 생성자: 스트림만 설정
		public UserService(Socket client_socket) {
			this.client_socket = client_socket;
			this.user_vc = UserVec; 
			
			try {
				in = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));
				out = new PrintWriter(client_socket.getOutputStream(), true);
			} catch(Exception e) {
				System.out.println("[오류] userService 스트림 생성 error");
			}
		}
		
		// [새 메서드] 유저 이름 초기화
		public boolean initUser() {
			try {
				String line1 = in.readLine(); // LOGIN::[이름]
				if (line1 == null) return false;
				
				String[] msg = line1.split("::"); 
				this.UserName = msg[1].trim();
				System.out.println("[접속] " + UserName + " 이름 확인");
				return true;
			} catch(Exception e) {
				System.out.println("[오류] initUser error");
				return false;
			}
		}
		
		// [새 메서드] 유저 목록 전송 및 입장 메시지 브로드캐스트
		public void broadcastJoin() {
			// [수정] 기존 사용자들에게 새 사용자(나)의 접속을 알리고
			//       새 사용자(나)에게 기존 사용자들의 정보를 보냄
			for (UserService user : user_vc) { 
				if (user == this) {
					// 1. 나에게 "환영" 메시지
					user.WriteOne("CHAT::게임에 참가해주셔서 감사합니다.");
				} else {
					// 2. 기존 유저에게: "새 유저(나)가 로그인했다" (참가자 목록 갱신용)
					user.WriteOne("LOGIN::" + this.UserName);
					
					// 3. 새 유저(나)에게: "이런 기존 유저가 있다" (참가자 목록 갱신용)
					this.WriteOne("LOGIN::" + user.UserName);
				}
			}
			// [수정] WriteAll은 이제 '나'를 포함한 모두에게 전송
			WriteAll("CHAT::" + this.UserName + "님이 입장했습니다.");
		}
		
		
		//이 클라이언트에게만 메시지 전달
		public void WriteOne(String msg) {
			try {
				out.println(msg);
 			} catch(Exception e) { 
 				System.out.println("WriteOne() error: " + e.getMessage());
 				// (에러 처리 로직... 현재는 생략)
 			}
		}
		
		//Broadcast - 모든 클라이언트에게 메시지 전송 (자신 포함)
		public void WriteAll(String str) {
			System.out.println("[메시지 발송] " + str);
			for (int i = 0; i < user_vc.size(); i++) {
				UserService user = user_vc.get(i); 
				user.WriteOne(str); 
			}
		}
		
		//클라이언트로부터 메시지를 계속 수신
		public void run() {
			while(true) {
				try {
					String msg = in.readLine();
					
                    if (msg == null) {
                        System.out.println("[접속 종료] " + UserName + " 님이 연결을 끊었습니다.");
                        throw new IOException("Client disconnected"); 
                    }
					
					msg = msg.trim();
					System.out.println("[메시지 수신] " + UserName + ": " + msg); 
					
					if (msg.startsWith("CHAT::") || msg.startsWith("DRAW::") || msg.startsWith("CLEAR::")) {
						WriteAll(msg);
					}
					// [TODO] 향후 ANSWER:: 등 다른 프로토콜 처리 로직 추가
					
				} catch (IOException e) { 
                    System.out.println("[오류] " + UserName + " 스레드 오류: " + e.getMessage());
                    try { 
                        out.close(); 
 						in.close(); 
                        client_socket.close();
                        
                        UserVec.removeElement(this); 
                        System.out.println("[퇴장] 사용자 퇴장. (남은 참가자 수 " + UserVec.size() + "명)");
                        
                        // [추가] 퇴장 메시지 전송
                        WriteAll("CHAT::" + UserName + "님이 퇴장했습니다.");
                        // [TODO] 유저가 나가면 UserStatusPanel 제거하는 LOGOUT:: 프로토콜 전송 필요
                        
                        break; 
                    } catch (Exception ee) {
                        break; 
                    } 
                }
			}
		}
	}
	
	public static void main(String[] args) {
		new Server(); //서버 실행
	}
}