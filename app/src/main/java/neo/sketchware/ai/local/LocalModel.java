package neo.sketchware.ai.local;

import java.io.Serializable;

public class LocalModel implements Serializable {
    public String id; public String name; public String publisher; public String description; public String sizeLabel;
    public String fileName; public String filePath; public String downloadUrl;
    public boolean imported; public boolean downloaded;
    public LocalModel(String id,String name,String publisher,String description,String sizeLabel,String fileName,String downloadUrl){this.id=id;this.name=name;this.publisher=publisher;this.description=description;this.sizeLabel=sizeLabel;this.fileName=fileName;this.downloadUrl=downloadUrl;}
}
