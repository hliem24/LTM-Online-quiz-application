package quiz;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuizServer {
    private final int port;
    private final List<Question> bank;

    private static final String RESULT_FILE = "results.csv";
    private static final Object FILE_LOCK = new Object();
    private static final String[] CSV_HEADERS = new String[]{
            "sessionId","username","score","total","percent",
            "startAt","endAt","durationMs","clientIP","clientHost"
    };

    public QuizServer(int port) {
        this.port = port;
        this.bank = buildSampleBank();
        ensureCsvWithHeader();
    }

    // Tạo file CSV nếu chưa có & ghi header
    private void ensureCsvWithHeader() {
        synchronized (FILE_LOCK) {
            Path p = Paths.get(RESULT_FILE);
            try {
                if (Files.notExists(p)) {
                    Files.createFile(p);
                    try (BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
                        bw.write(String.join(",", CSV_HEADERS));
                        bw.newLine();
                    }
                }
            } catch (IOException e) {
                System.err.println("Không thể tạo file kết quả: " + e.getMessage());
            }
        }
    }

    // Ngân hàng câu hỏi mẫu
    private List<Question> buildSampleBank() {
        List<Question> qs = new ArrayList<>();
        qs.add(new Question(
                "TCP thuộc tầng nào trong mô hình TCP/IP?",
                Arrays.asList("Ứng dụng", "Giao vận (Transport)", "Mạng (Internet)", "Liên kết dữ liệu"),
                1
        ));
        qs.add(new Question(
                "Trong Java, lớp nào dùng để lắng nghe kết nối TCP?",
                Arrays.asList("ServerSocket", "Socket", "DatagramSocket", "MulticastSocket"),
                0
        ));
        qs.add(new Question(
                "Phát biểu đúng về TCP:",
                Arrays.asList("Không kết nối, không tin cậy", "Kết nối, tin cậy", "Chỉ phát broadcast", "Chỉ hoạt động trên UDP"),
                1
        ));
        return qs;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("QuizServer đang lắng nghe tại port " + port + " ...");

            ExecutorService pool = Executors.newCachedThreadPool();
            while (true) {
                Socket client = serverSocket.accept();
                pool.submit(new ClientHandler(client, bank));
            }
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final List<Question> bank;

        ClientHandler(Socket socket, List<Question> bank) {
            this.socket = socket;
            this.bank = bank;
        }

        @Override
        public void run() {
            String clientIP = socket.getInetAddress().getHostAddress();
            String clientHost = socket.getInetAddress().getHostName();
            String sessionId = UUID.randomUUID().toString();

            long startMs = System.currentTimeMillis();
            String startAt = ts(startMs);

            System.out.println("Client kết nối: " + clientIP + " (" + clientHost + "), session=" + sessionId);
            try (
                    ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream  ois = new ObjectInputStream(socket.getInputStream())
            ) {
                // 1) Chào & yêu cầu tên
                oos.writeUTF("WELCOME|Nhap ten cua ban:");
                oos.flush();
                String username = ois.readUTF();

                // 2) Gửi số câu hỏi
                oos.writeInt(bank.size());
                oos.flush();

                int correct = 0;
                for (Question q : bank) {
                    oos.writeObject(q);
                    oos.flush();

                    int ansIndex = ois.readInt();
                    boolean isCorrect = (ansIndex == q.getCorrectIndex());
                    if (isCorrect) correct++;

                    oos.writeBoolean(isCorrect);
                    oos.flush();
                }

                // 3) Tổng kết
                String summary = "RESULT|" + username + "|" + correct + "/" + bank.size();
                oos.writeUTF(summary);
                oos.flush();

                long endMs = System.currentTimeMillis();
                String endAt = ts(endMs);
                long duration = endMs - startMs;
                double percent = bank.isEmpty() ? 0 : (100.0 * correct / bank.size());

                System.out.printf("KQ %s: %d/%d (%.2f%%), session=%s, dur=%dms%n",
                        username, correct, bank.size(), percent, sessionId, duration);

                // Lưu chi tiết CSV
                saveCsv(sessionId, username, correct, bank.size(), percent, startAt, endAt, duration, clientIP, clientHost);

            } catch (IOException e) {
                System.err.println("Lỗi xử lý client (" + sessionId + "): " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private static String ts(long ms) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(ms));
        }

        // Ghi một dòng CSV, thread-safe
        private void saveCsv(String sessionId, String username, int score, int total, double percent,
                             String startAt, String endAt, long durationMs, String clientIP, String clientHost) {
            String line = String.join(",",
                    safe(sessionId),
                    safe(username),
                    String.valueOf(score),
                    String.valueOf(total),
                    String.format(Locale.US, "%.2f", percent),
                    safe(startAt),
                    safe(endAt),
                    String.valueOf(durationMs),
                    safe(clientIP),
                    safe(clientHost)
            );
            Path p = Paths.get("results.csv");
            synchronized (QuizServer.FILE_LOCK) {
                try (BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
                    bw.write(line);
                    bw.newLine();
                } catch (IOException e) {
                    System.err.println("Không thể lưu kết quả CSV: " + e.getMessage());
                }
            }
        }

        // Escape dấu phẩy/ngoặc kép đơn giản cho CSV
        private String safe(String s) {
            if (s == null) return "";
            if (s.contains(",") || s.contains("\"")) {
                return "\"" + s.replace("\"", "\"\"") + "\"";
            }
            return s;
        }
    }

    public static void main(String[] args) throws IOException {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 5555;
        new QuizServer(port).start();
    }
}
