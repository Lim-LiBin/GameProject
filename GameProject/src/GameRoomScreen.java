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
    private static final float ERASER_STROKE = 10.0f; 

    // --- 내부 클래스: 그림판 패널 ---
    class DrawingPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private List<StrokePath> paths = new ArrayList<>();
        private StrokePath currentPath = null;

        public DrawingPanel() {
            setBackground(Color.WHITE);
            setBorder(new LineBorder(Color.BLACK));
        }

        public void executeDraw(int x, int y, String type, Color color) {
            if ("START".equals(type)) {
                currentPath = new StrokePath(color, 2.0f); 
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

    class UserStatusPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        JLabel nameLabel;
        public UserStatusPanel(String userName) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createCompoundBorder(new LineBorder(Color.GRAY), new EmptyBorder(5, 10, 5, 10)));
            setPreferredSize(new Dimension(150, 40));
            setName(userName); // 식별을 위해 name 속성 설정
            nameLabel = new JLabel(userName);
            add(nameLabel);
        }
    }

    public GameRoomScreen(String nickname, String roomTitle, String serverAddress, int serverPort) {
        this.nickname = nickname;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.roomTitle = roomTitle;
        
        setTitle(roomTitle + " - " + nickname);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); 
        setBounds(100, 100, 1000, 750);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        contentPane.setLayout(new BorderLayout(10, 10));
        setContentPane(contentPane);

        // 상단 패널
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 5));
        JLabel roomNameLabel = new JLabel("방 이름: " + roomTitle);
        
        startGameBtn = new JButton("게임 시작");
        startGameBtn.addActionListener(e -> sendProtocol("GAME_START::")); 

        keywordLabel = new JLabel("게임 대기 중...");
        keywordLabel.setFont(keywordLabel.getFont().deriveFont(20.0f));
        scoreLabel = new JLabel("SCORE: 0");

        topPanel.add(roomNameLabel);
        topPanel.add(startGameBtn); 
        topPanel.add(keywordLabel);
        topPanel.add(scoreLabel);
        contentPane.add(topPanel, BorderLayout.NORTH);

        // 우측 유저 목록
        userListPanel = new JPanel();
        userListPanel.setLayout(new BoxLayout(userListPanel, BoxLayout.Y_AXIS));
        userListPanel.setBorder(new TitledBorder("참가자"));
        
        JScrollPane userListScrollPane = new JScrollPane(userListPanel);
        userListScrollPane.setPreferredSize(new Dimension(170, 0));
        contentPane.add(userListScrollPane, BorderLayout.EAST);

        // 중앙 그림판 및 도구
        JPanel drawingAreaPanel = new JPanel(new BorderLayout(5, 5));
        JPanel toolPanel = new JPanel();
        toolPanel.setLayout(new BoxLayout(toolPanel, BoxLayout.Y_AXIS));
        toolPanel.setPreferredSize(new Dimension(80, 0));
        
        toolPanel.add(createColorButton(Color.BLACK));
        toolPanel.add(createColorButton(Color.RED));
        toolPanel.add(createColorButton(Color.ORANGE));
        toolPanel.add(createColorButton(Color.YELLOW));
        toolPanel.add(createColorButton(Color.GREEN));
        toolPanel.add(createColorButton(Color.BLUE));
        
        JButton eraserButton = new JButton("지우개");
        eraserButton.addActionListener(e -> {
        	currentColor = drawingCanvas.getBackground(); 
        	currentStroke = ERASER_STROKE;
        });
        toolPanel.add(eraserButton);
        
        JButton clearButton = new JButton("전체삭제");
        clearButton.addActionListener(e -> sendProtocol("CLEAR::")); 
        toolPanel.add(clearButton);

        drawingAreaPanel.add(toolPanel, BorderLayout.WEST);
        drawingCanvas = new DrawingPanel();
        drawingAreaPanel.add(drawingCanvas, BorderLayout.CENTER);
        contentPane.add(drawingAreaPanel, BorderLayout.CENTER);

        // 하단 채팅
        JPanel chatAndGuessPanel = new JPanel(new BorderLayout());
        chatAndGuessPanel.setPreferredSize(new Dimension(0, 150));
        
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

        // 이벤트 핸들러
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
    
    private JButton createColorButton(Color color) {
        JButton btn = new JButton();
        btn.setPreferredSize(new Dimension(50, 30));
        btn.setMaximumSize(new Dimension(50, 30));
        btn.setBackground(color);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.addActionListener(e -> {
            currentColor = color; 
            currentStroke = 2.0f; 
            if (isDrawer) {
                String rgbMsg = "RGB::" + color.getRed() + "," + color.getGreen() + "," + color.getBlue();
                sendProtocol(rgbMsg);
            }
        });
        return btn;
    }

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
        startGameBtn.setEnabled(false); // 게임 중엔 시작 버튼 비활성화

        if (isDrawer) {
            keywordLabel.setText("제시어: " + info);
            keywordLabel.setForeground(Color.BLUE);
            appendToChat("[알림] 당신은 '출제자'입니다. 제시어를 그려주세요!");
        } else {
            try {
                int len = Integer.parseInt(info);
                StringBuilder sb = new StringBuilder("단어: ");
                for(int i=0; i<len; i++) sb.append("_ ");
                keywordLabel.setText(sb.toString());
                keywordLabel.setForeground(Color.BLACK);
                chatInput.requestFocus();
                appendToChat("[알림] 당신은 '정답자'입니다. 그림을 보고 정답을 맞춰보세요!");
            } catch (NumberFormatException e) {
                keywordLabel.setText("단어: ???");
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
            // [중요 수정] JOptionPane 제거 (Blocking 방지)
            // 대신 라벨과 채팅으로 안내
            String content = msg.substring("NOTICE_CORRECT::".length());
            keywordLabel.setText("정답! 다음 라운드 준비 중...");
            keywordLabel.setForeground(Color.RED);
            drawingCanvas.clear(); 
            startGameBtn.setEnabled(false); // 재시작은 자동이므로 버튼 비활성화 유지
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
                JPanel panel = (JPanel) userListPanel.getComponent(i);
                if (panel.getName() != null && panel.getName().equals(nameToRemove)) { 
                    userListPanel.remove(panel);
                    userListPanel.revalidate();
                    userListPanel.repaint();
                    break;
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