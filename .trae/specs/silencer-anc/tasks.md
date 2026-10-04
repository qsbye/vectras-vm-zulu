# 消音器标签页 - 实施计划

实施顺序：先纯 Kotlin 算法核心（可单测）→ Android 引擎与 UI（真机验证）→ desktop 对等移植 → 构建/真机证据 → 独立审查。音频参数统一 44100Hz/mono/16bit/chunk=512。新增算法类放在 `app/.../dsp/anc/`，desktop 端复制到 `desktop/.../dsp/anc/`（仅 package 行与音频后端不同）。

## Task 1: 周期检测与时延标定 DSP（含单测）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 新增 `app/src/main/java/com/example/audio_stream_app/dsp/anc/PeriodDetector.kt`：对 Float 归一化信号做归一化自相关（带能量归一与抛物线插值），输出基频周期（采样）、基频 Hz、周期性置信度；搜索范围对应 30–1200Hz。
  - 新增 `LatencyCalibrator.kt`：生成已知标定信号（chirp/脉冲串）；对回收录音做与原信号的归一化互相关，返回延迟采样数与峰置信度；无显著峰返回 null。
  - 新增单测 `app/src/test/java/com/example/audio_stream_app/dsp/anc/PeriodDetectorTest.kt`、`LatencyCalibratorTest.kt`。
- **Acceptance Criteria Addressed**: AC-2, AC-3
- **Test Requirements**:
  - `rule` TR-1.1：合成 60/120/400Hz 谐波信号（含 -20dB 白噪）基频误差 ≤1%、置信度达阈值；白噪与线性 chirp 置信度低于阈值（AC-2 证据）
  - `rule` TR-1.2：标定信号经 137/1024/4096 采样延迟 + 噪声后估计误差 ≤2 采样；纯噪声输入返回 null（AC-3 证据）
- **Notes**: 复用 HowlingSuppressor 中已有的 radix-2 FFT 思路（如需频域互相关）；类必须为纯 Kotlin、无 Android import。

## Task 2: 端上神经网络（MLP + Adam 训练器，含单测）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 1
- **Description**:
  - 新增 `WaveformMlp.kt`：结构 = 2K Fourier 相位特征（sin/cos, k=1..K，K=min(10,floor(Nyquist/f0))）→ Dense(32,tanh) → Dense(32,tanh) → Dense(1,linear)；提供前向 `predict(phase)`、批量前向、反向传播（手写梯度）与权重 getter/setter；权重数 ≤3000。
  - 新增 `AdamTrainer.kt`：mini-batch Adam（β1=0.9,β2=0.999）、学习率调度与早停；输入录音+基频周期，逐 epoch 回调（epoch、总轮次、loss、可取消标志）；提供训练耗时统计。
  - 单测 `WaveformMlpTrainerTest.kt`：合成多谐波信号训练，断言整周期 NMSE 与提前 lead 的预测相关性。
- **Acceptance Criteria Addressed**: AC-1, AC-9
- **Test Requirements**:
  - `rule` TR-2.1：合成信号（基频+3 谐波+噪声）训练后逐相位推理 NMSE ≤ -15dB；lead=2048 采样处预测 vs 真实未来样本相关系数 ≥0.99（AC-1 证据：测试打印两指标）
  - `rule` TR-2.2：单测中训练在固定 epoch 预算内结束、可取消标志生效（中断后返回），全部类无 Android/第三方 import（AC-9）
- **Notes**: 训练样本目标 = 该采样的归一化幅度；输入相位 φ=(n mod P)/P。用 He/Xavier 初始化与 tanh 输出缩放，保证收敛。

## Task 3: 实时消音控制器 AncController（含仿真单测）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 1, Task 2
- **Description**:
  - 新增 `AncController.kt(sampleRate, chunkSamples)`：持有训练好的 WaveformMlp、基频、leadSamples、输出增益上限；内部组合 `NlmsAec`（剥离本机播放回声）、PLL 相位/频偏跟踪、周期分量 RMS 幅度慢跟踪、增益缓升（≥1s）与失锁淡出（0.5s）。
  - 核心 `fun process(mic: ShortArray, out: ShortArray, count: Int)`：逐采样产出反相波并写入 out；暴露 `locked:Boolean`、`instantF0:Float`、`antiGain:Float`、`bandEnergyBefore/NowDb`（周期频带能量指标）。
  - `reset()`、`updateLead(samples)`、`setEnabled` 平滑切换。
  - 单测 `AncControllerTest.kt`：喂合成稳态周期信号 → 锁定后输出为同频反相（与输入相关 ≤ -0.9 即极性相反）；中途换白噪 → 0.5s 内输出能量衰减到阈值下；lead 改变不产生 NaN/爆量。
- **Acceptance Criteria Addressed**: AC-7, NFR-1
- **Test Requirements**:
  - `rule` TR-3.1：稳态合成输入锁定后，输出与输入周期分量相关系数 ≤ -0.9，且输出不超过设定上限（AC-7 正向）
  - `rule` TR-3.2：切换白噪后 0.5s（22050 采样）内输出 RMS ≤ 稳态输出的 10%；全程无 NaN、无越界（AC-7）
  - `rubric` TR-3.3：单 chunk 512 采样处理耗时（JVM 单测计时，桌面 JVM 参照）显著小于 11.6ms；scale 1-5；1=>11.6ms，3=5–11.6ms，5=<3ms；threshold >=4；证据为测试打印的毫秒数
- **Notes**: 回声剥离的参考信号取上一块实际写出的播放值（跨块连续），与扩音器页 AEC 用法一致。

