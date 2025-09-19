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

    // Cấu trúc ngân hàng đề: questions/<type>/<set>.csv (+ tùy chọn <set>.cfg: seconds=NNN)
    private final Path questionRoot = Paths.get("questions");

    /* ==== Results CSV ==== */
    private static final String RESULT_FILE = "results.csv";
    private static final Object RESULT_LOCK = new Object();
    private static final String[] CSV_HEADERS = {
            "sessionId","username","score","total","percent",
            "startAt","endAt","durationMs","clientIP","clientHost","type","set"
    };

    /* ==== Users CSV (lưu hash đã băm ở client) ==== */
    private static final Path USERS_FILE = Paths.get("users.csv");
    private static final Object USERS_LOCK = new Object();
    // header: username,password_hash,created_at
    private static final String USERS_HEADER = "username,password_hash,created_at";

    private static final int DEFAULT_EXAM_SECONDS = 600; // 10 phút

    public QuizServer(int port) { this.port = port; }

    public void start() throws IOException {
        ensureResultHeader();
        ensureUsersHeader();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("QuizServer lắng nghe port " + port + " ...");
            ExecutorService pool = Executors.newCachedThreadPool();
            while (true) {
                Socket client = serverSocket.accept();
                pool.submit(new ClientHandler(client));
            }
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        ClientHandler(Socket socket) { this.socket = socket; }

        @Override public void run() {
            String clientIP = socket.getInetAddress().getHostAddress();
            String clientHost = socket.getInetAddress().getHostName();
            String sessionId = UUID.randomUUID().toString();

            long startMs = System.currentTimeMillis();
            String startAt = ts(startMs);

            try (ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream  ois = new ObjectInputStream(socket.getInputStream())) {

                /* ========== 1) AUTH ========== */
                oos.writeUTF("AUTH");  // báo client: vào pha xác thực
                oos.flush();

                String cmd = ois.readUTF();           // "LOGIN" hoặc "REGISTER"
                String username = ois.readUTF().trim();
                String passHash = ois.readUTF().trim();

                String authResp = handleAuth(cmd, username, passHash);
                oos.writeUTF(authResp); // "OK" hoặc "ERR|message"
                oos.flush();
                if (!"OK".equals(authResp)) {
                    System.out.println("AUTH FAIL " + clientIP + ": " + authResp);
                    return; // đóng kết nối
                }

                /* ========== 2) Catalog (types -> sets) ========== */
                Map<String, List<String>> catalog = scanCatalog();
                sendCatalog(oos, catalog);

                /* ========== 3) Lựa chọn đề ========== */
                String selType = ois.readUTF();
                String selSet  = ois.readUTF();

                /* ========== 4) Load đề + thời gian ========== */
                List<Question> bank = loadBank(selType, selSet);
                if (bank.isEmpty()) bank = sampleBank();
                int examSeconds = loadExamSeconds(selType, selSet);

                oos.writeInt(bank.size());
                oos.writeInt(examSeconds);
                oos.flush();

                /* ========== 5) Q&A loop ========== */
                int correct = 0;
                for (Question q : bank) {
                    oos.writeObject(q);
                    oos.flush();

                    int ansIndex = ois.readInt(); // -1 nếu bỏ qua/timeout
                    boolean isCorrect = (ansIndex == q.getCorrectIndex());
                    if (isCorrect) correct++;
                    oos.writeBoolean(isCorrect);
                    oos.flush();
                }

                /* ========== 6) Summary + Lưu kết quả ========== */
                String summary = "RESULT|" + username + "|" + correct + "/" + bank.size();
                oos.writeUTF(summary);
                oos.flush();

                long endMs = System.currentTimeMillis();
                String endAt = ts(endMs);
                long duration = endMs - startMs;
                double percent = bank.isEmpty() ? 0 : (100.0 * correct / bank.size());

                System.out.printf(Locale.US,
                        "KQ %s: %d/%d (%.2f%%), type=%s, set=%s, session=%s, dur=%dms%n",
                        username, correct, bank.size(), percent, selType, selSet, sessionId, duration);

                saveResultCsv(sessionId, username, correct, bank.size(), percent,
                        startAt, endAt, duration, clientIP, clientHost, selType, selSet);

            } catch (IOException e) {
                System.err.println("Lỗi client: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        /* ========== AUTH helpers ========== */
        private String handleAuth(String cmd, String username, String passHash) {
            if (username.isEmpty() || passHash.isEmpty()) return "ERR|Thiếu thông tin";

            try {
                if ("REGISTER".equalsIgnoreCase(cmd)) {
                    synchronized (USERS_LOCK) {
                        Map<String,String> users = readAllUsers();
                        if (users.containsKey(username)) return "ERR|Tài khoản đã tồn tại";
                        appendUser(username, passHash);
                        System.out.println("Đăng ký mới: " + username);
                        return "OK";
                    }
                } else if ("LOGIN".equalsIgnoreCase(cmd)) {
                    synchronized (USERS_LOCK) {
                        Map<String,String> users = readAllUsers();
                        String stored = users.get(username);
                        if (stored == null) return "ERR|Không tìm thấy tài khoản";
                        if (!stored.equalsIgnoreCase(passHash)) return "ERR|Mật khẩu không đúng";
                        return "OK";
                    }
                } else {
                    return "ERR|Lệnh không hợp lệ";
                }
            } catch (Exception e) {
                return "ERR|" + e.getMessage();
            }
        }

        private Map<String,String> readAllUsers() throws IOException {
            Map<String,String> map = new LinkedHashMap<>();
            if (!Files.exists(USERS_FILE)) return map;
            try (BufferedReader br = Files.newBufferedReader(USERS_FILE, StandardCharsets.UTF_8)) {
                String line; boolean first = true;
                while ((line = br.readLine()) != null) {
                    if (first) { first = false; continue; } // bỏ header
                    if (line.trim().isEmpty()) continue;
                    String[] p = parseCsvLine(line);
                    if (p.length >= 2) map.put(p[0], p[1]);
                }
            }
            return map;
        }

        private void appendUser(String username, String passHash) throws IOException {
            boolean needHeader = !Files.exists(USERS_FILE);
            try (BufferedWriter bw = Files.newBufferedWriter(USERS_FILE, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if (needHeader) { bw.write(USERS_HEADER); bw.newLine(); }
                bw.write(String.join(",", safe(username), safe(passHash), safe(ts(System.currentTimeMillis()))));
                bw.newLine();
            }
        }

        /* ========== Catalog / Bank ========== */
        private void sendCatalog(ObjectOutputStream oos, Map<String, List<String>> catalog) throws IOException {
            oos.writeInt(catalog.size());
            for (Map.Entry<String, List<String>> e : catalog.entrySet()) {
                oos.writeUTF(e.getKey());
                List<String> sets = e.getValue();
                oos.writeInt(sets.size());
                for (String s : sets) oos.writeUTF(s);
            }
            oos.flush();
        }

        private Map<String, List<String>> scanCatalog() {
            Map<String, List<String>> map = new LinkedHashMap<>();
            try {
                if (!Files.exists(questionRoot)) return map;
                try (DirectoryStream<Path> types = Files.newDirectoryStream(questionRoot)) {
                    for (Path typeDir : types) {
                        if (!Files.isDirectory(typeDir)) continue;
                        String typeName = typeDir.getFileName().toString();
                        List<String> sets = new ArrayList<>();
                        try (DirectoryStream<Path> setFiles = Files.newDirectoryStream(typeDir, "*.csv")) {
                            for (Path f : setFiles) {
                                String base = f.getFileName().toString();
                                if (base.toLowerCase().endsWith(".csv")) {
                                    sets.add(base.substring(0, base.length() - 4));
                                }
                            }
                        }
                        Collections.sort(sets);
                        if (!sets.isEmpty()) map.put(typeName, sets);
                    }
                }
            } catch (IOException ignored) {}
            // đảm bảo thứ tự theo tên
            return new TreeMap<>(map);
        }

        private List<Question> loadBank(String type, String set) {
            if (type == null || set == null) return Collections.emptyList();
            Path file = questionRoot.resolve(type).resolve(set + ".csv");
            if (!Files.exists(file)) return Collections.emptyList();
            List<Question> list = new ArrayList<>();
            try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty() || line.startsWith("#")) continue;
                    String[] parts = parseCsvLine(line);
                    // CSV: prompt, A, B, C, D, correctIndex(0-3)
                    if (parts.length >= 6) {
                        String prompt = parts[0];
                        List<String> ops = Arrays.asList(parts[1], parts[2], parts[3], parts[4]);
                        int correct = Integer.parseInt(parts[5].trim());
                        list.add(new Question(prompt, ops, correct));
                    }
                }
            } catch (Exception e) {
                System.err.println("Lỗi đọc đề: " + e.getMessage());
            }
            return list;
        }

        private int loadExamSeconds(String type, String set) {
            try {
                Path cfg = questionRoot.resolve(type).resolve(set + ".cfg");
                if (Files.exists(cfg)) {
                    Properties p = new Properties();
                    try (InputStream in = Files.newInputStream(cfg)) { p.load(in); }
                    String v = p.getProperty("seconds");
                    if (v != null) return Integer.parseInt(v.trim());
                }
            } catch (Exception ignored) {}
            return DEFAULT_EXAM_SECONDS;
        }

        private String[] parseCsvLine(String line) {
            List<String> fields = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean inQuote = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '"') inQuote = !inQuote;
                else if (c == ',' && !inQuote) { fields.add(cur.toString()); cur.setLength(0); }
                else cur.append(c);
            }
            fields.add(cur.toString());
            return fields.toArray(new String[0]);
        }
    }

    /* ==== CSV Headers ==== */
    private void ensureResultHeader() {
        synchronized (RESULT_LOCK) {
            try {
                boolean exists = Files.exists(Paths.get(RESULT_FILE));
                if (!exists) {
                    try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(RESULT_FILE), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                        bw.write(String.join(",", CSV_HEADERS));
                        bw.newLine();
                    }
                }
            } catch (IOException e) {
                System.err.println("Không thể tạo header CSV: " + e.getMessage());
            }
        }
    }
    private void ensureUsersHeader() {
        synchronized (USERS_LOCK) {
            try {
                if (!Files.exists(USERS_FILE)) {
                    try (BufferedWriter bw = Files.newBufferedWriter(USERS_FILE, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                        bw.write(USERS_HEADER);
                        bw.newLine();
                    }
                }
            } catch (IOException e) {
                System.err.println("Không thể tạo users.csv: " + e.getMessage());
            }
        }
    }

    /* ==== Save Results ==== */
    private void saveResultCsv(String sessionId, String username, int score, int total, double percent,
                               String startAt, String endAt, long duration, String clientIP, String clientHost,
                               String type, String set) {
        synchronized (RESULT_LOCK) {
            try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(RESULT_FILE), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                bw.write(String.join(",",
                        safe(sessionId), safe(username),
                        String.valueOf(score), String.valueOf(total),
                        String.format(Locale.US, "%.2f", percent),
                        safe(startAt), safe(endAt), String.valueOf(duration),
                        safe(clientIP), safe(clientHost), safe(type), safe(set)));
                bw.newLine();
            } catch (IOException e) {
                System.err.println("Không thể lưu kết quả CSV: " + e.getMessage());
            }
        }
    }

    /* ==== Utils ==== */
    private String safe(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
    private static String ts(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(ms));
    }

    // Fallback khi không có file câu hỏi
    private List<Question> sampleBank() {
        List<Question> qs = new ArrayList<>();
        qs.add(new Question("TCP thuộc tầng nào trong mô hình TCP/IP?",
                Arrays.asList("Ứng dụng", "Giao vận (Transport)", "Mạng (Internet)", "Liên kết dữ liệu"), 1));
        qs.add(new Question("Trong Java, lớp nào dùng để lắng nghe kết nối TCP?",
                Arrays.asList("ServerSocket", "Socket", "DatagramSocket", "MulticastSocket"), 0));
        qs.add(new Question("Phát biểu đúng về TCP:",
                Arrays.asList("Không kết nối, không tin cậy", "Kết nối, tin cậy", "Chỉ phát broadcast", "Chỉ hoạt động trên UDP"), 1));
        return qs;
    }

    public static void main(String[] args) throws IOException {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 5555;
        new QuizServer(port).start();
    }
}
