package quiz;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;

public class QuizClientSwing extends JFrame {

    // ===== Server mặc định =====
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int    SERVER_PORT = 5555;

    // ===== Theme =====
    private static final Color PAGE_BG = new Color(28, 21, 69);
    private static final Color CARD    = new Color(250, 250, 255);
    private static final Color TEXT    = new Color(25, 28, 33);
    private static final Color SUBTEXT = new Color(110, 114, 120);
    private static final Color PRIMARY = new Color(110, 85, 255);
    private static final Color PRIMARY_HOVER = new Color(85, 65, 220);
    private static final Color DANGER  = new Color(240, 71, 71);
    private static final Color BORDER  = new Color(230, 232, 245);

    // ===== Header buttons =====
    private JButton btnResults;
    private JButton btnChooseSet;
    private JButton btnLogout;
    private JLabel  btnReview;
    private JLabel  lblTimer;

    // ===== Footer buttons =====
    private JButton btnPrev, btnNext, btnFinish;

    // ===== UI center =====
    private JLabel lblStatus, lblQuestion, lblSession;
    private JProgressBar progress;
    private final ButtonGroup group = new ButtonGroup();
    private final JRadioButton[] options = new JRadioButton[4];

    // ===== Network =====
    private Socket socket;
    private ObjectOutputStream oos;
    private ObjectInputStream  ois;

    // ===== Auth / catalog =====
    private String currentUser;
    private String savedPassHash; // SHA-256
    private Map<String, List<String>> catalogCache; // lưu sau khi login
    private String chosenSetName = "";

    // ===== Exam state =====
    private int total = 0;
    private int currentIndex = 0;
    private int correct = 0;

    private final List<Question> history = new ArrayList<>();
    private final List<Integer>  sentChoices = new ArrayList<>();
    private int viewIndex = -1;
    private boolean inExam = false;

    // ===== Timer =====
    private javax.swing.Timer countdownTimer;
    private int examSeconds = 600;
    private int timeLeft = 600;

