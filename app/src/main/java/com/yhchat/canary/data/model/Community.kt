package com.yhchat.canary.data.model

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 社区分区
 */
data class CommunityBoard(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("avatar")
    val avatar: String,
    
    @SerializedName("delTime")
    val delTime: Long,
    
    @SerializedName("createTime")
    val createTime: Long,
    
    @SerializedName("lastActive")
    val lastActive: Long,
    
    @SerializedName("memberNum")
    val memberNum: Int,
    
    @SerializedName("postNum")
    val postNum: Int,
    
    @SerializedName("groupNum")
    val groupNum: Int,
    
    @SerializedName("createTimeText")
    val createTimeText: String,
    
    @SerializedName("isFollowed")
    val isFollowed: String
)

/**
 * 社区文章
 */
data class CommunityPost(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("baId")
    val baId: Int,
    
    @SerializedName("senderId")
    val senderId: String,
    
    @SerializedName("senderNicknameId")
    val senderNicknameId: Int,
    
    @SerializedName("senderAvatarId")
    val senderAvatarId: Int,
    
    @SerializedName("groupId")
    val groupId: String,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("contentType")
    val contentType: Int, // 1-文本，2-markdown
    
    @SerializedName("content")
    val content: String,
    
    @SerializedName("delTime")
    val delTime: Long,
    
    @SerializedName("createTime")
    val createTime: Long,
    
    @SerializedName("updateTime")
    val updateTime: Long,
    
    @SerializedName("editTime")
    val editTime: Long,
    
    @SerializedName("lastActive")
    val lastActive: Long,
    
    @SerializedName("likeNum")
    val likeNum: Int,
    
    @SerializedName("commentNum")
    val commentNum: Int,
    
    @SerializedName("collectNum")
    val collectNum: Int,

    @SerializedName("isDraft")
    val isDraft: Int = 0,
    
    @SerializedName("amountNum")
    val amountNum: Double,
    
    @SerializedName("senderNickname")
    val senderNickname: String,
    
    @SerializedName("senderAvatar")
    val senderAvatar: String,
    
    @SerializedName("createTimeText")
    val createTimeText: String = "",
    
    @SerializedName("group")
    val group: CommunityGroup?,
    
    @SerializedName("isLiked")
    val isLiked: String,
    
    @SerializedName("isCollected")
    val isCollected: Int,
    
    @SerializedName("isReward")
    val isReward: Int,
    
    @SerializedName("isSticky")
    val isSticky: Int = 0,
    
    @SerializedName("score")
    val score: Double? = null,
    
    @SerializedName("isVip")
    val isVip: Int
) {
    val displayCreateTimeText: String
        get() = formatCommunityTimeText(createTimeText, createTime)
}

private fun formatCommunityTimeText(createTimeText: String, createTime: Long): String {
    if (createTimeText.isNotBlank()) return createTimeText
    if (createTime <= 0L) return ""

    val millis = if (createTime < 1_000_000_000_000L) createTime * 1000L else createTime
    val date = Date(millis)
    val now = Date()
    val diffMillis = now.time - date.time
    val diffHours = diffMillis / (1000L * 60L * 60L)

    val nowCal = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = date }
    val isSameYear = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)

    return when {
        diffHours < 1L -> "刚刚"
        diffHours < 24L -> "${diffHours}小时前"
        diffHours < 24L * 7L -> "${diffHours / 24L}天前"
        isSameYear -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
    }
}

/**
 * 社区群组
 */
data class CommunityGroup(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("groupId")
    val groupId: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("introduction")
    val introduction: String,
    
    @SerializedName("createBy")
    val createBy: String,
    
    @SerializedName("createTime")
    val createTime: Long,
    
    @SerializedName("avatarId")
    val avatarId: Int,
    
    @SerializedName("del_flag")
    val delFlag: Int,
    
    @SerializedName("avatarUrl")
    val avatarUrl: String,
    
    @SerializedName("headcount")
    val headcount: Int,
    
    @SerializedName("readHistory")
    val readHistory: Int,
    
    @SerializedName("alwaysAgree")
    val alwaysAgree: Int,
    
    @SerializedName("categoryId")
    val categoryId: Int,
    
    @SerializedName("category")
    val category: String,
    
    @SerializedName("private")
    val private: Int,
    
    @SerializedName("banId")
    val banId: Int,
    
    @SerializedName("gag")
    val gag: Int,
    
    @SerializedName("gagBy")
    val gagBy: String,
    
    @SerializedName("msgTypeLimit")
    val msgTypeLimit: String
)

