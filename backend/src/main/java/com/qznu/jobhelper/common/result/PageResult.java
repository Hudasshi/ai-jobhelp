package com.qznu.jobhelper.common.result;

import java.util.List;

/**
 * 分页统一返回封装（Record写法，JDK16+）
 * @author HU
 * @version 1.0
 * @date 2026/11/7
 * @description 分页结果实体，封装分页列表信息
 * @param records 当前页数据列表
 * @param total 数据总条数
 * @param page 当前页码
 * @param size 每页条数
 */
public record PageResult<T>(List<T> records, long total, long page, long size) {
}
