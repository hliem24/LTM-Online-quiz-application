package quiz;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;
import java.util.List;

public class QuizClientSwing extends JFrame {

    // Palette
    private static final Color BG = new Color(18, 18, 18);
    private static final Color CARD = new Color(28, 28, 30);
    private static final Color TEXT = new Color(235, 235, 235);
    private static final Color SUBTEXT = new Color(170, 170, 170);
    private static final Color ACCENT = new Color(88, 101, 242);
    private static final Color ACCENT_HOVER = new Color(106, 119, 255);
    private static final Color SUCCESS = new Color(48, 199, 90);
    private static final Color DANGER = new Color(255, 92, 92);
    private static final Color BORDER = new Color(45, 45, 47);

    // UI
    private JTextField txtHost, txtPort, txtUser;
    private JButton btnConnect, btnNext, btnViewResults;
    private JLabel lblAppTitle, lblStatus, lblQuestion, lblFeedback, lblProgressText;
    private JRadioButton[] options = new JRadioButton[4];
    private ButtonGroup group = new ButtonGroup();

    // Network
    private Socket socket;
    private ObjectOutputStream oos;
    private ObjectInputStream  ois;

    // State
    private int total = 0;
    private int currentIndex = 0;
    private int correct = 0;

    public QuizClientSwing() {
        setTitle("Online Quiz (TCP) - Client");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(880, 560));
        setLocationRelativeTo(null);

