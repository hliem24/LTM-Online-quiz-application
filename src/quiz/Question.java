package quiz;

import java.io.Serializable;
import java.util.List;

public class Question implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String prompt;
    private final List<String> options;
    private final int correctIndex; // 0-based

    public Question(String prompt, List<String> options, int correctIndex) {
        this.prompt = prompt;
        this.options = options;
        this.correctIndex = correctIndex;
    }

    public String getPrompt() { return prompt; }
    public List<String> getOptions() { return options; }
    public int getCorrectIndex() { return correctIndex; }
}
