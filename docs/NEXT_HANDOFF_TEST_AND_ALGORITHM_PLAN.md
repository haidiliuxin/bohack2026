# NeuroGarden 接班开发与测试说明

本文档用于交接当前 NeuroGarden 项目的后续开发、测试和算法精进工作。当前项目已经具备 Android 手机端、Wear OS 手表端、被动采集、Demo Mode、异常事件、三种使用模式、MiniMax Agent 接入和异常弹窗舒缓能力，但仍有几类关键问题需要接班同学继续解决。

## 1. 当前核心问题总结

### 1.1 前端可视化面板没有完全连接真实数据

当前 Dashboard 中有“今日 / 历史 / 守护 / 设置”四栏，也有状态评分曲线、心率曲线、呼吸曲线、实时指标、天气因素、异常事件列表等 UI。

但部分可视化仍然是半模拟或静态拼接：

- 心率曲线目前包含固定点和最新 packet，并不是完整读取 `sensor_records` 时间序列。
- 呼吸曲线同样没有完整绑定数据库中的多点真实记录。
- 打字速度、删除频率、停顿时长在“实时指标”里仍以模拟值展示，没有直接展示最近一次真实无障碍采集快照。
- 今日页的“当前状态评分”优先使用风险事件，否则使用 ViewModel 当前状态，容易和历史趋势不同步。
- 历史页趋势图主要取 `risk_events`，没有把 `habit_samples`、`sensor_records` 的时间序列充分可视化。

后续目标：让所有图表优先读取 Room 中真实/验收数据，而不是 UI 层临时拼点。

### 1.2 很多数据已有入库，但没有被前端充分消费

当前数据库已经有：

- `habit_samples`
- `sensor_records`
- `risk_events`
- `user_habit_baselines`
- `threshold_profiles`
- `feedback_records`
- `conversation_summaries`

但前端主要展示的是摘要和事件，很多原始结构化数据没有形成清晰的图表、列表或解释。

后续需要补：

- 最近 24 小时心率折线图
- 最近 24 小时呼吸折线图
- 最近 24 小时打字速度趋势
- 最近 24 小时删除频率趋势
- 最近 24 小时停顿时长趋势
- 运动干扰标记
- 天气与异常事件关联
- Agent 判定结果与本地算法判定结果对照

### 1.3 三种模式下“难受边界”还不够精确

当前三种模式已有策略：

- 自我监测模式
- 家庭守护模式
- 特殊关怀模式

但“什么情况下算用户难受、需要弹窗、需要守护确认”仍然偏经验阈值。后续必须把边界算法做得更精确、可解释、可调参。

### 1.4 多端交互体验仍需打磨

项目涉及：

- Android 手机端
- Wear OS 手表端
- Android 无障碍服务
- 系统通知
- 异常弹窗
- MiniMax Agent

这些端口之间已经能跑通一部分，但整体体验还需要系统验收：

- 手表端采集后，手机端是否及时显示来源和数据。
- 手机端弹窗是否打扰过强。
- 通知和弹窗是否重复。
- 无障碍采集状态是否容易理解。
- Agent 回复是否足够轻柔、不会像诊断。
- 老人、年轻人、心理敏感用户看到的文案是否都能接受。

## 2. 接班人第一天应该先做什么

### 2.1 先确认项目能运行

使用 Android Studio 打开：

```text
F:\bohark\bahack\bahack\NeuroGarden
```

构建手机端：

```bash
./gradlew :app:assembleDebug
```

