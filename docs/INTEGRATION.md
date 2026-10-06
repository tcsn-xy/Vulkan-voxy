# 私人集成接口说明

上游版本固定于 UPSTREAM.md 所列提交。以下是接口与行为说明，不包含上游代码或再分发补丁。

## 游戏接入

VulkanInterop 获取 RenderPearl 已启用设备、命令编码器和提交回收回调；LevelRenderer/Vulkan 接入层把远景接入世界绘制。游戏近景通过 SodiumNearCoverage 收集实际可见的已编译区段。CPU 贴图加载后交给 CpuBlockAtlas，ModelUploadSink 将软件模型结果交给 VulkanModels；模型工厂需暴露稳定的面元数据快照。

## 数据核心修改

WorldSection 的解压数组申请与复用必须接入 DataMemoryBudget，ActiveSectionTracker 增加字节约束与 ColdSectionCodec 压缩冷缓存。区段版本、实时更新守卫及任务去重要贯穿 WorldUpdater、VoxelIngestService、WorldImporter。实时探索结果优先于历史导入。RocksDB 缓存和 memtable 需要显式约束及观测。

## 网格与资源

网格完成须区分 ready 的空网格与未构建网格；同版本 queued 保留直到发布结果被渲染线程消费。软件模型烘焙从 CPU 纹理读取，移除 OpenGL 读回。MeshClusters 使用模型面透明属性划分簇，原四边形格式不变。上传之后的资源仍计入预算，只有提交完成回调销毁资源时释放预算。

## UI 与生命周期

VoxyClient/ClientSessionEvents 在 Vulkan 后端初始化渲染器，维度切换、资源包重载与关闭时正确失效或退休 GPU 资源。VoxyConfig 添加堆、原生、GPU、上传预算及 HiZ 开关，默认 384/256/768/8 MiB、两个构建线程。设置保存不能调用 Vulkan 下停用的 Iris 重载逻辑。语言资源使用新增 zh_cn.json。

这些接口改动尚未在本源码组件仓库中实现，单独复制新增目录不能得到可安装模组。

## 重建与退休

关闭渲染器后，GPU资源仍计入预算直到提交完成。LevelRenderer 接入层需要保留待创建的 WorldEngine，等 GpuBudgetManager.readyForRenderer 为真时在后续世界帧创建。关闭/切换世界或停用时取消待创建请求；不能同步等待设备空闲。改变 GPU 预算时旧预算对象继续负责旧分配，全部退休后再创建新预算对象。

HiZ是可选加速资源。尺寸变化时先退休旧缓冲并失效历史，新缓冲预算不足时保留正常绘制、按时间间隔重试并回收细分缓存。VulkanHiZ构造失败须回收已创建的视图、图像、查询池和遮挡掩码；只有真实计量被释放后才能恢复分配。

## v4额外接口

工作集输入仅包含位置、距离、阈值、尺寸和世界高度，不能用相机朝向控制驻留细分。压力控制器逐步降低前沿容量，保留当前前沿和可用父级。

当前存档刷新需要WorldUpdater的明确当前来源策略：允许覆盖以前会话的记录，保护当前会话观察并始终更新父LOD。WorldImporter固定源目录，补齐省略的空气区段，后台先建立一致性检查点；SectionStorage须委托唯一RocksDB叶后端的checkpoint。刷新与普通游标分开。

GPU展开缓冲保存4字节源四边形索引，顶点阶段从分页几何和来源表解码。静水顶部元数据记录游戏图集精确像素区域，共用动画图集与采样器，不复制游戏纹理到另一个水面图集。

Vitrail适配仅管理其LevelPass/GeometryHold租用与合成时机。使用基础后合成远景，不重写加载器，不宣称完整BSL远景着色协议已实现。将资源准备放在世界绘制图执行前，禁止在未结束的图形通道内提交传输/计算命令。
