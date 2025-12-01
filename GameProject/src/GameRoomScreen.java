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

public class GameRoomScreen extends JFrame {

    private static final long serialVersionUID = 1L;
    private JPanel contentPane;
    private JTextArea chatDisplay;
    private JTextField chatInput;
    private DrawingPanel drawingCanvas;
    private JPanel userListPanel;
    private JLabel keywordLabel;
    private JLabel scoreLabel;
    private JButton startGameBtn; 

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String nickname;
    private String serverAddress;
    private int serverPort;
    private String roomTitle;

    private boolean isDrawer = false; 
    private Color currentPenColor = Color.BLACK; 
    private Color currentColor = Color.BLACK; 
    private float currentStroke = 2.0f; 
    
    private boolean isGameRunning = false; 
    private int score = 0; 
    private boolean isHost = false; 
    
    private static final float ERASER_STROKE = 50.0f;

    // 디자인 상수
    private static final Color BG_COLOR = new Color(230, 240, 255); 
    private static final Color BTN_COLOR = new Color(0, 51, 153); 
    private static final Color TEXT_COLOR = Color.WHITE; 
    private static Font MAIN_FONT; 

    // 프로필 이미지 리스트
    private List<ImageIcon> profileImages = new ArrayList<>();
    private int userCount = 0; 

    static {
        try {
            MAIN_FONT = Font.createFont(Font.TRUETYPE_FONT, new File("Jalnan2TTF.ttf")).deriveFont(16f);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(MAIN_FONT);
        } catch (IOException | FontFormatException e) {
            MAIN_FONT = new Font("맑은 고딕", Font.BOLD, 16); 
        }
    }
    