如果 Windows 本地没有 `gradlew`，可以使用本机 Gradle：

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT="$env:LOCALAPPDATA\Android\Sdk"
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.13-all\54h0s9kvb6g2sinako7ub77ku\gradle-8.13\bin\gradle.bat" :app:assembleDebug --no-daemon
```

构建手表端：

```bash
./gradlew :wear:assembleDebug
```

### 2.2 安装和启动

手机端 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

手表端 APK：

```text
wear/build/outputs/apk/debug/wear-debug.apk
```

### 2.3 配置 MiniMax Agent

项目根目录 `key.txt`：

```text
URL=https://api.minimaxi.com/anthropic
APIkey=你的MiniMax_API_Key
MODEL=MiniMax-M2.7
```

注意：

- `key.txt` 不要提交到仓库。
- 当前 MiniMax 主要用于结构化风险判断，以及异常弹窗内用户主动输入后的短时情绪舒缓。
- 不允许把无障碍被动采集到的输入原文发给 Agent。

## 3. 手动测试教程

### 3.1 最小验收流程

1. 打开 App。
2. 进入“设置”。
3. 选择一个使用模式：自我监测、家庭守护或特殊关怀。
4. 点击 Demo Mode 中的“导入多日验收数据”。
5. 回到“今日”页。
6. 查看是否出现状态波动弹窗。
7. 在弹窗中点击“和我聊聊”，输入一句简单内容，例如“有点累”。
8. 观察 MiniMax 是否返回温和舒缓回复。
9. 点击“详情”，进入异常事件详情。
10. 检查结构化原因、风险评分、天气、时间段、反馈按钮是否展示。
11. 进入“历史”，检查 7 天摘要是否出现。
12. 进入“守护”，检查当前模式的提醒阈值和每日提醒上限。

### 3.2 无障碍输入节奏测试

1. 进入“设置”。
2. 点击“前往系统无障碍设置”。
3. 开启“NeuroGarden 输入节奏特征”。
4. 回到 App。
5. 打开任意输入框或聊天软件。
6. 输入一些文字、删除几次、停顿几秒。
7. 回到 NeuroGarden 设置页。
8. 检查：
   - 无障碍服务是否显示已开启。
   - 今日输入节奏样本数是否增加。
   - 最近采集时间是否更新。
   - 当前采集中状态是否合理。

注意：系统只应统计结构化特征，不得保存输入原文。

### 3.3 手表端测试

1. 启动 Wear OS 模拟器或真机。
2. 安装并运行 `wear` 模块。
3. 在手表端点击手动采集或模拟心率。
4. 手机端应能通过 Wearable Data Layer 接收数据。
5. 今日页“最近心率来源”应从 Mock 变为 Real，或至少展示 Wear OS 采集状态。

后续要重点检查：

- 手机和手表是否都安装了同一套应用签名。
- 手表端点击采集后，手机端是否能收到 packet。
- 手机端是否把 Wear 数据写入 `habit_samples` 或 `sensor_records`。

### 3.4 弹窗测试

弹窗当前触发条件：

- 存在今天或最近的风险事件。
- 当前没有打开事件详情。
- 事件没有被本轮手动关闭过。
- `riskScore >= 0.60`
- `riskLevel != stable`

最快触发方式：

1. 设置页点击“导入多日验收数据”。
2. 回到今日页。
3. 应弹出“检测到状态波动”。

如果不弹：

- 检查数据库里是否有 `risk_events`。
- 检查今天是否有高风险事件。
- 检查事件是否刚被关闭过。
- 检查 `riskScore` 是否低于 0.60。

## 4. 三种模式的“难受边界”算法方案

这里的“难受”不要理解为医学诊断，而应定义为：

> 用户当前状态相对个人日常节奏出现持续偏离，并且这种偏离可能值得本人或授权守护人温和关注。

后续需要把边界算法做成可解释、可调参、多信号融合的系统。

### 4.1 输入信号

建议参与计算的信号：

- 心率偏离百分比 `heartRateDeviationPercent`
- 呼吸频率偏离百分比 `breathRateDeviationPercent`
- 打字速度偏离百分比 `typingSpeedDeviationPercent`
- 删除频率偏离百分比 `deleteRateDeviationPercent`
- 停顿时长偏离百分比 `pauseDurationDeviationPercent`
- 运动干扰 `motionLevel`
- 数据可信度 `dataQualityLevel`
- 时间段 `timeSegment`
- 天气 `weather`
- 近期趋势 `sustainedDeviationMinutes`
- 用户反馈历史 `feedback_records`

### 4.2 基础分数建议

建议先计算一个本地基础不适分 `discomfortScore`：

```text
physiologyScore =
  0.55 * normalizedHeartDeviation +
  0.45 * normalizedBreathDeviation