        // Nimbus + màu tối
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) { UIManager.setLookAndFeel(info.getClassName()); break; }
            }
        } catch (Exception ignored) {}
        UIManager.put("control", BG);
        UIManager.put("text", TEXT);
        UIManager.put("nimbusLightBackground", CARD);
        UIManager.put("nimbusBase", ACCENT.darker());
        UIManager.put("nimbusFocus", ACCENT);
        UIManager.put("info", CARD);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        setContentPane(root);

        JPanel appBar = makeAppBar();
        root.add(appBar, BorderLayout.NORTH);

        JPanel center = new RoundedPanel(20);
        center.setBackground(CARD);
        center.setBorder(new EmptyBorder(24, 24, 24, 24));
        root.add(pad(center, 24, 24, 24, 24), BorderLayout.CENTER);

        lblQuestion = new JLabel("Câu hỏi sẽ xuất hiện tại đây");
        lblQuestion.setForeground(TEXT);
        lblQuestion.setFont(lblQuestion.getFont().deriveFont(Font.BOLD, 20f));

        JPanel optionsPanel = new JPanel();
        optionsPanel.setOpaque(false);
        optionsPanel.setLayout(new GridLayout(4, 1, 10, 12));
        for (int i = 0; i < 4; i++) {
            options[i] = makeOption("Lựa chọn " + (i + 1));
            group.add(options[i]);
            optionsPanel.add(options[i]);
            options[i].setEnabled(false);
        }

        lblFeedback = new JLabel(" ");
        lblFeedback.setForeground(SUBTEXT);
        lblFeedback.setFont(lblFeedback.getFont().deriveFont(Font.PLAIN, 14f));

        // 🔥 CHỈ GIỮ TEXT TIẾN ĐỘ – KHÔNG CÒN PROGRESS BAR
        lblProgressText = new JLabel("Tiến độ: 0/0");
        lblProgressText.setForeground(SUBTEXT);

        JPanel progressRow = new JPanel(new BorderLayout());
        progressRow.setOpaque(false);
        progressRow.add(lblProgressText, BorderLayout.EAST);

        btnNext = makeAccentButton("Next ▶");
        btnNext.setEnabled(false);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        actions.add(btnNext);

        Box box = Box.createVerticalBox();
        box.add(lblQuestion);
        box.add(Box.createVerticalStrut(16));
        box.add(optionsPanel);
        box.add(Box.createVerticalStrut(12));
        box.add(lblFeedback);
        box.add(Box.createVerticalStrut(16));
        box.add(progressRow);
        box.add(Box.createVerticalStrut(18));
        box.add(actions);
        center.setLayout(new BorderLayout());
        center.add(box, BorderLayout.CENTER);

        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(BG);
        status.setBorder(new EmptyBorder(6, 24, 12, 24));
        lblStatus = new JLabel("Chưa kết nối.");
        lblStatus.setForeground(SUBTEXT);
        status.add(lblStatus, BorderLayout.WEST);
        root.add(status, BorderLayout.SOUTH);

        // Events
        btnConnect.addActionListener(this::onConnect);
        btnNext.addActionListener(this::onNext);
        btnViewResults.addActionListener(this::onViewResults);
    }

    // --- AppBar ---
    private JPanel makeAppBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BG);
        bar.setBorder(new EmptyBorder(16, 24, 8, 24));

        lblAppTitle = new JLabel("🧠 Online Quiz (TCP)");
        lblAppTitle.setForeground(TEXT);
        lblAppTitle.setFont(lblAppTitle.getFont().deriveFont(Font.BOLD, 22f));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        txtHost = makeField("127.0.0.1", 10);
        txtPort = makeField("5555", 5);
        txtUser = makeField("User", 10);

        btnConnect = makeAccentButton("Kết nối");
        btnViewResults = makeSoftButton("📄 Xem kết quả");

        right.add(makeLabel("Host"));
        right.add(txtHost);
        right.add(makeLabel("Port"));
        right.add(txtPort);
        right.add(makeLabel("Username"));
        right.add(txtUser);
        right.add(btnConnect);
        right.add(btnViewResults);

        bar.add(lblAppTitle, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        return bar;
    }

    private JLabel makeLabel(String text) { JLabel lb = new JLabel(text); lb.setForeground(SUBTEXT); return lb; }
    private JTextField makeField(String def, int cols) {
        JTextField tf = new JTextField(def, cols);
        tf.setBackground(new Color(32, 32, 34));
        tf.setForeground(TEXT);
        tf.setCaretColor(TEXT);
        tf.setBorder(new EmptyBorder(8, 10, 8, 10));
        return tf;
    }
    private JRadioButton makeOption(String text) {
        JRadioButton rb = new JRadioButton(text);
        rb.setOpaque(false);
        rb.setForeground(TEXT);
        rb.setBorder(new EmptyBorder(8, 10, 8, 10));
        rb.setFocusPainted(false);
        return rb;
    }
    private JButton makeAccentButton(String text) {
        JButton b = new JButton(text);
        b.setForeground(Color.white);
        b.setBackground(ACCENT);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(10, 16, 10, 16));
        b.setUI(new FlatButtonUI(ACCENT, ACCENT_HOVER));
        return b;
    }
    private JButton makeSoftButton(String text) {
        JButton b = new JButton(text);
        b.setForeground(TEXT);
        b.setBackground(new Color(40, 40, 42));
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(10, 14, 10, 14));
        b.setUI(new FlatButtonUI(new Color(50,50,52), new Color(60,60,62)));
        return b;
    }

    // --- Actions ---
    private void onConnect(ActionEvent e) {
        String host = txtHost.getText().trim();
        int port = Integer.parseInt(txtPort.getText().trim());
        String user = txtUser.getText().trim().isEmpty() ? "User" : txtUser.getText().trim();

        try {
            socket = new Socket(host, port);
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());

            String greeting = ois.readUTF();
            lblStatus.setText("Server: " + greeting.replace('|', ' '));

            oos.writeUTF(user);
            oos.flush();

            total = ois.readInt();
            currentIndex = 0;
            correct = 0;

            updateProgressText();
            enableOptions(true);
            btnNext.setEnabled(true);

            loadNextQuestion();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối: " + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            closeQuietly();
        }
    }

    private void loadNextQuestion() {
        try {
            if (currentIndex >= total) {
                String summary = ois.readUTF();
                lblStatus.setText("Hoàn thành.");
                btnNext.setEnabled(false);
                enableOptions(false);
                JOptionPane.showMessageDialog(this,
                        summary.replace('|', ' ') + "\nĐúng: " + correct + "/" + total,
                        "Kết quả",
                        JOptionPane.INFORMATION_MESSAGE);
                closeQuietly();
                return;
            }

            Question q = (Question) ois.readObject();
            lblQuestion.setText("Câu " + (currentIndex + 1) + ": " + q.getPrompt());

            List<String> op = q.getOptions();
            for (int i = 0; i < options.length; i++) {
                if (i < op.size()) { options[i].setText(op.get(i)); options[i].setVisible(true); }
                else { options[i].setVisible(false); }
                options[i].setSelected(false);
            }

            lblFeedback.setText(" ");
            updateProgressText();
            btnNext.setText(currentIndex == total - 1 ? "Finish ✔" : "Next ▶");

        } catch (IOException | ClassNotFoundException ex) {
            JOptionPane.showMessageDialog(this, "Lỗi tải câu hỏi: " + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            closeQuietly();
        }
    }

    private void onNext(ActionEvent e) {
        int chosen = -1;
        for (int i = 0; i < options.length; i++) {
            if (options[i].isVisible() && options[i].isSelected()) { chosen = i; break; }
        }
        if (chosen == -1) {
            JOptionPane.showMessageDialog(this, "Hãy chọn một đáp án trước khi tiếp tục.",
                    "Thiếu lựa chọn", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            oos.writeInt(chosen);
            oos.flush();

            boolean isCorrect = ois.readBoolean();
            if (isCorrect) { correct++; lblFeedback.setText("✅ Chính xác!"); lblFeedback.setForeground(SUCCESS); }
            else { lblFeedback.setText("❌ Chưa đúng."); lblFeedback.setForeground(DANGER); }

            currentIndex++;
            updateProgressText();
            if (currentIndex < total) loadNextQuestion(); else loadNextQuestion();

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Lỗi gửi/nhận dữ liệu: " + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            closeQuietly();
        }
    }

    private void onViewResults(ActionEvent e) {
        File f = new File("results.csv");
        if (!f.exists()) {
            JOptionPane.showMessageDialog(this,
                    "Không tìm thấy 'results.csv' trong thư mục hiện tại.\n" +
                            "Gợi ý: mở trên MÁY CHẠY SERVER, hoặc copy file về.",
                    "Chưa có dữ liệu", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ResultsViewerSwing.showViewer(this, f.getAbsolutePath(), BG, CARD, TEXT, SUBTEXT, BORDER, ACCENT);
    }

    // Helpers
    private void enableOptions(boolean enable) { for (JRadioButton r : options) r.setEnabled(enable); }
    private void updateProgressText() {
        lblProgressText.setText(String.format("Tiến độ: %d/%d   |   Điểm tạm: %d",
                Math.min(currentIndex, total), total, correct));
        lblProgressText.setForeground(SUBTEXT);
    }
    private static JComponent pad(JComponent c, int top, int left, int bottom, int right) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(top, left, bottom, right));
        p.add(c, BorderLayout.CENTER);
        return p;
    }
    private void closeQuietly() {
        try { if (ois != null) ois.close(); } catch (Exception ignored) {}
        try { if (oos != null) oos.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }

    // Custom UI
    private static class RoundedPanel extends JPanel {
        private final int radius;
        public RoundedPanel(int radius) { this.radius = radius; setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }
    private static class FlatButtonUI extends BasicButtonUI {
        private final Color base, hover;
        public FlatButtonUI(Color base, Color hover) { this.base = base; this.hover = hover; }
        @Override public void installUI (JComponent c) {
            super.installUI(c);
            JButton b = (JButton) c;
            b.setOpaque(true);
            b.setBorderPainted(false);
            b.addChangeListener(e -> {
                if (b.getModel().isPressed()) b.setBackground(base.darker());
                else if (b.getModel().isRollover()) b.setBackground(hover);
                else b.setBackground(base);
            });
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new QuizClientSwing().setVisible(true));
    }
}
