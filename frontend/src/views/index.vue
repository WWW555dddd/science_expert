<template>
  <div class="app-container platform-home">
    <div class="welcome">
      <div>
        <span>科技保险专家管理与协同服务平台</span>
        <h1>{{ greeting }}，{{ userStore.nickName || userStore.name }}</h1>
        <p>{{ roleDescription }}</p>
      </div>
      <div class="role-mark">{{ roleTitle }}</div>
    </div>

    <el-row :gutter="16" class="entry-grid">
      <el-col v-for="entry in entries" :key="entry.path" :xs="24" :sm="12" :lg="8">
        <el-card shadow="never" class="entry-card" @click="router.push(entry.path)">
          <el-icon :size="30"><component :is="entry.icon" /></el-icon>
          <h3>{{ entry.title }}</h3>
          <p>{{ entry.description }}</p>
          <span>进入功能 →</span>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="entries.length === 0" shadow="never">
      <el-empty description="当前账号尚未分配业务角色，请联系管理员配置专家或审核人员角色。" />
    </el-card>
  </div>
</template>

<script setup name="Index">
import useUserStore from "@/store/modules/user"

const router = useRouter()
const userStore = useUserStore()
const roles = computed(() => userStore.roles || [])
const roleTitle = computed(() => {
  if (roles.value.includes("admin")) return "管理人员"
  if (roles.value.includes("reviewer")) return "审核人员"
  if (roles.value.includes("expert")) return "专家"
  return "待配置"
})
const roleDescription = computed(() => ({
  专家: "维护个人档案与可服务时间，提交审核材料，查看能力画像和接单状态。",
  审核人员: "处理Agent预审结果，核验专家资料和字段映射，完成专家入库审核。",
  管理人员: "管理平台用户、角色权限、专家资源、Agent任务和审计日志。",
  待配置: "登录成功，但仍需管理员分配业务角色。"
})[roleTitle.value])
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return "上午好"
  if (hour < 18) return "下午好"
  return "晚上好"
})
const entries = computed(() => {
  const result = []
  if (roles.value.includes("expert")) {
    result.push(
      { title: "专家工作台", description: "查看档案完成度、资料审核进度和待办事项。", path: "/expert-center/workspace", icon: "Grid" },
      { title: "我的档案与时间表", description: "维护专家信息和每周可服务时间。", path: "/expert-center/profile", icon: "User" },
      { title: "资料文件", description: "上传证明材料并跟踪Agent处理。", path: "/expert-center/materials", icon: "Upload" }
    )
  }
  if (roles.value.includes("reviewer")) {
    result.push(
      { title: "审核工作台", description: "查看待审核规模和Agent异常任务。", path: "/review-center/workspace", icon: "DocumentChecked" },
      { title: "专家审核队列", description: "核验资料、字段映射并作出审核决定。", path: "/review-center/queue", icon: "Checked" },
      { title: "正式专家库", description: "查询已经审核入库的专家。", path: "/review-center/approved", icon: "Collection" }
    )
  }
  if (roles.value.includes("admin")) {
    result.push(
      { title: "管理控制台", description: "查看平台运行指标和Agent任务。", path: "/management-center", icon: "Monitor" },
      { title: "用户管理", description: "创建用户、分配专家或审核人员角色。", path: "/system/user", icon: "UserFilled" },
      { title: "角色权限", description: "配置角色菜单和操作权限。", path: "/system/role", icon: "Lock" }
    )
  }
  return result
})
</script>

<style scoped lang="scss">
.platform-home {
  .welcome {
    display: flex;
    justify-content: space-between;
    align-items: center;
    min-height: 230px;
    padding: 38px 42px;
    border-radius: 16px;
    color: #fff;
    background:
      radial-gradient(circle at 83% 22%, rgba(137, 211, 190, .28), transparent 30%),
      linear-gradient(120deg, #102c44, #20516b 62%, #34796f);
  }
  .welcome h1 {
    margin: 10px 0 14px;
    font-size: 34px;
  }
  .welcome p {
    max-width: 720px;
    margin: 0;
    color: rgba(255, 255, 255, .78);
    font-size: 15px;
    line-height: 1.8;
  }
  .welcome > div > span {
    font-size: 13px;
    letter-spacing: 1.5px;
    color: rgba(255, 255, 255, .68);
  }
  .role-mark {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 110px;
    height: 110px;
    border: 1px solid rgba(255, 255, 255, .35);
    border-radius: 50%;
    font-size: 20px;
    font-weight: 600;
    background: rgba(255, 255, 255, .08);
  }
  .entry-grid {
    margin-top: 18px;
  }
  .entry-card {
    height: 210px;
    margin-bottom: 16px;
    cursor: pointer;
    transition: transform .2s, box-shadow .2s;
    &:hover {
      transform: translateY(-3px);
      box-shadow: 0 12px 30px rgba(32, 65, 91, .12);
    }
    h3 {
      margin: 16px 0 10px;
      color: #203b51;
    }
    p {
      min-height: 48px;
      margin: 0 0 18px;
      color: #75838f;
      line-height: 1.7;
    }
    span {
      color: #2f7a70;
      font-size: 13px;
    }
  }
}
</style>
