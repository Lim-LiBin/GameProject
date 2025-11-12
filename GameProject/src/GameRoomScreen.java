import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
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

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.border.LineBorder;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory; // Border 생성을 위해 추가
import javax.swing.JOptionPane; // 이 줄을 맨 위 import 목록에 추가

public class GameRoomScreen extends JFrame {

	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	private JTextArea chatDisplay; // [추가] 채팅 내용 표시 영역
	private JTextField chatInput; // [추가] 채팅/정답 입력 영역
	private DrawingPanel drawingCanvas; // 그림판 (커스텀 클래스)
	private JPanel userListPanel; // [수정] 오른쪽 유저 패널들을 담을 컨테이너
	private JLabel keywordLabel; // 제시어 표시 레이블
	private JLabel scoreLabel; // 점수 표시 레이블

	// --- 네트워크 관련 변수 ---
	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	private String nickname;

	// --- 그리기 도구 관련 변수 ---
	private Color currentColor = Color.BLACK;
	private int currentStroke = 2; // 펜 굵기
	private final int ERASER_STROKE = 10; // 지우개 굵기

	// --- 내부 클래스: 그려진 선(획) 정보 ---
	class CustomStroke {
		Color color;
		Stroke stroke;
		List<Point> points = new ArrayList<>();

		CustomStroke(Color color, Stroke stroke) {
			this.color = color;
			this.stroke = stroke;
			this.points.add(new Point(-1, -1)); // [수정] 획의 시작을 알리는 구분점
		}
	}

	// --- 내부 클래스: 그림판 패널 ---
	class DrawingPanel extends JPanel {
		private static final long serialVersionUID = 1L;
		private List<CustomStroke> strokes = new ArrayList<>();
		private CustomStroke currentStroke;

		public DrawingPanel() {
			setBackground(Color.WHITE);
			setBorder(new LineBorder(Color.BLACK)); // 그림판 테두리
		}

		public void startStroke(Point p, Color color, int thickness) {
			currentColor = color;
			currentStroke = new CustomStroke(color, new BasicStroke(thickness));
			currentStroke.points.add(p);
			strokes.add(currentStroke);
			repaint();
		}

		public void addPointToStroke(Point p) {
			if (currentStroke != null) {
				currentStroke.points.add(p);
				repaint();
			}
		}

		public void clearDrawing() {
			strokes.clear();
			currentStroke = null;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			for (CustomStroke stroke : strokes) {
				g2.setColor(stroke.color);
				g2.setStroke(stroke.stroke);
				Point prevPoint = null;
				for (Point p : stroke.points) {
					if (p.x == -1) { // 획의 시작점 구분
						prevPoint = null;
						continue;
					}
					if (prevPoint != null) {
						g2.drawLine(prevPoint.x, prevPoint.y, p.x, p.y);
					}
					prevPoint = p;
				}
			}
		}
	}
	
	// --- [새 내부 클래스] 오른쪽 유저 상태 패널 ---
	class UserStatusPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        JLabel nameLabel;
        JLabel guessLabel; // "답"을 표시할 레이블

