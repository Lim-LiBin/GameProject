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

public class CreateRoomScreen extends JFrame {

	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	private JList<String> roomList;
	private DefaultListModel<String> listModel; //방 목록 데이터를 관리할 모델
	private Font baseFont; // 폰트 변수
	
	//소켓 통신 변수
	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	private String nickname;

	/**
	 * Launch the application.
	 */
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

	/**
	 * Create the frame.
	 */
	public CreateRoomScreen(String nickname) {
		this.nickname = nickname;
		setTitle("캐치마인드 - 로비");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 800, 600);
		
		//폰트 로드
		try {
			File fontFile = new File("Jalnan2TTF.ttf");
			baseFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
			GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			ge.registerFont(baseFont);
		} catch(IOException | FontFormatException e) {
			System.out.println("폰트 로드 실패. 기본 폰트 사용.");
	        baseFont = new Font("맑은 고딕", Font.BOLD, 12);
		}

		contentPane = new JPanel();
		contentPane.setBackground(new Color(230, 240, 255)); // 배경색 설정
		contentPane.setBorder(new EmptyBorder(10, 10, 10, 10)); // 여백 약간 늘림
		contentPane.setLayout(new BorderLayout(10, 10)); // 간격 10으로 조정
		setContentPane(contentPane);
		
		//오른쪽 패널 (버튼 2개 + 환영 메시지)
		JPanel eastPanel = new JPanel(new GridBagLayout()); 
		eastPanel.setPreferredSize(new Dimension(200, 0));
		eastPanel.setOpaque(false); 
		
