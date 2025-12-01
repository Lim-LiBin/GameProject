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

//클라이언트 초기 진입 화면
//게임 접속을 위한 첫 화면
//사용자 닉네임 입력 검증 및 서버 접속을 위한 방 목록 화면으로의 전환을 담당
//JFrame을 상속받아 GUI를 구성하며, BorderLayout을 기반으로 5개 영역으로 분할하여 배치함
public class InitialScreen extends JFrame {

	private static final long serialVersionUID = 1L;
	private JPanel contentPane; //모든 UI 컴포넌트가 부착될 최상위 패널
	private JTextField textField; //닉네임 입력 필드, 사용자의 입력을 받음
	private Font baseFont; //글꼴로 사용할 폰트 객체

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

	//UI 컴포넌트 초기화 및 화면 레이아웃 구성
	public InitialScreen() {
		//프레임 기본 설정
		//윈도우 창 제목, 종료 동작, 초기 크기 설정
		setTitle("캐치마인드 - 로그인");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 800, 600);
		
		//메인 컨테이너 설정
		contentPane = new JPanel();
		contentPane.setBackground(new Color(230, 240, 255)); //배경색 지정
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		
		//BorderLayout 적용 (수직 간격 20px로 설정)
		contentPane.setLayout(new BorderLayout(0, 20));
		
		//폰트 리소스 로드
		try {
			File fontFile = new File("Jalnan2TTF.ttf");
			baseFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
			GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			ge.registerFont(baseFont);
		} catch(IOException | FontFormatException e) {
			//폰트 파일이 없을 경우 프로그램이 멈추지 않도록 기본 시스템 폰트로 예외 처리
			System.out.println("폰트 로드 실패. 기본 폰트 사용.");
	        baseFont = new Font("맑은 고딕", Font.BOLD, 12);
		}
		
		//NORTH - 상단 타이틀 영역
		//게임 타이틀 표시
		JLabel titleLabel = new JLabel("캐치마인드");
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		titleLabel.setForeground(new Color(0, 51, 153));
		titleLabel.setFont(baseFont.deriveFont(Font.BOLD, 60f));
		
		JPanel northPanel = new JPanel();
		northPanel.setOpaque(false); //배경색 투명하게 하여 메인 컨테이너의 배경색이 보이도록 함
		northPanel.setBorder(new EmptyBorder(50, 0, 0, 0)); //상단 50px 여백 확보하여 타이틀이 너무 위에 붙지 않게 하기 위함
		northPanel.add(titleLabel);
		
		//메인 컨테이너에 부착
		contentPane.add(northPanel, BorderLayout.NORTH);
		
		
		//EAST - 우측 이미지 영역
		JPanel eastPanel = new JPanel();
		eastPanel.setOpaque(false);
		eastPanel.setLayout(new GridBagLayout()); //이미지를 세로 중앙에 배치하기 위해 GridBagLayout 사용
		eastPanel.setPreferredSize(new Dimension(250, 0)); //레이아웃 균형을 위해 고정 폭 할당
		
		try {
			//이미지 로드
			ImageIcon originalIcon = new ImageIcon("image3.png");
			
			//이미지 크기 조정
			java.awt.Image image = originalIcon.getImage().getScaledInstance(189, 267, java.awt.Image.SCALE_SMOOTH);
			ImageIcon resizedIcon = new ImageIcon(image);
			
			//Label 컴포넌트를 사용하여 이미지 표시
			JLabel imageLabel = new JLabel(resizedIcon);
			eastPanel.add(imageLabel);
		} catch (Exception e) {
			System.err.println("이미지 없음");
		}
		
		//메인 컨테이너에 부착 
		contentPane.add(eastPanel, BorderLayout.EAST);

		
		//WEST - 좌측 여백 영역
		//BorderLayout 특성상 좌우 대칭을 맞춰주기 위해 빈 패널 배치
		JPanel westPanel = new JPanel();
		westPanel.setOpaque(false);
		westPanel.setPreferredSize(new Dimension(200, 0)); 
		
		//메인 컨테이너에 부착
		contentPane.add(westPanel, BorderLayout.WEST);
		
		//CENTER - 닉네임 입력 영역
		JPanel centerPanel = new JPanel();
		centerPanel.setOpaque(false);
		centerPanel.setLayout(new GridBagLayout()); // 입력창 그룹을 화면 정중앙에 배치
				
		//Label과 텍스트 필드를 가로로 나란히 묶어주는 서브 컨테이너
		JPanel inputGroupPanel = new JPanel();
		inputGroupPanel.setOpaque(false);
		inputGroupPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 0));
		
		JLabel nickname = new JLabel("닉네임");
		nickname.setFont(baseFont.deriveFont(24f));
		inputGroupPanel.add(nickname);
		
		
		//닉네임 입력 필드 객체 생성
		textField = new JTextField();
		textField.setColumns(10);
		textField.setFont(baseFont.deriveFont(20f));
		textField.setHorizontalAlignment(SwingConstants.CENTER); //입력 텍스트 중앙 정렬
		//입력창 강조를 위해 커스텀 테두리 적용
		textField.setBorder(javax.swing.BorderFactory.createLineBorder(new Color(0, 102, 204), 3));
		inputGroupPanel.add(textField);
		
		//서브 패널을 중앙 패널에 추가
		centerPanel.add(inputGroupPanel);
		
		//메인 컨테이너에 부착
		contentPane.add(centerPanel, BorderLayout.CENTER);
		
		//SOUTH - 하단 버튼 영역
		JButton join_btn = new JButton("참여하기");
		join_btn.setFont(baseFont.deriveFont(25f));
		join_btn.setBackground(new Color(0, 51, 153)); //버튼 색상 설정
		join_btn.setForeground(Color.WHITE); //글자 색상 설정
		join_btn.setPreferredSize(new Dimension(200, 60)); //버튼 크기 설정
		join_btn.setFocusPainted(false); //버튼 클릭 시 생기는 테두리 제거
		
		//버튼 클릭 시 이벤트 리스너 등록
		join_btn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String nickname = textField.getText().trim(); //앞뒤 공백 제거하여 입력값 가져오기
				
				//유효성 검사: 빈 닉네임 방지
				if (nickname.isEmpty()) {
					JOptionPane.showMessageDialog(InitialScreen.this, "닉네임을 입력하세요.", "오류", JOptionPane.ERROR_MESSAGE);
					return;
				}
				
				//화면 전환 로직
				//다음 화면 객체를 생성하면서 닉네임 데이터를 전달
				new CreateRoomScreen(nickname).setVisible(true); 
				
				//현재 리소스 해제
				//메모리에서 해제하고 창 닫음
				dispose();
			}
		});
		
		//버튼을 감싸는 하단 패널 (하단 여백 확보용)
		JPanel southPanel = new JPanel();
		southPanel.setOpaque(false);
		southPanel.setBorder(new EmptyBorder(0, 0, 40, 0));
		southPanel.add(join_btn);
		
		//메인 컨테이너에 부착
		contentPane.add(southPanel, BorderLayout.SOUTH);
	}
}