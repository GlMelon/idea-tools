package com.parkdev.vehicle.entity;

import java.time.LocalDateTime;

/**
 * 车辆基本信息 实体类
 * <p>对应数据库表: park_dev.vehicle</p>
 *
 * @author fixture
 */
public class Vehicle {

    /**
     * 主键id
     */
    private Long id;

    /**
     * 资产id
     */
    private Long assetId;

    /**
     * 编号
     */
    private String number;

    /**
     * 车牌号
     */
    private String license;

    /**
     * 车辆类型
     */
    private Long type;

    /**
     * 车辆种类
     */
    private Long category;

    /**
     * 车辆小类(字典)
     */
    private Long subCategory;

    /**
     * 变速箱类型
     */
    private Long gearboxType;

    /**
     * 燃油类型
     */
    private Long oilType;

    /**
     * 作业模式
     */
    private String operationType;

    /**
     * 相关附件key，照片
     */
    private String attachmentIds;

    /**
     * 是否绑定设备
     */
    private Boolean hasDevice;

    /**
     * 扩展字段，用于存储车辆的其他信息
     */
    private String extend;

    /**
     * 数据创建人
     */
    private Long creator;

    /**
     * 部门 id
     */
    private Long deptId;

    /**
     * 数据修改人
     */
    private Long updater;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 通用枚举:删除状态:0: 未删除，删除就是当前数据的主键id用于代表唯一性
     */
    private Long deleted;

    /**
     * 专项类型(字典)
     */
    private Long specialType;

    /**
     * 项目id
     */
    private Long projectId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public Long getType() {
        return type;
    }

    public void setType(Long type) {
        this.type = type;
    }

    public Long getCategory() {
        return category;
    }

    public void setCategory(Long category) {
        this.category = category;
    }

    public Long getSubCategory() {
        return subCategory;
    }

    public void setSubCategory(Long subCategory) {
        this.subCategory = subCategory;
    }

    public Long getGearboxType() {
        return gearboxType;
    }

    public void setGearboxType(Long gearboxType) {
        this.gearboxType = gearboxType;
    }

    public Long getOilType() {
        return oilType;
    }

    public void setOilType(Long oilType) {
        this.oilType = oilType;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getAttachmentIds() {
        return attachmentIds;
    }

    public void setAttachmentIds(String attachmentIds) {
        this.attachmentIds = attachmentIds;
    }

    public Boolean getHasDevice() {
        return hasDevice;
    }

    public void setHasDevice(Boolean hasDevice) {
        this.hasDevice = hasDevice;
    }

    public String getExtend() {
        return extend;
    }

    public void setExtend(String extend) {
        this.extend = extend;
    }

    public Long getCreator() {
        return creator;
    }

    public void setCreator(Long creator) {
        this.creator = creator;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getUpdater() {
        return updater;
    }

    public void setUpdater(Long updater) {
        this.updater = updater;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Long getDeleted() {
        return deleted;
    }

    public void setDeleted(Long deleted) {
        this.deleted = deleted;
    }

    public Long getSpecialType() {
        return specialType;
    }

    public void setSpecialType(Long specialType) {
        this.specialType = specialType;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}