interactionScore =
  0.30 * normalizedTypingSlowdown +
  0.35 * normalizedDeleteLift +
  0.35 * normalizedPauseLift

rawDiscomfortScore =
  0.52 * physiologyScore +
  0.38 * interactionScore +
  0.10 * trendScore
```

运动干扰修正：

```text
if motionLevel >= 0.60:
    rawDiscomfortScore *= 0.55
    confidence -= 0.25
```

数据可信度修正：

```text
if dataQualityLevel == low:
    allowStrongAlert = false
    confidence = min(confidence, 0.50)
```

### 4.3 三种模式边界

#### 自我监测模式

目标：减少打扰，不通知监护人，只做本人温和提醒。

建议边界：

```text
observe >= 0.45
popup >= 0.68
localReminder >= 0.72
guardianNotify = false
```

触发文案：

```text
今日状态波动较明显，建议稍后查看状态摘要。
```

适用用户：

- 普通年轻用户
- 想自我观察的人
- 不希望被打扰的人

#### 家庭守护模式

目标：平衡准确性和守护确认。

建议边界：

```text
observe >= 0.40
popup >= 0.62
guardianCheck >= 0.78
dailyGuardianLimit = 3
cooldown = 15 minutes
```

触发文案：

```text
检测到用户状态持续偏离日常节奏，建议进行一次状态确认。
```

适用用户：

- 家庭成员互相关心
- 需要适度守护但仍保留自主权

#### 特殊关怀模式

目标：对持续偏离更敏感，但避免频繁打扰。

建议边界：

```text
observe >= 0.35
popup >= 0.56
careCheck >= 0.68
dailyCareLimit = 2
cooldown = 30 minutes
requireHigherPrivacyNotice = true
```

触发文案：

```text
检测到被照护者出现持续状态偏离，建议照护者进行确认。
```

适用用户：

- 独居老人
- 需要照护的人
- 对情绪或身体状态更敏感的人

### 4.4 持续性边界

不要只看单次高分。建议加入持续性判断：

```text
if discomfortScore >= popupThreshold for 2 consecutive windows:
    showPopup = true

if discomfortScore >= guardianThreshold and sustainedDeviationMinutes >= 15:
    allowGuardianCheck = true
