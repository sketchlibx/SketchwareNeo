package neo.sketchware.ai.local;

import android.content.Context;
import android.net.Uri;
import java.io.File; import java.io.FileInputStream; import java.io.FileOutputStream; import java.io.InputStream; import java.util.ArrayList; import java.util.List;

public final class LocalModelManager {
    private LocalModelManager() {}
    public static File getDirectory(Context c){File d=new File(c.getFilesDir(),"local_models");if(!d.exists())d.mkdirs();return d;}
    public static List<LocalModel> catalog(){List<LocalModel> l=new ArrayList<>();
        l.add(new LocalModel("llama-3.2-1b","Llama 3.2 1B","Meta","Compact general-purpose model","≈ 1.3 GB","llama-3.2-1b.gguf",""));
        l.add(new LocalModel("llama-3.2-3b","Llama 3.2 3B","Meta","Better quality for general and coding tasks","≈ 2.0 GB","llama-3.2-3b.gguf",""));
        l.add(new LocalModel("phi-3-mini","Phi 3 Mini","Microsoft","Fast and lightweight","≈ 2.4 GB","phi-3-mini.gguf",""));
        l.add(new LocalModel("gemma-2-2b","Gemma 2 2B","Google","Small model for chat and reasoning","≈ 1.8 GB","gemma-2-2b.gguf",""));
        l.add(new LocalModel("mistral-7b","Mistral 7B","Mistral AI","Higher quality local responses","≈ 4.4 GB","mistral-7b.gguf",""));
        l.add(new LocalModel("qwen-2.5-3b","Qwen 2.5 3B","Alibaba","Strong multilingual support","≈ 2.1 GB","qwen-2.5-3b.gguf",""));
        l.add(new LocalModel("deepseek-r1","DeepSeek R1 (Distill)","DeepSeek","Strong coding and reasoning variants","≈ 2–5 GB","deepseek-r1.gguf",""));
        return l;
    }
    public static List<File> installed(Context c){File[] files=getDirectory(c).listFiles((dir,n)->n.endsWith(".gguf"));List<File> out=new ArrayList<>();if(files!=null)for(File f:files)out.add(f);return out;}
    public static File importModel(Context c, Uri uri, String name) throws Exception {File target=new File(getDirectory(c),name.endsWith(".gguf")?name:name+".gguf");try(InputStream in=c.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(target)){if(in==null)throw new IllegalStateException("Unable to open model");byte[] b=new byte[64*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return target;}
    public static boolean delete(Context c,String name){return new File(getDirectory(c),name).delete();}
}
