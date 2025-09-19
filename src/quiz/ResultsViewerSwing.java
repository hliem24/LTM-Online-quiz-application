package quiz;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ResultsViewerSwing extends JFrame {

    public static void showViewer(Component parent, String csvPath,
                                  Color BG, Color CARD, Color TEXT, Color SUBTEXT, Color BORDER, Color ACCENT) {
        ResultsViewerSwing v = new ResultsViewerSwing(csvPath, BG, CARD, TEXT, SUBTEXT, BORDER, ACCENT);
        v.setLocationRelativeTo(parent);
        v.setVisible(true);
    }

    public ResultsViewerSwing(String csvPath,
                              Color BG, Color CARD, Color TEXT, Color SUBTEXT, Color BORDER, Color ACCENT) {
        setTitle("Kết quả");
        setSize(920, 580);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Page
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(BG);
        page.setBorder(new EmptyBorder(22, 22, 22, 22));
        setContentPane(page);

        // Card
        JPanel card = new RoundedPanel(24);
        card.setBackground(CARD);
        card.setBorder(new EmptyBorder(18, 18, 18, 18));
        card.setLayout(new BorderLayout(16, 16));
        page.add(card, BorderLayout.CENTER);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Bảng kết quả");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(TEXT);
        header.add(title, BorderLayout.WEST);
        card.add(header, BorderLayout.NORTH);

        // Table
        String[] cols = {"Tên", "Bộ đề", "Đúng", "Sai", "Điểm (10)", "%"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(model) {
            @Override public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    c.setBackground((row % 2 == 0) ? Color.WHITE : new Color(249, 250, 255));
                }
                if (c instanceof JComponent jc) {
                    // padding đồng nhất giữa header và cell
                    ((JComponent) c).setBorder(new EmptyBorder(8, 10, 8, 10));
                }
                return c;
            }
        };
        table.setFillsViewportHeight(true);
        table.setRowHeight(32);
        table.setShowGrid(true);
        table.setShowVerticalLines(true); // bật kẻ dọc để thẳng hàng với header
        table.setGridColor(BORDER);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setBackground(Color.WHITE);
        table.setForeground(TEXT);
        table.setSelectionBackground(new Color(245, 246, 255));
        table.setSelectionForeground(TEXT);

        // Header renderer: đồng bộ padding + căn phải cho cột số
        TableCellRenderer hdrLeft  = mkHeaderRenderer(SwingConstants.LEFT);
        TableCellRenderer hdrRight = mkHeaderRenderer(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(0).setHeaderRenderer(hdrLeft);  // Tên
        table.getColumnModel().getColumn(1).setHeaderRenderer(hdrLeft);  // Bộ đề
        table.getColumnModel().getColumn(2).setHeaderRenderer(hdrRight); // Đúng
        table.getColumnModel().getColumn(3).setHeaderRenderer(hdrRight); // Sai
        table.getColumnModel().getColumn(4).setHeaderRenderer(hdrRight); // Điểm (10)
        table.getColumnModel().getColumn(5).setHeaderRenderer(hdrRight); // %

        // Căn phải ô dữ liệu số
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(2).setCellRenderer(right);
        table.getColumnModel().getColumn(3).setCellRenderer(right);
        table.getColumnModel().getColumn(4).setCellRenderer(right);
        table.getColumnModel().getColumn(5).setCellRenderer(right);

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(new Color(235, 236, 248)));
        sp.getViewport().setBackground(Color.WHITE);
        card.add(sp, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        JLabel lblInfo = new JLabel(" ");
        lblInfo.setForeground(SUBTEXT);
        footer.add(lblInfo, BorderLayout.WEST);
        JButton btnClose = primary("Đóng", ACCENT);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.add(btnClose);
        footer.add(actions, BorderLayout.EAST);
        card.add(footer, BorderLayout.SOUTH);
        btnClose.addActionListener(e -> dispose());

        // Load CSV
        try {
            List<String[]> rows = readCsv(csvPath);
            if (rows.isEmpty()) {
                model.addRow(new Object[]{"(chưa có)", "-", 0, 0, "0.00", "0.00"});
                lblInfo.setText("0 bản ghi");
            } else {
                String[] first = rows.get(0);
                boolean hasHeader = looksLikeHeader(first);
                int startRow = hasHeader ? 1 : 0;
                Indexes idx = hasHeader ? fromHeader(first) : Indexes.defaultServerOrder();

                int added = 0;
                for (int i = startRow; i < rows.size(); i++) {
                    String[] r = rows.get(i);
                    String name   = val(r, idx.username, "-");
                    int score     = parseInt(val(r, idx.score, "0"));
                    int total     = parseInt(val(r, idx.total, "0"));
                    String set    = val(r, idx.set, "-");
                    if ("-".equals(set)) set = val(r, idx.type, "-");
                    int wrong     = Math.max(0, total - score);
                    double pct    = parseDouble(val(r, idx.percent, total == 0 ? "0" : (100.0 * score / total) + ""));
                    double diem10 = pct / 10.0;

                    model.addRow(new Object[]{
                            name, (set == null || set.isBlank()) ? "-" : set,
                            score, wrong,
                            String.format("%.2f", diem10),
                            String.format("%.2f", pct)
                    });
                    added++;
                }
                if (added == 0) model.addRow(new Object[]{"(chưa có)", "-", 0, 0, "0.00", "0.00"});
                lblInfo.setText(added + " bản ghi");
            }
        } catch (IOException ex) {
            JTextArea area = new JTextArea("Không thể đọc file kết quả:\n" + ex.getMessage());
            area.setEditable(false);
            area.setBackground(CARD);
            area.setForeground(TEXT);
            area.setBorder(new EmptyBorder(12, 12, 12, 12));
            card.add(area, BorderLayout.CENTER);
        }
    }

    /* ===== Header renderer thống nhất ===== */
    private TableCellRenderer mkHeaderRenderer(int align) {
        return (table, value, isSelected, hasFocus, row, column) -> {
            JLabel lab = new JLabel(String.valueOf(value));
            lab.setOpaque(true);
            lab.setHorizontalAlignment(align);
            lab.setBackground(new Color(245, 246, 255));
            lab.setForeground(new Color(70, 75, 90));
            lab.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(235, 236, 248)),
                    new EmptyBorder(8, 10, 8, 10) // padding giống cell
            ));
            return lab;
        };
    }

    /* ===== Styles helpers ===== */
    private JButton primary(String text, Color ACCENT) {
        JButton b = new JButton(text);
        b.setForeground(Color.WHITE);
        b.setBackground(ACCENT);
        b.setUI(new FlatButtonUI(ACCENT, ACCENT.darker()));
        b.setBorder(new EmptyBorder(10, 18, 10, 18));
        b.setFocusPainted(false);
        return b;
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

    /* ===== CSV helpers ===== */
    private static List<String[]> readCsv(String path) throws IOException {
        List<String[]> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line; boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first && line.length() > 0 && line.charAt(0) == '\uFEFF') line = line.substring(1); // BOM
                first = false;
                out.add(parseCsvLine(line));
            }
        }
        return out;
    }
    private static String[] parseCsvLine(String line) {
        java.util.List<String> cells = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuote) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else inQuote = false;
                } else cur.append(ch);
            } else {
                if (ch == '"') inQuote = true;
                else if (ch == ',') { cells.add(cur.toString()); cur.setLength(0); }
                else cur.append(ch);
            }
        }
        cells.add(cur.toString());
        return cells.toArray(new String[0]);
    }

    private static boolean looksLikeHeader(String[] row0) {
        for (String s : row0) {
            if (s == null) continue;
            String t = s.trim().toLowerCase();
            if (t.equals("username") || t.equals("score") || t.equals("total")
                    || t.equals("percent") || t.equals("type") || t.equals("set")) return true;
        }
        return false;
    }

    private static class Indexes {
        int username = -1, score = -1, total = -1, percent = -1, type = -1, set = -1;
        static Indexes defaultServerOrder() {
            Indexes i = new Indexes();
            // server: sessionId, username, score, total, percent, startAt, endAt, durationMs, clientIP, clientHost, type, set
            i.username = 1; i.score = 2; i.total = 3; i.percent = 4; i.type = 10; i.set = 11;
            return i;
        }
    }
    private static Indexes fromHeader(String[] header) {
        Indexes idx = new Indexes();
        idx.username = indexOfIgnoreCase(header, "username");
        idx.score    = indexOfIgnoreCase(header, "score");
        idx.total    = indexOfIgnoreCase(header, "total");
        idx.percent  = indexOfIgnoreCase(header, "percent");
        idx.type     = firstIndex(header, new String[]{"type","questionType","loai","category"});
        idx.set      = firstIndex(header, new String[]{"set","questionSet","boDe","bode"});
        return idx;
    }
    private static int indexOfIgnoreCase(String[] header, String name) {
        for (int i = 0; i < header.length; i++) if (name.equalsIgnoreCase(header[i])) return i;
        return -1;
    }
    private static int firstIndex(String[] header, String[] candidates) {
        for (String c : candidates) {
            int i = indexOfIgnoreCase(header, c);
            if (i >= 0) return i;
        }
        return -1;
    }
    private static String val(String[] row, int idx, String def) {
        if (idx < 0 || idx >= row.length) return def;
        String v = row[idx];
        return (v == null || v.isBlank()) ? def : v.trim();
    }
    private static int parseInt(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; } }
    private static double parseDouble(String s) { try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0.0; } }
}
