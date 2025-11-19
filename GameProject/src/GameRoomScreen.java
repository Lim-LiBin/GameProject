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
    private JButton startGameBtn; // [추가] 게임 시작 버튼

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

        // 1. 상단: 방 정보, 게임 시작 버튼, 제시어
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 5));
        JLabel roomNameLabel = new JLabel("방 이름: " + roomTitle);
        
        // [추가] 게임 시작 버튼
        startGameBtn = new JButton("게임 시작");
        startGameBtn.addActionListener(e -> sendProtocol("GAME_START::")); // 서버에 시작 요청

        keywordLabel = new JLabel("게임 대기 중...");
        keywordLabel.setFont(keywordLabel.getFont().deriveFont(20.0f));
        scoreLabel = new JLabel("SCORE: 0");

        topPanel.add(roomNameLabel);
        topPanel.add(startGameBtn); // 버튼 배치
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

        // 메시지 전송 (엔터 키 및 버튼)
        ActionListener sendAction = e -> handleInput();
        chatInput.addActionListener(sendAction);
        sendButton.addActionListener(sendAction);

        // 마우스 그리기 이벤트
        drawingCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // 출제자만 그림 그리기 가능
                if (isDrawer) {
                    // 그림 시작점을 알리기 위해 START 타입 전송
                    sendProtocol("DRAW::" + e.getX() + "," + e.getY() + ",START");
                }
            }
        });

        drawingCanvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // 출제자만 그림 그리기 가능
                if (isDrawer) {
                    // 드래그 중임을 알리기 위해 DRAG 타입 전송
                    sendProtocol("DRAW::" + e.getX() + "," + e.getY() + ",DRAG");
                }
            }
        });

        // 서버 연결 시작
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
                // RGB 프로토콜: r,g,b
                String rgbMsg = "RGB::" + color.getRed() + "," + color.getGreen() + "," + color.getBlue();
                sendProtocol(rgbMsg);
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
        return btn;
    }

    // 입력창 처리 로직
    private void handleInput() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;

        if (isDrawer) {
            // 출제자는 채팅만 가능 (정답 입력 불가)
            sendProtocol("CHAT::" + text); // 이름은 서버에서 붙이는 경우가 많으나, 여기선 메시지만 전송
        } else {
            // 정답자는 ANSWER 프로토콜로 정답 시도
            sendProtocol("ANSWER::" + text);
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
        chatInput.setText("");
    }

    // --- 네트워크 통신 ---

    private void connectToServer() {
        try {
            socket = new Socket(serverAddress, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // [프로토콜] 로그인
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

    // 서버 메시지 수신 루프
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

    // 프로토콜 파싱 및 UI 업데이트 로직
    private void processMessage(String msg) {
        if (msg.startsWith("CHAT::")) {
            appendToChat(msg.substring(6));
        } 
        else if (msg.startsWith("START::drawer,")) {
            // [프로토콜] 출제자로 게임 시작
            // 예: START::drawer,사과
            String keyword = msg.substring("START::drawer,".length());
            setupRound(true, keyword);
            startGameBtn.setEnabled(false); // 게임 중 버튼 비활성화
        } 
        else if (msg.startsWith("START::guesser,")) {
            // [프로토콜] 정답자로 게임 시작
            // 예: START::guesser,2 (글자수)
            String lengthStr = msg.substring("START::guesser,".length());
            setupRound(false, lengthStr);
            startGameBtn.setEnabled(false);
        } 
        // [수정] 요구사항에 맞춰 NOTICE_CORRECT:: 로 변경
        else if (msg.startsWith("NOTICE_CORRECT::")) {
            // [정답] 알림
            String content = msg.substring("NOTICE_CORRECT::".length());
            JOptionPane.showMessageDialog(this, content, "정답!", JOptionPane.INFORMATION_MESSAGE);
            
            // 라운드 종료 처리
            keywordLabel.setText("다음 라운드 대기 중...");
            keywordLabel.setForeground(Color.BLACK);
            drawingCanvas.clear();
            currentPenColor = Color.BLACK; 
            startGameBtn.setEnabled(true); // 다시 게임 시작 가능하도록 활성화
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
            // 색상 변경 적용 (RGB::000,000,000 형태 처리)
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
            // 유저 입장 알림 등
            String newName = msg.substring(7);
            userListPanel.add(new UserStatusPanel(newName));
            userListPanel.revalidate();
            userListPanel.repaint();
        }
    }

    // 라운드 설정 (출제자/정답자 UI 분기 처리)
    private void setupRound(boolean isMeDrawer, String info) {
        this.isDrawer = isMeDrawer;
        drawingCanvas.clear();
        currentPenColor = Color.BLACK;

        if (isDrawer) {
            keywordLabel.setText("제시어: " + info);
            keywordLabel.setForeground(Color.BLUE);
            chatInput.setEnabled(true); // 출제자도 채팅은 가능
            appendToChat("[알림] 당신은 '출제자'입니다. 제시어를 그려주세요!");
        } else {
            try {
                // 정답자에게는 글자수만 보여줌 (예: "_ _")
                int len = Integer.parseInt(info);
                StringBuilder sb = new StringBuilder();
                for(int i=0; i<len; i++) sb.append("_ ");
                keywordLabel.setText("단어: " + sb.toString());
                keywordLabel.setForeground(Color.BLACK);
                chatInput.setEnabled(true);
                chatInput.requestFocus();
                appendToChat("[알림] 당신은 '정답자'입니다. 그림을 보고 정답을 맞춰보세요!");
            } catch (Exception e) {
                keywordLabel.setText("단어: ???");
            }
        }
    }

    private void appendToChat(String text) {
        chatDisplay.append(text + "\n");
        chatDisplay.setCaretPosition(chatDisplay.getDocument().getLength());
    }
}