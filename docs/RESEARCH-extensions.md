# Rokid Focus — 扩展能力调研（明天对齐用）

date: 2026-09-19
scope: 三个问题 ①「一键选择说」 ②Rokid 接入 agent（Hermes/爱马仕） ③对话中即时解释生词

当前项目现状（用于对齐）：
- 眼镜端自研 app `com.chenniuniu.rokidfocus.glass`（RG-glasses，Android 12 / API 32）。
- 手机端 `com.chenniuniu.rokidfocus`，通过 CXR-L `client-l` 连接；眼镜端用 `cxr-service-bridge`。
- 双 mic 双讯飞 lane：眼镜=YOU，手机=THEM（`role_type=2` 盲分，支持 them/them2/them3）。
- 眼镜端能本地放音（chime 走 `ChimePlayer` → 扬声器），但**没有系统 TTS 引擎**（见下）。

---

## ① 「一键选择说」——选中 A/B/C 后念出来

### 结论：可行，但不在 CXR-L 的能力里，需要自己补一条音频下行链路。

**证据**
- CXR-L（手机侧）SDK 的「音频」能力只有**输入**：`startAudioStream()` 接收眼镜 mic 的 PCM 落盘，能力矩阵里没有音频输出。官方 CXR-L 文档：<https://open.rokid.com/sdk?lang=zh>，镜像全文 <https://github.com/e7naq3y/CXR-L-SDK>。
- 眼镜端**本地放音是可行的**（我们自己的 chime 就在放），说明有可用的音频输出通路。
- 但实测这台眼镜（RG-glasses, Android 12）**没有安装任何 TTS 引擎**：
  - `settings get secure tts_default_synth` → `null`
  - `pm list packages` 无 `*.tts` / iflytek / google tts
  - → 标准 Android `TextToSpeech` 直接不可用。

**三条可选路线**

| 路线 | 做法 | 优点 | 代价 |
|---|---|---|---|
| A. 云端 TTS → 眼镜播音（**推荐先做**） | 手机调 TTS（讯飞/MiniMax/ElevenLabs）拿到 PCM/音频 → 经 Caps 下行 → 眼镜 `AudioTrack` 播放 | 音色/多语种可控，不依赖眼镜引擎；可做「播报给本人听，再自己说」的提词器 | 需要设计音频下行（Caps 有大小限制，建议传 URL 让眼镜下载，或分片流） |
| B. 眼镜端离线 TTS | 用 Rokid Glass3 SDK `GlassSdk.getGlassOfflineTtsService()?.playTtsMsg(str)` | 零延迟、私密、无网络 | 该 API 属 `com.rokid.glass` Glass3 SDK；我们当前是 `cxr-service-bridge`，**需先验证这台设备是否暴露该服务** |
| C. 预渲染缓存 | 对固定短语（"再说一遍"、常用回复）预生成音频内置 | 零延迟、稳 | 只能覆盖固定短语 |

Glass3 TTS/ASR 参考：<https://x-docs.rokid.com/docs/代码示例/35-voice-ai/02-眼镜端-TTS-与-ASR.html>（含 `playTtsMsg`、`startSpeech`、`SpeechCallback.onIntermediateVad`）。

**产品注意**
- 「选择说」应该是**念给佩戴者自己听**（开放式扬声器/骨传导，旁人听不到），当作提词 + 发音参考；不是外放给对方。
- 这正好也回答了一个自然延伸：外语场景里，选中即小声念出来，用户跟着说。
- 与现有 LISTEN 的冲突点：`docs/LISTEN.md` 警告 **CustomApp 录音时不要调 `startAudioStream`（会抢眼镜 mic）**——所以音频下行不要复用 CXR 音频流，走我们自己的 Caps 通道。

---

## ② Rokid 接入 agent（Hermes / 爱马仕）

### 结论：最成熟的是走 **灵珠（Rizon）平台「自定义智能体」** + 一个公网 SSE 桥接；已有现成开源实现可直接抄。

**官方推荐链路**（来自 `rokid-hermes-bridge`）：
```
Rokid 眼镜 → Rokid AI App → 灵珠平台 → 公网 HTTPS /api/sse
           → Bridge（自建）→ Hermes Gateway /v1/chat/completions
           → LLM + MCP + Skills
```
- 仓库：<https://github.com/xingdongcai/rokid-hermes-bridge>（原始）、<https://github.com/h4dex/rokid-hermes-bridge>（增强版，MIT）。后者带零基础部署教程、腾讯云/Cloudflare Tunnel 方案。
- 已在做的能力值得直接复用：自动去 markdown、口语化短回复、**眼镜拍照 → base64 走视觉模型**、按用户隔离的多轮记忆、SSE 心跳保活。
- 灵珠配置：`agent-develop.rokid.com` → 创建三方智能体 →「自定义智能体」→ URL 填 `https://<域名>/api/sse` → 鉴权 Bearer + 自生成 `ROKID_AK`。**不要点「提审」**，不提审只有自己能用。
- SSE 协议（`docs/lingzhu-protocol.md`）：请求是 POST body（`message_id`/`agent_id`/`user`/`metadata`）；响应必须 `event:message` 开头，数据 `{role:"agent", type:"answer", answer_stream, is_finish}`，支持流式与 `follow_up`，头要 `X-Accel-Buffering: no`。
- 灵珠「自定义智能体」是 2026-02 上线的功能，可接 OpenClaw 及私有大模型（新闻源见文末）。

**另一条：ROKID.js / AIUI**（Rokid 新 agent 框架）
- <https://js.rokid.com> — 「Build AIUI Agents for AR Glasses」，AIUI 智能体框架，Tool Rendering + A2UI；另有 `aiui-mcp`（PyPI）。
- 适合「从 0 做原生 AIUI 应用」，但对我们这种自研眼镜 app + 自控 mic 的路线是另一套体系，未必需要。

