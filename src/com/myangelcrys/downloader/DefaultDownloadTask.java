package com.myangelcrys.downloader;

import com.sun.istack.internal.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by cs on 16-10-2.
 */
public class DefaultDownloadTask extends AbstractDownloadTask{
    private FileChannel channel = null;
    RandomAccessFile randomAccessFile;
    int redirectTimes=5;

    public DefaultDownloadTask(TaskInfo taskInfo, DownloadManager dm, File f) {
        super(taskInfo,dm);
        try {
            randomAccessFile=new RandomAccessFile(f,"rw");
            randomAccessFile.seek(taskInfo.getStartByte()+taskInfo.getDownloadedBytes());
            channel = randomAccessFile.getChannel();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    @NotNull
    InputStream initInputStream() throws IOException{
        URI uri=getTaskInfo().getUri();
        HttpURLConnection s = null;
        if (getTaskInfo().getProxy()==null) s=(HttpURLConnection) uri.toURL().openConnection();
        else {
            s=(HttpURLConnection) uri.toURL().openConnection(getTaskInfo().getProxy());
        }
        if (getDownloadManager().isSupportMultiThread()){
            s.setRequestProperty("Range", "bytes="
                    +(getTaskInfo().getStartByte()+getTaskInfo().getDownloadedBytes()) + "-" + (0>=getTaskInfo().getStopByte()?"":getTaskInfo().getStopByte())
            );
        }
        if (getTaskInfo().getHeaders()!=null){
            for (Map.Entry<String,String> entry:getTaskInfo().getHeaders().entrySet()){
                s.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        s.connect();
        System.out.println(getTaskInfo().getHeaders());
        System.out.println(s.getHeaderFields());
        int code=s.getResponseCode();
        if(code/100==3){//3xx
            if (redirectTimes--<=0)return null;
            String cookie = s.getHeaderField("Set-Cookie");
            Map<String, String> headers = getTaskInfo().getHeaders();
            if (cookie!=null)headers.put("Cookie",cookie);
            headers.put("Refer",getTaskInfo().getUri().toString());//FIXME refer ok?
            getTaskInfo().setHeaders(headers);
            try {
                getTaskInfo().setUri(new URI(s.getHeaderField("Location")));
                System.out.println("redirecting to:"+getTaskInfo().getUri());
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            return initInputStream();
        }
        else if (code>=400){
            for (TaskEventListener taskEventListener:getTaskEventListeners()) {
                taskEventListener.onErrorCode(this,code);
            }
            return null;
        }
        //TODO
        for (TaskEventListener taskEventListener:getTaskEventListeners()){
            taskEventListener.onConnected(s);
        }
        return s.getInputStream();
    }

    @Override
    void processData(ByteBuffer byteBuffer) {
        try {
            channel.write(byteBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}