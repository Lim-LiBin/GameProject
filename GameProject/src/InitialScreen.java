import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.File;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.JLabel;
import java.awt.BorderLayout; 
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout; 
import java.awt.GridBagLayout; 

import javax.swing.SwingConstants;
import javax.swing.JTextField;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;

public class InitialScreen extends JFrame {

	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	private JTextField textField;
	private Font baseFont; 

	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					InitialScreen frame = new InitialScreen();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public InitialScreen() {
		setTitle("캐치마인드 - 로그인");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 800, 600);
		
		contentPane = new JPanel();
		contentPane.setBackground(new Color(230, 240, 255)); //<- 하늘 배경
		//contentPane.setBackground(Color.WHITE); <- 흰 배경
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(0, 20));
		
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
		
		//상단 타이틀
		JLabel titleLabel = new JLabel("캐치마인드");
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		titleLabel.setForeground(new Color(0, 51, 153));
		titleLabel.setFont(baseFont.deriveFont(Font.BOLD, 60f)); // 폰트 크기 키움
		
		JPanel northPanel = new JPanel();
		northPanel.setOpaque(false);
		northPanel.setBorder(new EmptyBorder(50, 0, 0, 0));
		northPanel.add(titleLabel);
		
		contentPane.add(northPanel, BorderLayout.NORTH);
		
		
		// 오른쪽:이미지
		JPanel eastPanel = new JPanel();
		eastPanel.setOpaque(false);
		eastPanel.setLayout(new GridBagLayout());
		//패널 크기 고정
		eastPanel.setPreferredSize(new Dimension(250, 0)); 
		
		try {
			ImageIcon originalIcon = new ImageIcon("image3.png");
			java.awt.Image image = originalIcon.getImage().getScaledInstance(189, 267, java.awt.Image.SCALE_SMOOTH);
			ImageIcon resizedIcon = new ImageIcon(image);
					
			JLabel imageLabel = new JLabel(resizedIcon);
			eastPanel.add(imageLabel);
		} catch (Exception e) {
			System.err.println("이미지 없음");
		}
		
		contentPane.add(eastPanel, BorderLayout.EAST);

		
		//왼쪽: 균형 맞추기용 투명 패널
		JPanel westPanel = new JPanel();
		westPanel.setOpaque(false);
		westPanel.setPreferredSize(new Dimension(200, 0)); 
		
		contentPane.add(westPanel, BorderLayout.WEST);
		
		//중앙: 닉네임 입력창
		JPanel centerPanel = new JPanel();
		centerPanel.setOpaque(false);
		centerPanel.setLayout(new GridBagLayout()); // 정중앙 배치
				
		JPanel inputGroupPanel = new JPanel();
		inputGroupPanel.setOpaque(false);
		inputGroupPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 0));
		
		JLabel nickname = new JLabel("닉네임");
		nickname.setFont(baseFont.deriveFont(24f)); // 글씨 좀 더 키움
		inputGroupPanel.add(nickname);
		
		textField = new JTextField();
		textField.setColumns(10);
		textField.setFont(baseFont.deriveFont(20f));
		textField.setHorizontalAlignment(SwingConstants.CENTER);
		textField.setBorder(javax.swing.BorderFactory.createLineBorder(new Color(0, 102, 204), 3));
		inputGroupPanel.add(textField);
		
		centerPanel.add(inputGroupPanel);
		
		contentPane.add(centerPanel, BorderLayout.CENTER);
		
		//하단 버튼
		JButton join_btn = new JButton("참여하기");
		join_btn.setFont(baseFont.deriveFont(25f));
		join_btn.setBackground(new Color(0, 51, 153));
		join_btn.setForeground(Color.WHITE);
		join_btn.setPreferredSize(new Dimension(200, 60));
		join_btn.setFocusPainted(false);
		
		join_btn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String nickname = textField.getText().trim();
				if (nickname.isEmpty()) {
					JOptionPane.showMessageDialog(InitialScreen.this, "닉네임을 입력하세요.", "오류", JOptionPane.ERROR_MESSAGE);
					return;
				}
				new CreateRoomScreen(nickname).setVisible(true); 
				dispose(); 
			}
		});
		
		JPanel southPanel = new JPanel();
		southPanel.setOpaque(false);
		southPanel.setBorder(new EmptyBorder(0, 0, 40, 0)); // 하단 여백
		southPanel.add(join_btn);
		
		contentPane.add(southPanel, BorderLayout.SOUTH);
	}
}