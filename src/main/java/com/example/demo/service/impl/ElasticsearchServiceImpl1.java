package com.example.demo.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONReader;
import com.example.demo.core.bean.ElasticMapperBean;
import com.example.demo.core.entity.BulkCaseRequestBody;
import com.example.demo.core.entity.BulkResponseBody;
import com.example.demo.core.enums.ElasticTypeEnum;
import com.example.demo.core.utils.ESBulkModel;
import com.example.demo.core.utils.ExecutorsUtil;
import com.example.demo.elastic.ConvertPipeline;
import com.example.demo.elastic.xmlbean.CaseRecordXmlAnaly;
import com.example.demo.service.ElasticsearchService1;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.cluster.GetSettings;
import io.searchbox.core.*;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.DeleteIndex;
import io.searchbox.indices.aliases.GetAliases;
import io.searchbox.indices.mapping.GetMapping;
import io.searchbox.indices.mapping.PutMapping;
import io.searchbox.params.Parameters;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
public class ElasticsearchServiceImpl1 implements ElasticsearchService1 {
    private Logger logger = LoggerFactory.getLogger("elasticsearch-server");

    @Autowired
    JestClient jestClient;

    @Autowired
    private BulkProcessor bulkProcessor;

    //通过注解引入配置
    @Resource(name = "defaultThreadPool")
    private ExecutorsUtil executor;

