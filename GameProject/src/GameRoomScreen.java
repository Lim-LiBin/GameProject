import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

// 게임의 메인 화면 (그림판, 채팅, 사용자 목록, 서버 통신 담당)
public class GameRoomScreen extends JFrame {

    private static final long serialVersionUID = 1L;
    
    // UI 컴포넌트 선언
    private JPanel contentPane;
    private JTextArea chatDisplay;    // 채팅 기록 표시
    private JTextField chatInput;     // 채팅 입력
    private DrawingPanel drawingCanvas; // 그림판 패널
    private JPanel userListPanel;     // 접속자 목록 표시 패널
    private JLabel keywordLabel;      // 제시어 또는 힌트 표시
    private JLabel scoreLabel;        // 점수 표시
    private JButton startGameBtn;     // 게임 시작 버튼 (방장만 활성화)

    // 통신 관련 변수
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String nickname;
    private String serverAddress;
    private int serverPort;
    private String roomTitle;

    // 그리기 도구 및 상태 변수
    private boolean isDrawer = false;            // 현재 내가 출제자(그리는 사람)인지 여부
    private Color currentPenColor = Color.BLACK; // 현재 펜 색상
    private Color currentColor = Color.BLACK;    // 선택된 색상 저장용
    private float currentStroke = 2.0f;          // 선 굵기
    
    // 게임 진행 상태 변수
    private boolean isGameRunning = false; // 게임 진행 중 여부
    private int score = 0;                 // 내 점수
    private boolean isHost = false;        // 내가 방장인지 여부
    
    private static final float ERASER_STROKE = 50.0f; // 지우개 굵기

    // 디자인 상수 (색상, 폰트)
    private static final Color BG_COLOR = new Color(230, 240, 255); 
    private static final Color BTN_COLOR = new Color(0, 51, 153); 
    private static final Color TEXT_COLOR = Color.WHITE; 
    private static Font MAIN_FONT; 

    // 프로필 이미지 관리
    private List<ImageIcon> profileImages = new ArrayList<>();
    private int userCount = 0; 

    // 폰트 로딩 (파일이 없으면 기본 폰트 사용)
    static {
        try {
            MAIN_FONT = Font.createFont(Font.TRUETYPE_FONT, new File("Jalnan2TTF.ttf")).deriveFont(16f);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(MAIN_FONT);
        } catch (IOException | FontFormatException e) {
            MAIN_FONT = new Font("맑은 고딕", Font.BOLD, 16); 
        }
    }
    
