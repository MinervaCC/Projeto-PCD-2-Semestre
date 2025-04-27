package Communication;

// Enumera os comandos para a troca das mensagens entre o cliente e o servidor

public enum Command {
    ConnectionRequest,
    String,
    Terminate,
    FileSearchResult,
    WordSearchMessage,
    DownloadMessage,
    DownloadResult
}
