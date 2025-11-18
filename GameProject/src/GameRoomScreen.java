import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

public class GameRoomScreen extends JFrame {

	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	private JTextArea chatDisplay;
	private JTextField chatInput;
	private DrawingPanel drawingCanvas;
	private JPanel userListPanel;
	private JLabel keywordLabel;
	private JLabel scoreLabel;

	// --- 네트워크 관련 변수 ---
	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	private String nickname;
	private String serverAddress;
	private int serverPort;

	// --- 게임 상태 변수 ---
	private boolean isDrawer = false; // 내가 출제자인지 여부
	private Color currentPenColor = Color.BLACK; // 현재 펜 색상

	// --- 내부 클래스: 그림판 패널 ---
	class DrawingPanel extends JPanel {
		private static final long serialVersionUID = 1L;
		private List<StrokePath> paths = new ArrayList<>();
		private StrokePath currentPath = null;

		public DrawingPanel() {
			setBackground(Color.WHITE);
			setBorder(new LineBorder(Color.BLACK));
		}

		// 외부에서(서버로부터) 그리기 명령이 왔을 때 호출
		public void executeDraw(int x, int y, String type, Color color) {
			if ("START".equals(type)) {
				currentPath = new StrokePath(color, 2); 
				currentPath.addPoint(new Point(x, y));
				paths.add(currentPath);
			} else if ("DRAG".equals(type)) {
				if (currentPath != null) {
					currentPath.addPoint(new Point(x, y));
				}
			}
			repaint();
		}

		public void clear() {
			paths.clear();
			currentPath = null;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setStroke(new BasicStroke(2));

			for (StrokePath path : paths) {
				g2.setColor(path.color);
				List<Point> points = path.points;
				if (points.size() > 1) {
					for (int i = 0; i < points.size() - 1; i++) {
						Point p1 = points.get(i);
						Point p2 = points.get(i + 1);
						g2.drawLine(p1.x, p1.y, p2.x, p2.y);
					}
				}
			}
		}
	}

	// 선 정보를 저장하는 클래스
	class StrokePath {
		Color color;
		int strokeSize;
		List<Point> points = new ArrayList<>();

		public StrokePath(Color color, int strokeSize) {
			this.color = color;
			this.strokeSize = strokeSize;
		}

		public void addPoint(Point p) {
			points.add(p);
		}
	}

	// --- 내부 클래스: 유저 상태 패널 ---
	class UserStatusPanel extends JPanel {
		private static final long serialVersionUID = 1L;
		JLabel nameLabel;

		public UserStatusPanel(String userName) {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBorder(BorderFactory.createCompoundBorder(new LineBorder(Color.GRAY), new EmptyBorder(5, 10, 5, 10)));
			setPreferredSize(new Dimension(150, 40));
			setMaximumSize(new Dimension(150, 40));
			nameLabel = new JLabel(userName);
			add(nameLabel);
		}
	}

	/**
	 * 생성자
	 */
	public GameRoomScreen(String nickname, String roomTitle, String serverAddress, int serverPort) {
		this.nickname = nickname;
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;

		setTitle(roomTitle + " - " + nickname);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setBounds(100, 100, 1000, 750);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(10, 10));
		setContentPane(contentPane);

