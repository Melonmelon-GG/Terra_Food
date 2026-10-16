<script setup lang="ts">
import axios from 'axios'
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { chatWithAgent, createFoodComment } from '../api'
import { useAuth } from '../auth'
import { currentFoodContextId } from '../foodContext'
import { getMusicTracks } from '../music'
import type { AgentCommentDraft, AgentFoodRecommendation } from '../types'

interface AgentMessage {
  id: number
  role: 'assistant' | 'user'
  content: string
  recommendations?: AgentFoodRecommendation[]
  commentDraft?: AgentCommentDraft
  commentPublished?: boolean
}

const props = defineProps<{ launcherVisible?: boolean }>()

const { t } = useI18n()
const auth = useAuth()
let active = true
const manifestController = new AbortController()
let chatController: AbortController | undefined
const open = ref(false)
const input = ref('')
const sending = ref(false)
const publishingCommentMessageId = ref<number>()
const error = ref('')
const availableTracks = ref<string[]>([])
const currentFoodId = currentFoodContextId
const messageList = ref<HTMLElement>()
const messages = ref<AgentMessage[]>([
  { id: 1, role: 'assistant', content: t('agent.greeting') },
])
let nextMessageId = 2

async function scrollToBottom() {
  await nextTick()
  messageList.value?.scrollTo({ top: messageList.value.scrollHeight, behavior: 'smooth' })
}

async function submit(messageOverride?: string) {
  const message = (messageOverride ?? input.value).trim()
  if (!message || sending.value || !auth.sessionConfirmed.value) return
  const revision = auth.getSessionRevision()
  const userId = auth.currentUser.value?.id
  const stillCurrent = () => active && revision === auth.getSessionRevision() && userId === auth.currentUser.value?.id

  messages.value.push({ id: nextMessageId++, role: 'user', content: message })
  input.value = ''
  sending.value = true
  error.value = ''
  await scrollToBottom()

  try {
    chatController = new AbortController()
    const response = await chatWithAgent({
      message,
      availableTracks: availableTracks.value,
      currentFoodId: currentFoodId.value,
    }, chatController.signal)
    if (!stillCurrent()) return
    messages.value.push({
      id: nextMessageId++,
      role: 'assistant',
      content: response.reply,
      recommendations: response.recommendations,
      commentDraft: response.commentDraft,
    })
    if (response.clientAction?.type === 'SWITCH_MUSIC') {
      window.dispatchEvent(new CustomEvent('agent:music-switch', {
        detail: { query: response.clientAction.query },
      }))
    } else if (response.clientAction?.type === 'COMMENT_PUBLISHED') {
      window.dispatchEvent(new CustomEvent('agent:comment-published', {
        detail: { foodId: Number(response.clientAction.query) },
      }))
    }
  } catch (requestError) {
    if (!stillCurrent() || axios.isCancel(requestError)) return
    error.value = axios.isAxiosError(requestError) && requestError.response?.status === 503
      ? t('agent.notConfigured')
      : t('agent.error')
  } finally {
    if (stillCurrent()) { sending.value = false; await scrollToBottom() }
  }
}

async function publishComment(message: AgentMessage) {
  const draft = message.commentDraft
  if (!draft || message.commentPublished || publishingCommentMessageId.value !== undefined || !auth.sessionConfirmed.value) return
  const revision = auth.getSessionRevision()
  const userId = auth.currentUser.value?.id
  const stillCurrent = () => active && revision === auth.getSessionRevision() && userId === auth.currentUser.value?.id

  publishingCommentMessageId.value = message.id
  error.value = ''
  try {
    await createFoodComment(draft.foodId, { content: draft.content })
    if (!stillCurrent()) return
    message.commentPublished = true
    window.dispatchEvent(new CustomEvent('agent:comment-published', {
      detail: { foodId: draft.foodId },
    }))
  } catch (requestError) {
    if (!stillCurrent()) return
    error.value = axios.isAxiosError(requestError)
      ? requestError.response?.data?.message || t('agent.commentPublishError')
      : t('agent.commentPublishError')
  } finally {
    if (stillCurrent()) { publishingCommentMessageId.value = undefined; await scrollToBottom() }
  }
}

onMounted(async () => {
  window.addEventListener('home:open-agent', openFromHome)
  try {
    const tracks = await getMusicTracks(manifestController.signal)
    if (!active) return
    availableTracks.value = tracks.map((track) =>
      track.artist ? `${track.name} — ${track.artist}` : track.name,
    )
  } catch {
    availableTracks.value = []
  }
})

function openFromHome() {
  if (!auth.sessionConfirmed.value) return
  open.value = true
}

onBeforeUnmount(() => {
  active = false
  manifestController.abort()
  chatController?.abort()
  window.removeEventListener('home:open-agent', openFromHome)
})
</script>

<template>
  <div class="agent-shell" :class="{ open }">
    <button
      v-if="!open && props.launcherVisible !== false"
      class="agent-orb"
      type="button"
      :aria-label="t('agent.open')"
      @click="open = true"
    >
      <span>余</span>
    </button>

    <section v-if="open" class="agent-panel" :aria-label="t('agent.title')">
      <header>
        <div class="agent-portrait" aria-hidden="true">余</div>
        <div>
          <strong>{{ t('agent.title') }}</strong>
          <small>{{ t('agent.subtitle') }}</small>
        </div>
        <button type="button" :aria-label="t('agent.close')" @click="open = false">×</button>
      </header>

      <div ref="messageList" class="agent-messages" aria-live="polite">
        <div
          v-for="message in messages"
          :key="message.id"
          class="agent-message"
          :class="`is-${message.role}`"
        >
          <span>{{ message.content }}</span>
          <nav v-if="message.recommendations?.length" class="agent-recommendations">
            <RouterLink
              v-for="food in message.recommendations"
              :key="food.id"
              class="agent-food-link"
              :to="`/foods/${food.id}`"
              @click="open = false"
            >
              <span>{{ food.name }}</span>
              <strong>{{ t('agent.viewFood') }}</strong>
            </RouterLink>
          </nav>
          <section v-if="message.commentDraft" class="agent-comment-draft">
            <small>{{ t('agent.commentDraftTitle', { name: message.commentDraft.foodName }) }}</small>
            <p>{{ message.commentDraft.content }}</p>
            <button
              type="button"
              :disabled="message.commentPublished || publishingCommentMessageId !== undefined"
              @click="publishComment(message)"
            >
              {{ message.commentPublished
                ? t('agent.commentPublished')
                : publishingCommentMessageId === message.id
                  ? t('agent.commentPublishing')
                  : t('agent.publishComment') }}
            </button>
          </section>
        </div>
        <div v-if="sending" class="agent-message is-assistant is-thinking">
          {{ t('agent.thinking') }}
        </div>
      </div>

      <div class="agent-quick-actions">
        <button type="button" :disabled="sending" @click="submit(t('agent.chatPrompt'))">
          {{ t('agent.chat') }}
        </button>
      </div>
      <p v-if="error" class="agent-error">{{ error }}</p>
      <form @submit.prevent="submit()">
        <textarea
          v-model="input"
          maxlength="1000"
          :placeholder="t('agent.placeholder')"
          @keydown.enter.exact.prevent="submit()"
        />
        <button type="submit" :disabled="sending || !input.trim()">
          {{ t('agent.send') }}
        </button>
      </form>
      <small class="agent-disclaimer">{{ t('agent.disclaimer') }}</small>
    </section>
  </div>
</template>
