package neo.sketchware.ai.local;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker; import androidx.work.WorkerParameters;
import java.io.File; import java.io.FileOutputStream; import java.io.InputStream; import java.net.HttpURLConnection; import java.net.URL;

public class LocalModelDownloadWorker extends Worker {
    public LocalModelDownloadWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
    @NonNull @Override public Result doWork(){String url=getInputData().getString("url"),name=getInputData().getString("name");if(url==null||name==null)return Result.failure();HttpURLConnection c=null;File part=new File(LocalModelManager.getDirectory(getApplicationContext()),name+".part");File out=new File(LocalModelManager.getDirectory(getApplicationContext()),name);try{c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(true);int code=c.getResponseCode();if(code<200||code>=300)throw new IllegalStateException("HTTP "+code);long total=c.getContentLengthLong();try(InputStream in=c.getInputStream();FileOutputStream fos=new FileOutputStream(part)){byte[] b=new byte[64*1024];long done=0;int n;while((n=in.read(b))!=-1){if(isStopped())return Result.failure();fos.write(b,0,n);done+=n;if(total>0)setProgressAsync(new androidx.work.Data.Builder().putInt("progress",(int)(done*100/total)).build());}}if(out.exists())out.delete();if(!part.renameTo(out))throw new IllegalStateException("Unable to finalize model file");return Result.success(new androidx.work.Data.Builder().putString("path",out.getAbsolutePath()).build());}catch(Exception e){return Result.failure(new androidx.work.Data.Builder().putString("error",e.getMessage()==null?"Download failed":e.getMessage()).build());}finally{if(c!=null)c.disconnect();}}
}
