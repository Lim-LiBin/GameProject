import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

//게임 대기실(로비) 화면
//로그인 성공 후 진입하는 화면
//실시간 방 목록 확인, 로비 채팅, 방 생성/참가 기능 수행
public class CreateRoomScreen extends JFrame {

	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	
	//방 목록 리스트 컴포넌트
	private JList<String> roomList;
	//방 목록 데이터를 동적으로 관리하기 위한 모델 객체
	private DefaultListModel<String> listModel;
	private Font baseFont; // 폰트 변수
	
	//채팅 관련 UI 컴포넌트
	private JTextArea chatDisplay; //서버로부터 수신한 채팅 로그를 보여주는 영역
	private JTextField chatInput; //사용자가 메시지를 입력하는 영역
	private JButton chatSendBtn; //전송 버튼
	
	//소켓 통신을 위한 입출력 스트림
	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	
	//현재 로그인한 사용자 ID
	private String nickname;

	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					CreateRoomScreen frame = new CreateRoomScreen("테스트");
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	//UI 구성요소 초기화 및 서버 연결 프로세스 시작
	public CreateRoomScreen(String nickname) {
		this.nickname = nickname;
		setTitle("캐치마인드 - 로비");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 800, 600);
		
		//외부 폰트 로드
		try {
			File fontFile = new File("Jalnan2TTF.ttf");
			baseFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
			GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			ge.registerFont(baseFont);
		} catch(IOException | FontFormatException e) {
			//실패 시 기본 폰트로 대체
			System.out.println("폰트 로드 실패. 기본 폰트 사용.");
	        baseFont = new Font("맑은 고딕", Font.BOLD, 12);
		}

		//전체 패널 설정
		contentPane = new JPanel();
		contentPane.setBackground(new Color(230, 240, 255)); // 배경색 설정
		contentPane.setBorder(new EmptyBorder(10, 10, 10, 10)); //윈도우 테두리와의 여백
		contentPane.setLayout(new BorderLayout(10, 10)); //컴포넌트 간 간격 확보
		setContentPane(contentPane);
		
		//EAST - 사용자 정보 및 기능 버튼 패널
		JPanel eastPanel = new JPanel(new GridBagLayout()); 
		eastPanel.setPreferredSize(new Dimension(200, 0));
		eastPanel.setOpaque(false); 
		
