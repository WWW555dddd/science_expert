package com.ruoyi.expert.service;

import java.io.InputStream;

/**
 * 专家资料对象存储抽象。
 */
public interface ExpertObjectStorage
{
    String upload(Long userId, String originalFilename, String contentType, long size, InputStream input);

    byte[] download(String objectUri);

    void delete(String objectUri);

    boolean isConfigured();
}
