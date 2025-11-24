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
    private static final float ERASER_STROKE = 50.0f;

    // 디자인 상수
    private static final Color BG_COLOR = new Color(230, 240, 255); 
    private static final Color BTN_COLOR = new Color(0, 51, 153); 
    private static final Color TEXT_COLOR = Color.WHITE; 
    private static Font MAIN_FONT; 

    // 프로필 이미지 리스트
    private List<ImageIcon> profileImages = new ArrayList<>();
    private int userCount = 0; 

    // 폰트 로딩
    static {
        try {
            MAIN_FONT = Font.createFont(Font.TRUETYPE_FONT, new File("Jalnan2TTF.ttf")).deriveFont(16f);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(MAIN_FONT);
        } catch (IOException | FontFormatException e) {
            MAIN_FONT = new Font("맑은 고딕", Font.BOLD, 16); 
            System.out.println("폰트 로드 실패. 기본 폰트를 사용합니다.");
        }
    }
    
    private void loadImages() {
        try {
            profileImages.add(new ImageIcon(new ImageIcon("image.png").getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH)));
            profileImages.add(new ImageIcon(new ImageIcon("image3.png").getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH)));
        } catch (Exception e) {
            System.out.println("프로필 이미지 로드 실패: " + e.getMessage());
        }
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
            setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    if (isDrawer) {
                        mousePoint = e.getPoint();
                        repaint();
                    }
                }
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (isDrawer) {
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
                    setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
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
            
            if (isDrawer && mousePoint != null) {
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
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); 
        setBounds(100, 100, 1280, 800);
        contentPane = new JPanel();
        contentPane.setBackground(BG_COLOR);
        contentPane.setBorder(new EmptyBorder(20, 20, 20, 20));
        contentPane.setLayout(new BorderLayout(20, 20));
        setContentPane(contentPane);

        // ==================== [수정] 상단 패널 레이아웃 ====================
        JPanel topPanel = new JPanel(new BorderLayout()); 
        topPanel.setBackground(BG_COLOR);
        topPanel.setBorder(new EmptyBorder(0, 0, 10, 0)); // 하단 여백

        // 1. [왼쪽 그룹] 방 이름 + 게임 시작 버튼
        // - 왼쪽 여백 80px: 그림판 라인과 맞춤
        JPanel topLeftGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0)); 
        topLeftGroup.setBackground(BG_COLOR);
        topLeftGroup.setBorder(new EmptyBorder(0, 80, 0, 0)); 
        
        JLabel roomNameLabel = createStyledLabel("방 이름: " + roomTitle, 16f);
        roomNameLabel.setPreferredSize(new Dimension(150, 50));
        roomNameLabel.setHorizontalAlignment(SwingConstants.CENTER); 

        startGameBtn = createStyledButton("게임 시작");
        startGameBtn.setPreferredSize(new Dimension(150, 50));
        startGameBtn.addActionListener(e -> sendProtocol("GAME_START::"));
        
        // 왼쪽 그룹에는 방 이름과 버튼만 넣습니다.
        topLeftGroup.add(roomNameLabel);
        topLeftGroup.add(startGameBtn);

        // 2. [가운데 그룹] 제시어 (여기가 수정됨!)
        // - 제시어를 별도의 그룹에서 빼서 BorderLayout.CENTER에 넣음
        // - 이렇게 하면 버튼과 오른쪽 스코어 사이의 공간을 혼자 차지하며 자동으로 가운데 정렬됨
        keywordLabel = new JLabel("제시어");
        keywordLabel.setFont(MAIN_FONT.deriveFont(Font.BOLD, 40f));
        keywordLabel.setForeground(Color.BLACK);
        keywordLabel.setHorizontalAlignment(SwingConstants.CENTER); // 라벨 내부에서도 텍스트 가운데 정렬

        // 3. [오른쪽 그룹] 스코어
        JPanel topRightGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        topRightGroup.setBackground(BG_COLOR);
        topRightGroup.setPreferredSize(new Dimension(300, 50)); // 채팅창 너비와 동일
        
        scoreLabel = createStyledLabel("SCORE: 0", 16f);
        scoreLabel.setPreferredSize(new Dimension(150, 50)); 
        scoreLabel.setHorizontalAlignment(SwingConstants.CENTER);

        topRightGroup.add(scoreLabel);

        // [최종 배치]
        topPanel.add(topLeftGroup, BorderLayout.WEST);   // 왼쪽: 버튼들
        topPanel.add(keywordLabel, BorderLayout.CENTER); // 가운데: 제시어 (남은 공간의 중앙)
        topPanel.add(topRightGroup, BorderLayout.EAST);  // 오른쪽: 스코어

        contentPane.add(topPanel, BorderLayout.NORTH);
        // ===================================================================

        // ==================== 중앙 패널 ====================
        JPanel centerPanel = new JPanel(new BorderLayout(20, 0));
        centerPanel.setBackground(BG_COLOR);

        // ----- 왼쪽 도구 패널 -----
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
            currentColor = Color.WHITE;
            currentStroke = ERASER_STROKE; 
            drawingCanvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            if (isDrawer) sendProtocol("RGB::255,255,255");
        });
        toolPanel.add(eraserButton); toolPanel.add(Box.createVerticalStrut(10));    
        
        JButton clearButton = createToolButton("삭제");
        clearButton.addActionListener(e -> sendProtocol("CLEAR::")); 
        toolPanel.add(clearButton);

        toolPanel.add(Box.createVerticalGlue());

        centerPanel.add(toolPanel, BorderLayout.WEST);

        // ----- 가운데 그림판 -----
        drawingCanvas = new DrawingPanel();
        centerPanel.add(drawingCanvas, BorderLayout.CENTER);

        // ----- 오른쪽 채팅 패널 -----
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
                if (isDrawer) {
                    drawingCanvas.startStroke(e.getPoint(), currentColor, currentStroke);
                    sendProtocol("DRAW::" + e.getX() + "," + e.getY() + ",START");
                }
            }
        });

        drawingCanvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isDrawer) {
                    drawingCanvas.addPointToStroke(e.getPoint());
                    sendProtocol("DRAW::" + e.getX() + "," + e.getY() + ",DRAG");
                }
            }
        });
        
        connectToServer();
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
        // 패딩(내부 여백) 제거: setPreferredSize로 크기 제어
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
        this.isDrawer = isMeDrawer;
        drawingCanvas.clear();
        currentPenColor = Color.BLACK; 
        currentColor = Color.BLACK; 
        currentStroke = 2.0f; 
        
        drawingCanvas.setEnabled(isDrawer);
        startGameBtn.setEnabled(false); 

        if (isDrawer) {
            keywordLabel.setText(info); 
            appendToChat("[알림] 당신은 '출제자'입니다. 제시어를 그려주세요!");
        } else {
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
            appendToChat("[시스템] 서버 연결 종료.");
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
            keywordLabel.setText("정답!");
            keywordLabel.setForeground(Color.RED);
            drawingCanvas.clear(); 
            startGameBtn.setEnabled(false); 
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
        else if (msg.startsWith("LOGIN::")) {
            String newName = msg.substring(7);
            userListPanel.add(new UserStatusPanel(newName));
            userListPanel.revalidate();
            userListPanel.repaint();
        }
        else if (msg.startsWith("LOGOUT::")) {
            String nameToRemove = msg.substring(8);
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