    // 사용자 프로필용 이미지 로드
    private void loadImages() {
        try {
            profileImages.add(new ImageIcon(new ImageIcon("image.png").getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH)));
            profileImages.add(new ImageIcon(new ImageIcon("image3.png").getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH)));
        } catch (Exception e) {}
    }

    // 내부 클래스: 그림판 패널
    // 실제 그림이 그려지는 영역. 마우스 이벤트를 감지하여 서버로 전송하고, 화면을 다시 그립니다.
    class DrawingPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private List<StrokePath> paths = new ArrayList<>(); // 그려진 모든 선(획)들의 정보를 저장
        private StrokePath currentPath = null;              // 현재 그리고 있는 선
        private Point mousePoint = null;                    // 마우스 커서 위치 (커서 시각화용)

        public DrawingPanel() {
            setBackground(Color.WHITE);
            setBorder(new LineBorder(Color.BLACK, 2)); 
            
            setCursor(Cursor.getDefaultCursor());

            // 마우스 이동 감지 (그리기 중 미리보기 및 커서 위치 갱신)
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    if (isDrawer && isGameRunning) {
                        mousePoint = e.getPoint();
                        repaint(); // 커서 위치 갱신을 위해 다시 그리기
                    }
                }
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (isDrawer && isGameRunning) {
                        mousePoint = e.getPoint();
                        repaint(); 
                    }
                }
            });

            // 마우스 진입/이탈 시 커서 상태 관리
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    mousePoint = null;
                    repaint();
                }
                @Override
                public void mouseEntered(MouseEvent e) {
                    // 출제자일 때만 십자 커서 표시
                    if (isGameRunning && isDrawer) {
                        setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
                    } else {
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
            });
        }

        // 수신 처리: 서버로부터 받은 그리기 데이터를 화면에 반영
        public void executeDraw(int x, int y, String type, Color color) {
            if ("START".equals(type)) { // 선 그리기 시작
                currentPath = new StrokePath(color, currentStroke); 
                currentPath.addPoint(new Point(x, y));
                paths.add(currentPath);
            } else if ("DRAG".equals(type)) { // 선 이어 그리기
                if (currentPath != null) currentPath.addPoint(new Point(x, y));
            }
            repaint(); // 화면 갱신 요청 -> paintComponent 호출됨
        }
        
        // 로컬 처리: 마우스 클릭 시 새로운 선 시작 (내가 그릴 때)
        public void startStroke(Point p, Color color, float strokeSize) {
            currentPath = new StrokePath(color, strokeSize);
            currentPath.addPoint(p);
            paths.add(currentPath);
            repaint();
        }
        
        // 로컬 처리: 마우스 드래그 시 점 추가 (내가 그릴 때)
        public void addPointToStroke(Point p) {
            if (currentPath != null) {
                currentPath.addPoint(p);
                repaint();
            }
        }
        
        // 캔버스 초기화
        public void clear() {
            paths.clear();
            currentPath = null;
            repaint();
        }

        // 실제 화면에 그림을 그리는 메서드 (Swing에 의해 자동 호출)
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // 계단 현상 제거
            
            // 저장된 모든 경로(선)를 순회하며 그림
            for (StrokePath path : paths) {
                g2.setColor(path.color);
                g2.setStroke(new BasicStroke(path.strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                
                List<Point> points = path.points;
                if (points.size() > 1) {
                    for (int i = 0; i < points.size() - 1; i++) {
                        Point p1 = points.get(i);
                        Point p2 = points.get(i + 1);
                        g2.drawLine(p1.x, p1.y, p2.x, p2.y);
                    }
                }
            }
            
            // 출제자 화면에 현재 브러시 크기만큼 동그라미(커서) 표시
            if (isDrawer && isGameRunning && mousePoint != null) {
                g2.setStroke(new BasicStroke(1.0f));
                g2.setColor(new Color(0, 0, 0, 128));
                int size = (int) currentStroke;
                g2.drawOval(mousePoint.x - size/2, mousePoint.y - size/2, size, size);
            }
        }
    }

    // 선 하나(색상, 굵기, 점들의 집합)를 표현하는 데이터 클래스
    class StrokePath {
        Color color;
        float strokeSize;
        List<Point> points = new ArrayList<>();
        public StrokePath(Color color, float strokeSize) {
            this.color = color;
            this.strokeSize = strokeSize;
        }
        public void addPoint(Point p) { points.add(p); }
    }

    // 내부 클래스: 유저 카드
    // 하단 유저 목록에 표시될 개별 사용자 패널
    class UserStatusPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        JLabel nameLabel;
        JLabel imageLabel;

        public UserStatusPanel(String userName) {
            setLayout(new BorderLayout(5, 5));
            setBackground(Color.WHITE);
            setBorder(new LineBorder(BTN_COLOR, 2, true));
            setPreferredSize(new Dimension(100, 120));
            setName(userName); // 컴포넌트 식별을 위해 이름 설정

            // 프로필 이미지 순환 할당
            ImageIcon profileIcon = null;
            if (!profileImages.isEmpty()) {
                profileIcon = profileImages.get(userCount % profileImages.size());
                userCount++;
            }

            imageLabel = new JLabel(profileIcon);
            imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imageLabel.setBorder(new EmptyBorder(10, 0, 0, 0));

            nameLabel = new JLabel(userName);
            nameLabel.setFont(MAIN_FONT.deriveFont(14f));
            nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
            nameLabel.setBorder(new EmptyBorder(0, 0, 10, 0));

            add(imageLabel, BorderLayout.CENTER);
            add(nameLabel, BorderLayout.SOUTH);
        }
    }

    // 생성자: UI 배치 및 초기화
    public GameRoomScreen(String nickname, String roomTitle, String serverAddress, int serverPort) {
        this.nickname = nickname;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.roomTitle = roomTitle;
        
        loadImages(); 

        setTitle(roomTitle + " - " + nickname);
        
        // 닫기 버튼 클릭 시 바로 종료하지 않고 확인 창 띄움
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                checkExit(); 
            }
        });
        
        setBounds(100, 100, 1280, 800);
        contentPane = new JPanel();
        contentPane.setBackground(BG_COLOR);
        contentPane.setBorder(new EmptyBorder(20, 20, 20, 20));
        contentPane.setLayout(new BorderLayout(20, 20));
        setContentPane(contentPane);

        // 상단 패널 (나가기, 방제, 시작버튼, 제시어/상태, 점수)
        JPanel topPanel = new JPanel(new BorderLayout()); 
        topPanel.setBackground(BG_COLOR);
        topPanel.setBorder(new EmptyBorder(0, 0, 10, 0));

        JPanel topLeftGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0)); 
        topLeftGroup.setBackground(BG_COLOR);
        topLeftGroup.setBorder(new EmptyBorder(0, 0, 0, 0)); 
        
        JButton exitBtn = createStyledButton("나가기");
        exitBtn.setBackground(new Color(220, 50, 50)); 
        exitBtn.setPreferredSize(new Dimension(100, 50));
        exitBtn.addActionListener(e -> checkExit()); 
        
        JLabel roomNameLabel = createStyledLabel(roomTitle, 20f);
        roomNameLabel.setPreferredSize(new Dimension(150, 50));
        roomNameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        roomNameLabel.setBackground(new Color(230, 240, 255));
        roomNameLabel.setForeground(Color.BLACK);
        roomNameLabel.setBorder(null);

        // 게임 시작 버튼: 클릭 시 서버로 GAME_START 프로토콜 전송
        startGameBtn = createStyledButton("게임 시작");
        startGameBtn.setPreferredSize(new Dimension(150, 50));
        startGameBtn.addActionListener(e -> sendProtocol("GAME_START::"));
        
        topLeftGroup.add(exitBtn);
        topLeftGroup.add(roomNameLabel);
        topLeftGroup.add(startGameBtn);

        // 중앙 상태 표시 라벨 (제시어 or 대기중 메시지)
        keywordLabel = new JLabel("게임 대기 중...");
        keywordLabel.setFont(MAIN_FONT.deriveFont(Font.BOLD, 40f));
        keywordLabel.setForeground(Color.BLACK);
        keywordLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel topRightGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        topRightGroup.setBackground(BG_COLOR);
        topRightGroup.setPreferredSize(new Dimension(300, 50)); 
        
        scoreLabel = createStyledLabel("SCORE: 0", 16f);
        scoreLabel.setPreferredSize(new Dimension(150, 50)); 
        scoreLabel.setHorizontalAlignment(SwingConstants.CENTER);

        topRightGroup.add(scoreLabel);

        topPanel.add(topLeftGroup, BorderLayout.WEST);    
        topPanel.add(keywordLabel, BorderLayout.CENTER); 
        topPanel.add(topRightGroup, BorderLayout.EAST);  

        contentPane.add(topPanel, BorderLayout.NORTH);

        // 중앙 패널 (왼쪽: 도구, 중앙: 캔버스, 오른쪽: 채팅)
        JPanel centerPanel = new JPanel(new BorderLayout(20, 0));
        centerPanel.setBackground(BG_COLOR);

        // [도구 패널] 색상 버튼 및 지우개/삭제 버튼
        JPanel toolPanel = new JPanel();
        toolPanel.setLayout(new BoxLayout(toolPanel, BoxLayout.Y_AXIS));
        toolPanel.setBackground(BG_COLOR);
        toolPanel.setPreferredSize(new Dimension(60, 0));
        toolPanel.setBorder(new EmptyBorder(0, 15, 0, 0)); 

        toolPanel.add(createColorButton(Color.RED)); toolPanel.add(Box.createVerticalStrut(10));
        toolPanel.add(createColorButton(Color.ORANGE)); toolPanel.add(Box.createVerticalStrut(10));
        toolPanel.add(createColorButton(Color.GREEN)); toolPanel.add(Box.createVerticalStrut(10));
        toolPanel.add(createColorButton(Color.BLUE)); toolPanel.add(Box.createVerticalStrut(10));
        toolPanel.add(createColorButton(new Color(128, 0, 128))); toolPanel.add(Box.createVerticalStrut(10));
        toolPanel.add(createColorButton(Color.BLACK)); toolPanel.add(Box.createVerticalStrut(20));
        
        // 지우개 버튼: 흰색 펜 + 굵은 두께로 변경
        JButton eraserButton = createToolButton("지우개");
        eraserButton.addActionListener(e -> {
            if (!isGameRunning) return; 
            currentColor = Color.WHITE;
            currentStroke = ERASER_STROKE; 
            drawingCanvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            if (isDrawer) sendProtocol("RGB::255,255,255");
        });
        toolPanel.add(eraserButton); toolPanel.add(Box.createVerticalStrut(10));    
        
        // 전체 삭제 버튼: 서버에 CLEAR 프로토콜 전송
        JButton clearButton = createToolButton("삭제");
        clearButton.addActionListener(e -> {
            if (isGameRunning) sendProtocol("CLEAR::");
        }); 
        toolPanel.add(clearButton);

        toolPanel.add(Box.createVerticalGlue());
        centerPanel.add(toolPanel, BorderLayout.WEST);

        // [그림판] 중앙 배치
        drawingCanvas = new DrawingPanel();
        centerPanel.add(drawingCanvas, BorderLayout.CENTER);

        // [채팅 패널] 오른쪽 배치
        JPanel chatPanel = new JPanel(new BorderLayout(0, 10));
        chatPanel.setBackground(BG_COLOR);
        chatPanel.setPreferredSize(new Dimension(300, 0)); 

        chatDisplay = new JTextArea();
        chatDisplay.setEditable(false);
        chatDisplay.setLineWrap(true);
        chatDisplay.setFont(MAIN_FONT.deriveFont(14f)); 
        chatDisplay.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(Color.BLACK, 2),
                new EmptyBorder(10, 10, 10, 10))); 
        
        JScrollPane chatScrollPane = new JScrollPane(chatDisplay);
        chatScrollPane.setBorder(null);

        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBackground(BG_COLOR);
        
        chatInput = new JTextField();
        chatInput.setFont(MAIN_FONT.deriveFont(14f));
        chatInput.setBorder(new LineBorder(Color.BLACK, 2));
        
        JButton sendButton = createStyledButton("전송");
        sendButton.setPreferredSize(new Dimension(80, 40));

        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        chatPanel.add(chatScrollPane, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        centerPanel.add(chatPanel, BorderLayout.EAST);
        contentPane.add(centerPanel, BorderLayout.CENTER);

        // 하단 패널 (접속자 목록)
        userListPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0)); 
        userListPanel.setBackground(BG_COLOR);
        userListPanel.setBorder(new EmptyBorder(0, 80, 0, 0)); 
        userListPanel.setPreferredSize(new Dimension(0, 130)); 
        
        JScrollPane userListScrollPane = new JScrollPane(userListPanel);
        userListScrollPane.setBackground(BG_COLOR);
        userListScrollPane.setBorder(null); 
        userListScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        userListScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        userListScrollPane.getViewport().setBackground(BG_COLOR); 

        contentPane.add(userListScrollPane, BorderLayout.SOUTH);

        // 이벤트 리스너 등록
        ActionListener sendAction = e -> handleInput();
        chatInput.addActionListener(sendAction);
        sendButton.addActionListener(sendAction);

        // 마우스 클릭 시: 그리기 시작 (START 프로토콜 전송)
        drawingCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isDrawer && isGameRunning) {
                    drawingCanvas.startStroke(e.getPoint(), currentColor, currentStroke);
                    sendProtocol("DRAW::" + e.getX() + "," + e.getY() + ",START");
                }
            }
        });

        // 마우스 드래그 시: 선 이어 그리기 (DRAG 프로토콜 전송)
        drawingCanvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isDrawer && isGameRunning) {
                    drawingCanvas.addPointToStroke(e.getPoint());
                    sendProtocol("DRAW::" + e.getX() + "," + e.getY() + ",DRAG");
                }
            }
        });
        
        // 서버 연결 시작
        connectToServer();
    }
    
    // 게임 종료 또는 창 닫기 시 호출
    private void checkExit() {
        if (isGameRunning) {
            JOptionPane.showMessageDialog(this, "게임 진행 중에는 나갈 수 없습니다!", "경고", JOptionPane.WARNING_MESSAGE);
        } else {
            int confirm = JOptionPane.showConfirmDialog(this, "정말 나가시겠습니까?", "나가기", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close(); // 소켓 연결 종료
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                new CreateRoomScreen(nickname).setVisible(true); // 대기실 화면으로 이동
                dispose(); 
            }
        }
    }
    
    // UI 헬퍼 메소드들 (버튼 생성 등 단순 작업)
    private JButton createColorButton(Color color) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(0, 0, getWidth(), getHeight()); 
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(40, 40));
        // (버튼 스타일 설정 생략) 
        btn.addActionListener(e -> {
            if (!isGameRunning) return;
            
            currentColor = color; 
            currentStroke = 2.0f; 
            drawingCanvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            
            // 변경된 색상 정보를 서버에 전송 (다른 클라이언트들과 동기화)
            if (isDrawer) {
                sendProtocol("RGB::" + color.getRed() + "," + color.getGreen() + "," + color.getBlue());
            }
        });
        return btn;
    }
    
    private JButton createStyledButton(String text) {
        // (버튼 스타일링 코드) 
        JButton btn = new JButton(text);
        btn.setFont(MAIN_FONT.deriveFont(Font.BOLD, 16f));
        btn.setBackground(BTN_COLOR);
        btn.setForeground(TEXT_COLOR);
        btn.setBorder(new LineBorder(BTN_COLOR, 2, true)); 
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton createToolButton(String text) {
        // (도구 버튼 스타일링 코드) 
        JButton btn = new JButton(text);
        btn.setFont(MAIN_FONT.deriveFont(11f));
        btn.setBackground(Color.WHITE); 
        btn.setForeground(Color.BLACK); 
        btn.setBorder(new LineBorder(Color.BLACK, 2));
        btn.setPreferredSize(new Dimension(50, 50));
        // ...
        return btn;
    }
    
    private JLabel createStyledLabel(String text, float fontSize) {
        // (라벨 스타일링 코드) 
        JLabel label = new JLabel(text);
        label.setFont(MAIN_FONT.deriveFont(Font.BOLD, fontSize));
        label.setOpaque(true);
        label.setBackground(BTN_COLOR);
        label.setForeground(TEXT_COLOR);
        label.setBorder(new LineBorder(BTN_COLOR, 2, true)); 
        return label;
    }

    // 로직 메소드
    
    // 채팅 입력 처리 (정답 제출도 포함)
    private void handleInput() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        sendProtocol("ANSWER::" + text); // 일반 채팅과 정답 구분 없이 서버로 전송 (서버에서 판단)
        chatInput.setText("");
    }
    
    // 라운드 시작 설정 (출제자/정답자 역할 분담)
    private void setupRound(boolean isMeDrawer, String info) {
        this.isGameRunning = true; // 게임 중 상태로 변경
        
        this.isDrawer = isMeDrawer;
        drawingCanvas.clear();
        currentPenColor = Color.BLACK; 
        currentColor = Color.BLACK; 
        currentStroke = 2.0f; 
        
        drawingCanvas.setEnabled(isDrawer);
        startGameBtn.setEnabled(false); 

        // 역할에 따른 UI 및 안내 메시지 설정
        if (isDrawer) {
            keywordLabel.setText(info); // 출제자는 제시어 표시
            drawingCanvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            appendToChat("[알림] 당신은 '출제자'입니다. 제시어를 그려주세요!");
        } else {
            drawingCanvas.setCursor(Cursor.getDefaultCursor());
            try {
                // 정답자는 글자 수 만큼 'O O O' 형태로 힌트 표시
                int len = Integer.parseInt(info);
                StringBuilder sb = new StringBuilder();
                for(int i=0; i<len; i++) sb.append("O "); 
                keywordLabel.setText(sb.toString());
                chatInput.requestFocus();
                appendToChat("[알림] 당신은 '정답자'입니다. 그림을 보고 정답을 맞춰보세요!");
            } catch (NumberFormatException e) {
                keywordLabel.setText("???");
            }
        }
    }

    // 서버 소켓 연결
    private void connectToServer() {
        try {
            socket = new Socket(serverAddress, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            sendProtocol("LOGIN::" + nickname);
            String fullTitle = getTitle();
            String roomName = fullTitle.split(" - ")[0]; 
            sendProtocol("JOIN_ROOM::" + roomName);
            
            appendToChat("[시스템] 서버에 연결되었습니다.");

            // 별도 스레드에서 서버 메시지 수신 대기
            new Thread(this::readServerMessages).start();

        } catch (IOException e) {
            appendToChat("[오류] 서버 연결 실패.");
        }
    }

    // 서버로 메시지 전송 편의 메서드
    private void sendProtocol(String msg) {
        if (out != null) out.println(msg);
    }

    // 서버 메시지 수신 루프
    private void readServerMessages() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                final String message = msg;
                // UI 업데이트는 반드시 EDT(Event Dispatch Thread)에서 실행
                EventQueue.invokeLater(() -> processMessage(message));
            }
        } catch (IOException e) {
            appendToChat("[시스템] 서버와 연결이 끊어졌습니다.");
        } finally {
            try { if(socket != null) socket.close(); } catch(IOException e) {}
        }
    }

    // 핵심: 서버로부터 받은 프로토콜 분석 및 처리
    private void processMessage(String msg) {
        if (msg.startsWith("CHAT::")) {
            appendToChat(msg.substring(6));
        } 
        else if (msg.startsWith("START::drawer,")) {
            // 게임 시작: 내가 출제자일 때
            String keyword = msg.substring("START::drawer,".length());
            setupRound(true, keyword);
        } 
        else if (msg.startsWith("START::guesser,")) {
            // 게임 시작: 내가 정답자일 때
            String lengthStr = msg.substring("START::guesser,".length());
            setupRound(false, lengthStr);
        } 
        else if (msg.startsWith("NOTICE_CORRECT::")) {
            // 정답자가 나왔을 때 처리
            String content = msg.substring("NOTICE_CORRECT::".length());
            
            // 정답자가 본인이면 점수 증가
            String winner = "";
            int index = content.indexOf("님이");
            if(index != -1) winner = content.substring(0, index);
            
            if (this.nickname.equals(winner) && !isDrawer) {
                score++;
                scoreLabel.setText("SCORE: " + score);
                appendToChat("축하합니다! 정답입니다!"); 
            } else { 
                appendToChat(content);
            }
            
            keywordLabel.setText("정답! 다음 라운드 준비 중...");
            keywordLabel.setForeground(Color.RED);
            drawingCanvas.clear(); 
            startGameBtn.setEnabled(false); 
        }
        else if (msg.startsWith("GAME_OVER::")) {
            // 게임 완전 종료: 점수판 팝업 및 초기화
            this.isGameRunning = false; 
            this.isDrawer = false; 
            
            drawingCanvas.setCursor(Cursor.getDefaultCursor());
            drawingCanvas.clear();
            
            String winner = msg.substring("GAME_OVER::".length());
            JOptionPane.showMessageDialog(this, winner + "님이 10점을 달성하여 우승했습니다!", "게임 종료", JOptionPane.INFORMATION_MESSAGE);
            
            keywordLabel.setText("게임 대기 중...");
            keywordLabel.setForeground(Color.BLACK);
            score = 0;
            scoreLabel.setText("SCORE: 0");
            appendToChat("[시스템] 새 게임을 시작할 준비가 되었습니다.");
            
            if (isHost) {
                startGameBtn.setEnabled(true);
            }
        }
        else if (msg.startsWith("DRAW::")) {
            // 다른 사람이 그린 그림 좌표 수신 -> 내 화면에 그림
            try {
                String[] parts = msg.substring(6).split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                String type = parts[2]; 
                drawingCanvas.executeDraw(x, y, type, currentPenColor); 
            } catch (Exception e) {}
        } 
        else if (msg.startsWith("RGB::")) {
            // 펜 색상 변경 동기화
            try {
                String[] colors = msg.substring(5).split(",");
                int r = Integer.parseInt(colors[0].trim());
                int g = Integer.parseInt(colors[1].trim());
                int b = Integer.parseInt(colors[2].trim());
                currentPenColor = new Color(r, g, b); 
                
                // 흰색(255,255,255)이면 지우개로 간주하여 굵기 조절
                if (r == 255 && g == 255 && b == 255) {
                    currentStroke = ERASER_STROKE;
                } else {
                    currentStroke = 2.0f;
                }
            } catch (Exception e) {}
        } 
        else if (msg.startsWith("CLEAR::")) {
            drawingCanvas.clear();
        }
        else if (msg.startsWith("ROLE::HOST")) { 
            // 방장 권한 획득
            startGameBtn.setEnabled(true);
            setTitle(roomTitle + " - " + nickname + " (방장)");
            isHost = true;
        }
        else if (msg.startsWith("ROLE::GUEST")) { 
            // 일반 참가자 설정
            startGameBtn.setEnabled(false);
            setTitle(roomTitle + " - " + nickname);
            isHost = false;
        }
        else if (msg.startsWith("LOGIN::")) {
            // 새로운 유저 입장 시 리스트에 추가
            String newName = msg.substring(7).trim(); 
            boolean exists = false;
            // 중복 확인
            for (int i = 0; i < userListPanel.getComponentCount(); i++) {
                Component comp = userListPanel.getComponent(i);
                if (comp instanceof UserStatusPanel) {
                    if (newName.equals(comp.getName())) {
                        exists = true;
                        break;
                    }
                }
            }
            if (!exists) {
                userListPanel.add(new UserStatusPanel(newName));
                userListPanel.revalidate();
                userListPanel.repaint();
            }
        }
        else if (msg.startsWith("LOGOUT::")) {
            // 유저 퇴장 시 리스트에서 제거
            String nameToRemove = msg.substring(8).trim(); 
            for (int i = 0; i < userListPanel.getComponentCount(); i++) {
                Component comp = userListPanel.getComponent(i);
                if (comp instanceof UserStatusPanel) {
                    UserStatusPanel panel = (UserStatusPanel) comp;
                    if (panel.getName() != null && panel.getName().equals(nameToRemove)) { 
                        userListPanel.remove(panel);
                        userListPanel.revalidate();
                        userListPanel.repaint();
                        break; 
                    }
                }
            }
        }
    }
    
    // 채팅창에 메시지를 추가하고, 스크롤을 항상 맨 아래로 내려 최신 메시지를 보여줌
    private void appendToChat(String text) {
        chatDisplay.append(text + "\n");
        chatDisplay.setCaretPosition(chatDisplay.getDocument().getLength());
    }
    
    /*
    // 프로그램 시작 지점 (테스트용)
    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    // 게임 시작 전 닉네임을 먼저 입력받음
                    String nickname = JOptionPane.showInputDialog("닉네임을 입력하세요", "Guest");
                    if (nickname != null && !nickname.isEmpty()) {
                        // 닉네임이 입력되면 게임 화면을 생성하고 표시
                        GameRoomScreen frame = new GameRoomScreen(nickname, "테스트 방", "localhost", 9999);
                        frame.setVisible(true);
                    }
                } catch (Exception e) {}
            }
        });
    }
    */
}