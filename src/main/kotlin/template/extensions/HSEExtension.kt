@file:Suppress("MagicNumber", "UnderscoresInNumericLiterals", "TooGenericExceptionCaught")

package template.extensions

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.scheduling.Scheduler
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.event.gateway.ReadyEvent
import io.ktor.client.HttpClient
import io.ktor.client.request.get

private val GUILD_ID = Snowflake(124255619791323136)
private val CHANNEL_ID = Snowflake(673116951341563923)

private const val URL = "https://vaccine.hse.ie/"
private const val POLLING_SECONDS = 60L

class HSEExtension : Extension() {
    override val name: String = "hse"

    private var ageGroups: List<Int>? = null
    private var channel: GuildMessageChannel? = null

    private val client = HttpClient()
    private val scheduler = Scheduler()

    override suspend fun setup() {
        slashCommand {
            name = "jabs"
            description = "Check what the current vaccination age group is in Ireland."

            guild(GUILD_ID)

            action {
                if (ageGroups == null) {
                    ephemeralFollowUp {
                        content =
                            "I don't currently know what the age group is. This could be for one of the following " +
                                    "reasons:\n\n" +

                                    "**»** An update to the HSE website broke the bot\n" +
                                    "**»** You ran the command just after the bot started up\n" +
                                    "**»** There's another problem with the bot\n\n" +

                                    "If you're reading this message, please notify <@109040264529608704> of the issue."
                    }
                } else {
                    ephemeralFollowUp {
                        content = when (ageGroups!!.size) {
                            1 -> "Current age group: `${ageGroups!!.first()}+`"
                            2 -> "Current age group: `${ageGroups!!.joinToString(" - ")}`"

                            else -> "Unknown age group - the HSE provided these numbers: " +
                                    ageGroups!!.joinToString(" / ") { "`$it`" }
                        }
                    }
                }
            }
        }

        event<ReadyEvent> {
            action {
                channel = kord.getChannelOf(CHANNEL_ID)
            }
        }

        check()

        scheduler.schedule(POLLING_SECONDS, callback = ::check)
    }

    private suspend fun check() {
        val html = try {
            client.get<String>(URL)
        } catch (T: Throwable) {
            println(T)

            return
        }

        val paragraph = html.split("<h2>Who can register</h2>", limit = 2)
            .last()
            .split("</p>", limit = 2)
            .first()
            .replace("<p>", "")
            .trim('.')
            .trim()

        val groups = paragraph.split(" ").filter { it.all { it.isDigit() } }.map { it.toInt() }

        if (ageGroups == null) {
            ageGroups = groups
        } else if (ageGroups != groups) {
            channel?.createMessage {
                content = "**The HSE has updated their site**\n\n" +
                        when (ageGroups!!.size) {
                            1 -> "Current age group: `${ageGroups!!.first()}+`"
                            2 -> "Current age group: `${ageGroups!!.joinToString(" - ")}`"

                            else -> "Unknown age group - the HSE provided these numbers: " +
                                    ageGroups!!.joinToString(" / ") { "`$it`" }
                        }
            }

            ageGroups = groups
        }
    }
}
