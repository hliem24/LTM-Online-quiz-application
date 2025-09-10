package quiz;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;

public class QuizClient {
    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : "127.0.0.1";
        int port     = (args.length > 1) ? Integer.parseInt(args[1]) : 5555;

        try (Socket socket = new Socket(host, port);
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream  ois = new ObjectInputStream(socket.getInputStream());
             Scanner sc = new Scanner(System.in)) {

            // Nhận chào mừng
            String greeting = ois.readUTF();
            System.out.println(greeting.replace('|', ' '));

            // Nhập tên và gửi
            String username = sc.nextLine().trim();
            if (username.isEmpty()) username = "User";
            oos.writeUTF(username);
            oos.flush();

            // Nhận số câu hỏi
            int total = ois.readInt();
            int correct = 0;

            for (int i = 0; i < total; i++) {
                Question q = (Question) ois.readObject();
                System.out.println("\nCâu " + (i+1) + ": " + q.getPrompt());
                List<String> ops = q.getOptions();
                for (int k = 0; k < ops.size(); k++) {
                    System.out.println("  " + (k+1) + ") " + ops.get(k));
                }

                int chosenIdx = -1;
                while (true) {
                    System.out.print("Chọn đáp án (1.." + ops.size() + "): ");
                    String line = sc.nextLine().trim();
                    try {
                        int val = Integer.parseInt(line);
                        if (val >= 1 && val <= ops.size()) {
                            chosenIdx = val - 1;
                            break;
                        }
                    } catch (NumberFormatException ignored) {}
                    System.out.println("Nhập không hợp lệ, thử lại.");
                }

                oos.writeInt(chosenIdx);
                oos.flush();

                boolean isCorrect = ois.readBoolean();
                System.out.println(isCorrect ? "✅ Chính xác!" : "❌ Chưa đúng.");
                if (isCorrect) correct++;
            }

            String summary = ois.readUTF();
            System.out.println("\n" + summary.replace('|', ' '));

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Lỗi client: " + e.getMessage());
        }
    }
}