    public QuizClientSwing() {
        setTitle("Bài kiểm tra mô phỏng - Client");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));
        setLocationRelativeTo(null);

        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(PAGE_BG);
        page.setBorder(new EmptyBorder(24, 24, 24, 24));
        setContentPane(page);

        JPanel shell = new RoundedPanel(24);
        shell.setBackground(CARD);
        shell.setBorder(new EmptyBorder(28, 32, 28, 32));
        shell.setLayout(new BorderLayout(20, 20));
        page.add(shell, BorderLayout.CENTER);

        // ===== Header =====
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JPanel row1 = new JPanel(new BorderLayout(16, 8));
        row1.setOpaque(false);

        JLabel title = new JLabel("Bài kiểm tra mô phỏng");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        row1.add(title, BorderLayout.WEST);

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        headerRight.setOpaque(false);
        btnResults   = softOnCard("📄 Kết quả");
        btnChooseSet = primary("Chọn bộ đề");
        btnLogout    = softOnCard("Đăng xuất");
        btnLogout.setEnabled(false); // bật sau khi đăng nhập
        btnReview = pill("Đánh dấu");
        lblTimer  = pill("00:00");
        lblTimer.setForeground(Color.WHITE);
        lblTimer.setBackground(PRIMARY);

        headerRight.add(btnResults);
        headerRight.add(btnChooseSet);
        headerRight.add(btnLogout);
        headerRight.add(btnReview);
        headerRight.add(lblTimer);
        row1.add(headerRight, BorderLayout.EAST);

        header.add(row1);
        header.add(Box.createVerticalStrut(8));

        progress = new JProgressBar(0, 100);
        progress.setValue(0);
        progress.setBorderPainted(false);
        progress.setForeground(PRIMARY);
        progress.setBackground(new Color(238, 239, 245));
        JPanel pbWrap = new JPanel(new BorderLayout());
        pbWrap.setOpaque(false);
        pbWrap.add(progress, BorderLayout.CENTER);
        header.add(pbWrap);

        shell.add(header, BorderLayout.NORTH);

        // ===== Center =====
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(0, 16, 0, 16));

        lblSession = new JLabel("Bộ đề: (chưa chọn)");
        lblSession.setForeground(SUBTEXT);
        center.add(lblSession);
        center.add(Box.createVerticalStrut(12));

        lblQuestion = new JLabel("Câu hỏi sẽ hiển thị ở đây");
        lblQuestion.setFont(lblQuestion.getFont().deriveFont(Font.BOLD, 18f));
        lblQuestion.setForeground(TEXT);
        center.add(lblQuestion);
        center.add(Box.createVerticalStrut(16));

        JPanel optionsPanel = new JPanel(new GridLayout(4, 1, 10, 12));
        optionsPanel.setOpaque(false);
        for (int i = 0; i < 4; i++) {
            options[i] = option("Lựa chọn " + (char)('A' + i));
            group.add(options[i]);
            optionsPanel.add(wrapOption(options[i]));
            options[i].setEnabled(false);
        }
        center.add(optionsPanel);

        shell.add(center, BorderLayout.CENTER);

        // ===== Footer =====
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);

        lblStatus = new JLabel("Hãy đăng nhập để bắt đầu");
        lblStatus.setForeground(SUBTEXT);
        footer.add(lblStatus, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        btnPrev   = primary("Trước");
        btnNext   = primary("Tiếp");
        btnFinish = primary("Nộp bài");
        btnPrev.setEnabled(false);
        btnNext.setEnabled(false);
        btnFinish.setEnabled(false);
        actions.add(btnPrev);
        actions.add(btnNext);
        actions.add(btnFinish);
        footer.add(actions, BorderLayout.EAST);

        shell.add(footer, BorderLayout.SOUTH);

        // events
        btnNext.addActionListener(this::onNext);
        btnPrev.addActionListener(this::onPrev);
        btnFinish.addActionListener(this::onFinish);
        btnResults.addActionListener(this::onViewResults);
        btnChooseSet.addActionListener(e -> openChooseSetFlow());
        btnLogout.addActionListener(e -> doLogout());

        // khởi động -> chọn hành động (đăng nhập/đăng ký)
        SwingUtilities.invokeLater(this::promptAccountAndConnect);
    }

    /* ==================== START FLOWS ==================== */

    private void promptAccountAndConnect() {
        while (true) {
            String action = showAccountActionCard(); // LOGIN / REGISTER / null
            if (action == null) { dispose(); return; }

            String[] creds;
            if ("LOGIN".equals(action)) {
                creds = showLoginDialogCard();
            } else {
                creds = showRegisterDialogCard();
            }
            if (creds == null) continue; // quay lại

            currentUser   = creds[0];
            savedPassHash = sha256(creds[1]);

            if (connectAndAuth(currentUser, savedPassHash, action)) {
                break; // đã kết nối, có catalog -> chờ bấm "Chọn bộ đề"
            }
        }
    }

    /** Kết nối & AUTH. Sau khi OK: đọc catalog và bật nút Chọn bộ đề + Đăng xuất. */
    private boolean connectAndAuth(String user, String passHash, String mode) {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());

            String hello = ois.readUTF(); // "AUTH"
            if (!"AUTH".equals(hello)) throw new IOException("Giao thức không khớp (AUTH).");

            oos.writeUTF(mode);   // LOGIN / REGISTER
            oos.writeUTF(user);
            oos.writeUTF(passHash);
            oos.flush();

            String resp = ois.readUTF(); // "OK" hoặc "ERR|..."
            if (!"OK".equals(resp)) {
                closeQuietly();
                showWarnDialog("Đăng nhập thất bại", resp.replace("ERR|", ""));
                return false;
            }

            // nhận catalog và lưu
            catalogCache = receiveCatalog(ois);

            lblStatus.setText(String.format("Đã đăng nhập: %s  •  Máy chủ %s:%d",
                    user, SERVER_HOST, SERVER_PORT));
            btnChooseSet.setEnabled(true);
            btnLogout.setEnabled(true);
            return true;

        } catch (Exception ex) {
            showWarnDialog("Lỗi", "Không thể kết nối: " + ex.getMessage());
            closeQuietly();
            return false;
        }
    }

    /* ==================== CHỌN BỘ ĐỀ & BẮT ĐẦU ==================== */

    private void openChooseSetFlow() {
        if (inExam) return;
        try {
            if (socket == null || socket.isClosed()) {
                // nếu đã đóng (sau khi nộp bài), tự nối lại bằng thông tin lưu
                if (currentUser == null || savedPassHash == null) { promptAccountAndConnect(); return; }
                if (!connectAndAuth(currentUser, savedPassHash, "LOGIN")) return;
            }
            if (catalogCache == null || catalogCache.isEmpty()) {
                catalogCache = receiveCatalog(ois); // dự phòng
            }

            String[] pick = showPickDialogCard(catalogCache);
            String selType = pick[0];
            String selSet  = pick[1];
            chosenSetName  = selSet;
            lblSession.setText("Bộ đề: " + selSet);

            // gửi lựa chọn
            oos.writeUTF(selType);
            oos.writeUTF(selSet);
            oos.flush();

            // nhận cấu hình đề
            total = ois.readInt();
            examSeconds = ois.readInt();
            timeLeft = examSeconds;

            // reset state
            history.clear(); sentChoices.clear();
            currentIndex = 0; correct = 0; viewIndex = -1; progress.setValue(0);

            // khóa logout & chọn đề khi đang thi
            inExam = true;
            btnChooseSet.setEnabled(false);
            btnLogout.setEnabled(false);

            fetchNextQuestionFromServer();
            startExamTimer();
            btnFinish.setEnabled(true);

        } catch (Exception ex) {
            showWarnDialog("Lỗi", "Không thể bắt đầu: " + ex.getMessage());
            closeQuietly();
        }
    }

    /* ==================== DIALOGS ==================== */

    /** Hộp chọn hành động: Đăng nhập / Đăng ký / Thoát (card style) */
    private String showAccountActionCard() {
        final String[] choice = { null };

        JDialog dlg = new JDialog(this, "Tài khoản", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(PAGE_BG);
        page.setBorder(new EmptyBorder(18,18,18,18));
        dlg.setContentPane(page);

        JPanel card = new RoundedPanel(20);
        card.setBackground(CARD);
        card.setBorder(new EmptyBorder(18,18,18,18));
        card.setLayout(new BorderLayout(12,12));
        page.add(card, BorderLayout.CENTER);

        JLabel title = new JLabel("Chọn hành động");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        card.add(title, BorderLayout.NORTH);

        JLabel sub = new JLabel("Vui lòng chọn Đăng nhập hoặc Đăng ký để tiếp tục.");
        sub.setForeground(SUBTEXT);
        card.add(sub, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
        actions.setOpaque(false);
        JButton exit = softOnCard("Thoát");
        JButton reg  = softOnCard("Đăng ký");
        JButton log  = primary("Đăng nhập");
        exit.addActionListener(e -> { choice[0] = null; dlg.dispose(); });
        reg.addActionListener(e -> { choice[0] = "REGISTER"; dlg.dispose(); });
        log.addActionListener(e -> { choice[0] = "LOGIN"; dlg.dispose(); });
        actions.add(exit); actions.add(reg); actions.add(log);
        card.add(actions, BorderLayout.SOUTH);

        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
        return choice[0];
    }

    /** Form Đăng nhập (card). Trả về {user, pass} hoặc null nếu Quay lại. */
    private String[] showLoginDialogCard() {
        final String[][] out = { null };

        JDialog dlg = new JDialog(this, "Đăng nhập", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel root = new RoundedPanel(18);
        root.setBackground(CARD);
        root.setBorder(new EmptyBorder(22,24,22,24));
        root.setLayout(new BorderLayout(16,16));

        JLabel hd = new JLabel("Đăng nhập");
        hd.setFont(hd.getFont().deriveFont(Font.BOLD, 18f));
        root.add(hd, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 0, 8, 10);
        gc.anchor = GridBagConstraints.WEST;

        JLabel lbUser = new JLabel("Tài khoản"); lbUser.setForeground(SUBTEXT);
        JLabel lbPass = new JLabel("Mật khẩu");  lbPass.setForeground(SUBTEXT);

        JTextField tfUser = new JTextField(18);
        tfUser.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(8,10,8,10)));
        JPasswordField tfPass = new JPasswordField(18);
        tfPass.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(8,10,8,10)));

        gc.gridx=0; gc.gridy=0; body.add(lbUser, gc);
        gc.gridx=1; gc.fill=GridBagConstraints.HORIZONTAL; gc.weightx=1.0; body.add(tfUser, gc);
        gc.gridx=0; gc.gridy=1; gc.fill=GridBagConstraints.NONE; gc.weightx=0; body.add(lbPass, gc);
        gc.gridx=1; gc.fill=GridBagConstraints.HORIZONTAL; gc.weightx=1.0; body.add(tfPass, gc);

        root.add(body, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
        btns.setOpaque(false);
        JButton back = softOnCard("Quay lại");
        JButton ok   = primary("Tiếp tục");
        back.addActionListener(e -> { out[0] = null; dlg.dispose(); });
        ok.addActionListener(e -> {
            String u = tfUser.getText().trim();
            String p = new String(tfPass.getPassword());
            if (u.isEmpty() || p.isEmpty()) { showWarnDialog("Thiếu thông tin","Vui lòng nhập tài khoản và mật khẩu."); return; }
            out[0] = new String[]{u,p}; dlg.dispose();
        });
        btns.add(back); btns.add(ok);
        root.add(btns, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        SwingUtilities.invokeLater(tfUser::requestFocusInWindow);
        dlg.setVisible(true);
        return out[0];
    }

    /** Form Đăng ký (card). Trả về {user, pass} hoặc null nếu Quay lại. */
    private String[] showRegisterDialogCard() {
        final String[][] out = { null };

        JDialog dlg = new JDialog(this, "Đăng ký", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel root = new RoundedPanel(18);
        root.setBackground(CARD);
        root.setBorder(new EmptyBorder(22,24,22,24));
        root.setLayout(new BorderLayout(16,16));

        JLabel hd = new JLabel("Đăng ký");
        hd.setFont(hd.getFont().deriveFont(Font.BOLD, 18f));
        root.add(hd, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 0, 8, 10);
        gc.anchor = GridBagConstraints.WEST;

        JLabel lbUser  = new JLabel("Tài khoản"); lbUser.setForeground(SUBTEXT);
        JLabel lbPass  = new JLabel("Mật khẩu");  lbPass.setForeground(SUBTEXT);
        JLabel lbPass2 = new JLabel("Xác nhận");  lbPass2.setForeground(SUBTEXT);

        JTextField tfUser = new JTextField(18);
        tfUser.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(8,10,8,10)));
        JPasswordField tfPass = new JPasswordField(18);
        tfPass.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(8,10,8,10)));
        JPasswordField tfPass2 = new JPasswordField(18);
        tfPass2.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(8,10,8,10)));

        gc.gridx=0; gc.gridy=0; body.add(lbUser, gc);
        gc.gridx=1; gc.fill=GridBagConstraints.HORIZONTAL; gc.weightx=1.0; body.add(tfUser, gc);
        gc.gridx=0; gc.gridy=1; gc.fill=GridBagConstraints.NONE; gc.weightx=0; body.add(lbPass, gc);
        gc.gridx=1; gc.fill=GridBagConstraints.HORIZONTAL; gc.weightx=1.0; body.add(tfPass, gc);
        gc.gridx=0; gc.gridy=2; gc.fill=GridBagConstraints.NONE; gc.weightx=0; body.add(lbPass2, gc);
        gc.gridx=1; gc.fill=GridBagConstraints.HORIZONTAL; gc.weightx=1.0; body.add(tfPass2, gc);

        root.add(body, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));
        btns.setOpaque(false);
        JButton back = softOnCard("Quay lại");
        JButton ok   = primary("Đăng ký");
        back.addActionListener(e -> { out[0] = null; dlg.dispose(); });
        ok.addActionListener(e -> {
            String u = tfUser.getText().trim();
            String p = new String(tfPass.getPassword());
            String p2= new String(tfPass2.getPassword());
            if (u.isEmpty() || p.isEmpty()) { showWarnDialog("Thiếu thông tin","Vui lòng nhập tài khoản và mật khẩu."); return; }
            if (p2.isEmpty() || !p.equals(p2)) { showWarnDialog("Lỗi","Mật khẩu xác nhận không khớp."); return; }
            out[0] = new String[]{u,p}; dlg.dispose();
        });
        btns.add(back); btns.add(ok);
        root.add(btns, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        SwingUtilities.invokeLater(tfUser::requestFocusInWindow);
        dlg.setVisible(true);
        return out[0];
    }

    /** Hộp chọn bộ đề (card). KHÔNG có nút “Kết quả”. */
    private String[] showPickDialogCard(Map<String, List<String>> catalog) {
        if (catalog == null || catalog.isEmpty()) return new String[]{"Default", "Sample"};

        final String[] result = new String[2];

        JDialog dlg = new JDialog(this, "Chọn bộ đề", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel root = new RoundedPanel(18);
        root.setBackground(CARD);
        root.setBorder(new EmptyBorder(20, 22, 20, 22));
        root.setLayout(new BorderLayout(16, 16));

        JLabel hd = new JLabel("Chọn bộ đề");
        hd.setFont(hd.getFont().deriveFont(Font.BOLD, 16f));
        root.add(hd, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 0, 6, 0);
        gc.anchor = GridBagConstraints.WEST;

        JLabel lbType = new JLabel("Loại câu hỏi"); lbType.setForeground(SUBTEXT);
        JLabel lbSet  = new JLabel("Bộ đề");        lbSet.setForeground(SUBTEXT);

        JComboBox<String> cbType = new JComboBox<>(catalog.keySet().toArray(new String[0]));
        styleCombo(cbType);
        JComboBox<String> cbSet = new JComboBox<>();
        styleCombo(cbSet);

        gc.gridx=0; gc.gridy=0; body.add(lbType, gc);
        gc.gridx=1; gc.fill=GridBagConstraints.HORIZONTAL; gc.weightx=1.0; body.add(cbType, gc);
        gc.gridx=0; gc.gridy=1; gc.fill=GridBagConstraints.NONE; gc.weightx=0; body.add(lbSet, gc);
        gc.gridx=1; gc.fill=GridBagConstraints.HORIZONTAL; gc.weightx=1.0; body.add(cbSet, gc);

        Runnable refill = () -> {
            cbSet.removeAllItems();
            List<String> sets = catalog.get(cbType.getSelectedItem());
            if (sets != null) for (String s : sets) cbSet.addItem(s);
            if (cbSet.getItemCount() > 0) cbSet.setSelectedIndex(0);
        };
        refill.run();
        cbType.addActionListener(e -> refill.run());

        root.add(body, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btns.setOpaque(false);
        JButton back = softOnCard("Quay lại");
        JButton ok   = primary("Bắt đầu");
        back.addActionListener(e -> { // nếu back -> chọn mặc định đầu tiên
            String t = catalog.keySet().iterator().next();
            String s = catalog.get(t).get(0);
            result[0] = t; result[1] = s; dlg.dispose();
        });
        ok.addActionListener(e -> {
            result[0] = (String) cbType.getSelectedItem();
            result[1] = (String) cbSet.getSelectedItem();
            dlg.dispose();
        });
        btns.add(back); btns.add(ok);
        root.add(btns, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
        return result;
    }

    /* ==================== NETWORK HELPERS ==================== */

    private Map<String, List<String>> receiveCatalog(ObjectInputStream ois) throws IOException {
        int typeCount = ois.readInt();
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i < typeCount; i++) {
            String type = ois.readUTF();
            int setCount = ois.readInt();
            List<String> sets = new ArrayList<>();
            for (int j = 0; j < setCount; j++) sets.add(ois.readUTF());
            map.put(type, sets);
        }
        return map;
    }

    private void fetchNextQuestionFromServer() throws IOException, ClassNotFoundException {
        Object obj = ois.readObject();
        if (!(obj instanceof Question)) throw new IOException("Dữ liệu câu hỏi không hợp lệ!");
        history.add((Question) obj);
        showQuestionAt(history.size() - 1);
    }

    /* ==================== NAVIGATION ==================== */

    private void showQuestionAt(int idx) {
        if (idx < 0 || idx >= history.size()) return;
        viewIndex = idx;
        Question q = history.get(idx);

        lblQuestion.setText("Câu " + (idx + 1) + ": " + q.getPrompt());
        List<String> op = q.getOptions();
        group.clearSelection();
        for (int i = 0; i < options.length; i++) {
            if (i < op.size()) { options[i].setText(op.get(i)); options[i].setVisible(true); }
            else options[i].setVisible(false);
        }
        boolean alreadySent = (idx < sentChoices.size());
        enableOptions(!alreadySent);
        if (alreadySent) {
            int chosen = sentChoices.get(idx);
            if (chosen >= 0 && chosen < options.length) options[chosen].setSelected(true);
        }
        updateNavButtons();
    }

    private void updateNavButtons() {
        btnPrev.setEnabled(viewIndex > 0);
        btnNext.setEnabled(viewIndex >= 0);
        btnFinish.setEnabled(inExam);
        lblStatus.setText((currentUser == null ? "" : (currentUser + " • ")) +
                (inExam ? ("Câu " + (Math.max(0, viewIndex) + 1) + "/" + total) : "Đã đăng nhập"));
    }

    private void onNext(ActionEvent e) {
        if (!inExam || viewIndex < 0) return;

        if (viewIndex < currentIndex) { // xem lại câu cũ
            if (viewIndex + 1 < history.size()) showQuestionAt(viewIndex + 1);
            return;
        }

        int chosen = -1;
        for (int i = 0; i < options.length; i++) {
            if (options[i].isVisible() && options[i].isSelected()) { chosen = i; break; }
        }
        if (chosen == -1) { showWarnDialog("Thiếu lựa chọn", "Hãy chọn một đáp án trước khi tiếp tục."); return; }

        try {
            oos.writeInt(chosen);
            oos.flush();

            boolean isCorrect = ois.readBoolean();
            if (isCorrect) correct++;
            sentChoices.add(chosen);
            currentIndex++;

            progress.setValue(Math.min(100, (int)(100.0 * currentIndex / Math.max(1, total))));

            if (currentIndex >= total) {
                String summary = ois.readUTF();
                onFinishSummary(summary);
                return;
            }
            fetchNextQuestionFromServer();

        } catch (Exception ex) {
            showWarnDialog("Lỗi", "Gửi/nhận dữ liệu thất bại: " + ex.getMessage());
            closeQuietly();
        }
    }

    private void onPrev(ActionEvent e) { if (inExam && viewIndex > 0) showQuestionAt(viewIndex - 1); }

    private void onFinish(ActionEvent e) {
        if (!inExam || total == 0) return;
        if (!showSubmitConfirmDialog()) return;

        try {
            if (currentIndex < total) { oos.writeInt(-1); oos.flush(); ois.readBoolean(); currentIndex++; }
            while (currentIndex < total) { ois.readObject(); oos.writeInt(-1); oos.flush(); ois.readBoolean(); currentIndex++; }
            String summary = ois.readUTF();
            onFinishSummary(summary);
        } catch (Exception ex) {
            showWarnDialog("Lỗi", "Nộp bài thất bại: " + ex.getMessage());
            closeQuietly();
            resetAfterExam();
        }
    }

    /* ==================== TIMER ==================== */

    private void startExamTimer() {
        stopExamTimer();
        timeLeft = examSeconds; updateTimerLabel();
        countdownTimer = new javax.swing.Timer(1000, e -> {
            timeLeft--; updateTimerLabel();
            progress.setValue(Math.min(100, (int)(100.0 * currentIndex / Math.max(1, total))));
            if (timeLeft <= 0) { stopExamTimer(); finishDueToTimeout(); }
        });
        countdownTimer.start();
    }
    private void stopExamTimer() { if (countdownTimer != null && countdownTimer.isRunning()) countdownTimer.stop(); }
    private void updateTimerLabel() {
        int m = Math.max(0, timeLeft) / 60, s = Math.max(0, timeLeft) % 60;
        lblTimer.setText(String.format("%02d:%02d", m, s));
        lblTimer.setBackground(timeLeft <= 30 ? DANGER : PRIMARY);
        lblTimer.setForeground(Color.WHITE);
    }

    /* ==================== END / SUMMARY ==================== */

    private void onFinishSummary(String summary) {
        stopExamTimer();
        showResultDialog(summary, false);
        closeQuietly();
        resetAfterExam();
    }

    private void finishDueToTimeout() {
        try {
            if (currentIndex < total) { oos.writeInt(-1); oos.flush(); ois.readBoolean(); currentIndex++; }
            while (currentIndex < total) { ois.readObject(); oos.writeInt(-1); oos.flush(); ois.readBoolean(); currentIndex++; }
            String summary = ois.readUTF();
            showResultDialog(summary, true);
        } catch (Exception ex) {
            showWarnDialog("Lỗi", "Lỗi khi hết giờ: " + ex.getMessage());
        } finally {
            closeQuietly();
            resetAfterExam();
        }
    }

    private void resetAfterExam() {
        inExam = false;
        btnChooseSet.setEnabled(true);
        btnLogout.setEnabled(true);
        total=0; currentIndex=0; correct=0; viewIndex=-1;
        history.clear(); sentChoices.clear(); progress.setValue(0);
        lblTimer.setText("00:00"); lblTimer.setBackground(PRIMARY);
        lblSession.setText("Bộ đề: (chưa chọn)");
        lblQuestion.setText("Câu hỏi sẽ hiển thị ở đây");
        group.clearSelection(); enableOptions(false);
        btnPrev.setEnabled(false); btnNext.setEnabled(false); btnFinish.setEnabled(false);
    }

    private void doLogout() {
        if (inExam) {
            showWarnDialog("Đang làm bài", "Không thể đăng xuất trong khi đang làm bài.");
            return;
        }
        closeQuietly();
        catalogCache = null;
        currentUser = null; // tùy bạn muốn giữ hay xóa
        savedPassHash = null;
        btnLogout.setEnabled(false);
        btnChooseSet.setEnabled(false);
        lblStatus.setText("Hãy đăng nhập để bắt đầu");
        promptAccountAndConnect();
    }

    /* ==================== CARD-STYLED DIALOGS ==================== */

    private boolean showSubmitConfirmDialog() {
        JDialog dlg = new JDialog(this, "Nộp bài", true);
        dlg.setSize(460, 230); dlg.setLocationRelativeTo(this);

        JPanel page = new JPanel(new BorderLayout()); page.setBackground(PAGE_BG);
        page.setBorder(new EmptyBorder(16,16,16,16)); dlg.setContentPane(page);

        JPanel card = new RoundedPanel(20); card.setBackground(CARD);
        card.setBorder(new EmptyBorder(16,16,16,16));
        card.setLayout(new BorderLayout(12,12)); page.add(card, BorderLayout.CENTER);

        JLabel title = new JLabel("Bạn muốn nộp bài ngay bây giờ?");
        title.setForeground(TEXT); title.setFont(title.getFont().deriveFont(Font.BOLD,18f));
        card.add(title, BorderLayout.NORTH);
        JLabel sub = new JLabel("Các câu còn lại sẽ tính là bỏ qua."); sub.setForeground(SUBTEXT);
        card.add(sub, BorderLayout.CENTER);

        final boolean[] ok = {false};
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0)); actions.setOpaque(false);
        JButton cancel = softOnCard("Hủy"); JButton submit = primary("Nộp bài");
        cancel.addActionListener(e -> { ok[0] = false; dlg.dispose(); });
        submit.addActionListener(e -> { ok[0] = true;  dlg.dispose(); });
        actions.add(cancel); actions.add(submit); card.add(actions, BorderLayout.SOUTH);

        dlg.setVisible(true);
        return ok[0];
    }

    private void showResultDialog(String summary, boolean timeOut) {
        String user = currentUser == null ? "User" : currentUser;
        int c = correct, t = total;
        try {
            String[] seg = summary.split("\\|");
            if (seg.length >= 3) {
                user = seg[1].trim();
                String[] ct = seg[2].split("/");
                c = Integer.parseInt(ct[0].trim());
                t = Integer.parseInt(ct[1].trim());
            }
        } catch (Exception ignored) { }

        int wrong = Math.max(0, t - c);
        double pct = t == 0 ? 0 : (100.0 * c / t);
        double diem10 = pct / 10.0;
        int used = Math.max(0, examSeconds - Math.max(0, timeLeft));
        String usedText = String.format("%02d:%02d", used/60, used%60);

        JDialog dlg = new JDialog(this, "Kết quả", true);
        dlg.setSize(480, 340); dlg.setResizable(false); dlg.setLocationRelativeTo(this);

        JPanel page = new JPanel(new BorderLayout()); page.setBackground(PAGE_BG);
        page.setBorder(new EmptyBorder(18,18,18,18)); dlg.setContentPane(page);

        JPanel card = new RoundedPanel(20); card.setBackground(CARD);
        card.setBorder(new EmptyBorder(18,18,18,18)); card.setLayout(new BorderLayout(14,14));
        page.add(card, BorderLayout.CENTER);

        JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false);
        JLabel title = new JLabel(timeOut ? "Hết giờ — bài làm đã được nộp" : "Hoàn thành bài làm");
        title.setForeground(TEXT); title.setFont(title.getFont().deriveFont(Font.BOLD,18f));
        header.add(title, BorderLayout.WEST);

        JLabel badge = new JLabel(String.format("Điểm: %.2f / 10", diem10));
        badge.setOpaque(true); badge.setBackground(timeOut ? DANGER : PRIMARY);
        badge.setForeground(Color.WHITE); badge.setBorder(new EmptyBorder(6,12,6,12));
        header.add(badge, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridLayout(0,1,0,8)); body.setOpaque(false);
        body.add(row("Người làm", user));
        body.add(row("Bộ đề", (chosenSetName == null || chosenSetName.isBlank()) ? "-" : chosenSetName));
        body.add(row("Đúng / Sai", c + " / " + wrong));
        body.add(row("Tổng câu", String.valueOf(t)));
        body.add(row("Tỷ lệ", String.format("%.2f%%", pct)));
        body.add(row("Thời gian", usedText + " / " + String.format("%02d:%02d", examSeconds/60, examSeconds%60)));
        card.add(body, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0)); actions.setOpaque(false);
        JButton open = softOnCard("Mở bảng kết quả"); open.addActionListener(this::onViewResults);
        JButton close = primary("Đóng"); close.addActionListener(e -> dlg.dispose());
        actions.add(open); actions.add(close); card.add(actions, BorderLayout.SOUTH);

        dlg.setVisible(true);
    }
    private JComponent row(String label, String value) {
        JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false);
        JLabel l = new JLabel(label); l.setForeground(SUBTEXT);
        JLabel v = new JLabel(value); v.setForeground(TEXT); v.setFont(v.getFont().deriveFont(Font.BOLD));
        p.add(l, BorderLayout.WEST); p.add(v, BorderLayout.EAST); return p;
    }

    private void showWarnDialog(String title, String message) {
        JDialog dlg = new JDialog(this, title, true);
        dlg.setSize(420, 220); dlg.setLocationRelativeTo(this);

        JPanel page = new JPanel(new BorderLayout()); page.setBackground(PAGE_BG);
        page.setBorder(new EmptyBorder(16,16,16,16)); dlg.setContentPane(page);

        JPanel card = new RoundedPanel(18); card.setBackground(CARD);
        card.setBorder(new EmptyBorder(16,16,16,16)); card.setLayout(new BorderLayout(10,10));
        page.add(card, BorderLayout.CENTER);

        JLabel t = new JLabel(title); t.setForeground(TEXT); t.setFont(t.getFont().deriveFont(Font.BOLD,16f));
        JLabel m = new JLabel(message); m.setForeground(SUBTEXT);
        card.add(t, BorderLayout.NORTH); card.add(m, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0)); actions.setOpaque(false);
        JButton ok = primary("OK"); ok.addActionListener(e -> dlg.dispose()); actions.add(ok);
        card.add(actions, BorderLayout.SOUTH);

        dlg.setVisible(true);
    }

    /* ==================== UI HELPERS (đã fix lỗi cannot resolve) ==================== */

    private JButton primary(String text) {
        JButton b = new JButton(text);
        b.setForeground(Color.white); b.setBackground(PRIMARY);
        b.setUI(new FlatButtonUI(PRIMARY, PRIMARY_HOVER));
        b.setBorder(new EmptyBorder(10,18,10,18)); b.setFocusPainted(false);
        return b;
    }
    private JButton softOnCard(String text) {
        JButton b = new JButton(text);
        b.setForeground(PRIMARY_HOVER); b.setBackground(new Color(245,246,255));
        b.setUI(new FlatButtonUI(new Color(245,246,255), new Color(235,236,250)));
        b.setBorder(new EmptyBorder(10,18,10,18)); b.setFocusPainted(false);
        return b;
    }
    private JLabel pill(String text) {
        JLabel l = new JLabel(text); l.setOpaque(true);
        l.setBackground(new Color(240,242,255)); l.setForeground(PRIMARY_HOVER);
        l.setBorder(new EmptyBorder(6,14,6,14)); return l;
    }
    private JRadioButton option(String text) {
        JRadioButton rb = new JRadioButton(text);
        rb.setOpaque(false); rb.setForeground(TEXT);
        rb.setBorder(new EmptyBorder(10,16,10,16)); rb.setFocusPainted(false);
        return rb;
    }
    private JComponent wrapOption(JComponent c) {
        JPanel p = new RoundedPanel(12); p.setBackground(Color.WHITE);
        p.setLayout(new BorderLayout()); p.add(c, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(235,235,248)),
                new EmptyBorder(2,6,2,6) )); return p;
    }
    private void styleCombo(JComboBox<?> cb) {
        cb.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(6, 8, 6, 8)
        ));
        cb.setBackground(Color.WHITE);
    }
    private void enableOptions(boolean enable) { for (JRadioButton r : options) r.setEnabled(enable); }

    /* ==================== MISC ==================== */

    private void onViewResults(ActionEvent e) {
        File f = new File("results.csv");
        if (!f.exists()) {
            showWarnDialog("Chưa có dữ liệu",
                    "Không thấy 'results.csv'. Hãy mở trên máy chạy server hoặc copy file về.");
            return;
        }
        ResultsViewerSwing.showViewer(this, f.getAbsolutePath(),
                PAGE_BG, CARD, TEXT, SUBTEXT, new Color(230,232,245), PRIMARY);
    }

    private void closeQuietly() {
        try { if (ois != null) ois.close(); } catch (Exception ignored) {}
        try { if (oos != null) oos.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null; oos = null; ois = null;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    // Rounded panel + Flat button
    private static class RoundedPanel extends JPanel {
        private final int radius;
        public RoundedPanel(int radius) { this.radius = radius; setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0,0,getWidth(),getHeight(), radius, radius);
            g2.dispose(); super.paintComponent(g);
        }
    }
    private static class FlatButtonUI extends BasicButtonUI {
        private final Color base, hover;
        public FlatButtonUI(Color base, Color hover) { this.base = base; this.hover = hover; }
        @Override public void installUI (JComponent c) {
            super.installUI(c);
            JButton b = (JButton) c; b.setOpaque(true); b.setBorderPainted(false);
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