/**
 * 社区评论
 */
data class CommunityComment(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("postId")
    val postId: Int,
    
    @SerializedName("parentId")
    val parentId: Int,
    
    @SerializedName("senderId")
    val senderId: String,
    
    @SerializedName("sender_nicknameId")
    val senderNicknameId: Int,
    
    @SerializedName("sender_avatarUd")
    val senderAvatarId: Int,
    
    @SerializedName("content")
    val content: String,
    
    @SerializedName("delTime")
    val delTime: Long,
    
    @SerializedName("createTime")
    val createTime: Long,
    
    @SerializedName("likeNum")
    val likeNum: Int,
    
    @SerializedName("repliesNum")
    val repliesNum: Int,
    
    @SerializedName("amountNum")
    val amountNum: Double,
    
    @SerializedName("auditStatus")
    val auditStatus: Int,
    
    @SerializedName("replies")
    val replies: List<CommunityComment>?,
    
    @SerializedName("senderNickname")
    val senderNickname: String,
    
    @SerializedName("senderAvatar")
    val senderAvatar: String,
    
    @SerializedName("createTimeText")
    val createTimeText: String,
    
    @SerializedName("isLiked")
    val isLiked: String,
    
    @SerializedName("isReward")
    val isReward: Int,
    
    @SerializedName("isVip")
    val isVip: Int
)

/**
 * 分区列表响应
 */
data class BoardListResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: BoardListData,
    
    @SerializedName("msg")
    val msg: String
)

data class BoardListData(
    @SerializedName("ba")
    val boards: List<CommunityBoard>,
    
    @SerializedName("total")
    val total: Int
)

/**
 * 文章列表响应
 */
data class PostListResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: PostListData,
    
    @SerializedName("msg")
    val msg: String
)

data class PostListData(
    @SerializedName("posts")
    val posts: List<CommunityPost>,
    
    @SerializedName("total")
    val total: Int
)

/**
 * 文章详情响应
 */
data class PostDetailResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: PostDetailData,
    
    @SerializedName("msg")
    val msg: String
)

data class PostDetailData(
    @SerializedName("ba")
    val board: CommunityBoard,
    
    @SerializedName("isAdmin")
    val isAdmin: Int,
    
    @SerializedName("post")
    val post: CommunityPost
)

/**
 * 评论列表响应
 */
data class CommentListResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: CommentListData,
    
    @SerializedName("msg")
    val msg: String
)

data class CommentListData(
    @SerializedName("comments")
    val comments: List<CommunityComment>,
    
    @SerializedName("isAdmin")
    val isAdmin: Int,
    
    @SerializedName("total")
    val total: Int
)

/**
 * 分区信息响应
 */
data class BoardInfoResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: BoardInfoData,
    
    @SerializedName("msg")
    val msg: String
)

data class BoardInfoData(
    @SerializedName("ba")
    val board: CommunityBoard
)

/**
 * 关注者列表响应
 */
data class BaFollowerListResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: BaFollowerListData,
    
    @SerializedName("msg")
    val msg: String
)

data class BaFollowerListData(
    @SerializedName("followers")
    val followers: List<BaFollowerItem>,
    
    @SerializedName("total")
    val total: Int
)

data class BaFollowerItem(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("baId")
    val baId: Int,
    
    @SerializedName("userId")
    val userId: String,
    
    @SerializedName("delTime")
    val delTime: Long,
    
    @SerializedName("followSource")
    val followSource: Int,
    
    @SerializedName("createTime")
    val createTime: Long,
    
    @SerializedName("userLevel")
    val userLevel: Int,
    
    @SerializedName("nickname")
    val nickname: String,
    
    @SerializedName("avatarUrl")
    val avatarUrl: String,
    
    @SerializedName("vipUserid")
    val vipUserid: String,
    
    @SerializedName("vipEndTime")
    val vipEndTime: Long
)

data class BoardsByCreateResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("data")
    val data: BoardsByCreateData,
    @SerializedName("msg")
    val msg: String
)

data class BoardsByCreateData(
    @SerializedName("ba")
    val boards: List<BoardsByCreateItem>
)

data class BoardsByCreateItem(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("avatar")
    val avatar: String
)

/**
 * 创建文章响应
 */
data class CreatePostResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("data")
    val data: CreatePostData,
    @SerializedName("msg")
    val msg: String
)

