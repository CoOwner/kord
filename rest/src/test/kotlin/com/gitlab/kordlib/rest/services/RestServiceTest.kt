package com.gitlab.kordlib.rest.services

import com.gitlab.kordlib.common.entity.*
import com.gitlab.kordlib.rest.json.request.*
import com.gitlab.kordlib.rest.ratelimit.ExclusionRequestHandler
import com.gitlab.kordlib.rest.ratelimit.RequestHandler
import com.gitlab.kordlib.rest.service.RestClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.defaultRequest
import io.ktor.client.request.header
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import java.util.*

fun image(path: String): String {
    val loader = Unit::class.java.classLoader
    val image = loader?.getResource(path)?.readBytes()
    val encoded = Base64.getEncoder().encodeToString(image)
    val imageType = path.split(".").last()
    return "data:image/$imageType;base64, $encoded"
}

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RestServiceTest {


    private val token = System.getenv("token")

    private lateinit var requestHandler: RequestHandler

    private lateinit var client: HttpClient

    private lateinit var rest: RestClient

    //created guild id
    private lateinit var guildId: String

    //created channel id
    private lateinit var channelId: String

    private lateinit var userId: String

    @BeforeAll
    fun setup() = runBlocking {


        client = HttpClient(CIO) {
            defaultRequest {
                header("Authorization", "Bot $token")
            }
        }
        requestHandler = ExclusionRequestHandler(client)
        rest = RestClient(ExclusionRequestHandler(client))

        userId = rest.user.getCurrentUser().id
    }

    @Test
    @Order(1)
    fun `create guild`() = runBlocking {
        val region = rest.voice.getVoiceRegions().first()
        val guilds = rest.user.getCurrentUserGuilds()

        guilds.filter { it.owner == true }.forEach {
            rest.guild.deleteGuild(it.id)
        }
        val request = GuildCreateRequest(
                "TEST GUILD",
                region.id,
                null,
                VerificationLevel.None,
                DefaultMessageNotificationLevel.AllMessages,
                ExplicitContentFilter.AllMembers,
                emptyList(),
                emptyList()
        )

        val guild = rest.guild.createGuild(request)



        guildId = guild.id

        rest.guild.getGuild(guildId)

        rest.guild.modifyGuild(guildId, GuildModifyRequest("Edited Guild Test"))

        rest.guild.getGuildVoiceRegions(guildId).first()


        Unit
    }

    @Test
    @Order(2)
    fun `create invite`() = runBlocking {
        val generalId = rest.guild.getGuildChannels(guildId).first { it.type == ChannelType.GuildText }.id

        rest.channel.createInvite(generalId, InviteCreatePostRequest())

        Unit
    }

    @Test
    @Order(3)
    fun `create channel`() = runBlocking {
        val channel = rest.guild.createGuildChannel(guildId, GuildCreateChannelRequest("BOT TEST RUN"))
        channelId = channel.id

        rest.channel.getChannel(channel.id)

        Unit
    }

    @Test
    @Order(4)
    fun `reaction in channel`() = runBlocking {
        with(rest.channel) {
            val message = createMessage(channelId, MessageCreatePostRequest("TEST"))
            editMessage(channelId, message.id, MessageEditPatchRequest("EDIT TEST"))

            createReaction(channelId, message.id, "\ud83d\udc4e")
            deleteOwnReaction(channelId, message.id, "\ud83d\udc4e")

            createReaction(channelId, message.id, "\ud83d\udc4d")
            deleteReaction(channelId, message.id, message.author!!.id, "\ud83d\udc4d")

            createReaction(channelId, message.id, "\ud83d\udc4e")
            getReactions(channelId, message.id, "\ud83d\udc4e")
            deleteAllReactions(channelId, message.id)

            deleteMessage(channelId, message.id)
        }
    }

    @Test
    @Order(5)
    fun `message in channel`() = runBlocking {
        with(rest.channel) {
            triggerTypingIndicator(channelId)

            val message = createMessage(channelId, MessageCreatePostRequest("TEST"))

            getMessage(channelId, message.id)

            deleteMessage(channelId, message.id)

            createMessage(channelId, MessageCreatePostRequest("TEST"))
            createMessage(channelId, MultipartMessageCreatePostRequest(MessageCreatePostRequest("TEST")))

            repeat(10) { //trying to get a ratelimit
                createMessage(channelId, MessageCreateRequest("TEST $it"))
            }

            val messages = getMessages(channelId)

            bulkDelete(channelId, BulkDeleteRequest(messages.map { it.id }))

        }
    }

    @Test
    @Order(6)
    fun `pinned messages in channel`() = runBlocking {
        with(rest.channel) {
            val pinnedMessage = createMessage(channelId, MessageCreatePostRequest("TEST"))

            addPinnedMessage(channelId, pinnedMessage.id)

            getChannelPins(channelId)

            deletePinnedMessage(channelId, pinnedMessage.id)

            Unit
        }

    }

    @Test
    @Order(7)
    fun `invites in channel`() = runBlocking {
        with(rest.channel) {
            getChannelInvites(channelId)

            Unit
        }

    }

    @Test
    @Order(8)
    fun `permissions in channels`() = runBlocking {
        val role = rest.guild.createGuildRole(guildId, GuildRoleCreateRequest())
        with(rest.channel) {
            val allow = Permissions { +Permission.CreateInstantInvite }
            val deny = Permissions { +Permission.SendTTSMessages }

            editChannelPermissions(channelId, role.id, ChannelPermissionEditRequest(allow, deny, "role"))

            deleteChannelPermission(channelId, role.id)

        }
    }

    @Test
    @Order(9)
    fun `modify channels`() = runBlocking {
        with(rest.channel) {
            //TODO Test Put method

            patchChannel(channelId, ChannelModifyPatchRequest("PATCH"))

            Unit

        }
    }

//TODO Add Group Channel Tests

    @Test
    @Disabled("Member is not added to guild yet due to Guild#addGuildMember")
    @Order(11)
    fun `member in guild`() = runBlocking {
        with(rest.guild) {
            val members = getGuildMembers(guildId)
            //TODO add member to guild

            modifyGuildMember(guildId, userId, GuildMemberModifyRequest("My nickname", mute = true, deaf = true))

            getGuildMember(guildId, userId)

            //deleteGuildMember(guildId, user)

            modifyCurrentUserNickname(guildId, CurrentUserNicknameModifyRequest("Kord"))

            Unit
        }
    }

    @Test
    @Order(12)
    fun `roles in guild`() = runBlocking {
        with(rest.guild) {
            val role = createGuildRole(
                    guildId,
                    GuildRoleCreateRequest(
                            "Sudoers",
                            Permissions { +Permission.Administrator },
                            5000,
                            true,
                            true
                    )
            )

            modifyGuildRole(guildId, role.id, GuildRoleModifyRequest("Edited role"))

            addRoleToGuildMember(guildId, userId, role.id)

            deleteRoleFromGuildMember(guildId, userId, role.id)

            modifyGuildRolePosition(guildId, com.gitlab.kordlib.rest.json.request.GuildRolePositionModifyRequest(listOf(role.id to 0)))

            getGuildRoles(guildId)

            deleteGuildRole(guildId, role.id)

            Unit
        }
    }

    @Test
    @Disabled("User to ban is not there.")
    @Order(13)
    fun `bans in guild`() = runBlocking {

        with(rest.guild) {

            //addGuildBan(guildId, user, AddGuildBanRequest())

            getGuildBans(guildId)

            //getGuildBan(guildId, user)

            //deleteGuildBan(guildId, user)

            Unit

        }
    }

    @Test
    @Order(14)
    fun `invites in guild`() = runBlocking {
        with(rest.guild) {
            //            getVanityInvite(guildId)  //¯\_(ツ)_/¯
            getGuildInvites(guildId)

            Unit
        }
    }

    @Test
    @Order(15)
    fun `prune members in guilds`() = runBlocking {
        with(rest.guild) {
            getGuildPruneCount(guildId)
            beginGuildPrune(guildId)

            Unit
        }
    }

    @Test
    @Disabled
    @Order(16)
    fun `integrations in guild`() = runBlocking {

        //TODO
    }

    @Test
    @Order(17)
    fun `embeds in guild`() = runBlocking {

        with(rest.guild) {

            modifyGuildEmbed(guildId, GuildEmbedModifyRequest(true, channelId))

            getGuildEmbed(guildId)

            Unit
        }
    }

    @Test
    @Order(18)
    fun `user`() = runBlocking {
        with(rest.user) {
            getCurrentUser()

            getCurrentUserGuilds()

            modifyCurrentUser(CurrentUserModifyRequest("Happy Kord"))

            getUserConnections()

            // getUser(user) // works, but can't be tested with bots client id, use your own id

            // createDM(CreateDMRequest(user)) // works, but can't be tested with bots client id use your own id

            //TODO test groups

            Unit
        }
    }

    @Test
    @Order(19)
    fun `emojis in guilds`() = runBlocking {
        with(rest.emoji) {

            val emoji = createEmoji(guildId, EmojiCreatePostRequest("kord", image("images/kord.png"), listOf(guildId)))

            modifyEmoji(guildId, emoji.id!!, EmojiModifyPatchRequest("edited"))

            getEmojis(guildId)

            getEmoji(guildId, emoji.id!!)

            deleteEmoji(guildId, emoji.id!!)

            Unit
        }
    }


    @Test
    @Order(Int.MAX_VALUE - 2)
    fun `delete channel`() = runBlocking {
        rest.channel.deleteChannel(channelId)

        Unit
    }

    @Test
    @Order(Int.MAX_VALUE - 1)
    fun `audit logs`() = runBlocking {
        rest.auditLog.getAuditLogs(guildId)

        Unit
    }


    @Test
    @Order(Int.MAX_VALUE)
    fun `delete guild`() = runBlocking {
        rest.guild.deleteGuild(guildId)

        Unit
    }

    @AfterAll
    fun close() {
        client.close()
    }

}

