package com.ruoyi.expert.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.expert.domain.ExpertObjectCleanupTask;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * 通过数据库补偿任务删除外部对象，避免数据库回滚后文件已经丢失。
 */
@Service
public class ExpertObjectCleanupService
{
    private static final Logger log = LoggerFactory.getLogger(ExpertObjectCleanupService.class);

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertObjectStorage objectStorage;

    public void queue(String objectUri)
    {
        if (objectUri == null || !objectUri.startsWith("minio://"))
        {
            return;
        }
        platformMapper.insertObjectCleanupTask(objectUri);
    }

    public void drainAfterCommit()
    {
        if (TransactionSynchronizationManager.isActualTransactionActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    CompletableFuture.runAsync(() -> drain());
                }
            });
            return;
        }
        drain();
    }

    @Scheduled(fixedDelay = 60000L)
    public void drain()
    {
        List<ExpertObjectCleanupTask> tasks = platformMapper.selectObjectCleanupTasks(20);
        for (ExpertObjectCleanupTask task : tasks)
        {
            try
            {
                objectStorage.delete(task.getObjectUri());
                platformMapper.markObjectCleanupDone(task.getCleanupId());
            }
            catch (Exception ex)
            {
                String message = limit(ex.getMessage(), 500);
                platformMapper.markObjectCleanupRetry(task.getCleanupId(), message);
                log.warn("MinIO 补偿删除失败，稍后重试: {}", task.getObjectUri());
            }
        }
    }

    private static String limit(String message, int maxLength)
    {
        String value = message == null ? "删除失败" : message;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
