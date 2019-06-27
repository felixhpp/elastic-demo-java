package com.example.demo.core.bean;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 创建一个bean来接收elastic mapper 配置信息
 */
@Component
@ConfigurationProperties(prefix="elastic-mapper") //接收application.yml中的elasticMapper下面的属性
public class ElasticMapperBean {
    /**
     * 是否启用elastic mapper, 默认false
     */
    private Boolean onMapper = false;

    private String defaultIndex = "csmsearch";

    final public Boolean getOnMapper() {
        return onMapper;
    }

    final public void setOnMapper(Boolean onMapper) {
        this.onMapper = onMapper;
    }

    final public String getDefaultIndex(){
        return this.defaultIndex;
    }

    final public void setDefaultIndex(String index){
        this.defaultIndex = index;
    }
}
