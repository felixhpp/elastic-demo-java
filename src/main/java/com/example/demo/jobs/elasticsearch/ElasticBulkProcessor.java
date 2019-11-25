package com.example.demo.jobs.elasticsearch;


import com.alibaba.fastjson.JSON;
import com.dhcc.csmsearch.elasticsearch.common.ElasticsearchManage;
import com.example.demo.core.entity.ESBulkModel;
import com.example.demo.core.utils.SpringUtils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.huawei.fusioninsight.elasticsearch.transport.client.PreBuiltHWTransportClient;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.huawei.fusioninsight.elasticsearch.transport.client.ClientFactory.isSecureMode;
import static java.util.concurrent.Executors.*;
import static javax.swing.text.html.HTML.Tag.OL;

/**
 * 批量导入HBase工具
 *
 * @author felix
 */
public final class ElasticBulkProcessor {
    private static Logger logger = LoggerFactory.getLogger(ElasticBulkProcessor.class);
    private final static int batchSize = 10000;
    private final static int poolSize = 2;
    private final static long totalFreeTime = 5 * 60 * 1000; // 5分钟
    private static ElasticBulkProcessor processor;
    PreBuiltHWTransportClient client = null;
    public static AtomicInteger activeThreadCount = new AtomicInteger(0);
    private ElasticsearchManage elasticsearchManage = SpringUtils.getBean("ElasticsearchManage");
    /**
     * 初始化队列
     */
    private static BlockingQueue<ESBulkModel> blockingQueue = new LinkedBlockingQueue<>(batchSize * (poolSize + 1));

    /**
     * 可重用固定个数的线程池
     */
    private static ExecutorService fixedThreadPool = null;

    private ElasticBulkProcessor(ElasticsearchManage elasticsearchManage) throws IOException {
        client = elasticsearchManage.getTransportClient();
    }

    /**
     * 获取单例实例
     *
     * @return HBaseBulkProcessor
     */
    public static ElasticBulkProcessor getInstance(ElasticsearchManage elasticsearchManage) throws IOException {
        if (null == processor) {
            // 多线程同步
            synchronized (ElasticBulkProcessor.class) {
                if (null == processor) {
                    processor = new ElasticBulkProcessor(elasticsearchManage);
                }
            }
        }

        return processor;
    }

    /**
     * 同步执行add
     *
     * @param model
     */
    public synchronized void add(ESBulkModel model) {
        try {
            // 将指定元素插入此队列中，将等待可用的空间.当>maxSize 时候，阻塞，直到能够有空间插入元素
            blockingQueue.put(model);
            execute();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭连接
     */
    public void closeConnect() {
        if (null != client) {
            try {
                client.close();
            } catch (Exception e) {
                logger.error("close elasticsearch failure !", e);
            }
        }
    }

    /**
     * 线程池执行
     */
    private void execute() {
        // 获取当前活动的线程数
        int curActiveCount = activeThreadCount.get();
        if (curActiveCount == 0) {
            ExecuteClass executeClass = new ExecuteClass();
            fixedThreadPool.submit(executeClass);
            activeThreadCount.incrementAndGet();
        } else if (blockingQueue.size() >= batchSize) {
            int freeThreadCount = poolSize - curActiveCount;
            if (freeThreadCount >= 1) {
                ExecuteClass executeClass = new ExecuteClass();
                fixedThreadPool.submit(executeClass);
                activeThreadCount.incrementAndGet();
            }
        }
    }

    private void DataInput(List<ESBulkModel> models){

        Map<String, Object> esJson = new HashMap<String, Object>();
        BulkRequestBuilder bulkRequest = client.prepare().prepareBulk();
        int commit = models.size();
        long starttime = System.currentTimeMillis();
        for (int j = 0; j < commit; j++) {
            ESBulkModel curModel = models.get(j);
            esJson.clear();
            esJson.put("id", curModel.getId());
            esJson.put("name", "Linda");
            esJson.put("sex", "man");
            esJson.put("age", 78);
            esJson.put("height", 210);
            esJson.put("weight", 180);
            bulkRequest.add(client.prepare()
                    .prepareIndex(curModel.getIndex(), curModel.getType())
                    .setId(curModel.getId())
                    .setRouting(curModel.getRouting())
                    .setSource(curModel.getMapData()));
        }
        BulkResponse bulkResponse = bulkRequest.get();
        if (bulkResponse.hasFailures()) {
            logger.info("Batch indexing fail!");
        } else {
            logger.info("Batch indexing success and put data time is " + (System.currentTimeMillis() - starttime));
        }
    }

    class ExecuteClass implements Callable<Integer> {

        @Override
        public Integer call() throws Exception {
            logger.info("start -" + Thread.currentThread().getName());
            long freeTime = 0;
            long curTotalFreeTime = 0;
            long sleep = 100;
            // 无限循环从blockQueue中取数据
            while (true) {
                if (blockingQueue != null && blockingQueue.size() >= batchSize) {
                    long curThreadStartTime = System.currentTimeMillis();
                    freeTime = 0;
                    curTotalFreeTime = 0;
                    List<ESBulkModel> models = new ArrayList<>();
                    blockingQueue.drainTo(models, batchSize);
                    if (models.size() == 0) {
                        logger.info(MessageFormat.format("currentThread {0} had no data ", Thread.currentThread().getName()));
                    } else {
                        DataInput(models);
                        long curThreadEndTime = System.currentTimeMillis();
                        logger.info(Thread.currentThread().getName() + "- execute[" + models.size() + "] count, time  tool:"
                                + (curThreadEndTime - curThreadStartTime) + "ms.");
                    }
                } else {
                    // 等待100ms
                    Thread.sleep(sleep);
                    freeTime = freeTime + sleep;
                    curTotalFreeTime = curTotalFreeTime + sleep;
                    // 如果30s内没有数据传入，自动插入一次
                    if (freeTime >= 30000) {
                        long curThreadStartTime = System.currentTimeMillis();
                        freeTime = 0;
                        if (blockingQueue.size() > 0) {
                            List<ESBulkModel> models = new ArrayList<>();
                            blockingQueue.drainTo(models);
                            DataInput(models);
                            long curThreadEndTime = System.currentTimeMillis();
                            //执行操作
                            logger.info(Thread.currentThread().getName() + "- execute[" + models.size() + "]count, time tool:"
                                    + (curThreadEndTime - curThreadStartTime) + "ms.");
                        }
                    }
                    // 如果总空闲时间超过totalFreeTime， 结束线程
                    if (curTotalFreeTime >= totalFreeTime) {
                        logger.info("stop Thread-" + Thread.currentThread().getName());
                        activeThreadCount.decrementAndGet();
                        break;
                    }
                }
            }
            return null;
        }
    }
}