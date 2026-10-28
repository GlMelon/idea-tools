package com.parkdev.vehicle.mapper;

import com.parkdev.vehicle.entity.Vehicle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 车辆基本信息 Mapper 接口 —— MyBatisCodeHelper-Pro 迁移功能验证 Fixture
 * <p>
 * 对应 XML: src/test/resources/mapper/VehicleMapper.xml
 * 数据库表: park_dev.vehicle (通过 DBX MCP 连接的真实数据源)
 * </p>
 *
 * @author fixture
 */
@Mapper
public interface VehicleMapper {

    /**
     * 按主键查询 —— 验证 #{} 参数引用跳转、resultMap 引用、SQL 语言注入
     */
    Vehicle selectById(@Param("id") Long id);

    /**
     * 条件查询 —— 验证动态 SQL(where/if)、OGNL 表达式检测与补全
     */
    List<Vehicle> selectByCondition(@Param("license") String license,
                                    @Param("type") Long type,
                                    @Param("projectId") Long projectId);

    /**
     * 查询全部 —— 验证 sql 片段 include/refid 引用
     */
    List<Vehicle> selectAll();

    /**
     * 新增车辆 —— 验证 insert 标签、parameterType 类型引用、keyProperty
     */
    int insertVehicle(Vehicle vehicle);

    /**
     * 更新车辆 —— 验证 set 动态标签
     */
    int updateVehicle(Vehicle vehicle);

    /**
     * 按主键删除 —— 验证 delete 标签
     */
    int deleteById(@Param("id") Long id);

    /**
     * 按主键列表查询 —— 验证 foreach 集合参数
     */
    List<Vehicle> selectByIdList(@Param("ids") List<Long> ids);

    /**
     * 按部门统计 —— 验证聚合函数 SQL 注入与检测
     */
    int countByDept(@Param("deptId") Long deptId);

    /**
     * 按车牌查询 —— 故意不提供对应 XML 标签,
     * 用于验证 MyBatisMethodNotInXmlInspection 检测及 GenerateSqlTagQuickFix 生成
     * (生成时应使用修复后的模板, 输出 #{license} 而非坏的 #{$param.name})
     */
    List<Vehicle> selectByLicense(@Param("license") String license);
}
