import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridLayout;
import java.awt.event.ActionEvent; // [추가]
import java.awt.event.ActionListener; // [추가]

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane; // [추가]
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

public class CreateRoomScreen extends JFrame {

	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	private JList<String> roomList; // [수정] JList를 멤버 변수로 변경 (리스너에서 접근해야 하므로)

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					CreateRoomScreen frame = new CreateRoomScreen("테스트"); //예시로 닉네임 = 테스트
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
	public CreateRoomScreen(String nickname) { //사용자 닉네임 전달
		setTitle("방 만들기");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 450, 300);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(5, 5));
		setContentPane(contentPane);
		
		//오른쪽 버튼 2개 + 환영 메시지
		JPanel eastPanel = new JPanel(new BorderLayout(5, 5));
		eastPanel.setPreferredSize(new Dimension(150,0));
		
		//버튼 2개 담을 패널(2행 1열)
		JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 5, 5));
		JButton createRoombtn = new JButton("방 만들기");
		JButton joinRoombtn = new JButton("참가하기");
		buttonPanel.add(createRoombtn);
		buttonPanel.add(joinRoombtn);
		
		// [추가] '참가하기' 버튼 리스너
		joinRoombtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String selectedRoom = roomList.getSelectedValue(); // 선택된 방 이름
				
				if (selectedRoom == null) {
					JOptionPane.showMessageDialog(CreateRoomScreen.this, "참여할 방을 선택하세요.", "오류", JOptionPane.ERROR_MESSAGE);
					return;
				}
				
				// [수정] 서버 주소와 포트 (Server.java와 일치해야 함)
				String serverAddress = "localhost";
				int serverPort = 9999; 
				
				// 게임방 생성 및 실행
				GameRoomScreen gameRoom = new GameRoomScreen(nickname, selectedRoom, serverAddress, serverPort);
				gameRoom.setVisible(true);
				
				// [선택 사항] 로비 창을 닫고 싶다면
				// dispose(); 
			}
		});
		
		// [추가] '방 만들기' 버튼 리스너 (임시)
		createRoombtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// [TODO] 방 만들기 로직 (지금은 '참가하기'와 동일하게 동작)
				String newRoomName = JOptionPane.showInputDialog("새 방 이름을 입력하세요:");
				if (newRoomName == null || newRoomName.trim().isEmpty()) {
					return;
				}

				String serverAddress = "localhost";
				int serverPort = 9999; 
				
				GameRoomScreen gameRoom = new GameRoomScreen(nickname, newRoomName, serverAddress, serverPort);
				gameRoom.setVisible(true);
			}
		});
		
		
		//환영 메시지 레이블
		JLabel welcomeLabel = new JLabel(nickname + "님 환영합니다!"); //사용자 닉네임 사용
		welcomeLabel.setHorizontalAlignment(SwingConstants.CENTER);
		
		eastPanel.add(buttonPanel, BorderLayout.NORTH);
		eastPanel.add(welcomeLabel, BorderLayout.CENTER);
		
		//왼쪽영역(방 목록 + 채팅 창)
		JPanel roomListPanel = new JPanel(new BorderLayout());
		
		roomListPanel.setBorder(new TitledBorder("방 목록"));
		String[] roomData = {"방 1", "방 2","방 3", "방 4", "방 5"}; //방 목록 예시 데이터
		roomList = new JList<>(roomData); // [수정] 멤버 변수에 할당
		roomListPanel.add(new JScrollPane(roomList), BorderLayout.CENTER);
		
		//채팅 패널
		JPanel chatPanel = new JPanel(new BorderLayout());
		chatPanel.setBorder(new TitledBorder("채팅"));
		
		JTextArea chatDisplay = new JTextArea(); //보낸 대화들 볼 수 있는 창
		chatDisplay.setEditable(false);
		
		JTextField sender = new JTextField(); //입력할 메시지 보내는 텍스트 필드
		JButton sender_btn = new JButton("전송");
		
		// [TODO] 로비 채팅 기능 구현 필요
		
		JPanel inputPanel = new JPanel(new BorderLayout());
		inputPanel.add(sender, BorderLayout.CENTER);
		inputPanel.add(sender_btn, BorderLayout.EAST);
		
		chatPanel.add(new JScrollPane(chatDisplay), BorderLayout.CENTER);
		chatPanel.add(inputPanel, BorderLayout.SOUTH);
		
		//왼쪽 영역을 상하로 분할
		JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		verticalSplit.setTopComponent(roomListPanel);
		verticalSplit.setBottomComponent(chatPanel);
		verticalSplit.setResizeWeight(0.5);
		
		//컨텐트팬에 붙이기
		contentPane.add(eastPanel, BorderLayout.EAST);
		contentPane.add(verticalSplit, BorderLayout.CENTER);
	}

}