data class CreatePostData(
    @SerializedName("audioUrl")
    val postId: Int  // 注意：API返回的字段名是audioUrl，但实际是文章ID
)

/**
 * 编辑文章响应
 */
data class EditPostResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: EditPostData,
    
    @SerializedName("msg")
    val msg: String
)

data class EditPostData(
    @SerializedName("id")
    val postId: Int
)

/**
 * 社区草稿条目
 */
data class CommunityDraftResponseItem(
    @SerializedName(value = "id", alternate = ["draftId"])
    val id: String = "",

    @SerializedName(value = "baId", alternate = ["boardId"])
    val boardId: Int = 0,

    @SerializedName(value = "baName", alternate = ["boardName", "name"])
    val boardName: String? = null,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("content")
    val content: String = "",

    @SerializedName(value = "contentType", alternate = ["markdownType"])
    val contentType: Int = 1,

    @SerializedName(value = "isMarkdownMode", alternate = ["markdownMode"])
    val isMarkdownMode: Boolean? = null,

    @SerializedName("createTime")
    val createTime: Long = 0L,

    @SerializedName("updateTime")
    val updateTime: Long = 0L
) {
    fun toDraft(): Draft {
        val markdownMode = isMarkdownMode ?: (contentType == 2)
        return Draft(
            id = id,
            title = title,
            content = content,
            boardId = boardId,
            boardName = boardName?.takeIf { it.isNotBlank() } ?: "分区 $boardId",
            isMarkdownMode = markdownMode,
            createTime = createTime,
            updateTime = updateTime
        )
    }
}

/**
 * 草稿列表响应
 */
data class DraftListResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("data")
    val data: DraftListData = DraftListData(),

    @SerializedName("msg")
    val msg: String = ""
)

data class DraftListData(
    @SerializedName(value = "drafts", alternate = ["list", "rows", "items"])
    val drafts: List<CommunityDraftResponseItem> = emptyList(),

    @SerializedName("total")
    val total: Int = 0
)

/**
 * 草稿响应
 */
data class DraftResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("data")
    val data: CommunityDraftResponseItem? = null,

    @SerializedName("msg")
    val msg: String = ""
)

/**
 * 保存草稿请求
 */
data class SaveDraftRequest(
    @SerializedName(value = "baId", alternate = ["boardId"])
    val boardId: Int,

    @SerializedName(value = "baName", alternate = ["boardName", "name"])
    val boardName: String? = null,

    @SerializedName("title")
    val title: String,

    @SerializedName("content")
    val content: String,

    @SerializedName("contentType")
    val contentType: Int
)

/**
 * 更新草稿请求
 */
data class UpdateDraftRequest(
    @SerializedName(value = "draftId", alternate = ["id"])
    val draftId: String,

    @SerializedName(value = "baId", alternate = ["boardId"])
    val boardId: Int? = null,

    @SerializedName(value = "baName", alternate = ["boardName", "name"])
    val boardName: String? = null,

    @SerializedName("title")
    val title: String,

    @SerializedName("content")
    val content: String,

    @SerializedName("contentType")
    val contentType: Int
)

/**
 * 删除草稿请求
 */
data class DeleteDraftRequest(
    @SerializedName(value = "draftId", alternate = ["id"])
    val draftId: String
)

/**
 * 我的文章列表响应
 */
data class MyPostListResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: MyPostListData,
    
    @SerializedName("msg")
    val msg: String
)

data class MyPostListData(
    @SerializedName("posts")
    val posts: List<CommunityPost>,
    
    @SerializedName("total")
    val total: Int
)

/**
 * 关注分区列表响应
 */
data class FollowingBoardListResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: FollowingBoardListData,
    
    @SerializedName("msg")
    val msg: String
)

data class FollowingBoardListData(
    @SerializedName("ba")
    val boards: List<CommunityBoard>,
    
    @SerializedName("total")
    val total: Int
)

/**
 * 屏蔽用户
 */
data class BlockedUser(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("user_id")
    val userId: String,
    
    @SerializedName("nickname")
    val nickname: String,
    
    @SerializedName("avatar_url")
    val avatarUrl: String
)

/**
 * 屏蔽用户列表响应
 */
data class BlockedUserListResponse(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("data")
    val data: BlockedUserListData,
    
    @SerializedName("msg")
    val msg: String
)

data class BlockedUserListData(
    @SerializedName("list")
    val list: List<BlockedUser>,
    
    @SerializedName("total")
    val total: Int
)
