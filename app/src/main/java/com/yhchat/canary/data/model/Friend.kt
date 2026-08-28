package com.yhchat.canary.data.model

import com.google.gson.annotations.SerializedName

/**
 * 删除好友/群聊/机器人请求
 */
data class DeleteFriendRequest(
    @SerializedName("chatId")
    val chatId: String,  // 目标对象ID
    
    @SerializedName("chatType")
    val chatType: Int  // 目标对象类别，1-用户，2-群聊，3-机器人
)

/**
 * 处理好友申请请求
 */
data class AgreeApplyRequest(
    @SerializedName("id")
    val id: Int,  // 申请ID
    
    @SerializedName("agree")
    val agree: Int  // 1-通过请求，2-拒绝请求，3-显示请求过期，4-显示已解散
)