    private void loadImages() {
        try {
            profileImages.add(new ImageIcon(new ImageIcon("image.png").getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH)));
            profileImages.add(new ImageIcon(new ImageIcon("image3.png").getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH)));
        } catch (Exception e) {}
    }

    // --- 내부 클래스: 그림판 ---
    class DrawingPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private List<StrokePath> paths = new ArrayList<>();
        private StrokePath currentPath = null;
        private Point mousePoint = null; 

        public DrawingPanel() {
            setBackground(Color.WHITE);
            setBorder(new LineBorder(Color.BLACK, 2)); 
            
            // [수정] 초기 커서는 기본 화살표로 설정
            setCursor(Cursor.getDefaultCursor());

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    if (isDrawer && isGameRunning) {
                        mousePoint = e.getPoint();
                        repaint();
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

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    mousePoint = null;
                    repaint();
                }
                @Override
                public void mouseEntered(MouseEvent e) {
                    // [핵심 수정] 게임 중이고 + 내가 출제자일 때만 십자 커서로 변경
                    if (isGameRunning && isDrawer) {
                        setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
                    } else {
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
            });
        }

        public void executeDraw(int x, int y, String type, Color color) {
            if ("START".equals(type)) {
                currentPath = new StrokePath(color, currentStroke); 
                currentPath.addPoint(new Point(x, y));
                paths.add(currentPath);
            } else if ("DRAG".equals(type)) {
                if (currentPath != null) currentPath.addPoint(new Point(x, y));
            }
            repaint();
        }
        
        public void startStroke(Point p, Color color, float strokeSize) {
            currentPath = new StrokePath(color, strokeSize);
            currentPath.addPoint(p);
            paths.add(currentPath);
            repaint();
        }
        
        public void addPointToStroke(Point p) {
            if (currentPath != null) {
                currentPath.addPoint(p);
                repaint();
            }
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
            
            // 게임 중일 때만 동그라미 표시
            if (isDrawer && isGameRunning && mousePoint != null) {
                g2.setStroke(new BasicStroke(1.0f));
                g2.setColor(new Color(0, 0, 0, 128));
                int size = (int) currentStroke;
                g2.drawOval(mousePoint.x - size/2, mousePoint.y - size/2, size, size);
            }
        }
    }

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

    // --- 내부 클래스: 유저 카드 ---
    class UserStatusPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        JLabel nameLabel;
        JLabel imageLabel;

        public UserStatusPanel(String userName) {
            setLayout(new BorderLayout(5, 5));
            setBackground(Color.WHITE);
            setBorder(new LineBorder(BTN_COLOR, 2, true));
            setPreferredSize(new Dimension(100, 120));
            setName(userName); 

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

    public GameRoomScreen(String nickname, String roomTitle, String serverAddress, int serverPort) {
        this.nickname = nickname;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.roomTitle = roomTitle;
        
        loadImages(); 

        setTitle(roomTitle + " - " + nickname);
        
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

        // ==================== 상단 패널 ====================
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

        

        startGameBtn = createStyledButton("게임 시작");
        startGameBtn.setPreferredSize(new Dimension(150, 50));
        startGameBtn.addActionListener(e -> sendProtocol("GAME_START::"));
        
        topLeftGroup.add(exitBtn);
        topLeftGroup.add(roomNameLabel);
        topLeftGroup.add(startGameBtn);

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

        // ==================== 중앙 패널 ====================
        JPanel centerPanel = new JPanel(new BorderLayout(20, 0));
        centerPanel.setBackground(BG_COLOR);

        // 도구
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
        
        JButton eraserButton = createToolButton("지우개");
        eraserButton.addActionListener(e -> {
            if (!isGameRunning) return; 
            currentColor = Color.WHITE;
            currentStroke = ERASER_STROKE; 
            drawingCanvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            if (isDrawer) sendProtocol("RGB::255,255,255");
        });
        toolPanel.add(eraserButton); toolPanel.add(Box.createVerticalStrut(10));    
        
        JButton clearButton = createToolButton("삭제");
        clearButton.addActionListener(e -> {
            if (isGameRunning) sendProtocol("CLEAR::");
        }); 
        toolPanel.add(clearButton);

        toolPanel.add(Box.createVerticalGlue());
        centerPanel.add(toolPanel, BorderLayout.WEST);

        // 그림판
        drawingCanvas = new DrawingPanel();
        centerPanel.add(drawingCanvas, BorderLayout.CENTER);

        // 채팅
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

        // ==================== 하단 패널 ====================
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

        // ==================== 이벤트 핸들러 ====================
        ActionListener sendAction = e -> handleInput();
        chatInput.addActionListener(sendAction);
        sendButton.addActionListener(sendAction);

        drawingCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isDrawer && isGameRunning) {
                    drawingCanvas.startStroke(e.getPoint(), currentColor, currentStroke);
                    sendProtocol("DRAW::" + e.getX() + "," + e.getY() + ",START");
                }
            }
        });

        drawingCanvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isDrawer && isGameRunning) {
                    drawingCanvas.addPointToStroke(e.getPoint());
                    sendProtocol("DRAW::" + e.getX() + "," + e.getY() + ",DRAG");
                }
            }
        });
        
        connectToServer();
    }
    
    private void checkExit() {
        if (isGameRunning) {
            JOptionPane.showMessageDialog(this, "게임 진행 중에는 나갈 수 없습니다!", "경고", JOptionPane.WARNING_MESSAGE);
        } else {
            int confirm = JOptionPane.showConfirmDialog(this, "정말 나가시겠습니까?", "나가기", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close(); 
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                new CreateRoomScreen(nickname).setVisible(true);
                dispose(); 
            }
        }
    }
    
    // --- UI 헬퍼 메소드들 ---
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
        btn.setMaximumSize(new Dimension(40, 40));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            if (!isGameRunning) return;
            
            currentColor = color; 
            currentStroke = 2.0f; 
            drawingCanvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            if (isDrawer) {
                sendProtocol("RGB::" + color.getRed() + "," + color.getGreen() + "," + color.getBlue());
            }
        });
        return btn;
    }
    
    private JButton createStyledButton(String text) {
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
        JButton btn = new JButton(text);
        btn.setFont(MAIN_FONT.deriveFont(11f));
        btn.setBackground(Color.WHITE); 
        btn.setForeground(Color.BLACK); 
        btn.setBorder(new LineBorder(Color.BLACK, 2));
        btn.setPreferredSize(new Dimension(50, 50));
        btn.setMaximumSize(new Dimension(50, 50));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setHorizontalTextPosition(JButton.CENTER);
        btn.setVerticalTextPosition(JButton.BOTTOM);
        return btn;
    }
    
    private JLabel createStyledLabel(String text, float fontSize) {
        JLabel label = new JLabel(text);
        label.setFont(MAIN_FONT.deriveFont(Font.BOLD, fontSize));
        label.setOpaque(true);
        label.setBackground(BTN_COLOR);
        label.setForeground(TEXT_COLOR);
        label.setBorder(new LineBorder(BTN_COLOR, 2, true)); 
        return label;
    }

    // --- 로직 메소드 ---
    private void handleInput() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        sendProtocol("ANSWER::" + text); 
        chatInput.setText("");
    }
    
    private void setupRound(boolean isMeDrawer, String info) {
        this.isGameRunning = true; // 게임 중 상태로 변경
        
        this.isDrawer = isMeDrawer;
        drawingCanvas.clear();
        currentPenColor = Color.BLACK; 
        currentColor = Color.BLACK; 
        currentStroke = 2.0f; 
        
        drawingCanvas.setEnabled(isDrawer);
        startGameBtn.setEnabled(false); 

        // [추가] 라운드 시작 시 출제자면 십자 커서, 아니면 기본 커서
        if (isDrawer) {
            keywordLabel.setText(info); 
            drawingCanvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            appendToChat("[알림] 당신은 '출제자'입니다. 제시어를 그려주세요!");
        } else {
            drawingCanvas.setCursor(Cursor.getDefaultCursor());
            try {
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

            new Thread(this::readServerMessages).start();

        } catch (IOException e) {
            appendToChat("[오류] 서버 연결 실패.");
        }
    }

    private void sendProtocol(String msg) {
        if (out != null) out.println(msg);
    }

    private void readServerMessages() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                final String message = msg;
                EventQueue.invokeLater(() -> processMessage(message));
            }
        } catch (IOException e) {
            appendToChat("[시스템] 서버와 연결이 끊어졌습니다.");
        } finally {
            try { if(socket != null) socket.close(); } catch(IOException e) {}
        }
    }

    private void processMessage(String msg) {
        if (msg.startsWith("CHAT::")) {
            appendToChat(msg.substring(6));
        } 
        else if (msg.startsWith("START::drawer,")) {
            String keyword = msg.substring("START::drawer,".length());
            setupRound(true, keyword);
        } 
        else if (msg.startsWith("START::guesser,")) {
            String lengthStr = msg.substring("START::guesser,".length());
            setupRound(false, lengthStr);
        } 
        else if (msg.startsWith("NOTICE_CORRECT::")) {
            String content = msg.substring("NOTICE_CORRECT::".length());
            
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
        	// [핵심] 게임 종료 시 모든 상태 초기화
        	this.isGameRunning = false; 
        	this.isDrawer = false; 
        	
        	// 커서 즉시 기본으로 변경
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
            try {
                String[] parts = msg.substring(6).split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                String type = parts[2]; 
                drawingCanvas.executeDraw(x, y, type, currentPenColor); 
            } catch (Exception e) {}
        } 
        else if (msg.startsWith("RGB::")) {
            try {
                String[] colors = msg.substring(5).split(",");
                int r = Integer.parseInt(colors[0].trim());
                int g = Integer.parseInt(colors[1].trim());
                int b = Integer.parseInt(colors[2].trim());
                currentPenColor = new Color(r, g, b); 
                
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
            startGameBtn.setEnabled(true);
            setTitle(roomTitle + " - " + nickname + " (방장)");
            isHost = true;
        }
        else if (msg.startsWith("ROLE::GUEST")) { 
            startGameBtn.setEnabled(false);
            setTitle(roomTitle + " - " + nickname);
            isHost = false;
        }
        else if (msg.startsWith("LOGIN::")) {
            String newName = msg.substring(7).trim(); 
            boolean exists = false;
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
    
    private void appendToChat(String text) {
        chatDisplay.append(text + "\n");
        chatDisplay.setCaretPosition(chatDisplay.getDocument().getLength());
    }
    
    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    String nickname = JOptionPane.showInputDialog("닉네임을 입력하세요", "Guest");
                    if (nickname != null && !nickname.isEmpty()) {
                        GameRoomScreen frame = new GameRoomScreen(nickname, "테스트 방", "localhost", 9999);
                        frame.setVisible(true);
                    }
                } catch (Exception e) {}
            }
        });
    }
}