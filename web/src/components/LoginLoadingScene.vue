<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{ progress: number; active: boolean }>()
const { t } = useI18n()
const canvas = ref<HTMLCanvasElement>()

interface SpherePoint {
  x: number
  y: number
  z: number
  size: number
}

interface ProjectedPoint extends SpherePoint {
  screenX: number
  screenY: number
  depth: number
}

const points: SpherePoint[] = []
const connections: Array<{ from: number; to: number; visible: boolean; phase: number; pulse: boolean }> = []
let animationFrame = 0
let resizeObserver: ResizeObserver | undefined
let motionPreference: MediaQueryList | undefined

// 固定种子的随机星群只生成一次，既避免规则网格，也避免切换页面时点阵跳动。
let seed = 73021
function random() {
  seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0
  return seed / 4294967296
}

const HUB_COUNT = 12
for (let index = 0; index < 720; index += 1) {
  let y = random() * 2 - 1
  const radius = Math.sqrt(1 - y * y)
  const angle = random() * Math.PI * 2
  let x = Math.cos(angle) * radius
  let z = Math.sin(angle) * radius
  if (index >= HUB_COUNT && random() < 0.22) {
    const hub = points[Math.floor(random() * HUB_COUNT)]
    x = hub.x + (random() - 0.5) * 0.48
    y = hub.y + (random() - 0.5) * 0.48
    z = hub.z + (random() - 0.5) * 0.48
  }
  const shell = (0.96 + random() * 0.04) / Math.hypot(x, y, z)
  points.push({ x: x * shell, y: y * shell, z: z * shell,
    size: index < HUB_COUNT ? 1.15 : 0.45 + random() * 0.45 })
}

const connectionKeys = new Set<string>()
function connect(from: number, to: number) {
  const key = `${Math.min(from, to)}:${Math.max(from, to)}`
  if (from === to || connectionKeys.has(key)) return
  connectionKeys.add(key)
  connections.push({ from, to, visible: random() < 0.24, phase: random(), pulse: random() < 0.3 })
}

points.forEach((point, index) => {
  const neighbors = points.map((other, otherIndex) => ({
    index: otherIndex,
    distance: (point.x - other.x) ** 2 + (point.y - other.y) ** 2 + (point.z - other.z) ** 2,
  })).filter((neighbor) => neighbor.index !== index && neighbor.distance < 0.15)
    .sort((a, b) => a.distance - b.distance)
  neighbors.slice(0, 2 + Math.floor(random() * 3)).forEach((neighbor) => connect(index, neighbor.index))
  // 少量跨区连接形成不对称枢纽，其余线路在光脉冲经过时才显现。
  if (index >= HUB_COUNT && random() < 0.22) connect(index, Math.floor(random() * HUB_COUNT))
})

const status = computed(() => {
  if (props.progress < 32) return t('login.preloadIdentity')
  if (props.progress < 82) return t('login.preloadFoods')
  if (props.progress < 100) return t('login.preloadSession')
  return t('login.preloadReady')
})