        public UserStatusPanel(String userName) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(Color.GRAY),
                new EmptyBorder(5, 10, 5, 10)
            ));
            setPreferredSize(new Dimension(150, 60)); // 패널 크기
            setMaximumSize(new Dimension(150, 60)); // 크기 고정

            nameLabel = new JLabel("사용자: " + userName);
            guessLabel = new JLabel("답: "); // 초기값
            
            add(nameLabel);
            add(guessLabel);
        }

        // 다른 유저의 '답'을 업데이트하는 메서드
        public void setGuess(String guess) {
            guessLabel.setText("답: " + guess);
        }
    }
	// --- UserStatusPanel 끝 ---

	/**
	 * Launch the application. (테스트용 main)
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					// --- (수정됨) ---
					// 1. 닉네임을 팝업창으로 물어봄
					String nickname = JOptionPane.showInputDialog("닉네임을 입력하세요", "test1");
					
					// 2. 닉네임이 null이 아니면 (취소를 누르지 않으면) 창을 띄움
					if (nickname != null && !nickname.isEmpty()) {
						GameRoomScreen frame = new GameRoomScreen(nickname, "즐거운 캐치마인드 방", "localhost", 12345);
						frame.setVisible(true);
						
						// [테스트] test1일 경우에만 출제자 역할 부여
						if (nickname.equals("test1")) {
							frame.setRole(true, "기린");
						} else {
							frame.setRole(false, "2"); // test2는 정답자
						}
					}
					// --- (여기까지 수정) ---
					
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the frame.
	 */
	public GameRoomScreen(String nickname, String roomTitle, String serverAddress, int serverPort) {
		this.nickname = nickname;

		setTitle(roomTitle);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setBounds(100, 100, 1000, 750); // 전체 프레임 크기
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(10, 10)); // 전체 레이아웃
		setContentPane(contentPane);

		// --- 1. 상단 (NORTH): 방이름, 제시어, 점수 ---
		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 50, 5)); // 가운데 정렬, 좌우 간격 50
		JLabel roomNameLabel = new JLabel("방 이름: " + roomTitle);
		keywordLabel = new JLabel("제시어: ???"); // 초기값
		scoreLabel = new JLabel("SCORE: 0"); // 초기값
		topPanel.add(roomNameLabel);
		topPanel.add(keywordLabel);
		topPanel.add(scoreLabel);
		contentPane.add(topPanel, BorderLayout.NORTH);

		// --- 2. 오른쪽 (EAST): 유저 목록 (스케치 반영) ---
		userListPanel = new JPanel();
		userListPanel.setLayout(new BoxLayout(userListPanel, BoxLayout.Y_AXIS)); // 세로 배치
		userListPanel.setBorder(new TitledBorder("참가자"));
		userListPanel.setPreferredSize(new Dimension(170, 0));
		
		// [수정] 샘플 유저 패널 -> '나'만 우선 추가. 나머지는 서버에서 받아야 함.
		userListPanel.add(new UserStatusPanel(nickname + " (나)"));
		// userListPanel.add(new UserStatusPanel("test2")); // [수정] 삭제
		// userListPanel.add(new UserStatusPanel("test3")); // [수정] 삭제
		
		JScrollPane userListScrollPane = new JScrollPane(userListPanel);
		userListScrollPane.setPreferredSize(new Dimension(170, 0));
		contentPane.add(userListScrollPane, BorderLayout.EAST);

		// --- 3. 중앙 (CENTER): 그림판 + 도구 (스케치 반영) ---
		JPanel drawingAreaPanel = new JPanel(new BorderLayout(5, 5));

		// 3-1. 왼쪽 도구 패널 (drawingAreaPanel의 WEST)
		JPanel toolPanel = new JPanel();
		toolPanel.setLayout(new BoxLayout(toolPanel, BoxLayout.Y_AXIS));
		toolPanel.setPreferredSize(new Dimension(80, 0));
		toolPanel.setBorder(new EmptyBorder(10, 5, 10, 5));
		
		// [수정] 색상 버튼 (JPanel 배경색으로)
		toolPanel.add(createColorButton(Color.RED));
		toolPanel.add(createColorButton(Color.ORANGE)); // 스케치 반영
		toolPanel.add(createColorButton(Color.YELLOW)); // 스케치 반영
		toolPanel.add(createColorButton(Color.GREEN));
		toolPanel.add(createColorButton(Color.BLUE));
		toolPanel.add(createColorButton(Color.BLACK)); // 검정색 추가
		
		toolPanel.add(new JLabel(" ")); // 간격
		
		JButton eraserButton = new JButton("지우개");
		JButton clearButton = new JButton("전체삭제");
		
		toolPanel.add(eraserButton);
		toolPanel.add(clearButton);
		
		drawingAreaPanel.add(toolPanel, BorderLayout.WEST);

		// 3-2. 그림판 (drawingAreaPanel의 CENTER)
		drawingCanvas = new DrawingPanel();
		drawingAreaPanel.add(drawingCanvas, BorderLayout.CENTER);
		
		contentPane.add(drawingAreaPanel, BorderLayout.CENTER);
		
		// --- 4. 하단 (SOUTH): 채팅창 및 입력 (프로토콜 요구사항 반영) ---
		JPanel chatAndGuessPanel = new JPanel(new BorderLayout());
		chatAndGuessPanel.setPreferredSize(new Dimension(0, 150)); // 하단 영역 높이
		
		chatDisplay = new JTextArea();
		chatDisplay.setEditable(false);
		chatDisplay.setLineWrap(true);
		chatDisplay.setBorder(new TitledBorder("채팅 / 정답"));
		
		JPanel inputPanel = new JPanel(new BorderLayout());
		chatInput = new JTextField();
		JButton sendButton = new JButton("전송");
		inputPanel.add(chatInput, BorderLayout.CENTER);
		inputPanel.add(sendButton, BorderLayout.EAST);
		
		chatAndGuessPanel.add(new JScrollPane(chatDisplay), BorderLayout.CENTER);
		chatAndGuessPanel.add(inputPanel, BorderLayout.SOUTH);
		
		contentPane.add(chatAndGuessPanel, BorderLayout.SOUTH);


		// --- 이벤트 리스너 추가 ---

		// 1. 채팅/정답 전송
		ActionListener sendChatListener = e -> sendChatMessage();
		sendButton.addActionListener(sendChatListener);
		chatInput.addActionListener(sendChatListener);

		// 2. 전체삭제 버튼
		clearButton.addActionListener(e -> {
			sendMessage("CLEAR::");
			drawingCanvas.clearDrawing();
		});

		// 3. 지우개 버튼
		eraserButton.addActionListener(e -> {
			currentColor = drawingCanvas.getBackground(); // 지우개 = 배경색
			currentStroke = ERASER_STROKE;
		});
		
		// 4. 그림판 마우스 이벤트 (수정됨)
		drawingCanvas.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				// (중요) 그리기 시작: 현재 색상과 굵기로 새 획 시작
				drawingCanvas.startStroke(e.getPoint(), currentColor, currentStroke);
				
				// [프로토콜 경고!] 현재 프로토콜은 색상/굵기/새 획 정보를 보낼 수 없음!
				// 임시로 '새 획 시작'을 알리는 좌표 전송 (서버와 협의 필요)
				sendMessage("DRAW::" + e.getX() + "," + e.getY() + ",START"); 
			}
		});

		drawingCanvas.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				drawingCanvas.addPointToStroke(e.getPoint());
				sendMessage("DRAW::" + e.getX() + "," + e.getY() + ",DRAG");
			}
		});

		// --- 서버 연결 시작 ---
		// [수정] 주석 해제!!
		connectToServer(serverAddress, serverPort); 
	}
	
	// [새 메서드] 스케치에 맞는 색상 버튼 생성
	private JButton createColorButton(Color color) {
        JButton colorButton = new JButton();
        colorButton.setPreferredSize(new Dimension(50, 30));
        colorButton.setMaximumSize(new Dimension(50, 30)); // 크기 고정
        colorButton.setBackground(color);
        colorButton.addActionListener(e -> {
            currentColor = color; // 해당 버튼의 색상으로 현재 색 변경
            currentStroke = 2; // 펜 굵기 기본값
        });
        return colorButton;
    }

	// [수정된 메서드] 역할에 따라 제시어 UI 변경
	public void setRole(boolean isDrawer, String keyword) {
		if (isDrawer) {
			// 내가 출제자일 때
			keywordLabel.setText("제시어: " + keyword);
			keywordLabel.setForeground(Color.BLUE);
			drawingCanvas.setEnabled(true);
		} else {
			// 내가 정답자일 때
			try {
				int length = Integer.parseInt(keyword);
				String hint = "정답: ";
				for (int i = 0; i < length; i++) {
					hint += "__ ";
				}
				keywordLabel.setText(hint);
				keywordLabel.setForeground(Color.BLACK);
				drawingCanvas.setEnabled(false); // 그림 그리기 비활성화
			} catch (NumberFormatException e) {
				System.err.println("START::guesser 프로토콜 글자수 오류: " + keyword);
			}
		}
	}
	
	// 채팅 메시지 전송 로직
	private void sendChatMessage() {
		String message = chatInput.getText();
        if (!message.isEmpty()) {
			// [TODO] 이게 정답인지, 채팅인지 판단하는 로직 필요
			// (예: keywordLabel.getText()와 message가 일치하면 ANSWER::)
			
			// 지금은 일단 CHAT::으로 전송
        	sendMessage("CHAT::" + this.nickname + ": " + message); // [수정] 닉네임 포함
            chatInput.setText("");
        }
	}

	// --- 네트워크 통신 메서드 (서버 연결, 스레드, 메시지 전송/처리) ---
	
	private void connectToServer(String address, int port) {
        try {
            socket = new Socket(address, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            chatDisplay.append("서버에 연결되었습니다.\n");
            sendMessage("LOGIN::" + nickname);
            new ClientSocketThread().start();
        } catch (IOException e) {
            chatDisplay.append("서버 연결 실패: " + e.getMessage() + "\n");
        }
    }
	
    class ClientSocketThread extends Thread {
        @Override
        public void run() {
            try {
                String serverMessage;
                while ((serverMessage = in.readLine()) != null) {
                	// [수정] 스윙 컴포넌트 업데이트는 EventQueue 스레드에서 처리
                	String finalMessage = serverMessage;
                    EventQueue.invokeLater(() -> processServerMessage(finalMessage));
                }
            } catch (IOException e) {
                EventQueue.invokeLater(() -> chatDisplay.append("서버 연결이 끊어졌습니다.\n"));
            } finally {
                try {
                    if (socket != null && !socket.isClosed()) socket.close();
                } catch (IOException e) { e.printStackTrace(); }
            }
        }
    }

    private void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        } else {
            System.out.println("[클라이언트 내부 시뮬레이션]: " + message);
        }
    }

    private void processServerMessage(String serverMessage) {
        System.out.println("[서버 수신]: " + serverMessage);
        
        if (serverMessage.startsWith("CHAT::")) {
            String chatContent = serverMessage.substring(6);
            chatDisplay.append(chatContent + "\n");
            chatDisplay.setCaretPosition(chatDisplay.getDocument().getLength());
        } 
        else if (serverMessage.startsWith("DRAW::")) {
            // [프로토콜 경고!] 서버가 좌표만 보내면, 클라이언트는 색상/굵기를 알 수 없음!
            // 이 코드는 현재 프로토콜을 임시로 확장 (예: DRAW::x,y,START 또는 DRAW::x,y,DRAG)
            String[] parts = serverMessage.substring(6).split(",");
            if (parts.length == 3) {
                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    String type = parts[2];
                    
                    if ("START".equals(type)) {
                    	// [TODO] 서버가 색상/굵기도 보내줘야 함
                    	// 지금은 임시로 검은색, 굵기 2로 그림
                        drawingCanvas.startStroke(new Point(x,y), Color.BLACK, 2);
                    } else if ("DRAG".equals(type)) {
                        drawingCanvas.addPointToStroke(new Point(x,y));
                    }
                } catch (NumberFormatException e) {
                    System.err.println("DRAW:: 프로토콜 좌표 형식 오류: " + serverMessage);
                }
            }
        } 
        else if (serverMessage.startsWith("CLEAR::")) {
            drawingCanvas.clearDrawing();
        } 
        else if (serverMessage.startsWith("START::drawer,")) {
            String keyword = serverMessage.substring("START::drawer,".length());
            setRole(true, keyword);
        } 
        else if (serverMessage.startsWith("START::guesser,")) {
            String lengthStr = serverMessage.substring("START::guesser,".length());
            setRole(false, lengthStr);
        }
        else if (serverMessage.startsWith("LOGIN::")) {
            // [수정] 새 유저가 접속하면 userListPanel에 UserStatusPanel 추가
            String newUserName = serverMessage.substring(7);
            userListPanel.add(new UserStatusPanel(newUserName));
            userListPanel.revalidate(); // [중요] UI 갱신
            userListPanel.repaint(); // [중요] UI 갱신
        }
        else if (serverMessage.startsWith("LOGOUT::")) {
            // [TODO] 유저가 나가면 userListPanel에서 UserStatusPanel 제거
        }
        else if (serverMessage.startsWith("UPDATE_GUESS::")) {
            // [TODO] "UPDATE_GUESS::유저이름:유저의답" 프로토콜을 받아서
            // 해당 유저의 UserStatusPanel의 setGuess() 메서드 호출
        }
	}
}