```

这样可以减少误报。

### 4.5 Agent 与本地算法的分工

本地算法负责：

- 快速计算基础风险分
- 判断是否记录事件
- 判断是否允许弹窗
- 判断通知冷却和每日上限
- 处理无网络 fallback

MiniMax Agent 负责：

- 基于结构化数据给出二次评判
- 输出温和解释
- 输出建议动作
- 给出置信度
- 在弹窗内对用户主动输入进行短时舒缓

Agent 不应负责：

- 医学诊断
- 疾病判断
- 极端行为预测
- 被动读取聊天文本
- 直接绕过本地隐私和通知策略

## 5. 前端可视化面板后续任务

### 5.1 今日页

已完成第一版：

- 心率曲线优先读取今日 `sensor_records`，没有数据时才回退当前 packet。
- 呼吸曲线优先读取今日 `sensor_records`，没有数据时才回退当前 packet。
- 输入速度、删除频率、停顿时长曲线读取今日 `habit_samples`。
- 状态评分曲线读取今日 `risk_events` 和 `sensor_records.stressScore`。
- 今日页已经展示 MiniMax 参与后的复杂情绪状态与多维分数。

仍需继续改造：

- 天气因素展示最近一次真实天气或事件天气。
- 数据可信度展示缺失原因，而不只是 high/medium/low。

### 5.2 历史页

需要改造：

- 7 天每日最高风险评分。
- 7 天每日平均风险评分。
- 7 天事件数量。
- 7 天误报数量。
- 7 天确认异常数量。
- 7 天主要异常指标。
- 7 天天气关联。

### 5.3 守护页

需要改造：

- 三种模式下显示不同边界：
  - 弹窗阈值
  - 守护确认阈值
  - 每日上限
  - 冷却时间
- 显示最近一次提醒是否被冷却拦截。
- 显示当前数据可信度是否允许强提醒。

### 5.4 设置页

需要补：

- Agent 配置状态：已配置 / 未配置，但不要显示 key。
- MiniMax 模型名称：`MiniMax-M2.7`
- 当前 API 模式：Anthropic compatible
- 最近一次 Agent 请求是否成功。

## 6. 多端交互体验优化

### 6.1 手机端

关注点：

- 弹窗不要太吓人。
- 文案不要像诊断。
- “我还好”“想有人陪”“和我聊聊”“详情”四个入口要清楚。
- 用户关闭弹窗后短时间内不要重复弹。

### 6.2 手表端

关注点：

- 手动采集按钮要明确。
- 成功发送到手机要有反馈。
- 失败时提示检查蓝牙、配对和权限。
- 后续可以加入轻震动呼吸引导。

### 6.3 系统通知

关注点：

- 通知和 App 内弹窗不要重复轰炸。
- 自我监测模式只给本人温和提示。
- 家庭守护模式最多每日 3 次。
- 特殊关怀模式最多每日 2 次，冷却更严格。

### 6.4 无障碍服务

关注点：

- 必须清楚告诉用户“不保存输入原文”。
- 设置页要显示是否开启。
- 要显示今日样本数和最近采集时间。
- 不能采集密码内容或具体语义文本。

## 7. 建议的开发顺序

### 第一优先级：数据连线

1. 把 `sensor_records` 接入今日心率/呼吸曲线。
2. 把 `habit_samples` 接入输入节奏曲线。
3. 把 `risk_events` 接入状态评分曲线。
4. 检查 Demo Mode 导入后前端是否立刻刷新。

### 第二优先级：边界算法

1. 已新建 `DiscomfortBoundaryCalculator`。
2. 已将三种模式阈值集中管理。
3. 输出：
   - `discomfortScore`
   - `riskLevel`
   - `shouldShowPopup`
   - `shouldNotifyGuardian`
   - `confidence`
   - `mainReasons`
4. 已在守护页展示观察阈值、弹窗阈值、守护确认阈值、每日上限和冷却时间。
5. 下一步需要在异常详情页展示本地算法与 MiniMax 模型的对照解释。

### 第三优先级：弹窗与 Agent

1. 弹窗只在异常时出现。
2. 弹窗内允许短时对话。
3. 对话仅由用户主动输入触发。
4. Agent 回复必须温和、短、不诊断。
5. 弹窗关闭后进入冷却。

### 第四优先级：多端体验

1. 手表采集成功反馈。
2. 手机接收 Wear 数据后的 UI 刷新。
3. 通知和弹窗去重。
4. 无障碍权限引导优化。

## 8. 验收标准

### 必须通过

- App 能构建并安装。
- 设置页能导入多日验收数据。
- 今日页能看到异常事件和天气。
- 高风险事件能触发弹窗。
- 弹窗内可以调用 MiniMax 进行短时舒缓。
- 历史页能看到 7 天摘要。
- 三种模式的阈值和提醒上限可解释。
- 无障碍状态页明确写明不保存输入原文。

### 不能出现

- 把用户判定为“抑郁症”等疾病。
- 说“预测自残”或类似高风险医疗化表达。
- 自动读取或上传聊天原文。
- 低数据可信度时直接强提醒。
- 用户关闭弹窗后立刻重复弹。

## 9. 给接班人的一句话

这个项目的核心不是“用 AI 诊断心理疾病”，而是：

> 在用户授权的前提下，用手机和手表的结构化信号学习个人日常节奏，当节奏持续偏离时，用温和、可解释、可反馈的方式提供 AI+疗养支持。

后续所有算法、前端和交互都应该围绕这条边界推进。
