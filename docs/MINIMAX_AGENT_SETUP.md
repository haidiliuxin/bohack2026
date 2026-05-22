# MiniMax Agent 接入说明

NeuroGarden 已支持 MiniMax 的 Anthropic 兼容接口。项目会在 `BuildConfig.GUARDIAN_API_URL` 包含 `/anthropic` 时，自动请求：

```text
https://api.minimaxi.com/anthropic/v1/messages
```

## key.txt 配置

在项目根目录 `key.txt` 中填写：

```text
URL=https://api.minimaxi.com/anthropic
APIkey=你的MiniMax_API_Key
MODEL=MiniMax-M2.7
```

`key.txt` 不要提交到仓库。

## 当前 Agent 能力

MiniMax-M2.7 会用于：

- 结构化信号分析：根据心率、呼吸、运动、输入节奏、基线和反馈判断状态偏离。
- 输出风险等级、建议动作、关怀提示、是否建议守护确认和置信度。
- 异常弹窗内的短时情绪舒缓：只在用户主动点击“和我聊聊”并输入内容后调用。

## 隐私边界

被动采集不会把用户输入原文发给 Agent。结构化评判默认只发送结构化特征、风险等级、基线可信度和用户主动反馈标签。

异常弹窗里的“和我聊聊”属于用户主动输入，MiniMax 只用于短时心理支持与情绪舒缓。App 不提供常驻聊天页面，也不把被动采集到的输入内容作为聊天上下文。

## 验证方式

1. 配好 `key.txt`。
2. 重新 Gradle Sync 或重新构建 App。
3. 在设置页使用 Demo Mode 导入多日验收数据。
4. 触发异常弹窗后点击“和我聊聊”。
5. 在弹窗内输入一句话，观察 MiniMax-M2.7 返回的温和舒缓回复。
6. 点击“详情”，查看异常事件中的风险等级、建议动作和 Agent 分析摘要。

如果 API 配置失败，项目会自动回退到 Mock Agent，不影响 App 演示。