**和我们现在做的关系**
- 我们当前是**自控路线**：自研眼镜 app + 手机 bridge + DeepSeek，完全掌控 mic/UI/翻译。
- 灵珠路线**接管对话入口**（Rokid AI App 驱动），换来成熟的 agent/MCP 接入与公开发布；代价是 UI/语音链路受平台约束，且要公网 HTTPS。
- **建议**：Hermes 接入先抄 `rokid-hermes-bridge`（省事、已验证）；我们的 Focus/listen 继续走自研，两者最终可用同一台服务器。

---

## ③ 对话中即时解释生词 / 概念（边上显示）

### 结论：用现有架构就能做，属于产品设计题不是技术难题。

**思路**
- 输入：`them` lane 的 definite 句子（我们已经在句尾触发 DeepSeek）。
- 检测：让 DeepSeek 返回结构化结果，除了「怎么回」再给一个 `gloss`：
  - 触发条件：低置信 ASR 词、专有名词/缩写、领域术语（生物/AR/融资…）、罕见词、用户自定义关注词。
  - 输出：1 行、≤N 字、口语化解释 + 可选「为什么现在重要」。
- 展示：眼镜 HUD 加一个**右侧/底部小面板**（和 transcript 分开，避免挤占主行）；手机 Convo tab 显示完整解释 + 来源链接。

**交互选项**
- (a) 全自动：检测到就弹，静默刷新。
- (b) 按需：用户 swipe/点一下，对「上一句」里被高亮的词做 explain（更省、更不打扰）。
- (c) 混合：默认高亮生词，点击展开（推荐）。

**风险 / 要注意**
- 眼镜是 glance，别把它变成第二个阅读器；解释要极短，或只在按需时展开。
- 延迟：用 `deepseek-flash` 流式，只对 `them` 句子、debounce；避免每个 partial 都调。
- 与翻译同屏竞争空间：双语 + 术语解释 + ABC 选项，需要排优先级（术语解释建议最小、可折叠）。
- 和 ②的 agent 天然协同：Hermes 有 MCP/Skills，术语解释可以直接是一个 tool。

---

## 风险与反证
- **CXR-L 无音频输出**是硬约束：必须自建下行或用 Glass3 SDK，先做设备可行性验证（B 路线）。
- **无系统 TTS**：任何依赖 Android `TextToSpeech` 的方案在这台眼镜上直接失败。
- **role_type=2 延迟**：为支持「对方多人」我们给 THEM lane 开了盲分，盲分会压中间结果延迟；若实测 caption 变慢，需要在「多人可分」和「逐词实时」之间做取舍（可能：只对 definite 做角色、或两条 lane 分别用不同 mode）。
- **灵珠需要公网 HTTPS + 账号**，且不改审只能自用；非技术门槛在 Nginx/Tunnel（仓库有教程）。
- 单来源风险：Q2 的证据主要来自 `rokid-hermes-bridge` 两个仓库 + 灵珠官方/新闻；建议明天直接在灵珠平台确认「自定义智能体」当前可用性与资费。

## 置信度
- Q2（Hermes 接入）：**高**——有可运行的开源实现 + 官方协议文档。
- Q1（选择说）：**中**——链路可行，但眼镜端 TTS 支持需实机验证（B 路线）。
- Q3（术语解释）：**中**——技术上现成，价值取决于如何不打扰 glance。

## 明天想请你拍板的
1. 选择说：走 **A 云端 TTS→下行**（我先做 PoC）还是先验 **B 眼镜离线 TTS**？
2. Hermes：直接抄 `rokid-hermes-bridge` 接灵珠，还是继续纯自研 phone bridge？
3. 术语解释：全自动 vs 点按展开？放主行下方还是独立小面板？
4. 双 lane + role_type：若是「逐词实时」和「多人分离」冲突，优先哪个？

## 主要来源
- CXR-L SDK 文档（手机侧，能力矩阵/音频仅输入）：https://open.rokid.com/sdk?lang=zh ；镜像 https://github.com/e7naq3y/CXR-L-SDK
- Glass3 眼镜端 TTS/ASR（`playTtsMsg`/`startSpeech`）：https://x-docs.rokid.com/docs/代码示例/35-voice-ai/02-眼镜端-TTS-与-ASR.html
- Glasses SDK（Rokid Glass3，端侧能力）：https://x-docs.rokid.com/docs/en/terminal-sdk/glasses/
- Rokid Hermes Bridge（原始）：https://github.com/xingdongcai/rokid-hermes-bridge
- Rokid Hermes Bridge（增强 + 教程）：https://github.com/h4dex/rokid-hermes-bridge
- 灵珠 SSE 协议：https://raw.githubusercontent.com/xingdongcai/rokid-hermes-bridge/main/docs/lingzhu-protocol.md
- 灵珠平台：https://agent-develop.rokid.com/ ；https://rizon.rokid.com/
- ROKID.js / AIUI：https://js.rokid.com ；aiui-mcp：https://pypi.org/project/aiui-mcp/
- 灵珠「自定义智能体」上线新闻：https://zhuanlan.zhihu.com/p/2005226515453978593
- 眼镜端自定义界面 + TTS 播报实践（天气 app）：https://segmentfault.com/a/1190000047424441
- 本机实测：`adb shell getprop ro.build.version.sdk`=32；`settings get secure tts_default_synth`=null；无 `*.tts` 包

（未验证：ROKID.js 页面为 JS 渲染，Jina 未取到正文；灵珠平台页面同样未取到正文，结论基于协议文档与新闻。）
