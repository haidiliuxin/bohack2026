# NeuroGarden 情绪地形图与家庭守护系统

## 项目简介

NeuroGarden 是一个面向手机端和手表端的多模态情绪状态感知与温和干预系统。

系统通过心率、呼吸、打字节奏等数据，识别用户状态偏离，并通过家庭守护和特殊关怀模式形成照护闭环。

**本项目不做医学诊断，不预测极端行为，仅用于压力感知、情绪支持、康养辅助和人文关怀。**

## 能做什么

### 1. 情绪状态检测
- 实时监测心率、呼吸节奏、打字速度、删除频率、停顿时长
- 手表运动状态感知
- 结合天气和时间段综合分析

### 2. 情绪地形图展示
- Canvas 动态绘制多维情绪状态
- 可视化压力分布和变化趋势

### 3. 三种使用模式

#### 模式一：自我监测（普通模式）
- 只做本地情绪状态识别
- 本地温和提醒和干预反馈
- 不通知监护人
- 适合个人日常使用

#### 模式二：家庭守护模式
- 需要配置监护人信息
- 需要授权状态
- 模拟通知监护人
- 生成通知记录
- 接收监护人反馈
- 形成家庭守护闭环
- 支持夜间紧急通知

#### 模式三：特殊关怀模式
- 在家庭守护基础上启用
- 更早关注连续状态偏离
- 四级状态偏离分级：
  - 轻微偏离
  - 需要观察
  - 建议照护确认
  - 重点关注
- 更重视夜间异常、高心率低运动、多指标连续偏离
- 更少打扰本人，更强调照护确认

### 4. 被动守护服务
- 前台服务方式运行
- 每 10 秒检测一次（30 秒内可响应异常）
- 无障碍权限采集打字节奏特征
- 只采集统计特征，不保存输入原文
- 本地高风险通知弹窗

### 5. 呼吸引导
- 呼吸光圈动画
- 震动反馈提醒
- 多种呼吸节奏模式

### 6. 数据可视化
- 今日摘要
- 历史趋势
- 7 天汇总
- 阈值历史

### 7. 反馈与调参
- 用户轻量标注
- 监护人反馈
- 误报反馈降低打扰强度
- 确认反馈提高敏感度
- 自动调整个人化阈值

## 技术架构

### 端
- **手机端**：Android + Kotlin + Jetpack Compose + Material 3
- **手表端**：Wear OS + Kotlin + Compose for Wear OS

### 数据存储
- Room 数据库：本地持久化
- DataStore：偏好设置

### 核心模块

| 模块 | 功能 |
|-----|------|
| `app/algorithm/` | 情绪算法、风险计算、习惯学习 |
| `app/guardian/` | 家庭守护通知策略、特殊关怀服务 |
| `app/passive/` | 被动守护服务、无障碍权限采集 |
| `app/ui/` | Compose 界面 |
| `wear/` | 手表端应用 |
| `shared/` | 双端共享模型 |

### 通知发送器适配层
- `MockGuardianNotificationSender`：模拟通知（默认）
- `SmsGuardianNotificationSender`：短信占位
- `EmailGuardianNotificationSender`：邮箱占位
- `WechatGuardianNotificationSender`：微信占位

后续可替换为真实服务实现。

## 核心功能详细说明

### 家庭守护通知策略
- 连续异常检测
- 夜间优先通知
- 高心率低运动检测
- 误报频率限制
- 冷却期控制
- 每日通知上限

### 特殊关怀分级
- 根据心率、呼吸、打字节奏、停顿时长多维度评估
- 连续偏离自动升级
- 运动状态自动降级（避免误判）
- 长时间无活动提醒

### 照护闭环状态
- OPEN → NOTIFIED → ACKNOWLEDGED → RESOLVED
- WATCHING（继续观察）
- FALSE_POSITIVE（误报标记）
- 所有状态变化自动记录

## 运行命令

### 构建

```bash
# Windows
.\gradlew.bat :app:assembleDebug :wear:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug :wear:assembleDebug
```

### 单元测试

```bash
# Windows
.\gradlew.bat :app:testDebugUnitTest

# macOS / Linux
./gradlew :app:testDebugUnitTest
```

## 隐私声明

- 不使用前置摄像头
- 不上传输入原文
- 不共享聊天内容
- 不做医学诊断
- 只采集打字速度、删除率、停顿时长等统计特征
- 用户可删除所有历史记录
- 监护人授权可随时撤销
- 当前通知为模拟通知，不会真实发送

## 隐私与权限说明

应用首次启动会请求以下权限：

| 权限 | 用途 |
|-----|------|
| 通知权限 | 发送状态提醒、守护确认 |
| 蓝牙连接 | 连接 Wear OS 手表 |
| 悬浮窗 | 显示守护提醒弹窗 |
| 无障碍 | 采集打字节奏特征（可选）|

## 技术栈

- Android 手机端：Kotlin、Jetpack Compose、Material 3、ViewModel、Coroutine + Flow、Room、DataStore
- Wear OS 手表端：Kotlin、Compose for Wear OS、MockHeartRateClient、Health Services 预留接口
- 共享模块：SensorPacket、StressResult、EmotionState、TherapyPlan 等数据模型

## 模块结构

- `app`：手机端主应用
- `wear`：Wear OS 手表应用
- `shared`：双端共享模型和工具

## 演示入口

在设置页面（Dashboard 底部导航）有 Demo Mode：

| 按钮 | 功能 |
|-----|------|
| 模拟稳定一天 | 生成低风险测试数据 |
| 模拟轻度波动 | 生成轻度异常事件 |
| 模拟夜间异常 | 生成夜间高风险事件 |
| 模拟监护人确认后调参 | 生成带反馈的事件 |
| 导入多日验收数据 | 生成 7 天综合测试数据 |

## 安装 APK

```bash
# 查看设备
adb devices

# 安装手机端
adb install app/build/outputs/apk/debug/app-debug.apk

# 安装手表端
adb install wear/build/outputs/apk/debug/wear-debug.apk
```

## 项目定位

NeuroGarden 是一个 AI 个体化情绪守护与康养关怀系统。它不试图诊断用户，而是学习每个人自己的日常节奏，在状态偏离时提供温和、可解释、可反馈的关怀建议，并通过用户反馈不断调整个人化阈值。
