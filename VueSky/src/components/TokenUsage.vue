<script setup lang="ts">
import { Coins } from 'lucide-vue-next'
import type { TokenUsage } from '../types/api'

defineProps<{
  usage: TokenUsage | null
}>()

function number(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}
</script>

<template>
  <section class="panel detail-panel" aria-labelledby="usage-title">
    <div class="panel-heading panel-heading--compact">
      <div>
        <p class="eyebrow">模型消耗</p>
        <h2 id="usage-title">Token 用量</h2>
      </div>
      <Coins :size="19" aria-hidden="true" />
    </div>
    <p v-if="!usage" class="muted-copy">运行后统计 Token 与模型调用。</p>
    <dl v-else class="usage-grid">
      <div class="usage-total"><dt>总 Token</dt><dd>{{ number(usage.total_tokens) }}</dd></div>
      <div><dt>输入</dt><dd>{{ number(usage.input_tokens) }}</dd></div>
      <div><dt>输出</dt><dd>{{ number(usage.output_tokens) }}</dd></div>
      <div><dt>调用次数</dt><dd>{{ number(usage.call_count) }}</dd></div>
      <div><dt>预估成本</dt><dd>¥{{ usage.cost_yuan.toFixed(4) }}</dd></div>
      <div><dt>模型</dt><dd>{{ usage.model }}</dd></div>
    </dl>
  </section>
</template>
