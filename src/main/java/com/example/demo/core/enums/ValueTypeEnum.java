package com.example.demo.core.enums;

public enum ValueTypeEnum {
    /**
     * 正数
     */
    PositiveNumValue,
    /**
     * 负数
     */
    NegativeNumValue,
    /**
     * 大于
     */
    GreaterThanNumValue,
    /**
     * 小于
     */
    LessThanNumValue,
    /**
     * 数值
     */
    DefaultNumber,
    /**
     * 忽略整数部分的情况，如 .1  .11
     */
    IgnoreIntger,
    /**
     * 其他
     */
    DefaultValue;

}