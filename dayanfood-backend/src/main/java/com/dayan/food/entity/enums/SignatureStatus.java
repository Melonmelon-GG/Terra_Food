package com.dayan.food.entity.enums;

/**
 * 用户个性签名审核状态。沿用菜品审核的三态语义，与新签名保持独立枚举。
 */
public enum SignatureStatus {
    PENDING,
    APPROVED,
    REJECTED
}