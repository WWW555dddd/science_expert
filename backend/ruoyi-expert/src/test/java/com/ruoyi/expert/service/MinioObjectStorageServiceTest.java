package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class MinioObjectStorageServiceTest
{
    @Test
    void canonicalRequestKeepsBlankLineAfterCanonicalHeaders()
    {
        String headers = "host:127.0.0.1:9000\n"
                + "x-amz-content-sha256:abc\n"
                + "x-amz-date:20261117T133000Z\n";

        String request = MinioObjectStorageService.canonicalRequest(
                "HEAD", "/science-expert", headers,
                "host;x-amz-content-sha256;x-amz-date", "abc");

        assertEquals("HEAD\n"
                + "/science-expert\n"
                + "\n"
                + "host:127.0.0.1:9000\n"
                + "x-amz-content-sha256:abc\n"
                + "x-amz-date:20261117T133000Z\n"
                + "\n"
                + "host;x-amz-content-sha256;x-amz-date\n"
                + "abc", request);
    }
}
