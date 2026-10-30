<script setup lang="ts">
import FitShell from '../../components/FitShell.vue';
</script>
<template>
  <FitShell title="协议与隐私" back
    ><view class="title">用户协议与隐私政策</view>
    <view class="card"
      ><view>服务与使用</view
      ><view class="sub"
        >循形Fitness小程序用于训练和体重记录。动作说明仅供日常健身参考，请根据自身情况安排训练</view
      ></view
    >
    <view class="card"
      ><view>邮箱登录</view
      ><view class="sub"
        >我们向你主动填写的邮箱发送验证码，用于验证邮箱归属并登录。首次验证新邮箱后会创建账号；使用已有邮箱可直接访问该账号的记录。无需微信身份关联、手机号授权或阿里云人机校验。请勿向他人提供验证码</view
      ></view
    >
    <view class="card"
      ><view>信息保存与管理</view
      ><view class="sub"
        >服务器保存邮箱、验证码与会话凭据摘要，以及你提交的训练、体重和资料。使用请求 IP
        进行发送限流。设备本地保存会话及按账号隔离的训练草稿，以便切聊天或重新打开后恢复。你可以查看、修改、删除已保存的记录，并放弃本地草稿。退出会撤销当前会话，保留账号记录与本地草稿；共享设备使用后请清理小程序本地数据</view
      ></view
    >
    <view class="card"
      ><view>当前版本</view
      ><view class="sub"
        >页面内休息计时不提供后台通知。基础版暂不提供饮食 AI、身材照片或微信运动接入</view
      ></view
    >
  </FitShell>
</template>