		JPanel innerPanel = new JPanel();
		innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.Y_AXIS));
		innerPanel.setOpaque(false);
		
		// 버튼 패널 설정
		JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10));
		buttonPanel.setOpaque(false);
		
		//버튼 패널 사이즈 (200, 140 vs 180, 90)
		buttonPanel.setPreferredSize(new Dimension(180, 90)); 
		buttonPanel.setMaximumSize(new Dimension(180, 90));
		buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT); // BoxLayout 내 가운데 정렬

		JButton createRoombtn = new JButton("방 만들기");
		createRoombtn.setFont(baseFont.deriveFont(18f));
		createRoombtn.setBackground(new Color(0, 51, 153));
		createRoombtn.setForeground(Color.WHITE);
		createRoombtn.setFocusPainted(false);
		
		JButton joinRoombtn = new JButton("참가하기");
		joinRoombtn.setFont(baseFont.deriveFont(18f));
		joinRoombtn.setBackground(new Color(0, 51, 153));
		joinRoombtn.setForeground(Color.WHITE);
		joinRoombtn.setFocusPainted(false);
		
		buttonPanel.add(createRoombtn);
		buttonPanel.add(joinRoombtn);
		
		//환영 메시지 설정
		JLabel welcomeLabel = new JLabel(nickname + "님 환영합니다!");
		welcomeLabel.setFont(baseFont.deriveFont(20f));
		welcomeLabel.setForeground(new Color(0, 51, 153));
		welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT); 
		
		//이미지 설정
		JLabel imageLabel = new JLabel();
		String imageName = "image.png"; 
		ImageIcon icon = new ImageIcon(imageName);
		
		if (icon.getIconWidth() > 0) {
			Image img = icon.getImage();
			Image resizedImg = img.getScaledInstance(180, -1, Image.SCALE_SMOOTH);
			icon = new ImageIcon(resizedImg);
			imageLabel.setIcon(icon);
		} else {
			imageLabel.setText("이미지 없음");
		}
		imageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

		//innerPanel에 순서대로 조립
		innerPanel.add(buttonPanel);                  // 버튼 (작은 크기 유지)
		innerPanel.add(Box.createVerticalStrut(30));  // 간격
		innerPanel.add(welcomeLabel);                 // 텍스트
		innerPanel.add(Box.createVerticalStrut(10));  // 간격
		innerPanel.add(imageLabel);                   // 이미지
		
		//최종적으로 eastPanel 중앙에 innerPanel 배치
		eastPanel.add(innerPanel);

		//왼쪽 영역 (방 목록 + 채팅 창)
		//방 목록 패널
		JPanel roomListPanel = new JPanel(new BorderLayout());
		roomListPanel.setOpaque(false);

		TitledBorder roomBorder = new TitledBorder("방 목록");
		roomBorder.setTitleFont(baseFont.deriveFont(14f));
		roomListPanel.setBorder(roomBorder);
		
		listModel = new DefaultListModel<>();
		roomList = new JList<>(listModel);
		
		roomList.setFont(baseFont.deriveFont(16f));
		roomList.setFixedCellHeight(40);
		
		roomList.setBackground(Color.WHITE);
		roomList.setSelectionBackground(new Color(200, 220, 255));
		roomList.setBorder(new LineBorder(new Color(180, 200, 230), 1));
		
		roomListPanel.add(new JScrollPane(roomList), BorderLayout.CENTER);
		
		//채팅 패널
		JPanel chatPanel = new JPanel(new BorderLayout());
		chatPanel.setOpaque(false);
		
		TitledBorder chatBorder = new TitledBorder("채팅");
		chatBorder.setTitleFont(baseFont.deriveFont(14f));
		chatPanel.setBorder(chatBorder);
		
		JTextArea chatDisplay = new JTextArea();
		chatDisplay.setEditable(false);
		chatDisplay.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
		
		// 입력창 부분
		JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
		inputPanel.setOpaque(false);
		
		JTextField sender = new JTextField();
		sender.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
		
		JButton sender_btn = new JButton("전송");
		sender_btn.setFont(baseFont.deriveFont(14f));
		sender_btn.setBackground(new Color(0, 51, 153));
		sender_btn.setForeground(Color.WHITE);
		
		inputPanel.add(sender, BorderLayout.CENTER);
		inputPanel.add(sender_btn, BorderLayout.EAST);
		
		chatPanel.add(new JScrollPane(chatDisplay), BorderLayout.CENTER);
		chatPanel.add(inputPanel, BorderLayout.SOUTH);
		
		// 스플릿 패널 설정
		JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		verticalSplit.setTopComponent(roomListPanel);
		verticalSplit.setBottomComponent(chatPanel);
		verticalSplit.setDividerSize(5); // 구분선 두께
		verticalSplit.setResizeWeight(0.5); // 위쪽(방 목록) 비율

		//화면이 다 그려진 뒤에 구분선 위치를 50%로 강제 설정
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				verticalSplit.setDividerLocation(0.5); 
			}
		});
		
		// 스플릿 패널 배경 투명화
		JPanel splitContainer = new JPanel(new BorderLayout());
		splitContainer.setOpaque(false);
		splitContainer.add(verticalSplit);
		
		// 컨텐트팬에 붙이기
		contentPane.add(eastPanel, BorderLayout.EAST);
		contentPane.add(splitContainer, BorderLayout.CENTER);
		
		// 방 참가하기 버튼
		joinRoombtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String selectedRoom = roomList.getSelectedValue();
						
				if (selectedRoom == null) {
					JOptionPane.showMessageDialog(CreateRoomScreen.this, "참여할 방을 선택하세요.", "오류", JOptionPane.ERROR_MESSAGE);
					return;
				}
				enterGameRoom(selectedRoom);
			}
		});
				
		// 방 만들기 버튼
			createRoombtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String newRoomName = JOptionPane.showInputDialog("새 방 이름을 입력하세요:");
				if (newRoomName == null || newRoomName.trim().isEmpty()) {
					return;
				}
						
				//서버에 방 생성 요청 (NullPointerException 방지)
				if (out != null) {
					out.println("CREATE_ROOM::" + newRoomName);
				}
						
				// 만든 사람은 바로 입장
				enterGameRoom(newRoomName);
			}
		});
				
		//서버 연결 시작
		connectToLobbyServer();
	}
			
			
	private void connectToLobbyServer() {
		new Thread(new Runnable() {
			public void run() {
				try {
					socket = new Socket("localhost", 9999); 
					out = new PrintWriter(socket.getOutputStream(), true);
					in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					
					out.println("LOGIN::" + nickname);
					
					String msg;
					while((msg = in.readLine()) != null) {
						final String message = msg;
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								processLobbyMessage(message);
							}
						});
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}
			
	private void processLobbyMessage(String msg) {
		if (msg.startsWith("NEW_ROOM::")) {
			String roomName = msg.substring("NEW_ROOM::".length());
			if (!listModel.contains(roomName)) {
				listModel.addElement(roomName);
			}
		} else if (msg.startsWith("ROOM_LIST::")) {
			String[] rooms = msg.substring("ROOM_LIST::".length()).split(",");
			listModel.clear();
			for(String r : rooms) {
				if(!r.trim().isEmpty() && !listModel.contains(r)) {
					listModel.addElement(r);
				}
			}
		}
	}
			
	private void enterGameRoom(String roomName) {
		GameRoomScreen gameRoom = new GameRoomScreen(nickname, roomName, "localhost", 9999);
		gameRoom.setVisible(true);
		dispose(); // 로비 창을 닫기
	}
}