		// 1. 상단: 방 정보 및 제시어
		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 50, 5));
		JLabel roomNameLabel = new JLabel("방 이름: " + roomTitle);
		keywordLabel = new JLabel("게임 대기 중...");
		keywordLabel.setFont(keywordLabel.getFont().deriveFont(20.0f));
		scoreLabel = new JLabel("SCORE: 0");

		topPanel.add(roomNameLabel);
		topPanel.add(keywordLabel);
		topPanel.add(scoreLabel);
		contentPane.add(topPanel, BorderLayout.NORTH);

		// 2. 오른쪽: 참가자 목록
		userListPanel = new JPanel();
		userListPanel.setLayout(new BoxLayout(userListPanel, BoxLayout.Y_AXIS));
		userListPanel.setBorder(new TitledBorder("참가자"));
		userListPanel.setPreferredSize(new Dimension(170, 0));
		contentPane.add(new JScrollPane(userListPanel), BorderLayout.EAST);

		// 3. 중앙: 도구 + 그림판
		JPanel drawingAreaPanel = new JPanel(new BorderLayout(5, 5));

		// 3-1. 도구 패널
		JPanel toolPanel = new JPanel();
		toolPanel.setLayout(new BoxLayout(toolPanel, BoxLayout.Y_AXIS));
		toolPanel.setPreferredSize(new Dimension(60, 0));
		
		// 색상 버튼들
		toolPanel.add(createColorButton(Color.BLACK));
		toolPanel.add(createColorButton(Color.RED));
		toolPanel.add(createColorButton(Color.BLUE));
		toolPanel.add(createColorButton(Color.GREEN));
		toolPanel.add(createColorButton(Color.YELLOW));
		
		toolPanel.add(new JLabel(" "));
		
		JButton clearButton = new JButton("C"); // Clear
		clearButton.setToolTipText("전체 삭제");
		clearButton.addActionListener(e -> sendProtocol("CLEAR::"));
		toolPanel.add(clearButton);

		drawingAreaPanel.add(toolPanel, BorderLayout.WEST);

		// 3-2. 그림판
		drawingCanvas = new DrawingPanel();
		drawingAreaPanel.add(drawingCanvas, BorderLayout.CENTER);
		contentPane.add(drawingAreaPanel, BorderLayout.CENTER);

		// 4. 하단: 채팅 및 정답 입력
		JPanel chatAndGuessPanel = new JPanel(new BorderLayout());
		chatAndGuessPanel.setPreferredSize(new Dimension(0, 200));

		chatDisplay = new JTextArea();
		chatDisplay.setEditable(false);
		chatDisplay.setLineWrap(true);
		
		JPanel inputPanel = new JPanel(new BorderLayout());
		chatInput = new JTextField();
		JButton sendButton = new JButton("입력");
		inputPanel.add(chatInput, BorderLayout.CENTER);
		inputPanel.add(sendButton, BorderLayout.EAST);

		chatAndGuessPanel.add(new JScrollPane(chatDisplay), BorderLayout.CENTER);
		chatAndGuessPanel.add(inputPanel, BorderLayout.SOUTH);

		contentPane.add(chatAndGuessPanel, BorderLayout.SOUTH);

		// --- 이벤트 리스너 ---

		// 메시지 전송
		ActionListener sendAction = e -> handleInput();
		chatInput.addActionListener(sendAction);
		sendButton.addActionListener(sendAction);

		// 마우스 그리기 이벤트
		drawingCanvas.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (isDrawer) {
					sendProtocol("DRAW::" + e.getX() + "," + e.getY() + ",START");
				}
			}
		});

		drawingCanvas.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				if (isDrawer) {
					sendProtocol("DRAW::" + e.getX() + "," + e.getY() + ",DRAG");
				}
			}
		});

		// 서버 연결
		connectToServer();
	}

	// 색상 버튼 생성 헬퍼 메서드
	private JButton createColorButton(Color color) {
		JButton btn = new JButton();
		btn.setPreferredSize(new Dimension(40, 40));
		btn.setMaximumSize(new Dimension(40, 40));
		btn.setBackground(color);
		btn.setOpaque(true);
		btn.setBorderPainted(false);
		
		btn.addActionListener(e -> {
			if (isDrawer) {
				// 수정된 Server.java는 RGB:: 프로토콜을 브로드캐스트 하므로 그대로 사용
				String rgbMsg = "RGB::" + color.getRed() + "," + color.getGreen() + "," + color.getBlue();
				sendProtocol(rgbMsg);
			}
		});
		return btn;
	}

	// 입력창 처리 로직
	private void handleInput() {
		String text = chatInput.getText().trim();
		if (text.isEmpty()) return;

		if (isDrawer) {
			// 출제자는 채팅만 가능 (정답 입력 불가)
			sendProtocol("CHAT::" + nickname + ": " + text);
		} else {
			// 정답자는 ANSWER 프로토콜로 정답 시도
			sendProtocol("ANSWER::" + text);
		}
		chatInput.setText("");
	}

	// --- 네트워크 통신 ---

	private void connectToServer() {
		try {
			socket = new Socket(serverAddress, serverPort);
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			sendProtocol("LOGIN::" + nickname);
			appendToChat("[시스템] 서버에 연결되었습니다.");

			new Thread(this::readServerMessages).start();

		} catch (IOException e) {
			appendToChat("[오류] 서버 연결 실패: " + e.getMessage());
		}
	}

	private void sendProtocol(String msg) {
		if (out != null) {
			out.println(msg);
		}
	}

	// 서버 메시지 수신
	private void readServerMessages() {
		try {
			String msg;
			while ((msg = in.readLine()) != null) {
				final String message = msg;
				EventQueue.invokeLater(() -> processMessage(message));
			}
		} catch (IOException e) {
			appendToChat("[시스템] 연결이 종료되었습니다.");
		}
	}

	// 프로토콜 파싱 및 UI 로직
	private void processMessage(String msg) {
		if (msg.startsWith("CHAT::")) {
			appendToChat(msg.substring(6));
		} 
		else if (msg.startsWith("START::drawer,")) {
			// [게임 시작] 출제자
			String keyword = msg.substring("START::drawer,".length());
			setupRound(true, keyword);
		} 
		else if (msg.startsWith("START::guesser,")) {
			// [게임 시작] 정답자
			String lengthStr = msg.substring("START::guesser,".length());
			setupRound(false, lengthStr);
		} 
		else if (msg.startsWith("CORRECT::")) {
			// [정답] 알림
			String content = msg.substring("CORRECT::".length());
			JOptionPane.showMessageDialog(this, content, "정답!", JOptionPane.INFORMATION_MESSAGE);
			
			// 라운드 종료 처리
			keywordLabel.setText("다음 라운드 대기 중...");
			keywordLabel.setForeground(Color.BLACK);
			drawingCanvas.clear();
			currentPenColor = Color.BLACK; 
		} 
		else if (msg.startsWith("DRAW::")) {
			// 그림 그리기: DRAW::x,y,type
			try {
				String[] parts = msg.substring(6).split(",");
				int x = Integer.parseInt(parts[0]);
				int y = Integer.parseInt(parts[1]);
				String type = parts[2]; 
				
				drawingCanvas.executeDraw(x, y, type, currentPenColor);
			} catch (Exception e) {
				// 좌표 파싱 오류 무시
			}
		} 
		else if (msg.startsWith("RGB::")) {
			// 색상 변경 적용
			try {
				String[] colors = msg.substring(5).split(",");
				int r = Integer.parseInt(colors[0].trim());
				int g = Integer.parseInt(colors[1].trim());
				int b = Integer.parseInt(colors[2].trim());
				currentPenColor = new Color(r, g, b);
			} catch (Exception e) {
				System.err.println("RGB Parse Error: " + msg);
			}
		} 
		else if (msg.startsWith("CLEAR::")) {
			drawingCanvas.clear();
		}
		else if (msg.startsWith("LOGIN::")) {
			String newName = msg.substring(7);
			userListPanel.add(new UserStatusPanel(newName));
			userListPanel.revalidate();
			userListPanel.repaint();
		}
	}

	// 라운드 설정
	private void setupRound(boolean isMeDrawer, String info) {
		this.isDrawer = isMeDrawer;
		drawingCanvas.clear();
		currentPenColor = Color.BLACK;

		if (isDrawer) {
			keywordLabel.setText("제시어: " + info);
			keywordLabel.setForeground(Color.BLUE);
			chatInput.setEnabled(true);
			appendToChat("[알림] 당신은 '출제자'입니다. 제시어를 그려주세요!");
		} else {
			try {
				int len = Integer.parseInt(info);
				StringBuilder sb = new StringBuilder();
				for(int i=0; i<len; i++) sb.append("_ ");
				keywordLabel.setText("단어: " + sb.toString());
				keywordLabel.setForeground(Color.BLACK);
				chatInput.setEnabled(true);
				chatInput.requestFocus();
				appendToChat("[알림] 당신은 '정답자'입니다. 그림을 보고 정답을 맞춰보세요!");
			} catch (Exception e) {
				keywordLabel.setText("문제 발생");
			}
		}
	}

	private void appendToChat(String text) {
		chatDisplay.append(text + "\n");
		chatDisplay.setCaretPosition(chatDisplay.getDocument().getLength());
	}
}