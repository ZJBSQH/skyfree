<script setup lang="ts">
import { ref } from 'vue'
import { LoaderCircle, Sparkles } from 'lucide-vue-next'
import type { NovelGenre, NovelLength, NovelRunInput, NovelStyle } from '../types/api'

const props = defineProps<{
  authenticated: boolean
  loading: boolean
}>()

const emit = defineEmits<{
  submit: [input: NovelRunInput]
}>()

const genres: NovelGenre[] = ['玄幻', '都市', '科幻', '悬疑', '仙侠', '奇幻']
const lengths: NovelLength[] = ['短篇', '中篇', '长篇开篇']
const styles: NovelStyle[] = ['热血', '轻松', '黑暗', '史诗', '爽文', '细腻']

const idea = ref('')
const genre = ref<NovelGenre>('玄幻')
const length = ref<NovelLength>('长篇开篇')
const style = ref<NovelStyle>('热血')
const validationMessage = ref('')

function submit() {
  if (!idea.value.trim()) {
    validationMessage.value = '请输入小说灵感'
    return
  }
  if (!props.authenticated) {
    validationMessage.value = '请先登录或注册后再开始创作'
    return
  }
  validationMessage.value = ''
  emit('submit', { idea: idea.value.trim(), genre: genre.value, length: length.value, style: style.value })
}
</script>

<template>
  <section class="panel creative-studio" aria-labelledby="creative-title">
    <div class="panel-heading">
      <div>
        <p class="eyebrow">新建作品</p>
        <h2 id="creative-title">创作设定</h2>
      </div>
    </div>
    <form class="creative-form" @submit.prevent="submit">
      <label for="novel-idea">小说灵感</label>
      <textarea
        id="novel-idea"
        v-model="idea"
        maxlength="2000"
        :disabled="loading"
        placeholder="例如：一个被宗门视为废物的少年，意外觉醒仇恨值系统"
        @input="validationMessage = ''"
      />
      <span class="field-counter">{{ idea.length }} / 2000</span>

      <div class="parameter-grid">
        <label>
          <span>小说类型</span>
          <select v-model="genre" :disabled="loading">
            <option v-for="item in genres" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
        <label>
          <span>篇幅倾向</span>
          <select v-model="length" :disabled="loading">
            <option v-for="item in lengths" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
        <label>
          <span>创作风格</span>
          <select v-model="style" :disabled="loading">
            <option v-for="item in styles" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
      </div>

      <p v-if="validationMessage" class="inline-error" role="alert">{{ validationMessage }}</p>
      <button class="button button--primary button--full button--large" type="submit" :disabled="loading" :aria-label="loading ? '创作中' : '开始创作'">
        <LoaderCircle v-if="loading" :size="18" class="spin" aria-hidden="true" />
        <Sparkles v-else :size="18" aria-hidden="true" />
        {{ loading ? '创作中' : '开始创作' }}
      </button>
      <p class="form-hint">六位 Agent 将依次完成设定、角色、剧情、写作与审核。</p>
    </form>
  </section>
</template>