		//내부 컴포넌트를 수직으로 쌓기 위해 BoxLayout 사용
		JPanel innerPanel = new JPanel();
		innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.Y_AXIS));
		innerPanel.setOpaque(false);
		
		// 버튼 그룹 패널 (GridLayout 2행 1열)
		JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10));
		buttonPanel.setOpaque(false);
		buttonPanel.setPreferredSize(new Dimension(180, 90)); 
		buttonPanel.setMaximumSize(new Dimension(180, 90));
		buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT); // BoxLayout 내 중앙 정렬

		//방 만들기 버튼
		JButton createRoombtn = new JButton("방 만들기");
		createRoombtn.setFont(baseFont.deriveFont(18f));
		createRoombtn.setBackground(new Color(0, 51, 153));
		createRoombtn.setForeground(Color.WHITE);
		createRoombtn.setFocusPainted(false);
		
		//참가하기 버튼
		JButton joinRoombtn = new JButton("참가하기");
		joinRoombtn.setFont(baseFont.deriveFont(18f));
		joinRoombtn.setBackground(new Color(0, 51, 153));
		joinRoombtn.setForeground(Color.WHITE);
		joinRoombtn.setFocusPainted(false);
		
		buttonPanel.add(createRoombtn);
		buttonPanel.add(joinRoombtn);
		
		//환영 메시지 label
		JLabel welcomeLabel = new JLabel(nickname + "님 환영합니다!");
		welcomeLabel.setFont(baseFont.deriveFont(20f));
		welcomeLabel.setForeground(new Color(0, 51, 153));
		welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT); 
		
		//캐릭터 이미지 표시
		JLabel imageLabel = new JLabel();
		String imageName = "image.png"; 
		ImageIcon icon = new ImageIcon(imageName);
		
		if (icon.getIconWidth() > 0) {
			Image img = icon.getImage();
			//이미지 깨짐 방지를 위해 SCALE_SMOOTH 사용
			Image resizedImg = img.getScaledInstance(180, -1, Image.SCALE_SMOOTH);
			icon = new ImageIcon(resizedImg);
			imageLabel.setIcon(icon);
		} else {
			imageLabel.setText("이미지 없음");
		}
		imageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

		//컴포넌트 조립
		//Box.createVerticalStrut으로 요소 간 간격 조정
		innerPanel.add(buttonPanel);                
		innerPanel.add(Box.createVerticalStrut(30));  
		innerPanel.add(welcomeLabel);                 
		innerPanel.add(Box.createVerticalStrut(10));  
		innerPanel.add(imageLabel);         
		
		//eastPanel에 추가
		eastPanel.add(innerPanel);

		//CENTER - 방 목록, 채팅창
		//상단 - 방 목록 패널
		JPanel roomListPanel = new JPanel(new BorderLayout());
		roomListPanel.setOpaque(false);

		TitledBorder roomBorder = new TitledBorder("방 목록");
		roomBorder.setTitleFont(baseFont.deriveFont(14f));
		roomListPanel.setBorder(roomBorder);
		
		//listModel 초기화
		//여기에 데이터를 추가하면 JList에 자동으로 반영됨
		listModel = new DefaultListModel<>();
		roomList = new JList<>(listModel);
		
		roomList.setFont(baseFont.deriveFont(16f));
		roomList.setFixedCellHeight(40); //행 높이 고정
		
		roomList.setBackground(Color.WHITE);
		roomList.setSelectionBackground(new Color(200, 220, 255)); //선택된 항목 색상 설정
		roomList.setBorder(new LineBorder(new Color(180, 200, 230), 1));
		
		//JScrollPane을 통해 스크롤 기능 추가
		roomListPanel.add(new JScrollPane(roomList), BorderLayout.CENTER);
		
		//하단 - 채팅 패널
		JPanel chatPanel = new JPanel(new BorderLayout());
		chatPanel.setOpaque(false);
		TitledBorder chatBorder = new TitledBorder("채팅");
		chatBorder.setTitleFont(baseFont.deriveFont(14f));
		chatPanel.setBorder(chatBorder);
		
		//채팅 내역 표시 영역
		chatDisplay = new JTextArea();
		chatDisplay.setEditable(false); //수정 불가 설정
		chatDisplay.setFont(baseFont.deriveFont(14f));
		
		//입력 영역 (채팅 입력창 + 전송 버튼)
		JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
		inputPanel.setOpaque(false);
		
		//채팅 입력창 설정
		chatInput = new JTextField();
		chatInput.setFont(baseFont.deriveFont(14f));
		
		//전송 버튼 설정
		JButton chatSendBtn = new JButton("전송");
		chatSendBtn.setFont(baseFont.deriveFont(14f));
		chatSendBtn.setBackground(new Color(0, 51, 153));
		chatSendBtn.setForeground(Color.WHITE);
		
		//입력 영역에 입력창, 전송 버튼 추가
		inputPanel.add(chatInput, BorderLayout.CENTER);
		inputPanel.add(chatSendBtn, BorderLayout.EAST);
		
		//채팅 패널에 채팅 내역 표시 영역, 입력 영역 추가
		chatPanel.add(new JScrollPane(chatDisplay), BorderLayout.CENTER);
		chatPanel.add(inputPanel, BorderLayout.SOUTH);
		
		//상하 분할 패널 구성하기 위해 SplitPane 설정
		//사용자가 경계선을 드래그하여 방 목록과 채팅창의 비율을 조절할 수 있게 함
		JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		verticalSplit.setTopComponent(roomListPanel);
		verticalSplit.setBottomComponent(chatPanel);
		verticalSplit.setDividerSize(5); // 구분선 두께
		verticalSplit.setResizeWeight(0.5); // 초기 비율 50:50

		//UI가 모두 그려진 직후 구분선 위치를 강제로 50%로 맞추기 위해 invokeLater 사용
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				verticalSplit.setDividerLocation(0.5); 
			}
		});
		
		//SplitPane 배경 투명화 처리용 래퍼 패널
		JPanel splitContainer = new JPanel(new BorderLayout());
		splitContainer.setOpaque(false);
		splitContainer.add(verticalSplit);
		
		//메인 패널에 배치
		contentPane.add(eastPanel, BorderLayout.EAST);
		contentPane.add(splitContainer, BorderLayout.CENTER);
		
		//이벤트 리스너 등록
		//채팅 전송 이벤트 리스너 (엔터키, 버튼 클릭 공용)
		ActionListener sendAction = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String msg = chatInput.getText().trim();
				//빈 메시지가 아니고, 서버 연결이 유효할 때만 전송
				if (!msg.isEmpty()&& out != null) {
					//서버로 로비 채팅 프로토콜 전송
					out.println("LOBBY_CHAT::" + msg);
					chatInput.setText(""); //입력창 초기화
				}
			}
		};
		chatInput.addActionListener(sendAction);
		chatSendBtn.addActionListener(sendAction);
		
		//방 참가하기 버튼 이벤트 리스너
		joinRoombtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//리스트에서 현재 선택된 방 이름 가져오기
				String selectedRoom = roomList.getSelectedValue();
					
				//선택된 방이 없다면
				if (selectedRoom == null) {
					JOptionPane.showMessageDialog(CreateRoomScreen.this, "참여할 방을 선택하세요.", "오류", JOptionPane.ERROR_MESSAGE);
					return;
				}
				
				if (out != null) {
					out.println("CHECK_ROOM::" + selectedRoom);
					return;
				}
			}
		});
				
		//방 만들기 버튼 이벤트 리스너
			createRoombtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String newRoomName = JOptionPane.showInputDialog("새 방 이름을 입력하세요:");
				if (newRoomName == null || newRoomName.trim().isEmpty()) {
					return;
				}
						
				//서버에 방 생성 요청 프로토콜 전송
				if (out != null) {
					out.println("CREATE_ROOM::" + newRoomName);
				}
						
				//방 만든 사람은 즉시 해당 방으로 입장 처리
				enterGameRoom(newRoomName);
			}
		});
				
		//초기화 완료 후 서버 연결 시도
		connectToLobbyServer();
	}
			
	//서버 연결 메서드
	private void connectToLobbyServer() {
		new Thread(new Runnable() {
			public void run() {
				try {
					socket = new Socket("localhost", 9999); //서버 접속 시도
					out = new PrintWriter(socket.getOutputStream(), true);
					in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					
					//로그인 프로토콜 전송
					out.println("LOGIN::" + nickname);
					
					//서버 메시지 수신 루프
					String msg;
					while((msg = in.readLine()) != null) {
						final String message = msg;
						
						//수신된 메시지로 UI를 갱신해야 하므로 invokeLater 사용
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								processLobbyMessage(message);
							}
						});
					}
				} catch (Exception e) {
					System.out.println("서버와의 연결이 종료되었습니다.");
					JOptionPane.showMessageDialog(null, "서버와 연결이 끊어졌습니다.");
					System.exit(0);
				}
			}
		}).start();
	}
			
	//프로토콜 처리 메서드
	//서버로부터 받은 메시지를 분석하여 알맞은 동작 수행
	private void processLobbyMessage(String msg) {
		//새로운 방 생성 알림
		if (msg.startsWith("NEW_ROOM::")) {
			String roomName = msg.substring("NEW_ROOM::".length());
			
			//현재 리스트에 없는 방 이름일 경우에만 추가
			if (!listModel.contains(roomName)) {
				listModel.addElement(roomName);
			}
		} 
		//접속 시 현재 개설된 전체 방 목록 수신
		else if (msg.startsWith("ROOM_LIST::")) {
			//콤마(,)로 구분된 방 이름 문자열을 파싱하여 배열로 변환
			String[] rooms = msg.substring("ROOM_LIST::".length()).split(",");
			listModel.clear(); //기존 목록 초기화
			
			for(String r : rooms) {
				//빈 문자열이 아니고, 중복되지 않는 방만 추가
				if(!r.trim().isEmpty() && !listModel.contains(r)) {
					listModel.addElement(r);
				}
			}
		}
		else if (msg.startsWith("JOIN_OK::")) {
			String roomName = msg.substring("JOIN_OK::".length());
			enterGameRoom(roomName);
		}
		else if (msg.startsWith("JOIN_FAIL::")) {
			String reason = msg.substring("JOIN_FAIL::".length());
			JOptionPane.showMessageDialog(this, reason, "입장 불가", JOptionPane.WARNING_MESSAGE);
		}
		//로비 전체 채팅 메시지 수신
		else if (msg.startsWith("LOBBY_CHAT::")) {
			String chatMsg = msg.substring("LOBBY_CHAT::".length());
			
			//채팅창에 메시지 추가
			chatDisplay.append(chatMsg + "\n");
			
			//스크롤을 자동으로 최하단으로 이동시켜 최신 메시지 표시
			chatDisplay.setCaretPosition(chatDisplay.getDocument().getLength());
		}
	}
		
	//게임방 입장 처리
	//현재 로비 화면을 닫고, 실제 게임 화면으로 이동
	private void enterGameRoom(String roomName) {
		GameRoomScreen gameRoom = new GameRoomScreen(nickname, roomName, "localhost", 9999);
		gameRoom.setVisible(true);
		dispose(); //현재 로비 창 리소스 해제
	}
}