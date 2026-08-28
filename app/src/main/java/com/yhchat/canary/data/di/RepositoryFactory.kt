package com.yhchat.canary.data.di

import android.content.Context
import androidx.room.Room
import com.yhchat.canary.data.api.ApiService
import com.yhchat.canary.data.api.WebApiService
import com.yhchat.canary.data.local.AppDatabase
import com.yhchat.canary.data.repository.BotRepository
import com.yhchat.canary.data.repository.CacheRepository
import com.yhchat.canary.data.repository.ChatBackgroundRepository
import com.yhchat.canary.data.repository.CoinRepository
import com.yhchat.canary.data.repository.CommunityRepository
import com.yhchat.canary.data.repository.ConversationRepository
import com.yhchat.canary.data.repository.DiskRepository
import com.yhchat.canary.data.repository.DraftRepository
import com.yhchat.canary.data.repository.ExpressionRepository
import com.yhchat.canary.data.repository.FriendRepository
import com.yhchat.canary.data.repository.GroupRepository
import com.yhchat.canary.data.repository.NavigationRepository
import com.yhchat.canary.data.repository.ReportRepository
import com.yhchat.canary.data.repository.ShareRepository
import com.yhchat.canary.data.repository.StickerRepository
import com.yhchat.canary.data.repository.TokenRepository
import com.yhchat.canary.data.repository.UserRepository
import com.yhchat.canary.data.repository.VipRepository
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Repository工厂类
 */
object RepositoryFactory {
    
    private const val BASE_URL = "https://chat-go.jwzhd.com/"
    private const val WEB_BASE_URL = "https://chat-web-go.jwzhd.com/"
    
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    }
    
    private val webRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(WEB_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    val webApiService: WebApiService by lazy {
        webRetrofit.create(WebApiService::class.java)
    }

    val communityRepository: CommunityRepository by lazy {
        CommunityRepository(apiService)
    }

    // FriendRepository需要通过函数创建，因为需要context来获取tokenRepository

    // BotRepository 需要通过函数创建，因为需要 context 来获取 tokenRepository
    
    /**
     * 获取社区仓库实例
     */
    fun getCommunityRepository(context: Context): CommunityRepository {
        return communityRepository
    }
    
    /**
     * 获取好友仓库实例
     */
    fun getFriendRepository(context: Context): FriendRepository {
        return FriendRepository(apiService, getTokenRepository(context))
    }
    
    /**
     * 获取Token仓库实例
     */
    fun getTokenRepository(context: Context): TokenRepository {
        val database = AppDatabase.getDatabase(context)
        return TokenRepository(database.userTokenDao(), context)
    }
    
    /**
     * 获取会话仓库实例
     */
    fun getConversationRepository(context: Context): ConversationRepository {
        val cacheRepository = CacheRepository(context.applicationContext)
        return ConversationRepository(apiService, cacheRepository)
    }
    
    /**
     * 获取消息仓库实例
     */
    fun getMessageRepository(context: Context): com.yhchat.canary.data.repository.MessageRepository {
        val cacheRepository = CacheRepository(context.applicationContext)
        return com.yhchat.canary.data.repository.MessageRepository(apiService, getTokenRepository(context), cacheRepository)
    }
    
    /**
     * 获取导航配置仓库实例
     */
    fun getNavigationRepository(context: Context): NavigationRepository {
        return NavigationRepository(context)
    }
    
    /**
     * 获取用户仓库实例
     */
    fun getUserRepository(context: Context): UserRepository {
        val repository = UserRepository(apiService, getTokenRepository(context), context)
        return repository
    }
    
    /**
     * 获取群组仓库实例
     */
    fun getGroupRepository(context: Context): GroupRepository {
        val repository = GroupRepository(apiService)
        repository.setTokenRepository(getTokenRepository(context))
        return repository
    }
    
    /**
     * 获取草稿仓库实例
     */
    fun getDraftRepository(context: Context): DraftRepository {
        return DraftRepository(context)
    }
    
    /**
     * 获取机器人仓库实例
     */
    fun getBotRepository(context: Context): BotRepository {
        return BotRepository(
            apiService = apiService,
            webApiService = webApiService,
            tokenRepository = getTokenRepository(context)
        )
    }
    
    /**
     * 获取发现仓库实例
     */
    fun getDiscoverRepository(context: Context): com.yhchat.canary.data.repository.DiscoverRepository {
        return com.yhchat.canary.data.repository.DiscoverRepository(
            apiService = apiService,
            tokenRepository = getTokenRepository(context),
            context = context
        )
    }
    
    /**
     * 获取表情包仓库实例
     */
    fun getExpressionRepository(context: Context): ExpressionRepository {
        return ExpressionRepository(
            apiService = apiService,
            tokenRepository = getTokenRepository(context)
        )
    }
    
    /**
     * 获取举报仓库实例
     */
    fun getReportRepository(context: Context): ReportRepository {
        return ReportRepository(
            apiService = apiService,
            tokenRepository = getTokenRepository(context)
        )
    }
    
    /**
     * 获取分享仓库实例
     */
    fun getShareRepository(context: Context): ShareRepository {
        return ShareRepository(
            apiService = apiService,
            tokenRepository = getTokenRepository(context)
        )
    }
    
    /**
     * 获取聊天背景仓库实例
     */
    fun getChatBackgroundRepository(context: Context): ChatBackgroundRepository {
        return ChatBackgroundRepository(
            apiService = apiService,
            tokenRepository = getTokenRepository(context)
        )
    }
    
    /**
     * 获取贴纸仓库实例
     */
    fun getStickerRepository(context: Context): StickerRepository {
        return StickerRepository(
            apiService = apiService,
            tokenRepository = getTokenRepository(context)
        )
    }
    
    /**
     * 获取云盘仓库实例
     */
    fun getDiskRepository(context: Context): DiskRepository {
        return DiskRepository(
            apiService = apiService,
            tokenRepository = getTokenRepository(context)
        )
    }
    
    /**
     * 获取金币仓库实例
     */
    fun getCoinRepository(context: Context): CoinRepository {
        return CoinRepository(
            apiService = apiService,
            tokenRepository = getTokenRepository(context)
        )
    }
    
    /**
     * 获取VIP仓库实例
     */
    fun getVipRepository(context: Context): VipRepository {
        return VipRepository(
            apiService = apiService,
            tokenRepository = getTokenRepository(context)
        )
    }
    /**
     * 获取更新仓库实例
     */
    fun getUpdateRepository(context: Context): com.yhchat.canary.data.repository.UpdateRepository {
        return com.yhchat.canary.data.repository.UpdateRepository(context)
    }



    /**
     * 获取黑名单仓库实例
     */
    fun getBlocklistRepository(context: Context): com.yhchat.canary.data.repository.BlocklistRepository {
        return com.yhchat.canary.data.repository.BlocklistRepository(context)
    }

    /**
     * 获取账号仓库实例
     */
    fun getAccountRepository(context: Context): com.yhchat.canary.data.repository.AccountRepository {
        val database = com.yhchat.canary.data.local.GlobalDatabase.getDatabase(context)
        return com.yhchat.canary.data.repository.AccountRepository(database.savedAccountDao(), context.applicationContext)
    }
}
