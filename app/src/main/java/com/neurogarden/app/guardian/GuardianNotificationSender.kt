package com.neurogarden.app.guardian

/**
 * 通知发送结果
 */
data class GuardianNotificationSendResult(
    val success: Boolean,
    val status: GuardianNotificationStatus,
    val message: String,
    val externalId: String? = null,
    val sentAt: Long = System.currentTimeMillis()
)

/**
 * 通知发送器接口
 * 负责将守护通知发送到指定渠道
 */
interface GuardianNotificationSender {
    suspend fun send(notification: GuardianNotificationPayload): GuardianNotificationSendResult

    val channel: GuardianNotificationChannel

    val displayName: String
}

/**
 * 通知负载数据类
 */
data class GuardianNotificationPayload(
    val notificationId: String,
    val eventId: Long,
    val guardianName: String,
    val relationship: String,
    val priority: GuardianPriority,
    val message: String,
    val riskLevel: String,
    val deviationLevel: SpecialCareDeviationLevel?,
    val strategyTags: List<String>
)

/**
 * 模拟通知发送器
 * 用于演示和本地测试，不真实发送通知
 */
class MockGuardianNotificationSender : GuardianNotificationSender {
    override val channel: GuardianNotificationChannel = GuardianNotificationChannel.APP
    override val displayName: String = "模拟通知"

    override suspend fun send(notification: GuardianNotificationPayload): GuardianNotificationSendResult {
        return GuardianNotificationSendResult(
            success = true,
            status = GuardianNotificationStatus.SENT,
            message = "模拟通知已生成（本地演示模式）",
            externalId = "mock_${notification.notificationId}"
        )
    }
}

/**
 * 短信通知发送器占位实现
 * 后续可接入真实短信服务（如阿里云短信、腾讯云短信等）
 */
class SmsGuardianNotificationSender : GuardianNotificationSender {
    override val channel: GuardianNotificationChannel = GuardianNotificationChannel.SMS
    override val displayName: String = "短信通知"

    override suspend fun send(notification: GuardianNotificationPayload): GuardianNotificationSendResult {
        // TODO: 接入真实短信服务
        // 示例接入方式：
        // 1. 阿里云短信: https://dysms.console.aliyun.com/
        // 2. 腾讯云短信: https://console.cloud.tencent.com/smsv2
        // 3. 华为云短信: https://console.huaweicloud.com/sms/
        return GuardianNotificationSendResult(
            success = false,
            status = GuardianNotificationStatus.SKIPPED,
            message = "短信服务尚未配置，请联系开发者配置短信服务后使用",
            externalId = null
        )
    }
}

/**
 * 邮箱通知发送器占位实现
 * 后续可接入真实邮件服务（如 SendGrid、Mailgun 等）
 */
class EmailGuardianNotificationSender : GuardianNotificationSender {
    override val channel: GuardianNotificationChannel = GuardianNotificationChannel.EMAIL
    override val displayName: String = "邮箱通知"

    override suspend fun send(notification: GuardianNotificationPayload): GuardianNotificationSendResult {
        // TODO: 接入真实邮件服务
        // 示例接入方式：
        // 1. SendGrid: https://sendgrid.com/
        // 2. Mailgun: https://www.mailgun.com/
        // 3. AWS SES: https://aws.amazon.com/ses/
        return GuardianNotificationSendResult(
            success = false,
            status = GuardianNotificationStatus.SKIPPED,
            message = "邮箱服务尚未配置，请联系开发者配置邮件服务后使用",
            externalId = null
        )
    }
}

/**
 * 微信通知发送器占位实现
 * 后续可接入真实微信服务（如企业微信、公众号模板消息等）
 */
class WechatGuardianNotificationSender : GuardianNotificationSender {
    override val channel: GuardianNotificationChannel = GuardianNotificationChannel.WECHAT
    override val displayName: String = "微信通知"

    override suspend fun send(notification: GuardianNotificationPayload): GuardianNotificationSendResult {
        // TODO: 接入真实微信服务
        // 示例接入方式：
        // 1. 企业微信应用消息: https://work.weixin.qq.com/
        // 2. 微信公众号模板消息: https://mp.weixin.qq.com/
        // 3. 微信小程序订阅消息
        return GuardianNotificationSendResult(
            success = false,
            status = GuardianNotificationStatus.SKIPPED,
            message = "微信服务尚未配置，请联系开发者配置微信服务后使用",
            externalId = null
        )
    }
}

/**
 * 通知发送器工厂
 * 根据渠道返回对应的发送器实现
 */
object GuardianNotificationSenderFactory {
    private val defaultSender: GuardianNotificationSender = MockGuardianNotificationSender()

    private val senders = mapOf(
        GuardianNotificationChannel.APP to MockGuardianNotificationSender(),
        GuardianNotificationChannel.SMS to SmsGuardianNotificationSender(),
        GuardianNotificationChannel.EMAIL to EmailGuardianNotificationSender(),
        GuardianNotificationChannel.WECHAT to WechatGuardianNotificationSender()
    )

    fun getSender(channel: GuardianNotificationChannel): GuardianNotificationSender {
        return senders[channel] ?: defaultSender
    }

    fun getDefaultSender(): GuardianNotificationSender = defaultSender

    fun getAllSenders(): List<GuardianNotificationSender> = senders.values.toList()
}