function render(time: number) {
  if (motionPreference?.matches) time = 0
  const element = canvas.value
  const context = element?.getContext('2d')
  if (!element || !context) return

  const width = element.clientWidth
  const height = element.clientHeight
  const pixelRatio = Math.min(window.devicePixelRatio || 1, 2)
  if (element.width !== Math.round(width * pixelRatio) || element.height !== Math.round(height * pixelRatio)) {
    element.width = Math.round(width * pixelRatio)
    element.height = Math.round(height * pixelRatio)
  }
  context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0)
  context.clearRect(0, 0, width, height)

  const centerX = width / 2
  const centerY = height / 2
  const sphereRadius = Math.min(width * 0.34, height * 0.4, 390)
  const rotation = time * 0.000075
  const tilt = -0.2
  const cosRotation = Math.cos(rotation)
  const sinRotation = Math.sin(rotation)
  const cosTilt = Math.cos(tilt)
  const sinTilt = Math.sin(tilt)

  const projected: ProjectedPoint[] = points.map((point) => {
    const rotatedX = point.x * cosRotation + point.z * sinRotation
    const rotatedZ = -point.x * sinRotation + point.z * cosRotation
    const rotatedY = point.y * cosTilt - rotatedZ * sinTilt
    const depth = point.y * sinTilt + rotatedZ * cosTilt
    const perspective = 1.72 / (2.72 - depth)
    return {
      size: point.size,
      x: rotatedX,
      y: rotatedY,
      z: depth,
      depth,
      screenX: centerX + rotatedX * sphereRadius * perspective,
      screenY: centerY + rotatedY * sphereRadius * perspective,
    }
  })

  const gold = props.active ? '247, 207, 107' : '169, 118, 26'
  connections.forEach((connection) => {
    const from = projected[connection.from]
    const to = projected[connection.to]
    const depthAlpha = Math.max(0.035, ((from.depth + to.depth) / 2 + 1.2) * 0.085)
    if (connection.visible) {
      context.lineWidth = 0.4
      context.strokeStyle = `rgba(${gold}, ${depthAlpha})`
      context.beginPath()
      context.moveTo(from.screenX, from.screenY)
      context.lineTo(to.screenX, to.screenY)
      context.stroke()
    }

    const cycle = (time * 0.000065 + connection.phase) % 1
    if (!connection.pulse || cycle >= 0.24) return
    const travel = cycle / 0.24
    const deltaX = to.screenX - from.screenX
    const deltaY = to.screenY - from.screenY
    const distance = Math.hypot(deltaX, deltaY)
    if (distance < 1) return
    const tail = Math.max(0, travel - Math.min(0.3, 18 / distance))
    const pulseX = from.screenX + deltaX * travel
    const pulseY = from.screenY + deltaY * travel
    const tailX = from.screenX + deltaX * tail
    const tailY = from.screenY + deltaY * tail
    const intensity = Math.sin(Math.PI * travel) * Math.min(0.95, 0.65 + depthAlpha * 3)
    // 只点亮光点身后的短线路，不使用原来的大面积模糊光晕。
    const trail = context.createLinearGradient(tailX, tailY, pulseX, pulseY)
    trail.addColorStop(0, `rgba(${gold}, 0)`)
    trail.addColorStop(1, `rgba(${gold}, ${intensity})`)
    context.lineWidth = 0.65
    context.strokeStyle = trail
    context.beginPath()
    context.moveTo(tailX, tailY)
    context.lineTo(pulseX, pulseY)
    context.stroke()
    context.fillStyle = `rgba(${props.active ? '255, 237, 165' : gold}, ${intensity})`
    context.beginPath()
    context.arc(pulseX, pulseY, 0.85, 0, Math.PI * 2)
    context.fill()
  })

  projected
    .slice()
    .sort((a, b) => a.depth - b.depth)
    .forEach((point) => {
      const alpha = Math.max(0.3, Math.min(0.95, (point.depth + 1.3) * 0.55))
      const dotRadius = point.size * (0.7 + (point.depth + 1) * 0.25)
      context.fillStyle = `rgba(${gold}, ${alpha})`
      context.beginPath()
      context.arc(point.screenX, point.screenY, dotRadius, 0, Math.PI * 2)
      context.fill()
    })

  if (!motionPreference?.matches) animationFrame = requestAnimationFrame(render)
}

function restartAnimation() {
  cancelAnimationFrame(animationFrame)
  animationFrame = requestAnimationFrame(render)
}

onMounted(() => {
  motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
  motionPreference.addEventListener('change', restartAnimation)
  if (canvas.value) {
    resizeObserver = new ResizeObserver(() => {
      if (motionPreference?.matches) restartAnimation()
    })
    resizeObserver.observe(canvas.value)
  }
  animationFrame = requestAnimationFrame(render)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(animationFrame)
  resizeObserver?.disconnect()
  motionPreference?.removeEventListener('change', restartAnimation)
})
</script>

<template>
    <section
      class="login-loading-scene"
      :class="{ 'is-active': active }"
      :role="active ? 'status' : undefined"
      :aria-hidden="!active"
      aria-live="polite"
    >
      <div class="login-loading-grain" />
      <canvas ref="canvas" class="login-loading-network" aria-hidden="true" />

      <div class="login-loading-server">
        <span class="login-loading-seal" aria-hidden="true">炎</span>
        <small>{{ t('login.loadingServer') }}</small>
        <strong>{{ t('login.loadingServerName') }}</strong>
        <b>#1</b>
      </div>

      <div class="login-loading-progress">
        <div class="login-loading-track" aria-hidden="true">
          <i :style="{ width: `${progress}%` }" />
        </div>
        <div class="login-loading-meta">
          <span>{{ status }}</span>
          <b>{{ Math.round(progress) }}%</b>
        </div>
      </div>

      <footer>
        <span class="login-loading-footer-seal">炎</span>
        <div>
          <b>{{ t('common.appName') }}</b>
          <small>{{ t('common.tagline') }}</small>
        </div>
      </footer>
    </section>
</template>

<style scoped>
.login-loading-scene {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  color: #fff8ed;
}

.login-loading-scene.is-active {
  z-index: 4;
  pointer-events: auto;
}

