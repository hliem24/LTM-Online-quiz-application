package quiz;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ResultsViewerSwing extends JFrame {
    private final String csvPath;
    private JTable table;
    private DefaultTableModel model;

    private final Color bg, card, text, subtext, border, accent;

    public static void showViewer(Frame owner, String csvPath,
                                  Color bg, Color card, Color text, Color subtext, Color border, Color accent) {
        ResultsViewerSwing v = new ResultsViewerSwing(csvPath, bg, card, text, subtext, border, accent);
        v.setLocationRelativeTo(owner);
        v.setVisible(true);
    }

    public ResultsViewerSwing(String csvPath, Color bg, Color card, Color text, Color subtext, Color border, Color accent) {
        super("Kết quả thi – " + csvPath);
        this.csvPath = csvPath;
        this.bg = bg; this.card = card; this.text = text; this.subtext = subtext; this.border = border; this.accent = accent;

        setSize(1000, 580);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(bg);
        setContentPane(root);

        JLabel title = new JLabel("📄 Kết quả (results.csv)");
        title.setForeground(text);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));

        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.setForeground(Color.WHITE);
        btnRefresh.setBackground(accent);
        btnRefresh.setFocusPainted(false);
        btnRefresh.setBorder(new EmptyBorder(8, 14, 8, 14));
        btnRefresh.addActionListener(e -> loadCsv());

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(bg);
        top.setBorder(new EmptyBorder(16, 24, 8, 24));
        top.add(title, BorderLayout.WEST);
        top.add(btnRefresh, BorderLayout.EAST);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, border));
        root.add(top, BorderLayout.NORTH);

        JPanel cardPanel = new JPanel(new BorderLayout());
        cardPanel.setBackground(card);
        cardPanel.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Model chỉ đọc
        model = new DefaultTableModel() {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };

        table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);

        // Bảng dark, chữ rõ
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setRowHeight(28);
        table.setForeground(Color.WHITE);
        table.setBackground(new Color(35,35,37));
        table.setGridColor(new Color(70,70,70));
        table.setSelectionBackground(accent.darker());
        table.setSelectionForeground(Color.WHITE);

        // Header rõ
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setBackground(new Color(45, 45, 47));
        table.getTableHeader().setOpaque(true);

        JScrollPane sp = new JScrollPane(table);
        sp.getViewport().setBackground(card);
        sp.setBorder(BorderFactory.createLineBorder(border));
        cardPanel.add(sp, BorderLayout.CENTER);

        root.add(pad(cardPanel, 24, 24, 24, 24), BorderLayout.CENTER);

        loadCsv();
    }

    private void loadCsv() {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvPath), StandardCharsets.UTF_8))) {

            String headerLine = br.readLine();
            if (headerLine == null) {
                JOptionPane.showMessageDialog(this, "File rỗng.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String[] rawHeaders = parseCsvLine(headerLine);

            // Ánh xạ tiêu đề sang tiếng Việt & loại bỏ “IP máy”, “Tên máy”
            List<String> displayHeaders = new ArrayList<>();
            List<Integer> keepIndexes = new ArrayList<>();
            for (int i = 0; i < rawHeaders.length; i++) {
                String name = rawHeaders[i];
                if (name.equals("clientIP") || name.equals("clientHost")) continue; // ẩn 2 cột này
                displayHeaders.add(mapHeader(name));
                keepIndexes.add(i);
            }

            model.setDataVector(new Object[0][0], displayHeaders.toArray(new Object[0]));

            String line;
            while ((line = br.readLine()) != null) {
                String[] allRow = parseCsvLine(line);
                List<String> filtered = new ArrayList<>();
                for (int idx : keepIndexes) {
                    if (idx < allRow.length) filtered.add(allRow[idx]);
                }
                model.addRow(filtered.toArray(new Object[0]));
            }

            autoResizeColumns();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Không thể đọc CSV: " + e.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String mapHeader(String key) {
        switch (key) {
            case "sessionId":   return "Mã phiên";
            case "username":    return "Người làm";
            case "score":       return "Số đúng";
            case "total":       return "Tổng số";
            case "percent":     return "Điểm (%)";
            case "startAt":     return "Bắt đầu";
            case "endAt":       return "Kết thúc";
            case "durationMs":  return "Thời gian (ms)";
            default:            return key;
        }
    }

    private void autoResizeColumns() {
        final int margin = 16;
        int rowsToCheck = Math.min(table.getRowCount(), 300);
        for (int col = 0; col < table.getColumnCount(); col++) {
            int width = table.getTableHeader().getDefaultRenderer()
                    .getTableCellRendererComponent(table, table.getColumnName(col), false, false, 0, col)
                    .getPreferredSize().width;

            for (int row = 0; row < rowsToCheck; row++) {
                Component c = table.getCellRenderer(row, col)
                        .getTableCellRendererComponent(table, table.getValueAt(row, col), false, false, row, col);
                width = Math.max(width, c.getPreferredSize().width);
            }
            TableColumn column = table.getColumnModel().getColumn(col);
            column.setPreferredWidth(width + margin);
        }
    }

    // Parser CSV đơn giản (hỗ trợ dấu phẩy/ngoặc kép đã escape)
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else { inQuotes = false; }
                } else cur.append(c);
            } else {
                if (c == '"') inQuotes = true;
                else if (c == ',') { fields.add(cur.toString()); cur.setLength(0); }
                else cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    private static JComponent pad(JComponent c, int t, int l, int b, int r) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(t, l, b, r));
        p.add(c, BorderLayout.CENTER);
        return p;
    }
}