    @Autowired
    private ElasticMapperBean mapperBean;
    /**
     * 创建索引
     * @param index
     */
    public void createIndex(String index) {
        try {
            JestResult jestResult = jestClient.execute(new CreateIndex.Builder(index).build());
            System.out.println("createIndex:{}" + jestResult.isSucceeded());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 删除索引
     * @param index
     */
    public void deleteIndex(String index) {
        try {
            JestResult jestResult = jestClient.execute(new DeleteIndex.Builder(index).build());
            System.out.println("deleteIndex result:{}" + jestResult.isSucceeded());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置index的mapping
     * @param index 索引名称
     * @param type 类型名称
     * @param mappingString 拼接好的json格式的mapping串
     */
    public void createIndexMapping(String index, String type, String mappingString) {
        //mappingString为拼接好的json格式的mapping串
        PutMapping.Builder builder = new PutMapping.Builder(index, type, mappingString);
        try {
            JestResult jestResult = jestClient.execute(builder.build());
            System.out.println("createIndexMapping result:{}" + jestResult.isSucceeded());
            if (!jestResult.isSucceeded()) {
                System.err.println("settingIndexMapping error:{}" + jestResult.getErrorMessage());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取index的mapping
     * @param indexName
     * @param typeName
     * @return
     */
    public String getMapping(String indexName, String typeName){
        try {
            GetMapping.Builder builder = new GetMapping.Builder();
            builder.addIndex(indexName).addType(typeName);
            JestResult result = jestClient.execute(builder.build());
            if (result != null && result.isSucceeded()) {
                String mappingStr = result.getSourceAsObject(JsonObject.class).toString();
                logger.info("es get mapping: {}", mappingStr);
                return mappingStr;
            }
            logger.error("es get mapping Exception: " + result.getErrorMessage());
        } catch (Exception e) {
            logger.error("Exception", e);
            e.printStackTrace();
        }
        return null;
    }

    public boolean getIndexSettings(String indexName){
        try {
            GetSettings.Builder builder = new GetSettings.Builder();
            JestResult jestResult = jestClient.execute(builder.build());
            System.out.println(jestResult.getJsonString());
            if (jestResult != null) {
                return jestResult.isSucceeded();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 获取索引别名
     * @param index
     * @return
     */
    public boolean getIndexAliases(String index) {
        try {
            JestResult jestResult = jestClient.execute(new GetAliases.Builder().addIndex(index).build());
            System.out.println(jestResult.getJsonString());
            if (jestResult != null) {
                return jestResult.isSucceeded();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 删除文档
     * @param indexId
     * @param indexName
     * @param indexType
     */
    public boolean deleteDoc(String indexId, String indexName, String indexType) {
        Delete.Builder builder = new Delete.Builder(indexId);

        builder.refresh(true);
        Delete delete = builder.index(indexName).type(indexType).build();
        try {
            JestResult result = jestClient.execute(delete);
            if (result != null && !result.isSucceeded()) {
                throw new RuntimeException(result.getErrorMessage()+"删除文档失败!");
            }
        } catch (Exception e) {
            logger.error("",e);
            return false;
        }
        return true;
    }

    /**
     * 插入或者更新数据
     * @param indexId
     * @param indexObject
     * @param indexName
     * @param indexType
     * @return
     */
    public boolean insertOrUpdateDoc(String indexId, Object indexObject, String indexName, String indexType) {
        Index.Builder builder = new Index.Builder(indexObject);
        builder.id(indexId);
        builder.refresh(true);
        Index index = builder.index(indexName).type(indexType).build();
        try{
            JestResult result = jestClient.execute(index);
            if (result != null && !result.isSucceeded()) {
                throw new RuntimeException(result.getErrorMessage()+"插入更新索引失败!");
            }
        } catch (Exception e){
            logger.error("",e);
            return false;
        }
        return true;
    }

    /**
     *
     * @param theme
     * @param dataJsonStr
     * @return
     */
    @Async
    public void bulk(String theme, String dataJsonStr){
        BulkResponseBody result = new BulkResponseBody();
        try {
            String indexName = mapperBean.getDefaultIndex();
            String typeName = ElasticTypeEnum.getByTheme(theme).getEsType();

            JSONReader reader = new JSONReader(new StringReader(dataJsonStr));//已流的方式处理，这里很快
            reader.startArray();
            List<Map<String, Object>> rsList = new ArrayList<Map<String, Object>>();
            Map<String, Object> map = null;
            int i = 0;
            while (reader.hasNext()) {
                i++;
                reader.startObject();//这边反序列化也是极速
                map = new HashMap<String, Object>();
                while (reader.hasNext()) {
                    String arrayListItemKey = reader.readString();
                    String arrayListItemValue = reader.readObject().toString();
                    map.put(arrayListItemKey, arrayListItemValue);
                }
                rsList.add(map);
                reader.endObject();
            }
            reader.endArray();

            //result = this.bulk(indexName,typeName, rsList);
            this.bulkApi(indexName,typeName, rsList);

        } catch (Exception e){
            result.setResultCode("-1");
            result.setResultContent("请求异常，错误信息:" + e.getMessage());
        }

        return;
    }
    public BulkResponseBody bulk(String indexName, String typeName, List<Map<String, Object>> dataArray){
        ElasticsearchClient client = null;
        BulkResponseBody result = new BulkResponseBody();
        try {

            ElasticTypeEnum typeEnum = ElasticTypeEnum.getByEsType(typeName);
            long startTime=System.currentTimeMillis();

            Bulk.Builder builder = ConvertPipeline.convertToBulkActions(typeEnum,
                    dataArray, mapperBean.getOnMapper(), indexName, typeName);
            long endTime=System.currentTimeMillis();
            System.out.println("convert耗时：" + (endTime-startTime)+"ms");
            BulkResult br = jestClient.execute(builder.build());

            int totleCount = br.getItems().size();
            int failedCount = br.getFailedItems().size();
            int successCount = totleCount - failedCount;
            long endTime1=System.currentTimeMillis();
            System.out.println("bulk耗时：" + (endTime1-endTime)+"ms");
            // 日志打印
            logger.info("[ bulk ] total: [{}], failed:[{}]", br.getItems().size(), br.getFailedItems().size());

            if(br.getFailedItems().size()> 0){
                logger.info("bulk failed \r\n[{}]", JSON.toJSONString(br.getFailedItems().get(0)));
                result.setResultCode("-1");
            }
            result.setResultContent("成功数量：" + successCount + ", 失败数量：" + failedCount);
            if(br != null && br.isSucceeded()){
                result.setResultCode("0");
            }
        }catch (IOException e) {
            logger.error("bulk error", e.getStackTrace()[0].toString());
            result.setResultCode("-1");
            result.setResultContent("请求异常，错误信息:" + e.getMessage());
        } catch (Exception e) {
            logger.error("bulk error", e.getStackTrace()[0].toString());
            result.setResultCode("-1");
            result.setResultContent("请求异常，错误信息:" + e.getMessage());
        }

        return result;
    }

    // 原生es client 测试
    @Async
    public void bulkApi(String indexName, String typeName, List<Map<String, Object>> dataArray){
        BulkResponseBody result = new BulkResponseBody();
        long startTime = System.currentTimeMillis();

        ElasticTypeEnum typeEnum = ElasticTypeEnum.getByEsType(typeName);
        int bulkSize = 2000;
        // 2000条数据一组
        List<List<Map<String, Object>>> subs = Lists.partition(dataArray, bulkSize);
        //生成一个集合
        List<Future> futureList = new ArrayList<>();
        subs.stream().forEach(list -> {
            Callable<Integer> callable = new Callable<Integer>() {
                public Integer call() throws Exception {
                    List<ESBulkModel> models = null;
                    try {
                        models = ConvertPipeline.convertToBulkModels(typeEnum,
                                list, mapperBean.getOnMapper());//
                        if(models != null && models.size() > 0){
                            for (ESBulkModel model : models){
                                IndexRequest request = new IndexRequest(indexName, typeName, model.getId())
                                        .source(model.getMapData())
                                        .routing(model.getRouting());
                                if(StringUtils.isEmpty(model.getParent())){
                                    request.parent(model.getParent());
                                }
                                bulkProcessor.add(request);
                            }
                        }
                    } catch (TaskRejectedException e) {
                        System.out.println("线程池满，等待1S。");
                        try{
                            Thread.sleep(500);
                        }catch(Exception e2){

                        }
                    }
                    return models == null ? 0 : models.size();
                }
            };
            //Future<Integer> future = executor.submit(callable);
            Future<Integer> future = executor.submit(callable);
            //获取当前线程池活动的线程数：
            //int count = executor.getActiveCount();
            //logger.debug("[x] - now threadpool active threads totalNum : " +count);
            futureList.add(future);
        });
        int successSize = 0;
        //添加转换执行的结果
        for (Future<Integer> future : futureList) {
            //CPU高速轮询：每个future都并发轮循，判断完成状态然后获取结果，
            // 这一行，是本实现方案的精髓所在。即有10个future在高速轮询，完成一个future的获取结果，就关闭一个轮询
            while (true) {
                try {
                    if (future.isDone() && !future.isCancelled()) {
                        // 获取future成功完成状态，如果想要限制每个任务的超时时间
                        // 取消本行的状态判断+future.get(1000*1, TimeUnit.MILLISECONDS)+catch超时异常使用即可。
                        Integer i = future.get(1000*1, TimeUnit.MILLISECONDS);
                        successSize = successSize + i;
                        System.out.println("完成 i =" + i + "条，" +new Date());
                        break;  //当前future获取结果完毕，跳出while
                    }else {
                        Thread.sleep(1);//每次轮询休息1毫秒（CPU纳秒级），避免CPU高速轮循耗空CPU-
                    }
                }catch (Exception e){
                    logger.error("error: {}", e);
                    break;
                }

            }
        }

        result.setResultCode("0");
        result.setResultContent("成功" + successSize + "条");
        long endTime = System.currentTimeMillis();
        //logger.info("=====bulk [" + indexName+ "],tool：" + (endTime- startTime) + "ms，message:" + result.getResultContent());

        return;
    }

    public BulkResponseBody bulk(String theme, List<Map<String, Object>>  dataList){
        BulkResponseBody result = new BulkResponseBody();
        try {
            String indexName = mapperBean.getDefaultIndex();
            String typeName = ElasticTypeEnum.getByTheme(theme).getEsType();

            this.bulkApi(indexName,typeName, dataList);
        } catch (Exception e){
            logger.error("bulk error: ", e);
            result.setResultCode("-1");
            result.setResultContent("请求异常，错误信息:" + e.getMessage());
        }

        return result;
    }

    /**
     * 批量导入病历
     * @param caseRequestBodies
     * @return
     */
    public BulkResponseBody bulkCase(List<BulkCaseRequestBody> caseRequestBodies){
        BulkResponseBody result = new BulkResponseBody();
        List<Map<String, Object>> dataArray = null;
        Map<String, ElasticTypeEnum> enumMap = new HashMap<>();
        try {
            Bulk.Builder builder = new Bulk.Builder();
            int size = caseRequestBodies.size();
            for (int i = 0; i< size; i++){
                BulkCaseRequestBody body = caseRequestBodies.get(i);

                //通过documenttypedesc获取theme
                String documenttypedesc = body.getDocumentTypeDesc();
                String theme = "";
                if(StringUtils.isEmpty(documenttypedesc)){
                    continue;
                }
                if(documenttypedesc.equals("入院记录")){
                    theme = "ryjl";
                }
                ElasticTypeEnum typeEnum = enumMap.get(theme);
                if(typeEnum == null){
                    typeEnum = ElasticTypeEnum.getByTheme(theme);
                    enumMap.put(theme, typeEnum);
                }
                // 解析document
               Map<String, Object> map = CaseRecordXmlAnaly
                        .analyCaseRecordXml(body.getDocumentContent(), true);
                if(map == null){
                    continue;
                }
                map.put("documentid", body.getDocumentId());
                map.put("patientid", body.getPatientId());
                map.put("visitnumber", body.getVisitNumber());
                ESBulkModel bulkMode = ConvertPipeline
                        .convertToBulkModel(typeEnum, map, mapperBean.getOnMapper());

                buildBulkAction(builder, mapperBean.getDefaultIndex(), typeEnum.getEsType(), bulkMode);
            }

            BulkResult br = jestClient.execute(builder.build());
            int totleCount = br.getItems().size();
            int failedCount = br.getFailedItems().size();
            int successCount = totleCount - failedCount;
            // 日志打印
            logger.info("[ bulk ] total: [{}], failed:[{}]", br.getItems().size(), br.getFailedItems().size());

            if(br.getFailedItems().size()> 0){
                logger.info("bulk failed \r\n[{}]", JSON.toJSONString(br.getFailedItems().get(0)));
                result.setResultCode("-1");
            }
            result.setResultContent("成功数量：" + successCount + ", 失败数量：" + failedCount);
            if(br != null && br.isSucceeded()){
                result.setResultCode("0");
            }
        }catch (Exception e){
            logger.error("bulk error", e.getStackTrace()[0].toString());
            result.setResultCode("-1");
            result.setResultContent("请求异常，错误信息:" + e.getMessage());
        }

        return result;
    }

    public String getPatientByRegNo(String regNo) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        if(StringUtils.isEmpty(regNo)){
            searchSourceBuilder.query(QueryBuilders.matchAllQuery());
            searchSourceBuilder.size(100);
        }else {
            searchSourceBuilder.query(QueryBuilders.termQuery("patpatientid", regNo));
        }

        Search.Builder builder = new Search.Builder(searchSourceBuilder.toString());
        builder.addIndex(mapperBean.getDefaultIndex()).addType("patient");
        JestResult jestResult = jestClient.execute(builder.build());

        return jestResult.getJsonString();
    }

    private void buildBulkAction(Bulk.Builder builder, String index, String type,
                                 ESBulkModel model){
        if(model == null){
            return;
        }
        Object obj = model.getData();
        Index.Builder indexBuilter = new Index.Builder(obj)
                .index(index)
                .type(type)
                .id(model.getId())
                .setParameter(Parameters.ROUTING, model.getRouting());
        if(!StringUtils.isEmpty(model.getParent())){
            indexBuilter.setParameter(Parameters.PARENT, model.getParent());
        }

        builder.addAction(indexBuilter.build());

    }
    private void buildBulkActions(Bulk.Builder builder, String index, String type,
                                 List<ESBulkModel> bulkModels){
        int len = bulkModels.size();
        for(int i = 0; i < len; i++) {
            ESBulkModel model = bulkModels.get(i);
            buildBulkAction(builder, index,type, model);
        }
    }
}