.login-loading-scene::before {
  position: absolute;
  inset: 0;
  content: '';
  opacity: 0;
  background:
    radial-gradient(circle at 50% 42%, rgba(99, 43, 29, 0.48), transparent 30%),
    radial-gradient(circle at 50% 45%, #291812 0%, #120d0b 45%, #080706 100%);
  transition: opacity 1100ms ease;
}

.login-loading-scene.is-active::before {
  opacity: 1;
}

.login-loading-grain {
  position: absolute;
  inset: 0;
  opacity: 0.18;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.018) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(circle, #000 15%, transparent 78%);
}

.login-loading-network {
  position: absolute;
  inset: 4% 0 13%;
  width: 100%;
  height: 83%;
  /* 初始仅露出球体下半部；变换复用画布，旋转与光点相位不会重置。 */
  transform: translateY(-65%) scale(1.55);
  transform-origin: center;
  transition: transform 1400ms cubic-bezier(0.45, 0, 0.16, 1);
}

.is-active .login-loading-network {
  transform: translateY(-4%) scale(1);
}

.login-loading-server,
.login-loading-progress,
.login-loading-scene footer {
  opacity: 0;
  transition: opacity 300ms ease;
}

.is-active .login-loading-server,
.is-active .login-loading-progress,
.login-loading-scene.is-active footer {
  opacity: 1;
  transition: opacity 700ms ease 650ms;
}

.login-loading-server {
  position: absolute;
  top: 42%;
  left: 50%;
  display: grid;
  place-items: center;
  width: clamp(154px, 17vw, 214px);
  min-height: clamp(210px, 28vh, 292px);
  padding: 24px;
  border: 1px solid rgba(239, 213, 183, 0.72);
  background: rgba(19, 13, 11, 0.68);
  box-shadow: 0 0 55px rgba(218, 148, 49, 0.14), inset 0 0 30px rgba(255, 241, 213, 0.04);
  backdrop-filter: blur(5px);
  transform: translate(-50%, -50%);
}

.login-loading-server::before,
.login-loading-server::after {
  position: absolute;
  width: 17px;
  height: 17px;
  content: '';
  border-color: #e6b043;
}

.login-loading-server::before {
  top: -1px;
  left: -1px;
  border-top: 3px solid;
  border-left: 3px solid;
}

.login-loading-server::after {
  right: -1px;
  bottom: -1px;
  border-right: 3px solid;
  border-bottom: 3px solid;
}

.login-loading-seal {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  color: #f1c45f;
  font-family: serif;
  font-size: 31px;
  border: 1px solid rgba(241, 196, 95, 0.75);
}

.login-loading-server small {
  color: #c9ad90;
  font-size: 12px;
  letter-spacing: 0.28em;
}

.login-loading-server strong {
  font-family: serif;
  font-size: clamp(28px, 3vw, 40px);
  font-weight: 500;
  letter-spacing: 0.12em;
}

.login-loading-server b {
  font-size: clamp(52px, 7vw, 88px);
  font-weight: 300;
  line-height: 0.85;
}

.login-loading-progress {
  position: absolute;
  right: clamp(24px, 8vw, 132px);
  bottom: clamp(92px, 13vh, 142px);
  left: clamp(24px, 8vw, 132px);
}

.login-loading-track {
  height: 3px;
  overflow: hidden;
  background: rgba(239, 213, 183, 0.18);
}

.login-loading-track i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #a75b42, #f0bd4f 80%, #fff2b8);
  box-shadow: 0 0 15px #e6b043;
  transition: width 180ms ease-out;
}

.login-loading-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  color: #d9c6b3;
  font-size: 12px;
  letter-spacing: 0.12em;
}

.login-loading-meta b {
  color: #f0bd4f;
  font-size: 15px;
}

.login-loading-scene footer {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  gap: 13px;
  min-height: 70px;
  padding: 12px clamp(24px, 4vw, 64px);
  border-top: 1px solid rgba(239, 213, 183, 0.08);
  background: rgba(4, 3, 3, 0.2);
}

.login-loading-scene footer div {
  display: grid;
  gap: 2px;
}

.login-loading-scene footer small {
  color: #a58b76;
  font-size: 10px;
}

.login-loading-footer-seal {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  color: #f0bd4f;
  font-family: serif;
  font-size: 23px;
  border: 1px solid #c79342;
}

@media (max-width: 640px) {
  .login-loading-network {
    inset: 3% -35% 17%;
    width: 170%;
  }

  .login-loading-server {
    top: 40%;
  }

  .login-loading-progress {
    bottom: 98px;
  }

  .login-loading-meta span {
    max-width: 72%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-loading-network,
  .login-loading-scene::before,
  .login-loading-server,
  .login-loading-progress,
  .login-loading-scene footer,
  .login-loading-scene.is-active .login-loading-server,
  .login-loading-scene.is-active .login-loading-progress,
  .login-loading-scene.is-active footer {
    transition: none;
  }
  .login-loading-track i {
    transition: none;
  }
}
</style>
