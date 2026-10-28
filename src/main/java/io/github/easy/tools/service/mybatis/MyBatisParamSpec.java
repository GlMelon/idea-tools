package io.github.easy.tools.service.mybatis;

import java.util.List;
import java.util.Set;

/**
 * MyBatis #{} 占位符参数规范常量
 * <p>
 * MyBatis 允许在占位符内声明参数属性: {@code #{param, javaType=..., jdbcType=..., mode=...}}，
 * 本类集中维护合法的属性 key 与取值集合，供补全与检测共用。
 * </p>
 *
 * @author haijun
 * @version 1.1.0
 * @date 2026-10-28
 * @since 1.1.0
 */
public final class MyBatisParamSpec {

    /**
     * 占位符内可配置的属性 key
     */
    public static final Set<String> PROPERTY_KEYS = Set.of(
            "javaType", "jdbcType", "mode", "numericScale", "resultMap", "typeHandler"
    );

    /**
     * org.apache.ibatis.type.JdbcType 枚举值
     */
    public static final List<String> JDBC_TYPES = List.of(
            "ARRAY", "BIGINT", "BINARY", "BIT", "BLOB", "BOOLEAN", "CHAR", "CLOB",
            "CURSOR", "DATALINK", "DATE", "DECIMAL", "DISTINCT", "DOUBLE", "FLOAT",
            "INTEGER", "JAVA_OBJECT", "LONGNVARCHAR", "LONGVARBINARY", "LONGVARCHAR",
            "NCHAR", "NCLOB", "NULL", "NUMERIC", "NVARCHAR", "OTHER", "REAL", "REF",
            "ROWID", "SMALLINT", "SQLXML", "STRUCT", "TIME", "TIMESTAMP", "TINYINT",
            "UNDEFINED", "VARBINARY", "VARCHAR"
    );

    /**
     * mode 属性合法值
     */
    public static final List<String> MODES = List.of("IN", "OUT", "INOUT");

    /**
     * javaType 常用类型补全建议
     */
    public static final List<String> JAVA_TYPES = List.of(
            "java.lang.String", "java.lang.Integer", "java.lang.Long", "java.lang.Double",
            "java.lang.Float", "java.lang.Short", "java.lang.Byte", "java.lang.Boolean",
            "java.math.BigDecimal", "java.util.Date", "java.sql.Date", "java.sql.Time",
            "java.sql.Timestamp", "java.time.LocalDate", "java.time.LocalDateTime",
            "java.time.LocalTime", "byte[]"
    );

    private MyBatisParamSpec() {
        throw new UnsupportedOperationException("Utility class");
    }
}
