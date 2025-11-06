import java.awt.EventQueue;
import java.awt.GridBagLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.JLabel;
import java.awt.BorderLayout;
import javax.swing.SwingConstants;
import javax.swing.JTextField;
import javax.swing.JButton;

public class InitialScreen extends JFrame {

	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	private JTextField textField;

	/**
	 * Launch the application.
	 */
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

	/**
	 * Create the frame.
	 */
	public InitialScreen() {
		setTitle("캐치마인드");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 450, 300);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(0, 0));
		
		//상단 타이틀레이블
		JLabel titleLabel = new JLabel("캐치마인드");
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		contentPane.add(titleLabel, BorderLayout.NORTH);
		
		
		//중앙 닉네임 레이블과 텍스트필드
		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());
		contentPane.add(panel, BorderLayout.CENTER);
		
		JPanel nicknamePanel = new JPanel();
		
		JLabel nickname = new JLabel("닉네임");
		nicknamePanel.add(nickname);
		
		textField = new JTextField();
		nicknamePanel.add(textField);
		textField.setColumns(10);
		
		panel.add(nicknamePanel);
		
		//하단 참여하기 버튼
		JButton join_btn = new JButton("참여하기");
		
		JPanel southPanel = new JPanel();
		southPanel.add(join_btn);
		
		contentPane.add(southPanel, BorderLayout.SOUTH);

	}

}
