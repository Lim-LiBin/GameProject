import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;

public class Server {
	private ServerSocket socket = null; //클라이언트 접속 기다리는 서버 소켓
	private Socket client_socket = null; //AcceptServer가 새로 접속한 클라이언트를 임시로 담을 객체
	private Vector<UserService> UserVec = new Vector<>(); //현재 접속 중인 모든 클라이언트를 관리하는 리스트
	
	//서버 생성자
	//서버가 시작되면 즉시 호출해 소켓 열고, AcceptServer 스레드를 시작하여 클라이언트 접속을 기다린다.
	public Server() {
		try {
			setupConnection(); //서버 소켓 설정
		} catch(IOException e) {
			handleError(e.getMessage());
		}
		
		//클라이언트 접속을 무한정 기다리는 스레드 생성 후 시작
		AcceptServer accept_server = new AcceptServer();
		accept_server.start();
	}
	
	public void setupConnection() throws IOException {
		socket = new ServerSocket(9999); //9999 포트에서 서버 소켓 생성
	}
	
	//오류 발생 시 메시지 출력 후 프로그램 종료
	private static void handleError(String string) {
		System.out.println(string);
		System.exit(1);
	}
	
	//클라이언트 접속 담당하는 전용 스레드
	private class AcceptServer extends Thread {
		@Override
		public void run() {
			//서버가 종료될 때까지 무한 루프 돌며 클라이언트 접속 기다림
			while(true) {
				try {
					System.out.println("Waiting Clients");
					client_socket = socket.accept(); //새 클라이언트 접속 시 Socket 객체 반환
					
					System.out.println("새로운 참가자 from " + client_socket);
					
					//접속한 클라이언트 담당할 UserService 스레드 생성
					UserService new_user = new UserService(client_socket);
					//전체 사용자 리스트에 새로 접속한 클라이언트 추가
					UserVec.add(new_user);
					System.out.println("사용자 입장. 현재 참가자 수 " + UserVec.size());
					//UserService 스레드 시작하여 클라이언트와의 통신 시작
					new_user.start();
				} catch(IOException e) {
					System.out.println("AcceptServer 에러 발생");
				}
			}
		}
	}
	
	//접속한 클라이언트 한 명을 담당하는 스레드
	//각 클라이언트의 메시지 수신 및 송신을 독립적으로 처리
	class UserService extends Thread {
		private DataInputStream dis;
		private DataOutputStream dos;
		
		private Socket client_socket; //해당 스레드가 담당하는 클라이언트의 소켓
		private Vector<UserService> user_vc; //모든 유저 목록
		private String UserName = ""; //이 클라이언트의 유저 이름
		
		//클라이언트 소켓을 받아 스트림 설정함
		//가장 먼저 클라이언트로부터 LOGIN:[이름] 프로토콜을 받아 사용자 이름 설정
		public UserService(Socket client_socket) {
			this.client_socket = client_socket;
			this.user_vc = UserVec; //서버의 UserVec 가리키도록 설정
			
			try {
				//클라이언트와 통신하기 위한 입출력 스트림 생성
				dis = new DataInputStream(client_socket.getInputStream());
				dos = new DataOutputStream(client_socket.getOutputStream());
				
				//클라이언트가 접속하자마자 보내는 첫 번째 메시지(LOGIN::[이름])를 읽음
				String line1 = dis.readUTF();
				String[] msg = line1.split("::"); //"::"를 기준으로 문자열 자름
				UserName = msg[1].trim(); //1번 인덱스가 사용자 이름
				
				System.out.println("새로운 참가자 " + UserName + "입장");
				
				//입장 메시지를 CHAT::[메시지] 프로토콜에 맞게 모든 클라이언트에게 전달
				WriteAll("CHAT::" + UserName + "님이 입장했습니다.\n");
				
				//환영 메시지를 CHAT::[메시지] 프로토콜에 맞게 이 클라이언트에게만 전달
				WriteOne("CHAT::게임에 참가해주셔서 감사합니다.\n");
			} catch(Exception e) {
				System.out.println("userService error");
			}
		}
		
		//이 클라이언트에게만 메시지 전달
		public void WriteOne(String msg) {
			try {
				dos.writeUTF(msg);
 			} catch(IOException e) {
 				//메시지 전송 실패 == 클라이언트가 접속을 끊었을 가능성 높음
 				System.out.println("dos.write() error");
 				
 				try { //스트림과 소켓을 닫아 리소스 정리
 					dos.close();
 					dis.close();
 					client_socket.close();
 				} catch(IOException e1) {
 					e1.printStackTrace();
 				}
 				
 				//전체 사용자 리스트에서 '나'를 제거
 				UserVec.removeElement(this);
 				System.out.println("사용자 퇴장. 현재 참가자 수 " + UserVec.size());
 			}
		}
		
		//Broadcast - 모든 클라이언트에게 메시지 전송 (자신 포함)
		public void WriteAll(String str) {
			//user_vc 리스트에 있는 모든 UserService 객체 순회
			for (int i = 0; i < user_vc.size(); i++) {
				UserService user = user_vc.get(i); //i번째 유저 가져와서
				user.WriteOne(str); //해당 유저에게 WriteOne() 호출하여 메시지 보냄
			}
		}
		
		//클라이언트로부터 메시지를 계속 수신하고
		//받은 메시지를 모든 클라이언트에게 중계함
		public void run() {
			while(true) {
				try {
					String msg = dis.readUTF(); //메시지 수신
					msg = msg.trim();
					System.out.println(msg); //서버 콘솔에 수신한 메시지 출력 (서버 GUI를 만들지..)
					
					//받은 메시지를 CHAT::이든 DRAW::이든 구분 없이
					//즉시 모든 클라이언트에게 보냄
					WriteAll(msg + "\n");
				} catch (IOException e) { //클라이언트가 강제 종료되거나 연결이 끊기면 IOException 발생
                    System.out.println("dis.readUTF() error");
                    try { //스트림과 소켓 닫음
                        dos.close();
                        dis.close();
                        client_socket.close();
                        
                        //전체 사용자 리스트에서 '나'를 제거
                        UserVec.removeElement(this); // 에러가 난 현재 객체를 벡터에서 지운다
                        System.out.println("사용자 퇴장. 남은 참가자 수 " + UserVec.size());
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
