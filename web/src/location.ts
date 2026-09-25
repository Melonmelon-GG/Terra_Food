import type { MapCoordinate } from './types'

export function validCoordinate(latitude: unknown, longitude: unknown): boolean {
  return typeof latitude === 'number' && typeof longitude === 'number'
    && Number.isFinite(latitude) && Number.isFinite(longitude)
    && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
}

// Browser permission prompts may outlive the browser's position timeout. Bound
// the entire UI operation and ignore late callbacks after manual entry/unmount.
export function requestBrowserLocation(
  success: (coordinate: MapCoordinate, accuracy: number) => void,
  failure: (messageKey: string) => void,
): () => void {
  let settled = false
  let timer: ReturnType<typeof setTimeout> | undefined
  const cancel = () => { settled = true; if (timer) clearTimeout(timer) }
  const fail = (key: string) => { if (!settled) { cancel(); failure(key) } }
  if (!window.isSecureContext) { fail('home.geolocationInsecure'); return cancel }
  if (!navigator.geolocation) { fail('home.geolocationUnsupported'); return cancel }
  timer = setTimeout(() => fail('home.geolocationTimeout'), 12_000)
  try {
    navigator.geolocation.getCurrentPosition(position => {
      if (settled) return
      const { latitude, longitude, accuracy } = position.coords
      if (!validCoordinate(latitude, longitude)) { fail('home.geolocationUnavailable'); return }
      if (latitude < 18 || latitude > 54 || longitude < 73 || longitude > 135.2) {
        fail('home.geolocationOutside'); return
      }
      cancel()
      success({ latitude, longitude }, accuracy)
    }, error => fail(error.code === 1 ? 'home.geolocationDenied'
      : error.code === 3 ? 'home.geolocationTimeout' : 'home.geolocationUnavailable'), {
      enableHighAccuracy: false, timeout: 10_000, maximumAge: 60_000,
    })
  } catch { fail('home.geolocationUnavailable') }
  return cancel
}
