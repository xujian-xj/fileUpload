package com.xj.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * @author xj
 * @date 2021/12/13下午 8:58
 * @description:
 */
@Slf4j
@Controller
public class UploadController {

    private final static String utf8 = "utf-8";

    @RequestMapping("/upload")
    @ResponseBody
    public void upload(HttpServletRequest request, HttpServletResponse response) throws Exception{
        response.setCharacterEncoding(utf8);
        Integer currChunk = null;   //当前分片数
        Integer totalChunk = null;  //总分片数
        String name = null;
        String filePath = "D:\\fileItem";
        BufferedOutputStream os = null;

        try {
            DiskFileItemFactory factory = new DiskFileItemFactory();
            factory.setSizeThreshold(1024); //设置缓冲区（缓存到内存，再写入硬盘）
            factory.setRepository(new File(filePath));  //设置存储目录
            ServletFileUpload upload = new ServletFileUpload(factory); //用于解析request
            upload.setFileSizeMax(5l *1024l *1024l*1024l);  //单个文件最大5，单位byte
            upload.setSizeMax(10l *1024l *1024l*1024l); //总文件最大10，单位byte
            List<FileItem> fileItems = upload.parseRequest(request);
            log.info("*******************fileItems数量："+fileItems.size());

            for (FileItem item : fileItems) {
                if (item.isFormField()){    //非文件，普通表单域
                    if ("chunk".equals(item.getFieldName()))
                        currChunk = Integer.parseInt(item.getString());
                    if ("chunks".equals(item.getFieldName()))
                        totalChunk = Integer.parseInt(item.getString());
                    if ("name".equals(item.getFieldName()))
                        name = item.getString(utf8);
                }
            }
            log.info("*******************currChunk：" + currChunk + "\t" + totalChunk + "\t" + name);

            for (FileItem item : fileItems) {
                if (!item.isFormField()){    //文件
                    String tempFileName = name; //如果文件未被分片，传输的就是整个文件，记录文件名
                    if (name != null){  //文件名不能为null
                        if (currChunk != null){ //表示文件被分片
                            //文件被分片，定义每个分片文件的文件名
                            tempFileName = currChunk + "_" + name;
                        }
                        //判断当前分片文件是否已存在？如果存在则不需要上传了
                        File tempFile = new File(filePath,tempFileName);
                        if (!tempFile.exists()){    //断点续传
                            item.write(tempFile);
                            log.info("不存在的文件："+ tempFileName);
                        }
                    }
                    log.info("**************tempFileName:"+tempFileName);
                }
            }

            //合并文件
            //合并条件：1.文件被分片；2.当最后一个分片上传完成时合并 (分片数从0开始)
            if (currChunk != null && currChunk.intValue() == totalChunk.intValue()-1){
                File file = new File(filePath,name);    //合并后的文件
                os = new BufferedOutputStream(new FileOutputStream(file));

                //按顺序取分片写入
                for(int i = 0; i < totalChunk; i++){
                    File tempFile = new File(filePath,i+"_"+name);
                    //存在以下情况：多个分片并发上传，最后一个分片已经传输完成，而其他分片还未传输完成的情况，所以需要判断所取的临时文件（分片）是否存在
                    while (!tempFile.exists()){
                        Thread.sleep(100);
                    }
                    byte[] bytes = FileUtils.readFileToByteArray(tempFile);
                    os.write(bytes);
                    os.flush();
                    tempFile.delete();
                }
                os.flush();
                response.getWriter().write("上传成功："+name);
            }
        }finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
