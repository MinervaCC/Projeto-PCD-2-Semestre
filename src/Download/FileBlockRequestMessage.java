package Download;

import Communication.GlobalConfig;
import Files.FileBlockInfo;
import Files.FileInfo;

import java.io.File;
import java.io.Serializable;

public class FileBlockRequestMessage implements Serializable {
    private final FileBlockInfo requestBlock;
    private final String fileName;
    private final String fileHash;
    private final String dtmUID;
    private final int blockID;

    public FileBlockRequestMessage(FileBlockInfo requestBlock, String fileName, String fileHash, String dtmUID, int blockID) {
        this.fileName = fileName;
        this.blockID = blockID;
        this.requestBlock = requestBlock;
        this.fileHash = fileHash;
        this.dtmUID = dtmUID;
    }

    public int getBlockID() {
        return blockID;
    }

    public String getDtmUID(){
        return dtmUID;
    }

    public String getFileHash() {
        return fileHash;
    }

    public byte[] getBlock(){
        GlobalConfig gc = GlobalConfig.getInstance();
        File[] files = gc.getFilesInDirectory();
        for (File file : files) {
            FileInfo info = new FileInfo(file);
            if (file.getName().equals(this.fileName)) {
                return requestBlock.readFileBytesInRange(file);
            }
        }
        return null;
    }

}