## Task 4: Android 消音器引擎与第三个标签页
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 3
- **Description**:
  - 新增 `SilencerEngine.kt`（Android）：独占 AudioRecord/AudioTrack 会话（API26+ 设置 `PERFORMANCE_MODE_LOW_LATENCY`，失败回退普通模式）；实现 `Idle/Recorded/Training/Ready/Calibrating/Active` 状态机：
    - 录制 2–6s（时长/电平回调）、回放试听、重录；
    - 后台线程执行 AdamTrainer（进度/loss 回调、可取消），成功产出 AncController 所需模型并报告 f0/置信度；
    - 标定：播放 LatencyCalibrator 信号并录音互相关，给系统报告时延夹值校验；
    - Active：音频循环调用 AncController.process 并写 AudioTrack，同步波形与状态指标；停止/失锁/切页正确释放。
  - 新增 `AncWaveformController`（或扩展现有 controller 支持第三路波形与状态文本），灰=麦克风、湖蓝 CanadianLake=反相波。
  - 新增 UI `SilencerPage.kt`：步骤式布局（录制按钮+电平、训练按钮+进度/loss、标定按钮+ms 与 ±50ms 微调滑条、消音开关+反噪增益滑条+防啸叫兜底勾选）、状态/锁定指示与周期带 dB 指标、波形区、功能说明文案；全部大地色 Material3 组件。
  - `MainActivity`：TabRow 增加「消音器」（位置在扩音器与关于之间），接入页面；保证两页音频会话互斥与生命周期释放。
- **Acceptance Criteria Addressed**: AC-4, AC-6, AC-7, AC-8, NFR-2, NFR-3, NFR-4, NFR-5
- **Test Requirements**:
  - `rule` TR-4.1：`:app:assembleDebug` 成功；真机安装后三 Tab 可切换，录制<2s 时训练按钮禁用（AC-4）
  - `rule` TR-4.2：真机完整走通 录制→训练（进度/loss 可见且可取消）→标定（显示 ms）→开启/关闭消音→切页/后台返回，logcat 无 FATAL（AC-4、NFR-4）
  - `rule` TR-4.3：真机白噪/突变声源下 0.5s 内 UI 显示「搜索中」且反噪淡出，无啸叫（AC-7）
  - `rubric` TR-4.4：外部周期声源实测周期频带能量下降 dB（AC-6 口径：1/3/5 锚点同 spec，threshold>=3；记录实测值，低于 3 时保留功能+实验性标注并提请用户确认）
  - `rubric` TR-4.5：视觉/交互对照扩音器页（AC-8 同尺度，threshold>=4；证据截图/录屏、波形目测 ≥25fps）
- **Notes**: 构建脚本不得新增依赖（NFR-2/AC-9）；版本号自动生成无需手改。

## Task 5: Desktop 端对等移植
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 4
- **Description**:
  - 复制 Task1–3 的 `anc/` 源码到 `desktop/src/main/kotlin/com/example/audio_stream_app/desktop/dsp/anc/`，仅改 package（与既有 NlmsAec/HowlingSuppressor 复制模式一致）。
  - `AudioEngine.kt` 扩展或新增 `SilencerEngine`（javax.sound.sampled，TargetDataLine/SourceDataLine，优先 44100，实际采样率传入算法）：录制/训练/标定/实时消音循环。
  - desktop `Main.kt` 增加第三个 tab 与状态持有；`Pages.kt` 增加与 Android 对等的 `SilencerPage`；`Waveform` 支持双路（灰/湖蓝）。
- **Acceptance Criteria Addressed**: AC-5
- **Test Requirements**:
  - `rule` TR-5.1：`:desktop:shadowJar` 构建成功，jar 可启动（AC-5）
  - `rule` TR-5.2：两端 anc 包文件一一对应、除 package 行外核心类逻辑一致（diff 核对，AC-5）
  - `rule` TR-5.3：桌面 UI 具备录制/训练/标定/微调/开关/波形全部控件，状态机与 Android 一致（启动 jar 目检截图）
- **Notes**: 无声学真场条件，桌面效果以 Task1–3 单测与构建为准。

## Task 6: 集成构建与真机验证取证
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 4, Task 5
- **Description**:
  - 运行 `:app:testDebugUnitTest`、`:app:assembleDebug`、`:desktop:shadowJar`；
  - `adb -s XPL0219C06017003 install -r` 安装，按 TR-4.x 逐项操作并保存 logcat、截图（三 Tab、训练中、就绪、消音中锁定、失锁搜索）；
  - 用音箱播放周期声源（风扇/电机录音）完成 AC-6 实测，记录 dB；
  - 汇总证据写入本文件 Completion Evidence。
- **Acceptance Criteria Addressed**: AC-4, AC-6, AC-7, AC-8
- **Test Requirements**:
  - `rule` TR-6.1：全部单测通过、两端构建成功（命令输出为证）
  - `rubric` TR-6.2：真机证据包完整（截图 ≥5 张 + logcat 无 FATAL + dB 实测记录）；scale 1-5；threshold>=4
- **Notes**: JBR17：`JAVA_HOME=/Users/workspace/Library/Java/JavaVirtualMachines/jbr-17.0.8.1/Contents/Home`。

## Task 7: 独立审查与修复闭环
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: Task 6
- **Description**: 队列清空后以全新上下文做一次只读独立审查，覆盖 AC-1…AC-9，产出 `review.md`；fail 项回炉为本文件 Issue 并修复后重新审查。
- **Acceptance Criteria Addressed**: 全部 AC
- **Test Requirements**:
  - `rule` TR-7.1：每个 AC 都有独立证据，最新 Review 结果 = pass
