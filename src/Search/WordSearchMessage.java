package Search;

import Communication.GlobalConfig;
import Files.FileInfo;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class WordSearchMessage implements Serializable {
    private final String message;

    public WordSearchMessage(String text) {
        this.message = text;
    }

    public List<FileInfo> search() {
        GlobalConfig gc = GlobalConfig.getInstance();
        Map<FileInfo, Integer> occurrenceMap = new TreeMap<>();
        File[] files = gc.getFilesInDirectory();

        for (File file : files) {
            FileInfo info = new FileInfo(file);
            int occurrences = countOccurrences(file.getName());

            // Verificação de completude via sistema de arquivos
            boolean isComplete = isFileComplete(info, gc.getDefaultPath());

            if (isComplete && occurrences != 0) {
                occurrenceMap.put(info, occurrences);
            }
        }
        return new ArrayList<>(occurrenceMap.keySet());
    }

    private int countOccurrences(String text) {
        int n = text.length();
        int m = this.message.length();
        int count = 0;

        for (int s = 0; s <= n - m; s++) {
            if (text.substring(s, s + m).equalsIgnoreCase(this.message)) {
                count++;
            }
        }
        return count;
    }

    private boolean isFileComplete(FileInfo info, String basePath) {
        File file = new File(basePath + info.name);
        return file.exists() && file.length() == info.fileSize;
    }

    public String getSearchTerm() {
        return message;
    }
}
