package com.example.demo.jobs.converter;

import org.springframework.util.StringUtils;

/**
 * 单个过滤对象bean
 * @author felix
 */
public final class FilterBean {
    // 过滤字段
    private String filterFieldName;
    // 过滤方法
    private String filterMethod;

    public String getFilterFieldName() {
        return filterFieldName;
    }

    public void setFilterFieldName(String filterFieldName) {
        this.filterFieldName = filterFieldName;
    }

    public String getFilterMethod() {
        return filterMethod;
    }

    public void setFilterMethod(String filterMethod) {
        this.filterMethod = filterMethod;
    }

    public boolean isEmpty(){
        return StringUtils.isEmpty(filterFieldName) && StringUtils.isEmpty(filterMethod);
    }
}